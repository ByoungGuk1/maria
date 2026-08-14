package com.app.maria.domain.dashboard.api;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.maria.domain.account.dto.response.AccountLimitUsageResponseDTO;
import com.app.maria.domain.account.type.Status;
import com.app.maria.domain.dashboard.dto.DashboardSummaryDTO;
import com.app.maria.domain.dashboard.service.DashboardService;
import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import com.app.maria.domain.settlement.type.BatchStatus;
import com.app.maria.global.audit.dto.response.AuditLogResponseDTO;
import com.app.maria.global.config.SecurityConfig;
import com.app.maria.global.jwt.JwtTokenProvider;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardApi.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class DashboardApiTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private DashboardService dashboardService;

    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    @Test
    void getDashboardReturnsSummaryAsJson() throws Exception {
        when(dashboardService.getDashboardSummary()).thenReturn(summary());

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("대시보드 조회 성공"))
                .andExpect(jsonPath("$.data.pendingAccountCount").value(3))
                .andExpect(jsonPath("$.data.provisionalExchangeCount").value(2))
                .andExpect(jsonPath("$.data.nearLimitAccountCount").value(1))
                .andExpect(jsonPath("$.data.todaySellAmount").value(15000000))
                .andExpect(jsonPath("$.data.todayApprovedCount").value(5))
                .andExpect(jsonPath("$.data.todayRejectedCount").value(1))
                .andExpect(jsonPath("$.data.latestSettlementBatch.batchId").value(100))
                .andExpect(jsonPath("$.data.latestSettlementBatch.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.priorityAccounts[0].accountNo").value("110-1"))
                .andExpect(jsonPath("$.data.priorityAccounts[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.recentAuditLogs[0].targetTable").value("ACCOUNT"));
    }

    @Test
    void getDashboardIsAccessibleToViewerRole() throws Exception {
        when(dashboardService.getDashboardSummary()).thenReturn(summary());

        mockMvc.perform(get("/api/admin/dashboard").with(user("viewer").roles("VIEWER")))
                .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void getDashboardRejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
    }

    private DashboardSummaryDTO summary() {
        AccountLimitUsageResponseDTO priorityAccount =
                AccountLimitUsageResponseDTO.builder()
                        .accountId(1L)
                        .accountNo("110-1")
                        .customerName("홍길동")
                        .status(Status.APPLIED)
                        .limitAmount(new BigDecimal("5000000"))
                        .usedAmount(BigDecimal.ZERO)
                        .build();

        SettlementBatchDTO batch =
                SettlementBatchDTO.builder().batchId(100L).status(BatchStatus.COMPLETED).build();

        AuditLogResponseDTO auditLog =
                AuditLogResponseDTO.builder()
                        .auditId(1L)
                        .adminId(1L)
                        .targetTable("ACCOUNT")
                        .targetPk("1")
                        .reasonCode("TEST")
                        .processedAt(LocalDateTime.of(2026, 8, 11, 9, 0))
                        .build();

        return DashboardSummaryDTO.builder()
                .referenceDateTime(LocalDateTime.of(2026, 8, 11, 10, 0))
                .pendingAccountCount(3)
                .provisionalExchangeCount(2)
                .nearLimitAccountCount(1)
                .todaySellAmount(new BigDecimal("15000000"))
                .todayApprovedCount(5)
                .todayRejectedCount(1)
                .latestSettlementBatch(batch)
                .priorityAccounts(List.of(priorityAccount))
                .recentAuditLogs(List.of(auditLog))
                .build();
    }
}
