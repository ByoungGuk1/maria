package com.app.maria.global.audit.dto;

import java.time.LocalDateTime;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class AuditLogSearchDTO {

    private Long adminId;
    private String targetTable;
    private String targetPk;
    private String reasonCode;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private int size;
    private int offset;
}
