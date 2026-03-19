package com.datalake.platform.datasource;

import com.datalake.platform.common.security.SecurityUtils;
import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
        @RequestParam("sourceId") Long sourceId,
        HttpServletRequest request
    ) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        try {
            return ApiResponse.success(
                dataImportService.importFile(file, datasetName, sourceId, SecurityUtils.currentUser().userId()),
                requestId(request)
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("文件导入失败: " + exception.getMessage(), exception);
        }
    }

    @PostMapping("/database")
    public ApiResponse<?> importDatabase(
        @RequestParam("sourceId") Long sourceId,
        @RequestParam("tableName") String tableName,
        HttpServletRequest request
    ) {
        throw new IllegalArgumentException("当前分支暂未启用数据库表导入，请优先使用文件导入");
    }

    @GetMapping("/history")
    public ApiResponse<?> history(HttpServletRequest request) {
        return ApiResponse.success(dataImportService.history(), requestId(request));
    }

    @GetMapping("/{importId}")
    public ApiResponse<?> detail(@PathVariable Long importId, HttpServletRequest request) {
        return ApiResponse.success(dataImportService.detail(importId), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString();
    }
}
