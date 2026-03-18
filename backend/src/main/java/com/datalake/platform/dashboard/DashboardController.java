package com.datalake.platform.dashboard;

import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.DemoDataFactory;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @GetMapping("/overview")
    public ApiResponse<?> overview(HttpServletRequest request) {
        return ApiResponse.success(DemoDataFactory.overview(), requestId(request));
    }

    @GetMapping("/task-trend")
    public ApiResponse<?> taskTrend(HttpServletRequest request) {
        return ApiResponse.success(DemoDataFactory.taskTrend(), requestId(request));
    }

    @GetMapping("/recent-tasks")
    public ApiResponse<?> recentTasks(HttpServletRequest request) {
        return ApiResponse.success(DemoDataFactory.recentTasks(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString();
    }
}

