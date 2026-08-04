package com.app.maria.domain.admin.service;

import com.app.maria.domain.admin.dto.AdminUserDTO;
import com.app.maria.domain.admin.dto.request.AdminLoginRequestDTO;
import com.app.maria.domain.admin.dto.response.AdminLoginResponseDTO;
import com.app.maria.domain.admin.exception.AdminException;
import com.app.maria.domain.admin.mapper.AdminMapper;
import com.app.maria.domain.admin.type.AdminRole;
import com.app.maria.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @Mock
    AdminMapper adminMapper;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    AdminServiceImpl adminService;

    private AdminUserDTO admin() {
        return AdminUserDTO.builder()
                .adminId(1L)
                .loginId("reviewer1")
                .passwordHash("encoded-password")
                .role(AdminRole.REVIEWER)
                .build();
    }

    @Test
    @DisplayName("정상 요청이면 토큰을 발급한다")
    void loginIssuesTokensOnSuccess() {
        AdminLoginRequestDTO request = AdminLoginRequestDTO.builder()
                .loginId("reviewer1")
                .password("raw-password")
                .build();

        when(adminMapper.selectAdminByLoginId("reviewer1")).thenReturn(Optional.of(admin()));
        when(passwordEncoder.matches("raw-password", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(1L, "reviewer1", AdminRole.REVIEWER)).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(1L)).thenReturn("refresh-token");

        AdminLoginResponseDTO result = adminService.login(request);

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("존재하지 않는 아이디면 예외를 던지고 토큰을 발급하지 않는다")
    void loginThrowsWhenLoginIdNotFound() {
        AdminLoginRequestDTO request = AdminLoginRequestDTO.builder()
                .loginId("nobody")
                .password("raw-password")
                .build();

        when(adminMapper.selectAdminByLoginId("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.login(request))
                .isInstanceOf(AdminException.class)
                .hasMessage("아이디 또는 비밀번호가 일치하지 않습니다.");

        verifyNoInteractions(passwordEncoder, jwtTokenProvider);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 예외를 던지고 토큰을 발급하지 않는다")
    void loginThrowsWhenPasswordIncorrect() {
        AdminLoginRequestDTO request = AdminLoginRequestDTO.builder()
                .loginId("reviewer1")
                .password("wrong-password")
                .build();

        when(adminMapper.selectAdminByLoginId("reviewer1")).thenReturn(Optional.of(admin()));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> adminService.login(request))
                .isInstanceOf(AdminException.class)
                .hasMessage("아이디 또는 비밀번호가 일치하지 않습니다.");

        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("역할이 배정되지 않은 계정이면 예외를 던지고 토큰을 발급하지 않는다")
    void loginThrowsWhenRoleNotAssigned() {
        AdminUserDTO adminWithoutRole = AdminUserDTO.builder()
                .adminId(1L)
                .loginId("reviewer1")
                .passwordHash("encoded-password")
                .role(null)
                .build();

        AdminLoginRequestDTO request = AdminLoginRequestDTO.builder()
                .loginId("reviewer1")
                .password("raw-password")
                .build();

        when(adminMapper.selectAdminByLoginId("reviewer1")).thenReturn(Optional.of(adminWithoutRole));
        when(passwordEncoder.matches("raw-password", "encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> adminService.login(request))
                .isInstanceOf(AdminException.class)
                .hasMessage("역할이 배정되지 않은 계정입니다. 관리자에게 문의하세요.");

        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("아이디 없음과 비밀번호 틀림의 에러 메시지가 동일하다")
    void loginReturnsSameMessageForMissingIdAndWrongPassword() {
        AdminLoginRequestDTO noSuchUser = AdminLoginRequestDTO.builder()
                .loginId("nobody")
                .password("x")
                .build();
        AdminLoginRequestDTO wrongPassword = AdminLoginRequestDTO.builder()
                .loginId("reviewer1")
                .password("wrong")
                .build();

        when(adminMapper.selectAdminByLoginId("nobody")).thenReturn(Optional.empty());
        when(adminMapper.selectAdminByLoginId("reviewer1")).thenReturn(Optional.of(admin()));
        when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);

        String messageForMissingUser = catchAdminExceptionMessage(() -> adminService.login(noSuchUser));
        String messageForWrongPassword = catchAdminExceptionMessage(() -> adminService.login(wrongPassword));

        assertThat(messageForMissingUser).isEqualTo(messageForWrongPassword);
    }

    private String catchAdminExceptionMessage(Runnable action) {
        try {
            action.run();
        } catch (AdminException e) {
            return e.getMessage();
        }
        throw new AssertionError("AdminException expected but not thrown");
    }
}
