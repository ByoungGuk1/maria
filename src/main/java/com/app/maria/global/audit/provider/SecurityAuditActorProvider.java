package com.app.maria.global.audit.provider;

import com.app.maria.global.audit.exception.AuditLogException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditActorProvider implements AuditActorProvider {

    @Override
    public Long getCurrentAdminId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long adminId)) {
            throw new AuditLogException("감사 로그를 위한 관리자 정보를 찾을 수 없습니다.");
        }
        return adminId;
    }
}
