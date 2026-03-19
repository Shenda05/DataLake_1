package com.datalake.platform.datasource;

import com.datalake.platform.common.util.SqlNameUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileParserService {

    private final ObjectMapper objectMapper;

    public FileParserService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedFile parse(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String format = detectFormat(filename);
        try (InputStream inputStream = file.getInputStream()) {
            return parse(inputStream, filename, format);
        }
    }

    public ParsedFile parse(Path filePath, String originalFileName, String formatType) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return parse(inputStream, originalFileName, formatType);
        }
    }

    private ParsedFile parse(InputStream inputStream, String filename, String formatType) throws IOException {
        List<Map<String, Object>> rows = switch (formatType.toUpperCase(Locale.ROOT)) {
            case "CSV" -> parseCsv(inputStream);
            case "JSON" -> parseJson(inputStream);
            case "EXCEL" -> parseExcel(inputStream);
            default -> throw new IllegalArgumentException("暂不支持的文件格式: " + formatType);
        };
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("导入文件没有可用数据");
        }
        List<ParsedColumn> columns = inferColumns(rows);
        return new ParsedFile(filename, formatType, columns, rows);
    }

    private List<Map<String, Object>> parseCsv(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            List<String> lines = reader.lines().filter(line -> !line.isBlank()).toList();
            if (lines.isEmpty()) {
                return List.of();
            }
            String[] headers = splitCsvLine(lines.get(0));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 1; i < lines.size(); i += 1) {
                String[] values = splitCsvLine(lines.get(i));
                Map<String, Object> row = new LinkedHashMap<>();
                for (int j = 0; j < headers.length; j += 1) {
                    row.put(headers[j].trim(), j < values.length ? normalizeValue(values[j]) : null);
                }
                rows.add(row);
            }
            return rows;
        }
    }

    private List<Map<String, Object>> parseJson(InputStream inputStream) throws IOException {
        List<Map<String, Object>> rows = objectMapper.readValue(inputStream, new TypeReference<>() {
        });
        return rows.stream()
            .map(row -> row.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> normalizeValue(entry.getValue()),
                (left, right) -> right,
                LinkedHashMap::new
            )))
            .map(row -> (Map<String, Object>) row)
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> parseExcel(InputStream inputStream) throws IOException {
        try (var workbook = WorkbookFactory.create(inputStream)) {
            var sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                return List.of();
            }
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(formatter.formatCellValue(cell).trim());
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i += 1) {
                Row rowRef = sheet.getRow(i);
                if (rowRef == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                boolean hasValue = false;
                for (int j = 0; j < headers.size(); j += 1) {
                    Cell cell = rowRef.getCell(j);
                    String value = cell == null ? null : formatter.formatCellValue(cell);
                    Object normalized = normalizeValue(value);
                    if (normalized != null && !normalized.toString().isBlank()) {
                        hasValue = true;
                    }
                    row.put(headers.get(j), normalized);
                }
                if (hasValue) {
                    rows.add(row);
                }
            }
            return rows;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Excel 解析失败: " + exception.getMessage(), exception);
        }
    }

    private List<ParsedColumn> inferColumns(List<Map<String, Object>> rows) {
        List<String> fields = new ArrayList<>(rows.get(0).keySet());
        Set<String> usedNames = SqlNameUtils.newNameSet();
        List<ParsedColumn> columns = new ArrayList<>();
        for (int i = 0; i < fields.size(); i += 1) {
            String fieldName = fields.get(i);
            List<Object> values = rows.stream().map(row -> row.get(fieldName)).toList();
            String physicalName = SqlNameUtils.ensureUnique(SqlNameUtils.sanitizeColumnName(fieldName), usedNames);
            columns.add(new ParsedColumn(
                fieldName,
                physicalName,
                inferType(values),
                values.stream().anyMatch(value -> value == null || value.toString().isBlank()),
                values.stream().filter(value -> value != null && !value.toString().isBlank()).findFirst().map(Object::toString).orElse(""),
                i
            ));
        }
        return columns;
    }

    private String inferType(List<Object> values) {
        List<String> nonEmpty = values.stream()
            .filter(value -> value != null && !value.toString().isBlank())
            .map(Object::toString)
            .toList();
        if (nonEmpty.isEmpty()) {
            return "VARCHAR";
        }
        if (nonEmpty.stream().allMatch(this::isInteger)) {
            return "BIGINT";
        }
        if (nonEmpty.stream().allMatch(this::isNumber)) {
            return "DOUBLE";
        }
        if (nonEmpty.stream().allMatch(this::isBoolean)) {
            return "BOOLEAN";
        }
        return "VARCHAR";
    }

    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        String raw = value.toString().trim();
        if (raw.isBlank()) {
            return null;
        }
        if (isInteger(raw)) {
            return Long.parseLong(raw);
        }
        if (isNumber(raw)) {
            return Double.parseDouble(raw);
        }
        if (isBoolean(raw)) {
            return Boolean.parseBoolean(raw.toLowerCase(Locale.ROOT));
        }
        return raw;
    }

    private boolean isInteger(String value) {
        return value.matches("^-?\\d+$");
    }

    private boolean isNumber(String value) {
        return value.matches("^-?\\d+(\\.\\d+)?$");
    }

    private boolean isBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }

    private String detectFormat(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return "CSV";
        }
        if (lower.endsWith(".json")) {
            return "JSON";
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return "EXCEL";
        }
        throw new IllegalArgumentException("仅支持 CSV、JSON、Excel 文件");
    }

    private String[] splitCsvLine(String line) {
        return line.split(",", -1);
    }

    public record ParsedFile(String filename, String formatType, List<ParsedColumn> columns, List<Map<String, Object>> rows) {
    }

    public record ParsedColumn(
        String fieldName,
        String physicalColumnName,
        String fieldType,
        boolean nullable,
        String sampleValue,
        int fieldOrder
    ) {
    }
}
