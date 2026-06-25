package com.datalake.platform.datasource;

import com.datalake.platform.common.security.SecurityUtils;
import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
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

    private final DataSourceService dataSourceService;

    public DataSourceController(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @GetMapping
    public ApiResponse<?> list(HttpServletRequest request) {
        return ApiResponse.success(dataSourceService.list(), requestId(request));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ACTION_source.manage')")
    public ApiResponse<?> create(@Valid @RequestBody UpsertDataSourceRequest body, HttpServletRequest request) {
        return ApiResponse.success(dataSourceService.create(body, SecurityUtils.currentUser().userId()), requestId(request));
    }

    @PutMapping("/{sourceId}")
    @PreAuthorize("hasAuthority('ACTION_source.manage')")
    public ApiResponse<?> update(@PathVariable Long sourceId, @Valid @RequestBody UpsertDataSourceRequest body, HttpServletRequest request) {
        return ApiResponse.success(dataSourceService.update(sourceId, body), requestId(request));
    }

    @DeleteMapping("/{sourceId}")
    @PreAuthorize("hasAuthority('ACTION_source.manage')")
    public ApiResponse<Void> delete(@PathVariable Long sourceId, HttpServletRequest request) {
        dataSourceService.delete(sourceId);
        return ApiResponse.success(requestId(request));
    }

    @PostMapping("/{sourceId}/test")
    @PreAuthorize("hasAuthority('ACTION_source.manage')")
    public ApiResponse<?> testConnection(@PathVariable Long sourceId, HttpServletRequest request) {
        return ApiResponse.success(dataSourceService.test(sourceId), requestId(request));
    }

    @PostMapping("/{sourceId}/status")
    @PreAuthorize("hasAuthority('ACTION_source.manage')")
    public ApiResponse<?> updateStatus(
        @PathVariable Long sourceId,
        @Valid @RequestBody UpdateSourceStatusRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(dataSourceService.updateStatus(sourceId, body.status()), requestId(request));
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
        String description,
        String duplicateConnectionStrategy
    ) {
    }

    public record UpdateSourceStatusRequest(@NotBlank(message = "status 不能为空") String status) {
    }
}
