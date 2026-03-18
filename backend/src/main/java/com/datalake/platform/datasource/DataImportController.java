package com.datalake.platform.datasource;

import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.DemoDataFactory;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
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
        String filename = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        return ApiResponse.success(
            Map.of(
                "importId", 5003L,
                "datasetName", datasetName,
                "sourceId", sourceId,
                "filename", filename,
                "status", "SUCCESS",
                "message", "文件上传成功，后续可接入真实解析逻辑"
            ),
            requestId(request)
        );
    }

    @PostMapping("/database")
    public ApiResponse<?> importDatabase(
        @RequestParam("sourceId") Long sourceId,
        @RequestParam("tableName") String tableName,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            Map.of("importId", 5004L, "sourceId", sourceId, "tableName", tableName, "status", "PENDING"),
            requestId(request)
        );
    }

    @GetMapping("/history")
    public ApiResponse<?> history(HttpServletRequest request) {
        return ApiResponse.success(DemoDataFactory.importHistory(), requestId(request));
    }

    @GetMapping("/{importId}")
    public ApiResponse<?> detail(@PathVariable Long importId, HttpServletRequest request) {
        return ApiResponse.success(
            Map.of("importId", importId, "status", "SUCCESS", "recordCount", 1280, "errorReason", ""),
            requestId(request)
        );
    }

    private String requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString();
    }
}

