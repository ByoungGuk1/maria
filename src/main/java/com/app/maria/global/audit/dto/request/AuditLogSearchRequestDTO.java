package com.app.maria.global.audit.dto.request;

import com.app.maria.global.audit.type.ReasonCode;
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
    private ReasonCode reasonCode;
    private String beforeValue;
    private String afterValue;

}
