package com.datalake.platform.dataset;

import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.DemoDataFactory;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DatasetController {

    @GetMapping("/api/datasets")
    public ApiResponse<?> list(HttpServletRequest request) {
        return ApiResponse.success(DemoDataFactory.datasets(), requestId(request));
    }

    @GetMapping("/api/datasets/{datasetId}")
    public ApiResponse<?> detail(@PathVariable Long datasetId, HttpServletRequest request) {
        return ApiResponse.success(DemoDataFactory.datasetDetail(datasetId), requestId(request));
    }

    @DeleteMapping("/api/datasets/{datasetId}")
    public ApiResponse<Void> delete(@PathVariable Long datasetId, HttpServletRequest request) {
        if (datasetId == 2001L) {
            throw new IllegalArgumentException("该数据集已被任务引用，不能直接删除");
        }
        return ApiResponse.success(requestId(request));
    }

    @GetMapping("/api/metadata/{datasetId}")
    public ApiResponse<?> metadata(@PathVariable Long datasetId, HttpServletRequest request) {
        return ApiResponse.success(DemoDataFactory.metadata(datasetId), requestId(request));
    }

    @GetMapping("/api/preview/{datasetId}")
    public ApiResponse<?> preview(@PathVariable Long datasetId, HttpServletRequest request) {
        return ApiResponse.success(DemoDataFactory.preview(datasetId), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString();
    }
}

