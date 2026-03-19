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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public ApiResponse<?> operators(HttpServletRequest request) {
        return ApiResponse.success(governanceService.operators(), requestId(request));
    }

    @GetMapping("/flows")
    public ApiResponse<?> flows(HttpServletRequest request) {
        return ApiResponse.success(governanceService.flows(), requestId(request));
    }

    @PostMapping("/flows")
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
    public ApiResponse<?> execute(@Valid @RequestBody ExecuteFlowRequest body, HttpServletRequest request) {
        Instant start = Instant.now();
        GovernanceService.ExecutionResult result = governanceService.execute(
            body.datasetId(),
            toOperatorSteps(body.operatorChain()),
            SecurityUtils.currentUser().userId(),
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
            SecurityUtils.currentUser().userId()
        );
        return ApiResponse.success(
            Map.of(
                "inputDatasetId", body.datasetId(),
                "outputDatasetId", result.outputDatasetId(),
                "outputDatasetName", result.outputDatasetName(),
                "operatorCount", result.operatorCount(),
                "logRef", logId,
                "summary", result.summary()
            ),
            requestId(request)
        );
    }

    private List<GovernanceService.OperatorStep> toOperatorSteps(List<OperatorStepRequest> steps) {
        return steps.stream().map(step -> new GovernanceService.OperatorStep(step.operatorKey(), step.params())).collect(Collectors.toList());
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
}
