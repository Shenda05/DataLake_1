package com.datalake.platform.dashboard;

import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datalake.platform.common.web.RequestIdFilter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DashboardControllerBlackBoxTest {

    private static final String REQUEST_ID = "dashboard-request-id";

    @Mock
    private DashboardService dashboardService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(dashboardService)).build();
    }

    @Test
    @DisplayName("黑盒测试：首页概览接口返回核心统计指标")
    void overviewReturnsDashboardMetrics() throws Exception {
        when(dashboardService.overview()).thenReturn(new DashboardService.OverviewResponse(
            4, 18, 3, 26, 2, 21, 3, 2
        ));

        mockMvc.perform(get("/api/dashboard/overview")
                .requestAttr(RequestIdFilter.REQUEST_ID_ATTR, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.dataSources").value(4))
            .andExpect(jsonPath("$.data.datasets").value(18))
            .andExpect(jsonPath("$.data.runningTasks").value(2))
            .andExpect(jsonPath("$.requestId").value(REQUEST_ID));
    }

    @Test
    @DisplayName("黑盒测试：任务趋势接口返回数组数据")
    void taskTrendReturnsTrendList() throws Exception {
        when(dashboardService.taskTrend()).thenReturn(List.of(
            new DashboardService.TrendPoint("2026-06-24", 3),
            new DashboardService.TrendPoint("2026-06-25", 5)
        ));

        mockMvc.perform(get("/api/dashboard/task-trend")
                .requestAttr(RequestIdFilter.REQUEST_ID_ATTR, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[*].day", contains("2026-06-24", "2026-06-25")))
            .andExpect(jsonPath("$.data[1].total").value(5));
    }

    @Test
    @DisplayName("黑盒测试：电商首页接口返回订单趋势和低库存数量")
    void ecommerceReturnsBusinessOverview() throws Exception {
        when(dashboardService.ecommerce()).thenReturn(new DashboardService.EcommerceOverviewResponse(
            List.of(new DashboardService.MetricPoint("2026-06-25", 12)),
            List.of(new DashboardService.MetricPoint("2026-06-25", 998.5)),
            List.of(new DashboardService.RankItem("SKU-1001", 8)),
            2
        ));

        mockMvc.perform(get("/api/dashboard/ecommerce")
                .requestAttr(RequestIdFilter.REQUEST_ID_ATTR, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.orderTrend[0].value").value(12))
            .andExpect(jsonPath("$.data.salesTrend[0].value").value(998.5))
            .andExpect(jsonPath("$.data.topProducts[0].name").value("SKU-1001"))
            .andExpect(jsonPath("$.data.lowStockCount").value(2));
    }
}
