package com.app.maria.domain.account.api;

import com.app.maria.domain.account.dto.request.AccountRequestDTO;
import com.app.maria.domain.account.dto.response.AccountResponseDTO;
import com.app.maria.domain.account.service.AccountService;
import com.app.maria.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountApiTest {

  private AccountService accountService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    accountService = mock(AccountService.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(new AccountApi(accountService))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  void applyAcceptsValidRequest() throws Exception {
    AccountResponseDTO response = mock(AccountResponseDTO.class);
    when(accountService.applyAccount(any(AccountRequestDTO.class))).thenReturn(response);

    mockMvc.perform(post("/api/account/applications")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "customerId": 1,
                  "limitAmount": 30000000
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.message").value("계좌 개설 신청 처리 완료"));

    verify(accountService).applyAccount(any(AccountRequestDTO.class));
  }

  @Test
  void applyRejectsMissingCustomerId() throws Exception {
    mockMvc.perform(post("/api/account/applications")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "limitAmount": 30000000
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("개설할 계좌의 사용자 정보는 필수입니다."));

    verify(accountService, never()).applyAccount(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "50000001", "1.5"})
  void applyRejectsInvalidLimit(String limitAmount) throws Exception {
    mockMvc.perform(post("/api/account/applications")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "customerId": 1,
                  "limitAmount": %s
                }
                """.formatted(limitAmount)))
        .andExpect(status().isBadRequest());

    verify(accountService, never()).applyAccount(any());
  }

  @Test
  void applyRejectsMissingLimit() throws Exception {
    mockMvc.perform(post("/api/account/applications")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "customerId": 1
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("계좌 한도는 필수입니다."));

    verify(accountService, never()).applyAccount(any());
  }
}
