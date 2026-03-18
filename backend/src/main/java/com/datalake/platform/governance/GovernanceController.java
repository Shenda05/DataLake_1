package com.datalake.platform.governance;

import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.DemoDataFactory;
import com.datalake.platform.common.web.RequestIdFilter;
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

    @GetMapping("/operators")
    public ApiResponse<?> operators(HttpServletRequest request) {
        return ApiResponse.success(DemoDataFactory.operators(), requestId(request));
    }

    @GetMapping("/flows")
    public ApiResponse<?> flows(HttpServletRequest request) {
        return ApiResponse.success(DemoDataFactory.governanceFlows(), requestId(request));
    }

    @PostMapping("/flows")
    public ApiResponse<?> createFlow(@Valid @RequestBody SaveFlowRequest body, HttpServletRequest request) {
        return ApiResponse.success(
            Map.of("flowId", 7003L, "flowName", body.flowName(), "operatorCount", body.operatorChain().size()),
            requestId(request)
        );
    }

    @PostMapping("/execute")
    public ApiResponse<?> execute(@Valid @RequestBody ExecuteFlowRequest body, HttpServletRequest request) {
        return ApiResponse.success(
            Map.of(
                "inputDatasetId", body.datasetId(),
                "outputDatasetId", 2109L,
                "operatorCount", body.operatorChain().size(),
                "logRef", 4009L,
                "summary", "治理完成，已生成新数据集"
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

