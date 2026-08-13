package com.app.maria.global.audit.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.maria.global.audit.dto.AuditLogDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditLogResponseDTOTest {

    private AuditLogDTO auditLog(String targetTable, String targetAdminName, String targetAccountNo) {
        return AuditLogDTO.builder()
                .targetTable(targetTable)
                .targetAdminName(targetAdminName)
                .targetAccountNo(targetAccountNo)
                .build();
    }

    @Test
    @DisplayName("targetTable이 ADMIN_USER면 targetAdminName을 targetName으로 쓴다")
    void targetNameUsesTargetAdminNameForAdminUser() {
        AuditLogResponseDTO response = new AuditLogResponseDTO(auditLog("ADMIN_USER", "이국희", null));

        assertThat(response.getTargetName()).isEqualTo("이국희");
    }

    @Test
    @DisplayName("targetTable이 SELL_ORDER면 targetAccountNo를 targetName으로 쓴다")
    void targetNameUsesTargetAccountNoForSellOrder() {
        AuditLogResponseDTO response = new AuditLogResponseDTO(auditLog("SELL_ORDER", null, "1234567890"));

        assertThat(response.getTargetName()).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("targetTable이 SYSTEM_CLOCK이면 targetTable 값 그대로 targetName으로 쓴다")
    void targetNameFallsBackToTargetTableForSystemClock() {
        AuditLogResponseDTO response = new AuditLogResponseDTO(auditLog("SYSTEM_CLOCK", null, null));

        assertThat(response.getTargetName()).isEqualTo("SYSTEM_CLOCK");
    }

    @Test
    @DisplayName("알려지지 않은 targetTable 값이 와도 하드코딩된 값이 아니라 실제 targetTable을 그대로 targetName으로 쓴다")
    void targetNameFallsBackToActualTargetTableForUnknownType() {
        AuditLogResponseDTO response = new AuditLogResponseDTO(auditLog("ACCOUNT", null, null));

        assertThat(response.getTargetName()).isEqualTo("ACCOUNT");
    }
}
