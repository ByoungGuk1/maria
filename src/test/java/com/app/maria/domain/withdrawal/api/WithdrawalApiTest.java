package com.app.maria.domain.withdrawal.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.maria.domain.withdrawal.dto.response.WithdrawalDetailResponseDTO;
import com.app.maria.domain.withdrawal.dto.response.WithdrawalListResponseDTO;
import com.app.maria.domain.withdrawal.exception.WithdrawalNotFoundException;
import com.app.maria.domain.withdrawal.service.WithdrawalQueryService;
import com.app.maria.domain.withdrawal.type.WithdrawalStatus;
import com.app.maria.global.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class WithdrawalApiTest {
    @Mock private WithdrawalQueryService withdrawalQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new WithdrawalApi(withdrawalQueryService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void statusFilterReturnsWithdrawalSummary() throws Exception {
        WithdrawalListResponseDTO response =
                WithdrawalListResponseDTO.builder()
                        .withdrawalId(10L)
                        .customerName("인출 고객")
                        .riaAccountNo("1234567890")
                        .requestedAmount(new BigDecimal("800"))
                        .destinationAccountNo("111122223333")
                        .status(WithdrawalStatus.COMPLETED)
                        .processedAt(LocalDateTime.of(2026, 8, 12, 10, 0))
                        .earningsAmount(new BigDecimal("100"))
                        .maturedPrincipalAmount(new BigDecimal("300"))
                        .immaturePrincipalAmount(new BigDecimal("400"))
                        .build();
        when(withdrawalQueryService.getWithdrawals(WithdrawalStatus.COMPLETED))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/withdrawals").param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].withdrawalId").value(10))
                .andExpect(jsonPath("$.data[0].customerName").value("인출 고객"))
                .andExpect(jsonPath("$.data[0].requestedAmount").value(800))
                .andExpect(jsonPath("$.data[0].immaturePrincipalAmount").value(400));

        verify(withdrawalQueryService).getWithdrawals(WithdrawalStatus.COMPLETED);
    }

    @Test
    void detailReturnsEarlyWithdrawalResult() throws Exception {
        WithdrawalDetailResponseDTO response =
                WithdrawalDetailResponseDTO.builder()
                        .withdrawalId(10L)
                        .earlyWithdrawal(true)
                        .allocations(List.of())
                        .build();
        when(withdrawalQueryService.getWithdrawal(10L)).thenReturn(response);

        mockMvc.perform(get("/api/withdrawals/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withdrawalId").value(10))
                .andExpect(jsonPath("$.data.earlyWithdrawal").value(true));
    }

    @Test
    void missingWithdrawalReturnsNotFound() throws Exception {
        when(withdrawalQueryService.getWithdrawal(99L))
                .thenThrow(new WithdrawalNotFoundException("인출 내역을 찾을 수 없습니다."));

        mockMvc.perform(get("/api/withdrawals/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("인출 내역을 찾을 수 없습니다."));
    }

    @Test
    void nonPositiveWithdrawalIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/withdrawals/0")).andExpect(status().isBadRequest());
    }
}
