package com.datalake.platform.dataset;

import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.DemoDataFactory;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QueryAnalysisController {

    @PostMapping("/api/queries/filter")
    public ApiResponse<?> filter(@Valid @RequestBody FilterQueryRequest body, HttpServletRequest request) {
        return ApiResponse.success(DemoDataFactory.queryResult(), requestId(request));
    }

    @PostMapping("/api/queries/sql")
    public ApiResponse<?> sql(@Valid @RequestBody SqlQueryRequest body, HttpServletRequest request) {
        if (!body.sql().toLowerCase().contains("select")) {
            throw new IllegalArgumentException("仅支持 SELECT 类查询");
        }
        return ApiResponse.success(DemoDataFactory.queryResult(), requestId(request));
    }

    @GetMapping("/api/analysis/{datasetId}/summary")
    public ApiResponse<?> summary(@PathVariable Long datasetId, HttpServletRequest request) {
        return ApiResponse.success(DemoDataFactory.analysisSummary(datasetId), requestId(request));
    }

    @GetMapping("/api/analysis/{datasetId}/charts")
    public ApiResponse<?> charts(@PathVariable Long datasetId, HttpServletRequest request) {
        return ApiResponse.success(DemoDataFactory.chartSeries(datasetId), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString();
    }

    public record FilterQueryRequest(
        @NotNull(message = "datasetId 不能为空") Long datasetId,
        @NotBlank(message = "field 不能为空") String field,
        @NotBlank(message = "operator 不能为空") String operator,
        @NotBlank(message = "value 不能为空") String value
    ) {
    }

    public record SqlQueryRequest(
        @NotNull(message = "datasetId 不能为空") Long datasetId,
        @NotBlank(message = "sql 不能为空") String sql
    ) {
    }
}

