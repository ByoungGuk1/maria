package com.app.maria.global.audit.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class AuditLogSearchDTO {

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private String keyword;
    private List<String> matchedTargetTables;
    private List<String> matchedReasonCodes;
    private List<String> matchedRoles;

    private int size;
    private int offset;
}
