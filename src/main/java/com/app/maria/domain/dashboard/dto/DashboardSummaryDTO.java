package com.app.maria.domain.dashboard.dto;

import com.app.maria.domain.account.dto.response.AccountLimitUsageResponseDTO;
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
public class DashboardSummaryDTO {

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
}
