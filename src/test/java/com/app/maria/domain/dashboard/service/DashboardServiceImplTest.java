package com.app.maria.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.maria.domain.account.dto.response.AccountLimitUsageResponseDTO;
import com.app.maria.domain.account.service.AccountLogService;
import com.app.maria.domain.account.service.AccountService;
import com.app.maria.domain.account.type.Status;
import com.app.maria.domain.dashboard.dto.DashboardSummaryDTO;
import com.app.maria.domain.sellorder.service.SellOrderService;
import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import com.app.maria.domain.settlement.service.SettlementService;
import com.app.maria.global.audit.dto.response.AuditLogResponseDTO;
import com.app.maria.global.audit.service.AuditLogService;
import com.app.maria.global.clock.service.BusinessClockService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock private AccountService accountService;

    @Mock private AccountLogService accountLogService;

    @Mock private SellOrderService sellOrderService;

    @Mock private SettlementService settlementService;

    @Mock private AuditLogService auditLogService;

    @Mock private BusinessClockService businessClockService;

    private DashboardServiceImpl newService() {
        return new DashboardServiceImpl(
                accountService,
                accountLogService,
                sellOrderService,
                settlementService,
                auditLogService,
                businessClockService);
    }

    @Test
    @DisplayName("각 필드가 해당 Service 응답값을 그대로 담는다")
    void getDashboardSummaryAssemblesAllFieldsFromEachService() {
        DashboardServiceImpl service = newService();
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 10, 0);
        when(businessClockService.now()).thenReturn(now);
        when(accountService.getAppliedAccountCount()).thenReturn(3);
        when(settlementService.getProvisionalExchangeCount()).thenReturn(2);
        when(sellOrderService.getTodaySellAmount()).thenReturn(new BigDecimal("15000000"));
        when(accountLogService.getTodayProcessedAccountCount(Status.OPENED)).thenReturn(5);
        when(accountLogService.getTodayProcessedAccountCount(Status.REJECTED)).thenReturn(1);
        when(accountService.selectAccountLimitUsage()).thenReturn(List.of());
        when(accountService.getAppliedAccounts()).thenReturn(List.of());
        SettlementBatchDTO batch = SettlementBatchDTO.builder().batchId(100L).build();
        when(settlementService.getSettlementBatches()).thenReturn(List.of(batch));
        when(auditLogService.searchAuditLogs(any())).thenReturn(List.of());

        DashboardSummaryDTO result = service.getDashboardSummary();

        assertThat(result.getReferenceDateTime()).isEqualTo(now);
        assertThat(result.getPendingAccountCount()).isEqualTo(3);
        assertThat(result.getProvisionalExchangeCount()).isEqualTo(2);
        assertThat(result.getTodaySellAmount()).isEqualByComparingTo("15000000");
        assertThat(result.getTodayApprovedCount()).isEqualTo(5);
        assertThat(result.getTodayRejectedCount()).isEqualTo(1);
        assertThat(result.getLatestSettlementBatch()).isEqualTo(batch);
    }

    @Test
    @DisplayName("사용률이 정확히 80%인 계좌는 한도 근접으로 포함된다 (>= 경계값)")
    void getDashboardSummaryIncludesAccountExactlyAtEightyPercentThreshold() {
        DashboardServiceImpl service = newService();
        stubUnrelatedDependencies();
        AccountLimitUsageResponseDTO exactlyEighty =
                accountUsage(1L, Status.OPENED, "8000000", "10000000");
        when(accountService.selectAccountLimitUsage()).thenReturn(List.of(exactlyEighty));
        when(accountService.getAppliedAccounts()).thenReturn(List.of());

        DashboardSummaryDTO result = service.getDashboardSummary();

        assertThat(result.getNearLimitAccountCount()).isEqualTo(1);
        assertThat(result.getPriorityAccounts()).containsExactly(exactlyEighty);
    }

    @Test
    @DisplayName("사용률이 80%에서 조금이라도 모자란 계좌는 제외된다")
    void getDashboardSummaryExcludesAccountJustBelowEightyPercentThreshold() {
        DashboardServiceImpl service = newService();
        stubUnrelatedDependencies();
        AccountLimitUsageResponseDTO justBelow =
                accountUsage(2L, Status.OPENED, "7994000", "10000000");
        when(accountService.selectAccountLimitUsage()).thenReturn(List.of(justBelow));
        when(accountService.getAppliedAccounts()).thenReturn(List.of());

        DashboardSummaryDTO result = service.getDashboardSummary();

        assertThat(result.getNearLimitAccountCount()).isZero();
        assertThat(result.getPriorityAccounts()).isEmpty();
    }

    @Test
    @DisplayName("한도가 0 이하인 계좌는 0으로 나누지 않고 한도근접 대상에서 제외된다")
    void getDashboardSummaryTreatsNonPositiveLimitAmountAsNotNearLimit() {
        DashboardServiceImpl service = newService();
        stubUnrelatedDependencies();
        AccountLimitUsageResponseDTO zeroLimit = accountUsage(3L, Status.OPENED, "1000", "0");
        when(accountService.selectAccountLimitUsage()).thenReturn(List.of(zeroLimit));
        when(accountService.getAppliedAccounts()).thenReturn(List.of());

        DashboardSummaryDTO result = service.getDashboardSummary();

        assertThat(result.getNearLimitAccountCount()).isZero();
        assertThat(result.getPriorityAccounts()).isEmpty();
    }

    @Test
    @DisplayName("심사대기 계좌는 한도 사용률과 무관하게 처리 계좌 목록에 항상 포함된다")
    void getDashboardSummaryKeepsAppliedAccountsInPriorityListRegardlessOfUsage() {
        DashboardServiceImpl service = newService();
        stubUnrelatedDependencies();
        AccountLimitUsageResponseDTO applied = accountUsage(4L, Status.APPLIED, "0", "5000000");
        when(accountService.selectAccountLimitUsage()).thenReturn(List.of());
        when(accountService.getAppliedAccounts()).thenReturn(List.of(applied));

        DashboardSummaryDTO result = service.getDashboardSummary();

        assertThat(result.getPriorityAccounts()).containsExactly(applied);
    }

    @Test
    @DisplayName("정산 배치가 하나도 없으면 최신 배치는 예외 없이 null이다")
    void getDashboardSummaryReturnsNullLatestBatchWhenNoneExist() {
        DashboardServiceImpl service = newService();
        stubUnrelatedDependencies();
        when(settlementService.getSettlementBatches()).thenReturn(List.of());

        DashboardSummaryDTO result = service.getDashboardSummary();

        assertThat(result.getLatestSettlementBatch()).isNull();
    }

    @Test
    @DisplayName("감사로그가 4건보다 많이 와도 최근 4건으로 잘라낸다")
    void getDashboardSummaryLimitsRecentAuditLogsToFourEvenWhenMoreExist() {
        DashboardServiceImpl service = newService();
        stubUnrelatedDependencies();
        List<AuditLogResponseDTO> sixLogs =
                List.of(
                        auditLog(1L),
                        auditLog(2L),
                        auditLog(3L),
                        auditLog(4L),
                        auditLog(5L),
                        auditLog(6L));
        when(auditLogService.searchAuditLogs(any())).thenReturn(sixLogs);

        DashboardSummaryDTO result = service.getDashboardSummary();

        assertThat(result.getRecentAuditLogs()).hasSize(4);
        assertThat(result.getRecentAuditLogs()).containsExactlyElementsOf(sixLogs.subList(0, 4));
    }

    @Test
    @DisplayName("오늘 승인 건수와 반려 건수가 서로 뒤바뀌지 않는다")
    void getDashboardSummaryDoesNotSwapApprovedAndRejectedCounts() {
        DashboardServiceImpl service = newService();
        stubUnrelatedDependencies();
        when(accountLogService.getTodayProcessedAccountCount(Status.OPENED)).thenReturn(7);
        when(accountLogService.getTodayProcessedAccountCount(Status.REJECTED)).thenReturn(2);

        DashboardSummaryDTO result = service.getDashboardSummary();

        assertThat(result.getTodayApprovedCount()).isEqualTo(7);
        assertThat(result.getTodayRejectedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("계좌별 한도 사용률 조회는 한 번만 호출된다 (건수용/목록용으로 중복 조회하지 않는다)")
    void getDashboardSummaryComputesAccountLimitUsageOnlyOnce() {
        DashboardServiceImpl service = newService();
        stubUnrelatedDependencies();

        service.getDashboardSummary();

        verify(accountService, times(1)).selectAccountLimitUsage();
    }

    private void stubUnrelatedDependencies() {
        when(businessClockService.now()).thenReturn(LocalDateTime.of(2026, 8, 11, 10, 0));
        when(settlementService.getSettlementBatches()).thenReturn(List.of());
        when(auditLogService.searchAuditLogs(any())).thenReturn(List.of());
    }

    private AccountLimitUsageResponseDTO accountUsage(
            Long accountId, Status status, String usedAmount, String limitAmount) {
        return AccountLimitUsageResponseDTO.builder()
                .accountId(accountId)
                .accountNo("110-" + accountId)
                .customerName("고객" + accountId)
                .status(status)
                .usedAmount(new BigDecimal(usedAmount))
                .limitAmount(new BigDecimal(limitAmount))
                .build();
    }

    private AuditLogResponseDTO auditLog(Long auditId) {
        return AuditLogResponseDTO.builder()
                .auditId(auditId)
                .adminId(1L)
                .targetTable("ACCOUNT")
                .targetPk(String.valueOf(auditId))
                .reasonCode("TEST")
                .processedAt(LocalDateTime.of(2026, 8, 11, 9, 0))
                .build();
    }
}
