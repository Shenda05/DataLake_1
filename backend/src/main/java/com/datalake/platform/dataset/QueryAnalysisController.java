package com.datalake.platform.dataset;

import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QueryAnalysisController {

    private final QueryAnalysisService queryAnalysisService;

    public QueryAnalysisController(QueryAnalysisService queryAnalysisService) {
        this.queryAnalysisService = queryAnalysisService;
    }

    @PostMapping("/api/queries/filter")
    public ApiResponse<?> filter(@Valid @RequestBody FilterQueryRequest body, HttpServletRequest request) {
        return ApiResponse.success(queryAnalysisService.filter(new QueryAnalysisService.FilterQueryRequest(
            body.datasetId(),
            body.field(),
            body.operator(),
            body.value(),
            body.pageNum(),
            body.pageSize()
        )), requestId(request));
    }

    @PostMapping("/api/queries/sql")
    public ApiResponse<?> sql(@Valid @RequestBody SqlQueryRequest body, HttpServletRequest request) {
        return ApiResponse.success(queryAnalysisService.sql(new QueryAnalysisService.SqlQueryRequest(body.datasetId(), body.sql())), requestId(request));
    }

    @PostMapping("/api/queries/filter/export")
    @PreAuthorize("hasAuthority('ACTION_query.export')")
    public ResponseEntity<byte[]> exportFilter(
        @Valid @RequestBody FilterQueryRequest body,
        @RequestParam(defaultValue = "csv") String format
    ) {
        TabularExportService.ExportedFile export = queryAnalysisService.exportFilter(
            new QueryAnalysisService.FilterQueryRequest(body.datasetId(), body.field(), body.operator(), body.value(), body.pageNum(), body.pageSize()),
            format
        );
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, export.contentType())
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(export.fileName(), StandardCharsets.UTF_8))
            .body(export.content());
    }

    @PostMapping("/api/queries/sql/export")
    @PreAuthorize("hasAuthority('ACTION_query.export')")
    public ResponseEntity<byte[]> exportSql(
        @Valid @RequestBody SqlQueryRequest body,
        @RequestParam(defaultValue = "csv") String format
    ) {
        TabularExportService.ExportedFile export = queryAnalysisService.exportSql(new QueryAnalysisService.SqlQueryRequest(body.datasetId(), body.sql()), format);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, export.contentType())
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(export.fileName(), StandardCharsets.UTF_8))
            .body(export.content());
    }

    @GetMapping("/api/analysis/{datasetId}/summary")
    public ApiResponse<?> summary(@PathVariable Long datasetId, HttpServletRequest request) {
        return ApiResponse.success(queryAnalysisService.summary(datasetId), requestId(request));
    }

    @GetMapping("/api/analysis/{datasetId}/charts")
    public ApiResponse<?> charts(@PathVariable Long datasetId, HttpServletRequest request) {
        return ApiResponse.success(queryAnalysisService.charts(datasetId), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString();
    }

    public record FilterQueryRequest(
        @NotNull(message = "datasetId 不能为空") Long datasetId,
        @NotBlank(message = "field 不能为空") String field,
        @NotBlank(message = "operator 不能为空") String operator,
        @NotBlank(message = "value 不能为空") String value,
        Integer pageNum,
        Integer pageSize
    ) {
    }

    public record SqlQueryRequest(
        @NotNull(message = "datasetId 不能为空") Long datasetId,
        @NotBlank(message = "sql 不能为空") String sql
    ) {
    }
}
