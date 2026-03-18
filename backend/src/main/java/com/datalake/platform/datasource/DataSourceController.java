package com.datalake.platform.datasource;

import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.DemoDataFactory;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data-sources")
public class DataSourceController {

    @GetMapping
    public ApiResponse<?> list(HttpServletRequest request) {
        return ApiResponse.success(DemoDataFactory.dataSources(), requestId(request));
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody UpsertDataSourceRequest body, HttpServletRequest request) {
        return ApiResponse.success(
            DemoDataFactory.ordered("sourceId", 1003L, "sourceName", body.sourceName(), "sourceType", body.sourceType(), "status", "ENABLED"),
            requestId(request)
        );
    }

    @PutMapping("/{sourceId}")
    public ApiResponse<?> update(@PathVariable Long sourceId, @Valid @RequestBody UpsertDataSourceRequest body, HttpServletRequest request) {
        return ApiResponse.success(
            DemoDataFactory.ordered("sourceId", sourceId, "sourceName", body.sourceName(), "sourceType", body.sourceType(), "status", "ENABLED"),
            requestId(request)
        );
    }

    @DeleteMapping("/{sourceId}")
    public ApiResponse<Void> delete(@PathVariable Long sourceId, HttpServletRequest request) {
        return ApiResponse.success(requestId(request));
    }

    @PostMapping("/{sourceId}/test")
    public ApiResponse<?> testConnection(@PathVariable Long sourceId, HttpServletRequest request) {
        return ApiResponse.success(DemoDataFactory.ordered("sourceId", sourceId, "connected", true, "message", "连接测试成功"), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString();
    }

    public record UpsertDataSourceRequest(
        @NotBlank(message = "sourceName 不能为空") String sourceName,
        @NotBlank(message = "sourceType 不能为空") String sourceType,
        String host,
        Integer port,
        String dbName,
        String username,
        String password,
        String description
    ) {
    }
}

