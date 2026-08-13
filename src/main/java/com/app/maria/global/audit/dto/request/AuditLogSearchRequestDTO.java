package com.app.maria.global.audit.dto.request;

import com.app.maria.global.audit.dto.AuditLogSearchDTO;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class AuditLogSearchRequestDTO {

    private static final Map<String, String> TARGET_TABLE_LABELS = Map.of(
            "ADMIN_USER", "관리자",
            "SELL_ORDER", "매도주문",
            "SYSTEM_CLOCK", "시스템 시각");

    private static final Map<String, String> REASON_CODE_LABELS = Map.of(
            "ADMIN_ROLE_UPDATE", "관리자 권한 변경",
            "SELL_ORDER_EXECUTED", "매도 체결",
            "SELL_ORDER_REJECTED", "매도 반려");

    private static final Map<String, String> ROLE_LABELS = Map.of(
            "VIEWER", "조회전용",
            "REVIEWER", "심사담당",
            "SETTLEMENT", "정산담당",
            "ADMIN", "최고관리자");

    private String keyword;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @AssertTrue(message = "시작일은 종료일보다 늦을 수 없습니다.")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !startDate.isAfter(endDate);
    }

    @Min(0)
    @Builder.Default
    private int page = 0;

    @Min(1)
    @Max(100)
    @Builder.Default
    private int size = 20;

    private List<String> matchLabels(Map<String, String> labels) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return labels.entrySet().stream()
                .filter(entry -> entry.getValue().contains(keyword))
                .map(Map.Entry::getKey)
                .toList();
    }

    public AuditLogSearchDTO toAuditLogSearchDTO() {
        return AuditLogSearchDTO.builder()
                .keyword(keyword)
                .matchedTargetTables(matchLabels(TARGET_TABLE_LABELS))
                .matchedReasonCodes(matchLabels(REASON_CODE_LABELS))
                .matchedRoles(matchLabels(ROLE_LABELS))
                .startDate(startDate)
                .endDate(endDate)
                .size(size)
                .offset(page * size)
                .build();
    }

}
