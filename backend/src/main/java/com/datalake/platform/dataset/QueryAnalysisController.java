package com.datalake.platform.dataset;

import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QueryAnalysisController {

    private final QueryAnalysisService queryAnalysisService;

    public QueryAnalysisController(QueryAnalysisService queryAnalysisService) {
        this.queryAnalysisService = queryAnalysisService;
    }

    @PostMapping("/api/queries/filter")
    public ApiResponse<?> filter(@Valid @RequestBody QueryAnalysisService.FilterQueryRequest body, HttpServletRequest request) {
        return ApiResponse.success(queryAnalysisService.filter(body), requestId(request));
    }

    @PostMapping("/api/queries/sql")
    public ApiResponse<?> sql(@Valid @RequestBody QueryAnalysisService.SqlQueryRequest body, HttpServletRequest request) {
        return ApiResponse.success(queryAnalysisService.sql(body), requestId(request));
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
}
