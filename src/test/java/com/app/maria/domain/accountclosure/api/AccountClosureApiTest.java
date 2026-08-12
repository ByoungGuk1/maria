package com.app.maria.domain.accountclosure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.maria.domain.accountclosure.dto.request.AccountClosureApplyRequestDTO;
import com.app.maria.domain.accountclosure.exception.AccountClosureNotAllowedException;
import com.app.maria.domain.accountclosure.exception.AccountClosureProcessingException;
import com.app.maria.domain.accountclosure.service.AccountClosureService;
import com.app.maria.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AccountClosureApiTest {

    @Mock private AccountClosureService accountClosureService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new AccountClosureApi(accountClosureService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void validRequestReturnsCreatedClosureRequestId() throws Exception {
        when(accountClosureService.applyClosure(eq(10L), any(AccountClosureApplyRequestDTO.class)))
                .thenReturn(30L);

        mockMvc.perform(
                        post("/api/account-closures")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("계좌 해지 신청 완료"))
                .andExpect(jsonPath("$.data").value(30));

        ArgumentCaptor<AccountClosureApplyRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(AccountClosureApplyRequestDTO.class);
        verify(accountClosureService).applyClosure(eq(10L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getDestinationGeneralAccountId()).isEqualTo(20L);
        assertThat(requestCaptor.getValue().isEarlyWithdrawalAgreed()).isTrue();
    }

    @Test
    void missingCustomerIdReturnsBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(
                        post("/api/account-closures")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "destinationGeneralAccountId": 20,
                                          "earlyWithdrawalAgreed": true
                                        }
                                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountClosureService);
    }

    @Test
    void nonPositiveDestinationAccountIdReturnsBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(
                        post("/api/account-closures")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "customerId": 10,
                                          "destinationGeneralAccountId": 0,
                                          "earlyWithdrawalAgreed": false
                                        }
                                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountClosureService);
    }

    @Test
    void notAllowedClosureReturnsBadRequest() throws Exception {
        when(accountClosureService.applyClosure(eq(10L), any(AccountClosureApplyRequestDTO.class)))
                .thenThrow(new AccountClosureNotAllowedException("해지를 신청할 수 없습니다."));

        mockMvc.perform(
                        post("/api/account-closures")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("해지를 신청할 수 없습니다."));
    }

    @Test
    void closureProcessingFailureReturnsInternalServerError() throws Exception {
        when(accountClosureService.applyClosure(eq(10L), any(AccountClosureApplyRequestDTO.class)))
                .thenThrow(new AccountClosureProcessingException("계좌 해지 신청 저장에 실패했습니다."));

        mockMvc.perform(
                        post("/api/account-closures")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("계좌 해지 신청 저장에 실패했습니다."));
    }

    private static String validRequest() {
        return """
                {
                  "customerId": 10,
                  "destinationGeneralAccountId": 20,
                  "earlyWithdrawalAgreed": true
                }
                """;
    }
}
