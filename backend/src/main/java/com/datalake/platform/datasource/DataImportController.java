package com.datalake.platform.datasource;

import com.datalake.platform.common.security.SecurityUtils;
import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
public class DataImportController {

    private final DataImportService dataImportService;

    public DataImportController(DataImportService dataImportService) {
        this.dataImportService = dataImportService;
    }

    @PostMapping("/file")
    public ApiResponse<?> importFile(
        @RequestParam("file") MultipartFile file,
        @RequestParam("datasetName") @NotBlank String datasetName,
        @RequestParam(value = "businessDomain", required = false) String businessDomain,
        @RequestParam("sourceId") Long sourceId,
        HttpServletRequest request
    ) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        try {
            return ApiResponse.success(
                dataImportService.importFile(file, datasetName, businessDomain, sourceId, SecurityUtils.currentUser().userId()),
                requestId(request)
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("文件导入失败: " + exception.getMessage(), exception);
        }
    }

    @PostMapping("/database")
    @PreAuthorize("hasAuthority('ACTION_import.database')")
    public ApiResponse<?> importDatabase(
        @Valid @RequestBody DatabaseImportRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            dataImportService.importDatabaseTable(
                body.sourceId(),
                body.schemaName(),
                body.tableName(),
                body.datasetName(),
                body.businessDomain(),
                body.description(),
                SecurityUtils.currentUser().userId()
            ),
            requestId(request)
        );
    }

    @GetMapping("/database/tables")
    @PreAuthorize("hasAuthority('ACTION_import.database')")
    public ApiResponse<?> listDatabaseTables(
        @RequestParam("sourceId") Long sourceId,
        @RequestParam(value = "schemaName", required = false) String schemaName,
        HttpServletRequest request
    ) {
        return ApiResponse.success(dataImportService.listDatabaseTables(sourceId, schemaName), requestId(request));
    }

    @GetMapping("/database/schemas")
    @PreAuthorize("hasAuthority('ACTION_import.database')")
    public ApiResponse<?> listDatabaseSchemas(
        @RequestParam("sourceId") Long sourceId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(dataImportService.listDatabaseSchemas(sourceId), requestId(request));
    }

    @GetMapping("/database/preview")
    @PreAuthorize("hasAuthority('ACTION_import.database')")
    public ApiResponse<?> previewDatabaseTable(
        @RequestParam("sourceId") Long sourceId,
        @RequestParam(value = "schemaName", required = false) String schemaName,
        @RequestParam("tableName") String tableName,
        @RequestParam(value = "limit", required = false) Integer limit,
        HttpServletRequest request
    ) {
        return ApiResponse.success(dataImportService.previewDatabaseTable(sourceId, schemaName, tableName, limit), requestId(request));
    }

    @GetMapping("/history")
    public ApiResponse<?> history(
        @RequestParam(value = "businessDomain", required = false) String businessDomain,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "startTime", required = false) String startTime,
        @RequestParam(value = "endTime", required = false) String endTime,
        HttpServletRequest request
    ) {
        return ApiResponse.success(dataImportService.history(businessDomain, status, startTime, endTime), requestId(request));
    }

    @GetMapping("/{importId}")
    public ApiResponse<?> detail(@PathVariable Long importId, HttpServletRequest request) {
        return ApiResponse.success(dataImportService.detail(importId), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString();
    }

    public record DatabaseImportRequest(
        @NotNull(message = "sourceId 不能为空") Long sourceId,
        String schemaName,
        @NotBlank(message = "tableName 不能为空") String tableName,
        @NotBlank(message = "datasetName 不能为空") String datasetName,
        String businessDomain,
        String description
    ) {
    }
}
