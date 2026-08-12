package com.app.maria.domain.targetproduct.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.maria.domain.targetproduct.dto.TargetProductJudgementListDTO;
import com.app.maria.domain.targetproduct.service.TargetProductService;
import com.app.maria.domain.targetproduct.type.StockType;
import com.app.maria.domain.targetproduct.type.TradeType;
import com.app.maria.global.config.SecurityConfig;
import com.app.maria.global.jwt.JwtTokenProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TargetProductApi.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "VIEWER")
class TargetProductApiTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TargetProductService targetProductService;

    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    @Test
    void getRecentJudgementsReturnsListAsJson() throws Exception {
        TargetProductJudgementListDTO dto =
                TargetProductJudgementListDTO.builder()
                        .judgementId(1L)
                        .customerName("홍길동")
                        .stockType(StockType.FOREIGN_STOCK)
                        .ticker("AAPL")
                        .isTarget(true)
                        .tradeType(TradeType.BUY)
                        .amount(new BigDecimal("1000000"))
                        .netBuyAmount(new BigDecimal("1000000"))
                        .tradeDate(LocalDate.of(2026, 3, 5))
                        .judgedAt(LocalDateTime.of(2026, 8, 7, 3, 0))
                        .build();
        when(targetProductService.getRecentJudgements()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/target-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].customerName").value("홍길동"))
                .andExpect(jsonPath("$.data[0].stockType").value("FOREIGN_STOCK"))
                .andExpect(jsonPath("$.data[0].ticker").value("AAPL"));
    }

    @Test
    @WithAnonymousUser
    void getRecentJudgementsRejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/target-products")).andExpect(status().isUnauthorized());
    }
}
