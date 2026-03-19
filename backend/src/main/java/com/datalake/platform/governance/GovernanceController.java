package com.datalake.platform.governance;

import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.RequestIdFilter;
import com.datalake.platform.common.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/governance")
public class GovernanceController {

    private final GovernanceService governanceService;

    public GovernanceController(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @GetMapping("/operators")
    public ApiResponse<?> operators(HttpServletRequest request) {
        return ApiResponse.success(governanceService.listOperators(), requestId(request));
    }

    @GetMapping("/flows")
    public ApiResponse<?> flows(HttpServletRequest request) {
        return ApiResponse.success(governanceService.listFlows(), requestId(request));
    }

    @PostMapping("/flows")
    public ApiResponse<?> createFlow(@Valid @RequestBody SaveFlowRequest body, HttpServletRequest request) {
        return ApiResponse.success(
            governanceService.saveFlow(
                body.flowName(),
                body.datasetId(),
                body.operatorChain().stream().map(step -> new GovernanceService.OperatorStepPayload(step.operatorKey(), step.params())).toList(),
                SecurityUtils.currentUser().userId()
            ),
            requestId(request)
        );
    }

    @PostMapping("/execute")
    public ApiResponse<?> execute(@Valid @RequestBody ExecuteFlowRequest body, HttpServletRequest request) {
        return ApiResponse.success(
            governanceService.execute(
                body.datasetId(),
                body.operatorChain().stream().map(step -> new GovernanceService.OperatorStepPayload(step.operatorKey(), step.params())).toList(),
                SecurityUtils.currentUser().userId()
            ),
            requestId(request)
        );
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
        @NotEmpty(message = "operatorChain 不能为空") List<OperatorStepRequest> operatorChain
    ) {
    }

    public record OperatorStepRequest(
        @NotBlank(message = "operatorKey 不能为空") String operatorKey,
        Map<String, Object> params
    ) {
    }
}
