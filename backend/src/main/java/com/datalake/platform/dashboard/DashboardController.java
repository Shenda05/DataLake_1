package com.datalake.platform.dashboard;

import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public ApiResponse<?> overview(HttpServletRequest request) {
        return ApiResponse.success(dashboardService.overview(), requestId(request));
    }

    @GetMapping("/task-trend")
    public ApiResponse<?> taskTrend(HttpServletRequest request) {
        return ApiResponse.success(dashboardService.taskTrend(), requestId(request));
    }

    @GetMapping("/recent-tasks")
    public ApiResponse<?> recentTasks(HttpServletRequest request) {
        return ApiResponse.success(dashboardService.recentTasks(), requestId(request));
    }

    @GetMapping("/ecommerce")
    public ApiResponse<?> ecommerce(HttpServletRequest request) {
        return ApiResponse.success(dashboardService.ecommerce(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString();
    }
}
