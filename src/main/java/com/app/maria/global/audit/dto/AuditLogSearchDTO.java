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

    private String targetTable;

    private String adminKeyword;
    private List<String> matchedRoles;

    private String targetKeyword;

    private String reasonKeyword;
    private List<String> matchedReasonCodes;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private int size;
    private int offset;
}
