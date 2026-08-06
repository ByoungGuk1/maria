package com.app.maria.global.audit.dto;

import lombok.*;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class AuditLogDTO {
    private Long auditId;
    private Long adminId;
    private String targetTable;
    private String targetPk;
    private String beforeValue;
    private String afterValue;
    private String reasonCode;
    private LocalDateTime processedAt;

}
