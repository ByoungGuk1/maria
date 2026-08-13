package com.app.maria.global.audit.dto.request;

import com.app.maria.global.audit.dto.AuditLogSearchDTO;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class AuditLogSearchRequestDTO {

    private Long adminId;
    private String targetTable;
    private String targetPk;
    private String reasonCode;

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

    public AuditLogSearchDTO toAuditLogSearchDTO() {
        return AuditLogSearchDTO.builder()
                .adminId(adminId)
                .targetTable(targetTable)
                .targetPk(targetPk)
                .reasonCode(reasonCode)
                .startDate(startDate)
                .endDate(endDate)
                .size(size)
                .offset(page * size)
                .build();
    }
}
