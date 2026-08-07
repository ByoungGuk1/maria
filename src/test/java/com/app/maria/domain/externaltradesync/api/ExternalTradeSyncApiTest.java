package com.app.maria.domain.externaltradesync.api;

import com.app.maria.domain.externaltradesync.launcher.ExternalTradeSyncLauncher;
import com.app.maria.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExternalTradeSyncApiTest {

    private ExternalTradeSyncLauncher externalTradeSyncLauncher;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        externalTradeSyncLauncher = mock(ExternalTradeSyncLauncher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ExternalTradeSyncApi(externalTradeSyncLauncher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/external-trade-sync/jobs 호출 시 202와 함께 동기화를 실행시킨다")
    void executeSyncTriggersLauncherAndReturnsAccepted() throws Exception {
        mockMvc.perform(post("/api/external-trade-sync/jobs"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("외부 순매수 동기화가 실행되었습니다."));

        verify(externalTradeSyncLauncher).launch();
    }
}