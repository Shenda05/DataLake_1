package com.datalake.platform.task;

import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.DemoDataFactory;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task-logs")
public class TaskLogController {

    @GetMapping
    public ApiResponse<?> list(HttpServletRequest request) {
        return ApiResponse.success(DemoDataFactory.taskLogs(), requestId(request));
    }

    @GetMapping("/{logId}")
    public ApiResponse<?> detail(@PathVariable Long logId, HttpServletRequest request) {
        return ApiResponse.success(
            Map.of("logId", logId, "status", "SUCCESS", "steps", DemoDataFactory.taskLogs()),
            requestId(request)
        );
    }

    private String requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString();
    }
}
