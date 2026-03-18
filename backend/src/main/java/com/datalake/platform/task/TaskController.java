package com.datalake.platform.task;

import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.DemoDataFactory;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @GetMapping
    public ApiResponse<?> list(HttpServletRequest request) {
        return ApiResponse.success(DemoDataFactory.tasks(), requestId(request));
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody SaveTaskRequest body, HttpServletRequest request) {
        return ApiResponse.success(
            Map.of("taskId", 3003L, "taskName", body.taskName(), "taskType", body.taskType(), "status", "ENABLED"),
            requestId(request)
        );
    }

    @PutMapping("/{taskId}")
    public ApiResponse<?> update(@PathVariable Long taskId, @Valid @RequestBody SaveTaskRequest body, HttpServletRequest request) {
        return ApiResponse.success(
            Map.of("taskId", taskId, "taskName", body.taskName(), "taskType", body.taskType(), "status", body.status()),
            requestId(request)
        );
    }

    @PostMapping("/{taskId}/trigger")
    public ApiResponse<?> trigger(@PathVariable Long taskId, HttpServletRequest request) {
        return ApiResponse.success(Map.of("taskId", taskId, "message", "任务已触发"), requestId(request));
    }

    @PostMapping("/{taskId}/pause")
    public ApiResponse<?> pause(@PathVariable Long taskId, HttpServletRequest request) {
        return ApiResponse.success(Map.of("taskId", taskId, "status", "PAUSED"), requestId(request));
    }

    @PostMapping("/{taskId}/resume")
    public ApiResponse<?> resume(@PathVariable Long taskId, HttpServletRequest request) {
        return ApiResponse.success(Map.of("taskId", taskId, "status", "ENABLED"), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString();
    }

    public record SaveTaskRequest(
        @NotBlank(message = "taskName 不能为空") String taskName,
        @NotBlank(message = "taskType 不能为空") String taskType,
        @NotNull(message = "targetId 不能为空") Long targetId,
        @NotBlank(message = "cronExpr 不能为空") String cronExpr,
        String description,
        String status
    ) {
    }
}

