package com.datalake.platform.dataset;

import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
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
        HttpServletRequest request
    ) {
        return ApiResponse.success(datasetService.preview(datasetId, pageNum, pageSize), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString();
    }
}
