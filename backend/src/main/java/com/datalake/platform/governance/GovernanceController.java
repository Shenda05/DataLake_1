package com.datalake.platform.governance;

import com.datalake.platform.common.security.SecurityUtils;
import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.RequestIdFilter;
import com.datalake.platform.task.TaskLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/governance")
public class GovernanceController {

    private final GovernanceService governanceService;
    private final TaskLogService taskLogService;

    public GovernanceController(GovernanceService governanceService, TaskLogService taskLogService) {
        this.governanceService = governanceService;
        this.taskLogService = taskLogService;
    }

    @GetMapping("/operators")
    public ApiResponse<?> operators(
        @RequestParam(value = "includeDisabled", defaultValue = "false") boolean includeDisabled,
        HttpServletRequest request
    ) {
        return ApiResponse.success(governanceService.operators(includeDisabled), requestId(request));
    }

    @PostMapping("/operators/{operatorKey}/status")
    @PreAuthorize("hasAuthority('ACTION_governance.manage')")
    public ApiResponse<?> updateOperatorStatus(
        @PathVariable String operatorKey,
        @Valid @RequestBody OperatorStatusRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(governanceService.updateOperatorStatus(operatorKey, body.status()), requestId(request));
    }

    @GetMapping("/flows")
    public ApiResponse<?> flows(HttpServletRequest request) {
        return ApiResponse.success(governanceService.flows(), requestId(request));
    }

    @PostMapping("/flows")
    @PreAuthorize("hasAuthority('ACTION_governance.manage')")
    public ApiResponse<?> createFlow(@Valid @RequestBody SaveFlowRequest body, HttpServletRequest request) {
        GovernanceService.SaveFlowResult result = governanceService.createFlow(
            body.flowName(),
            body.datasetId(),
            toOperatorSteps(body.operatorChain()),
            SecurityUtils.currentUser().userId()
        );
        return ApiResponse.success(result, requestId(request));
    }

    @PostMapping("/execute")
    @PreAuthorize("hasAuthority('ACTION_governance.execute')")
    public ApiResponse<?> execute(@Valid @RequestBody ExecuteFlowRequest body, HttpServletRequest request) {
        Instant start = Instant.now();
        Long userId = SecurityUtils.currentUser().userId();
        List<GovernanceService.OperatorStep> operatorSteps = toOperatorSteps(body.operatorChain());
        Map<String, Object> inputParams = buildInputParams(body, operatorSteps);
        try {
            GovernanceService.ExecutionResult result = governanceService.execute(
                body.datasetId(),
                operatorSteps,
                userId,
                body.executionName()
            );
            Instant end = Instant.now();
            Long logId = taskLogService.record(
                null,
                "GOVERNANCE",
                body.datasetId(),
                start,
                end,
                "SUCCESS",
                result.summary(),
                null,
                userId,
                inputParams,
                buildExecutionSteps(operatorSteps, null),
                null
            );
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("inputDatasetId", body.datasetId());
            payload.put("outputDatasetId", result.outputDatasetId());
            payload.put("outputDatasetName", result.outputDatasetName());
            payload.put("operatorCount", result.operatorCount());
            payload.put("inputRecordCount", result.inputRecordCount());
            payload.put("outputRecordCount", result.outputRecordCount());
            payload.put("abnormalHandledCount", result.abnormalHandledCount());
            payload.put("failedStep", "");
            payload.put("failedReason", "");
            payload.put("logRef", logId);
            payload.put("summary", result.summary());
            return ApiResponse.success(payload, requestId(request));
        } catch (GovernanceService.GovernanceExecutionException exception) {
            Instant end = Instant.now();
            GovernanceService.FailureStep step = exception.failureStep();
            TaskLogService.FailureReason failureReason = new TaskLogService.FailureReason(
                "GOVERNANCE_OPERATOR_FAILED",
                step == null ? "UNKNOWN" : "step-" + step.stepIndex() + ":" + step.operatorKey(),
                step == null ? exception.getMessage() : step.reason(),
                exception.getMessage()
            );
            Long logId = taskLogService.record(
                null,
                "GOVERNANCE",
                body.datasetId(),
                start,
                end,
                "FAILED",
                "治理执行失败",
                exception.getMessage(),
                userId,
                inputParams,
                buildExecutionSteps(operatorSteps, step),
                failureReason
            );
            throw new IllegalArgumentException(exception.getMessage() + "（日志编号: " + logId + "）", exception);
        } catch (Exception exception) {
            Instant end = Instant.now();
            Long logId = taskLogService.record(
                null,
                "GOVERNANCE",
                body.datasetId(),
                start,
                end,
                "FAILED",
                "治理执行失败",
                exception.getMessage(),
                userId,
                inputParams,
                buildExecutionSteps(operatorSteps, new GovernanceService.FailureStep(1, "UNKNOWN", exception.getMessage())),
                new TaskLogService.FailureReason("GOVERNANCE_EXECUTION_FAILED", "UNKNOWN", exception.getMessage(), exception.toString())
            );
            throw new IllegalArgumentException("治理执行失败: " + exception.getMessage() + "（日志编号: " + logId + "）", exception);
        }
    }

    private List<GovernanceService.OperatorStep> toOperatorSteps(List<OperatorStepRequest> steps) {
        return steps.stream().map(step -> new GovernanceService.OperatorStep(step.operatorKey(), step.params())).collect(Collectors.toList());
    }

    private Map<String, Object> buildInputParams(ExecuteFlowRequest body, List<GovernanceService.OperatorStep> steps) {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("datasetId", body.datasetId());
        params.put("executionName", body.executionName());
        params.put("operatorCount", steps.size());
        params.put("operatorKeys", steps.stream().map(GovernanceService.OperatorStep::operatorKey).toList());
        return params;
    }

    private List<TaskLogService.ExecutionStep> buildExecutionSteps(
        List<GovernanceService.OperatorStep> steps,
        GovernanceService.FailureStep failureStep
    ) {
        List<TaskLogService.ExecutionStep> executionSteps = new ArrayList<>();
        for (int index = 0; index < steps.size(); index++) {
            GovernanceService.OperatorStep step = steps.get(index);
            int stepIndex = index + 1;
            String status = "SUCCESS";
            String detail = "算子执行完成";
            if (failureStep != null) {
                if (stepIndex > failureStep.stepIndex()) {
                    status = "SKIPPED";
                    detail = "前置步骤失败，未执行";
                } else if (stepIndex == failureStep.stepIndex()) {
                    status = "FAILED";
                    detail = failureStep.reason();
                }
            }
            executionSteps.add(new TaskLogService.ExecutionStep(stepIndex, step.operatorKey(), status, detail));
        }
        return executionSteps;
    }

    private String requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString();
    }

    public record SaveFlowRequest(
        @NotBlank(message = "flowName 不能为空") String flowName,
        @NotNull(message = "datasetId 不能为空") Long datasetId,
        @NotEmpty(message = "operatorChain 不能为空") List<OperatorStepRequest> operatorChain
    ) {
    }

    public record ExecuteFlowRequest(
        @NotNull(message = "datasetId 不能为空") Long datasetId,
        @NotEmpty(message = "operatorChain 不能为空") List<OperatorStepRequest> operatorChain,
        String executionName
    ) {
    }

    public record OperatorStepRequest(
        @NotBlank(message = "operatorKey 不能为空") String operatorKey,
        Map<String, Object> params
    ) {
    }

    public record OperatorStatusRequest(
        @NotBlank(message = "status 不能为空") String status
    ) {
    }
}
