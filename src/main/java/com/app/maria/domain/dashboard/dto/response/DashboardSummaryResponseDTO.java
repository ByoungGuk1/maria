package com.app.maria.domain.dashboard.dto.response;

import com.app.maria.domain.account.dto.response.AccountLimitUsageResponseDTO;
import com.app.maria.domain.dashboard.dto.DashboardSummaryDTO;
import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import com.app.maria.global.audit.dto.response.AuditLogResponseDTO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class DashboardSummaryResponseDTO {

    private LocalDateTime referenceDateTime;
    private int pendingAccountCount;
    private int provisionalExchangeCount;
    private int nearLimitAccountCount;
    private BigDecimal todaySellAmount;
    private int todayApprovedCount;
    private int todayRejectedCount;
    private SettlementBatchDTO latestSettlementBatch;
    private List<AccountLimitUsageResponseDTO> priorityAccounts;
    private List<AuditLogResponseDTO> recentAuditLogs;

    public DashboardSummaryResponseDTO(DashboardSummaryDTO summaryDTO) {
        this.referenceDateTime = summaryDTO.getReferenceDateTime();
        this.pendingAccountCount = summaryDTO.getPendingAccountCount();
        this.provisionalExchangeCount = summaryDTO.getProvisionalExchangeCount();
        this.nearLimitAccountCount = summaryDTO.getNearLimitAccountCount();
        this.todaySellAmount = summaryDTO.getTodaySellAmount();
        this.todayApprovedCount = summaryDTO.getTodayApprovedCount();
        this.todayRejectedCount = summaryDTO.getTodayRejectedCount();
        this.latestSettlementBatch = summaryDTO.getLatestSettlementBatch();
        this.priorityAccounts = summaryDTO.getPriorityAccounts();
        this.recentAuditLogs = summaryDTO.getRecentAuditLogs();
    }
}
