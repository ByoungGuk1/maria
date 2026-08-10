package com.app.maria.global.audit.dto.request;

import com.app.maria.global.audit.type.ReasonCode;
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
    private ReasonCode reasonCode;
    private String beforeValue;
    private String afterValue;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

}
