package com.app.maria.global.audit.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.maria.global.audit.exception.AuditLogException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityAuditActorProviderTest {

    private final SecurityAuditActorProvider auditActorProvider = new SecurityAuditActorProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsAuthenticatedAdminId() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(99L, null, List.of()));

        assertThat(auditActorProvider.getCurrentAdminId()).isEqualTo(99L);
    }

    @Test
    void throwsWhenNoAdminIsAuthenticated() {
        assertThatThrownBy(auditActorProvider::getCurrentAdminId)
                .isInstanceOf(AuditLogException.class)
                .hasMessage("감사 로그를 위한 관리자 정보를 찾을 수 없습니다.");
    }

    @Test
    void throwsWhenAuthenticatedPrincipalIsNotAnAdminId() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken("admin", null, List.of()));

        assertThatThrownBy(auditActorProvider::getCurrentAdminId)
                .isInstanceOf(AuditLogException.class)
                .hasMessage("감사 로그를 위한 관리자 정보를 찾을 수 없습니다.");
    }
}
