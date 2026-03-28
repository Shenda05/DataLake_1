package com.datalake.platform.task;

import com.datalake.platform.common.security.SecurityUtils;
import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ApiResponse<?> list(HttpServletRequest request) {
        return ApiResponse.success(taskService.list(), requestId(request));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ACTION_task.manage')")
    public ApiResponse<?> create(@Valid @RequestBody SaveTaskRequest body, HttpServletRequest request) {
        return ApiResponse.success(
            taskService.create(
                new TaskService.SaveTaskCommand(
                    body.taskName(),
                    body.taskType(),
                    body.targetId(),
                    body.cronExpr(),
                    body.retryPolicy(),
                    body.description(),
                    body.status()
                ),
                SecurityUtils.currentUser().userId()
            ),
            requestId(request)
        );
    }

    @PutMapping("/{taskId}")
    @PreAuthorize("hasAuthority('ACTION_task.manage')")
    public ApiResponse<?> update(@PathVariable Long taskId, @Valid @RequestBody SaveTaskRequest body, HttpServletRequest request) {
        return ApiResponse.success(
            taskService.update(
                taskId,
                new TaskService.SaveTaskCommand(
                    body.taskName(),
                    body.taskType(),
                    body.targetId(),
                    body.cronExpr(),
                    body.retryPolicy(),
                    body.description(),
                    body.status()
                )
            ),
            requestId(request)
        );
    }

    @PostMapping("/{taskId}/trigger")
    @PreAuthorize("hasAuthority('ACTION_task.trigger')")
    public ApiResponse<?> trigger(@PathVariable Long taskId, HttpServletRequest request) {
        return ApiResponse.success(taskService.trigger(taskId, SecurityUtils.currentUser().userId()), requestId(request));
    }

    @PostMapping("/{taskId}/pause")
    @PreAuthorize("hasAuthority('ACTION_task.manage')")
    public ApiResponse<?> pause(@PathVariable Long taskId, HttpServletRequest request) {
        return ApiResponse.success(taskService.pause(taskId), requestId(request));
    }

    @PostMapping("/{taskId}/resume")
    @PreAuthorize("hasAuthority('ACTION_task.manage')")
    public ApiResponse<?> resume(@PathVariable Long taskId, HttpServletRequest request) {
        return ApiResponse.success(taskService.resume(taskId), requestId(request));
    }

    @DeleteMapping("/{taskId}")
    @PreAuthorize("hasAuthority('ACTION_task.manage')")
    public ApiResponse<Void> delete(@PathVariable Long taskId, HttpServletRequest request) {
        taskService.delete(taskId);
        return ApiResponse.success(requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString();
    }

    public record SaveTaskRequest(
        @NotBlank(message = "taskName 不能为空") String taskName,
        @NotBlank(message = "taskType 不能为空") String taskType,
        @NotNull(message = "targetId 不能为空") Long targetId,
        @NotBlank(message = "cronExpr 不能为空") String cronExpr,
        Integer retryPolicy,
        String description,
        String status
    ) {
    }
}
