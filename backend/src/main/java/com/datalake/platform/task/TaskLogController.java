package com.datalake.platform.task;

import com.datalake.platform.common.security.SecurityUtils;
import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task-logs")
public class TaskLogController {

    private final TaskLogService taskLogService;
    private final TaskService taskService;

    public TaskLogController(TaskLogService taskLogService, TaskService taskService) {
        this.taskLogService = taskLogService;
        this.taskService = taskService;
    }

    @GetMapping
    public ApiResponse<?> list(
        @RequestParam(required = false) String taskType,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long operatorUser,
        @RequestParam(required = false) String startTime,
        @RequestParam(required = false) String endTime,
        @RequestParam(required = false) String keyword,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            taskLogService.list(taskType, status, operatorUser, startTime, endTime, keyword),
            requestId(request)
        );
    }

    @GetMapping("/{logId}")
    public ApiResponse<?> detail(@PathVariable Long logId, HttpServletRequest request) {
        return ApiResponse.success(taskLogService.detail(logId), requestId(request));
    }

    @PostMapping("/{logId}/replay")
    @PreAuthorize("hasAuthority('ACTION_log.replay')")
    public ApiResponse<?> replay(@PathVariable Long logId, HttpServletRequest request) {
        return ApiResponse.success(taskService.replayFromLog(logId, SecurityUtils.currentUser().userId()), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString();
    }
}
