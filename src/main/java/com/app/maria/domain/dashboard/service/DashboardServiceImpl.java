package com.app.maria.domain.dashboard.service;

import com.app.maria.domain.account.dto.response.AccountLimitUsageResponseDTO;
import com.app.maria.domain.account.service.AccountLogService;
import com.app.maria.domain.account.service.AccountService;
import com.app.maria.domain.account.type.Status;
import com.app.maria.domain.dashboard.dto.DashboardSummaryDTO;
import com.app.maria.domain.sellorder.service.SellOrderService;
import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import com.app.maria.domain.settlement.service.SettlementService;
import com.app.maria.global.audit.dto.request.AuditLogSearchRequestDTO;
import com.app.maria.global.audit.dto.response.AuditLogResponseDTO;
import com.app.maria.global.audit.service.AuditLogService;
import com.app.maria.global.clock.service.BusinessClockService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final AccountService accountService;
    private final AccountLogService accountLogService;
    private final SellOrderService sellOrderService;
    private final SettlementService settlementService;
    private final AuditLogService auditLogService;
    private final BusinessClockService businessClockService;

    private static final BigDecimal NEAR_LIMIT_RATIO_THRESHOLD = new BigDecimal("0.8");
    private static final int RATIO_SCALE = 4;
    private static final int RECENT_AUDIT_LOG_LIMIT = 4;

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryDTO getDashboardSummary() {
        List<AccountLimitUsageResponseDTO> openAccountUsages =
                accountService.selectAccountLimitUsage();

        List<AccountLimitUsageResponseDTO> nearLimitAccounts =
                openAccountUsages.stream().filter(this::isNearLimit).toList();

        List<AccountLimitUsageResponseDTO> priorityAccounts = new ArrayList<>();
        priorityAccounts.addAll(accountService.getAppliedAccounts());
        priorityAccounts.addAll(nearLimitAccounts);

        List<SettlementBatchDTO> batches = settlementService.getSettlementBatches();
        SettlementBatchDTO latestBatch = batches.isEmpty() ? null : batches.get(0);

        List<AuditLogResponseDTO> recentAuditLogs =
                auditLogService
                        .searchAuditLogs(
                                AuditLogSearchRequestDTO.builder().size(RECENT_AUDIT_LOG_LIMIT).build())
                        .getContent();

        return DashboardSummaryDTO.builder()
                .referenceDateTime(businessClockService.now())
                .pendingAccountCount(accountService.getAppliedAccountCount())
                .provisionalExchangeCount(settlementService.getProvisionalExchangeCount())
                .nearLimitAccountCount(nearLimitAccounts.size())
                .todaySellAmount(sellOrderService.getTodaySellAmount())
                .todayApprovedCount(accountLogService.getTodayProcessedAccountCount(Status.OPENED))
                .todayRejectedCount(
                        accountLogService.getTodayProcessedAccountCount(Status.REJECTED))
                .latestSettlementBatch(latestBatch)
                .priorityAccounts(priorityAccounts)
                .recentAuditLogs(recentAuditLogs)
                .build();
    }

    private boolean isNearLimit(AccountLimitUsageResponseDTO usageDTO) {
        if (usageDTO.getLimitAmount() == null || usageDTO.getLimitAmount().signum() <= 0) {
            return false;
        }
        BigDecimal ratio =
                usageDTO.getUsedAmount()
                        .divide(usageDTO.getLimitAmount(), RATIO_SCALE, RoundingMode.HALF_UP);
        return ratio.compareTo(NEAR_LIMIT_RATIO_THRESHOLD) >= 0;
    }
}
