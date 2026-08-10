package com.app.maria.global.audit.dto.response;

import com.app.maria.global.audit.dto.AuditLogDTO;
import com.app.maria.global.audit.type.ReasonCode;
import lombok.*;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class AuditLogResponseDTO {

    private Long auditId;
    private Long adminId;
    private String targetTable;
    private String targetPk;
    private String beforeValue;
    private String afterValue;
    private ReasonCode reasonCode;
    private LocalDateTime processedAt;

    public AuditLogResponseDTO(AuditLogDTO dto) {
        this.auditId = dto.getAuditId();  // 관리자 이름으로 변환 예정
        this.targetTable = dto.getTargetTable();
        this.targetPk = dto.getTargetPk();
        this.beforeValue = dto.getBeforeValue();
        this.afterValue = dto.getAfterValue();
        this.reasonCode = dto.getReasonCode();
        this.processedAt = dto.getProcessedAt();
    }

}
