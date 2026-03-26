package com.datalake.platform.dataset;

import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @GetMapping("/api/datasets")
    public ApiResponse<?> list(HttpServletRequest request) {
        return ApiResponse.success(datasetService.list(), requestId(request));
    }

    @GetMapping("/api/datasets/{datasetId}")
    public ApiResponse<?> detail(@PathVariable Long datasetId, HttpServletRequest request) {
        return ApiResponse.success(datasetService.detail(datasetId), requestId(request));
    }

    @DeleteMapping("/api/datasets/{datasetId}")
    @PreAuthorize("hasAuthority('ACTION_dataset.delete')")
    public ApiResponse<Void> delete(@PathVariable Long datasetId, HttpServletRequest request) {
        datasetService.delete(datasetId);
        return ApiResponse.success(requestId(request));
    }

    @GetMapping("/api/metadata/{datasetId}")
    public ApiResponse<?> metadata(@PathVariable Long datasetId, HttpServletRequest request) {
        return ApiResponse.success(datasetService.metadata(datasetId), requestId(request));
    }

    @GetMapping("/api/preview/{datasetId}")
    public ApiResponse<?> preview(
        @PathVariable Long datasetId,
        @RequestParam(defaultValue = "1") int pageNum,
        @RequestParam(defaultValue = "10") int pageSize,
        @RequestParam(required = false) String field,
        @RequestParam(required = false) String keyword,
        HttpServletRequest request
    ) {
        return ApiResponse.success(datasetService.preview(datasetId, pageNum, pageSize, field, keyword), requestId(request));
    }

    @GetMapping("/api/datasets/{datasetId}/export")
    public ResponseEntity<byte[]> export(
        @PathVariable Long datasetId,
        @RequestParam(defaultValue = "csv") String format,
        @RequestParam(required = false) String field,
        @RequestParam(required = false) String keyword
    ) {
        TabularExportService.ExportedFile export = datasetService.export(datasetId, format, field, keyword);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, export.contentType())
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(export.fileName(), StandardCharsets.UTF_8))
            .body(export.content());
    }

    private String requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString();
    }
}
