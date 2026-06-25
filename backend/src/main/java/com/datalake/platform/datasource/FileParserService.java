package com.datalake.platform.datasource;

import com.datalake.platform.common.util.SqlNameUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
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
        String filename = requireFilename(file.getOriginalFilename());
        try (InputStream inputStream = file.getInputStream()) {
            return parse(inputStream, filename, defaultParseOptions());
        }
    }

    public ParsedFile parse(Path path, String originalFilename, String formatType) throws IOException {
        return parse(path, originalFilename, formatType, defaultParseOptions());
    }

    public ParsedFile parse(Path path, String originalFilename, String formatType, ParseOptions options) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return parse(
                inputStream,
                originalFilename == null ? "unknown." + formatType.toLowerCase() : originalFilename,
                options == null ? defaultParseOptions() : options
            );
        }
    }

    private ParsedFile parse(InputStream inputStream, String filename, ParseOptions options) throws IOException {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".csv")) {
            return parseCsv(inputStream, options);
        }
        if (lower.endsWith(".json")) {
            return parseJson(inputStream);
        }
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) {
            return parseExcel(inputStream, options);
        }
        throw new IllegalArgumentException("暂不支持的文件格式: " + filename);
    }

    private ParsedFile parseCsv(InputStream inputStream, ParseOptions options) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, resolveCharset(options)))) {
            List<String[]> values = reader.lines()
                .filter(line -> !line.isBlank())
                .map(line -> line.split(",", -1))
                .toList();
            if (values.isEmpty()) {
                throw new IllegalArgumentException("CSV 文件为空");
            }
            boolean headerRow = options == null || options.headerRow();
            String[] headers = headerRow ? values.get(0) : buildSyntheticHeaders(maxColumnCount(values));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = headerRow ? 1 : 0; i < values.size(); i++) {
                String[] rowValues = values.get(i);
                Map<String, Object> row = new LinkedHashMap<>();
                for (int j = 0; j < headers.length; j++) {
                    String header = headers[j].trim();
                    row.put(header.isBlank() ? "column_" + (j + 1) : header, j < rowValues.length ? rowValues[j].trim() : "");
                }
                rows.add(row);
            }
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("CSV 数据为空");
            }
            return new ParsedFile("CSV", inferColumns(rows), rows);
        }
    }

    private ParsedFile parseJson(InputStream inputStream) throws IOException {
        List<LinkedHashMap<String, Object>> rawRows = objectMapper.readValue(
            inputStream,
            new TypeReference<List<LinkedHashMap<String, Object>>>() {
            }
        );
        List<Map<String, Object>> rows = new ArrayList<>(rawRows);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("JSON 数据为空");
        }
        return new ParsedFile("JSON", inferColumns(rows), rows);
    }

    private ParsedFile parseExcel(InputStream inputStream, ParseOptions options) throws IOException {
        try (var workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            List<String> headers = new ArrayList<>();
            boolean headerRow = options == null || options.headerRow();
            int startRowNum = sheet.getFirstRowNum();
            if (headerRow) {
                Row actualHeaderRow = sheet.getRow(sheet.getFirstRowNum());
                if (actualHeaderRow == null) {
                    throw new IllegalArgumentException("Excel 表头为空");
                }
                actualHeaderRow.forEach(cell -> headers.add(formatter.formatCellValue(cell).trim()));
                startRowNum = sheet.getFirstRowNum() + 1;
            } else {
                int maxColumns = 0;
                for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row != null) {
                        maxColumns = Math.max(maxColumns, Math.max(row.getLastCellNum(), 0));
                    }
                }
                if (maxColumns <= 0) {
                    throw new IllegalArgumentException("Excel 数据为空");
                }
                for (String header : buildSyntheticHeaders(maxColumns)) {
                    headers.add(header);
                }
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = startRowNum; i <= sheet.getLastRowNum(); i++) {
                Row rowData = sheet.getRow(i);
                if (rowData == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                boolean hasValue = false;
                for (int j = 0; j < headers.size(); j++) {
                    String value = formatter.formatCellValue(rowData.getCell(j)).trim();
                    if (!value.isBlank()) {
                        hasValue = true;
                    }
                    row.put(headers.get(j), value);
                }
                if (hasValue) {
                    rows.add(row);
                }
            }
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("Excel 数据为空");
            }
            return new ParsedFile("EXCEL", inferColumns(rows), rows);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Excel 解析失败: " + exception.getMessage(), exception);
        }
    }

    private List<ParsedColumn> inferColumns(List<Map<String, Object>> rows) {
        Map<String, Object> sample = rows.get(0);
        List<ParsedColumn> columns = new ArrayList<>();
        int order = 0;
        for (String fieldName : sample.keySet()) {
            Object sampleValue = rows.stream()
                .map(item -> item.get(fieldName))
                .filter(Objects::nonNull)
                .filter(value -> !String.valueOf(value).isBlank())
                .findFirst()
                .orElse("");
            boolean nullable = rows.stream().anyMatch(item -> item.get(fieldName) == null || String.valueOf(item.get(fieldName)).isBlank());
            columns.add(new ParsedColumn(
                fieldName,
                SqlNameUtils.sanitizeColumnName(fieldName),
                inferType(sampleValue),
                nullable,
                String.valueOf(sampleValue),
                order
            ));
            order += 1;
        }
        return columns;
    }

    private String inferType(Object value) {
        if (value instanceof Boolean) {
            return "BOOLEAN";
        }
        if (value instanceof Integer || value instanceof Long) {
            return "BIGINT";
        }
        if (value instanceof Double || value instanceof Float) {
            return "DOUBLE";
        }
        String text = String.valueOf(value);
        if (text.matches("^-?\\d+$")) {
            return "BIGINT";
        }
        if (text.matches("^-?\\d+\\.\\d+$")) {
            return "DOUBLE";
        }
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return "BOOLEAN";
        }
        return "STRING";
    }

    private String requireFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        return filename;
    }

    private ParseOptions defaultParseOptions() {
        return new ParseOptions(StandardCharsets.UTF_8.name(), true);
    }

    private Charset resolveCharset(ParseOptions options) {
        String encoding = options == null || options.encoding() == null || options.encoding().isBlank()
            ? StandardCharsets.UTF_8.name()
            : options.encoding().trim();
        try {
            return Charset.forName(encoding);
        } catch (Exception exception) {
            throw new IllegalArgumentException("不支持的编码方式: " + encoding, exception);
        }
    }

    private int maxColumnCount(List<String[]> values) {
        int max = 0;
        for (String[] row : values) {
            max = Math.max(max, row.length);
        }
        return max;
    }

    private String[] buildSyntheticHeaders(int columnCount) {
        String[] headers = new String[columnCount];
        for (int index = 0; index < columnCount; index++) {
            headers[index] = "column_" + (index + 1);
        }
        return headers;
    }

    public record ParsedFile(String formatType, List<ParsedColumn> columns, List<Map<String, Object>> rows) {
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

    public record ParseOptions(String encoding, boolean headerRow) {
    }
}
