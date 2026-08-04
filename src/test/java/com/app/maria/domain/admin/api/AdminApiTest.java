package com.app.maria.domain.admin.api;

import com.app.maria.domain.admin.dto.request.AdminLoginRequestDTO;
import com.app.maria.domain.admin.dto.response.AdminLoginResponseDTO;
import com.app.maria.domain.admin.exception.AdminException;
import com.app.maria.domain.admin.service.AdminService;
import com.app.maria.global.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminApi.class)
@Import(SecurityConfig.class)
class AdminApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    AdminService adminService;

    private AdminLoginRequestDTO loginRequest(String loginId, String password) {
        return AdminLoginRequestDTO.builder()
                .loginId(loginId)
                .password(password)
                .build();
    }

    @Test
    @DisplayName("로그인 성공시 200과 토큰을 반환한다")
    void loginReturns200WithTokensOnSuccess() throws Exception {
        AdminLoginRequestDTO request = loginRequest("reviewer1", "raw-password");
        AdminLoginResponseDTO response = AdminLoginResponseDTO.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .build();

        when(adminService.login(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
    }

    @Test
    @DisplayName("서비스에서 인증 예외가 발생하면 401을 반환한다")
    void loginReturns401WhenServiceThrowsAdminException() throws Exception {
        AdminLoginRequestDTO request = loginRequest("reviewer1", "wrong-password");

        when(adminService.login(any()))
                .thenThrow(new AdminException("아이디 또는 비밀번호가 일치하지 않습니다."));

        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 일치하지 않습니다."));
    }

    @Test
    @DisplayName("아이디가 없으면 검증 실패로 400을 반환하고 서비스는 호출되지 않는다")
    void loginReturns400WhenLoginIdMissing() throws Exception {
        AdminLoginRequestDTO request = loginRequest(null, "raw-password");

        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(adminService, never()).login(any());
    }

    @Test
    @DisplayName("비밀번호가 없으면 검증 실패로 400을 반환하고 서비스는 호출되지 않는다")
    void loginReturns400WhenPasswordMissing() throws Exception {
        AdminLoginRequestDTO request = loginRequest("reviewer1", null);

        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(adminService, never()).login(any());
    }
}
