package com.app.maria.domain.externaltradesync.api;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.maria.domain.externaltradesync.launcher.ExternalTradeSyncLauncher;
import com.app.maria.global.config.SecurityConfig;
import com.app.maria.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExternalTradeSyncApi.class)
@Import(SecurityConfig.class)
class ExternalTradeSyncApiTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean ExternalTradeSyncLauncher externalTradeSyncLauncher;

    @MockitoBean JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("SETTLEMENT 권한이면 202와 함께 동기화를 실행시킨다")
    @WithMockUser(roles = "SETTLEMENT")
    void executeSyncTriggersLauncherAndReturnsAcceptedForSettlementRole() throws Exception {
        mockMvc.perform(post("/api/external-trade-sync/jobs"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("외부 순매수 동기화가 실행되었습니다."));

        verify(externalTradeSyncLauncher).launch();
    }

    @Test
    @DisplayName("ADMIN 권한이면 202와 함께 동기화를 실행시킨다")
    @WithMockUser(roles = "ADMIN")
    void executeSyncTriggersLauncherAndReturnsAcceptedForAdminRole() throws Exception {
        mockMvc.perform(post("/api/external-trade-sync/jobs")).andExpect(status().isAccepted());

        verify(externalTradeSyncLauncher).launch();
    }

    @Test
    @DisplayName("VIEWER 권한이면 403을 반환하고 동기화는 실행되지 않는다")
    @WithMockUser(roles = "VIEWER")
    void executeSyncReturns403ForViewerRole() throws Exception {
        mockMvc.perform(post("/api/external-trade-sync/jobs")).andExpect(status().isForbidden());

        verify(externalTradeSyncLauncher, never()).launch();
    }
}
