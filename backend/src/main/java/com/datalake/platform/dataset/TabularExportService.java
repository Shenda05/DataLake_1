package com.datalake.platform.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class TabularExportService {

    private final ObjectMapper objectMapper;

    public TabularExportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ExportedFile export(String baseName, List<String> headers, List<Map<String, Object>> rows, String format) {
        String normalizedFormat = format == null ? "csv" : format.toLowerCase();
        return switch (normalizedFormat) {
            case "json" -> exportJson(baseName, rows);
            case "xlsx" -> exportExcel(baseName, headers, rows);
            case "csv" -> exportCsv(baseName, headers, rows);
            default -> throw new IllegalArgumentException("仅支持导出 csv、json 或 xlsx");
        };
    }

    private ExportedFile exportCsv(String baseName, List<String> headers, List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append(String.join(",", headers)).append('\n');
        for (Map<String, Object> row : rows) {
            List<String> values = new ArrayList<>();
            for (String header : headers) {
                Object value = row.get(header);
                values.add(csvEscape(value == null ? "" : String.valueOf(value)));
            }
            builder.append(String.join(",", values)).append('\n');
        }
        return new ExportedFile(baseName + ".csv", "text/csv;charset=UTF-8", builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    private ExportedFile exportJson(String baseName, List<Map<String, Object>> rows) {
        try {
            return new ExportedFile(
                baseName + ".json",
                "application/json",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(rows)
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("JSON 导出失败: " + exception.getMessage(), exception);
        }
    }

    private ExportedFile exportExcel(String baseName, List<String> headers, List<Map<String, Object>> rows) {
        try (var workbook = new XSSFWorkbook(); var outputStream = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("data");
            var headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                var row = sheet.createRow(rowIndex + 1);
                Map<String, Object> values = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                    writeCell(row.createCell(columnIndex), values.get(headers.get(columnIndex)));
                }
            }
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(outputStream);
            return new ExportedFile(
                baseName + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                outputStream.toByteArray()
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("Excel 导出失败: " + exception.getMessage(), exception);
        }
    }

    private void writeCell(org.apache.poi.ss.usermodel.Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    private String csvEscape(String value) {
        String escaped = value.replace("\"", "\"\"");
        return escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") ? "\"" + escaped + "\"" : escaped;
    }

    public record ExportedFile(String fileName, String contentType, byte[] content) {
    }
}
