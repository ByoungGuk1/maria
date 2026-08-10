package com.app.maria.global.audit.dto.request;

import com.app.maria.global.audit.dto.AuditLogSearchDTO;
import lombok.*;

import java.time.LocalDateTime;

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

    public AuditLogSearchDTO toAuditLogSearchDTO() {
        return AuditLogSearchDTO.builder()
                .adminId(adminId)
                .targetTable(targetTable)
                .targetPk(targetPk)
                .reasonCode(reasonCode)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

}
