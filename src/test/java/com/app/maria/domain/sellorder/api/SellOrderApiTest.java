package com.app.maria.domain.sellorder.api;

import com.app.maria.domain.sellorder.dto.request.SellOrderRequestDTO;
import com.app.maria.domain.sellorder.dto.response.SellOrderResponseDTO;
import com.app.maria.domain.sellorder.exception.SellOrderException;
import com.app.maria.domain.sellorder.exception.SellOrderNotFoundException;
import com.app.maria.domain.sellorder.service.SellOrderService;
import com.app.maria.domain.sellorder.type.SellOrderStatus;
import com.app.maria.global.config.SecurityConfig;
import com.app.maria.global.jwt.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SellOrderApi.class)
@Import(SecurityConfig.class)
class SellOrderApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    SellOrderService sellOrderService;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    private SellOrderRequestDTO.SellOrderRequestDTOBuilder validRequestBuilder() {
        return SellOrderRequestDTO.builder()
                .inboundDetailId(1L)
                .sellQty(new BigDecimal("10"));
    }

    @Test
    @DisplayName("매도 주문 접수 성공 시 201과 결과를 반환한다")
    @WithMockUser(roles = "SETTLEMENT")
    void placeSellOrderReturns201WithResultOnSuccess() throws Exception {
        SellOrderRequestDTO request = validRequestBuilder().build();

        SellOrderResponseDTO response = SellOrderResponseDTO.builder()
                .orderId(100L)
                .inboundDetailId(1L)
                .sellQty(new BigDecimal("10"))
                .status(SellOrderStatus.RECEIVED)
                .build();

        when(sellOrderService.placeSellOrder(any())).thenReturn(response);

        mockMvc.perform(post("/api/sell-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderId").value(100))
                .andExpect(jsonPath("$.data.status").value("RECEIVED"));
    }

    @Test
    @DisplayName("매도 주문 접수 시 서비스에서 예외가 발생하면 400을 반환한다")
    @WithMockUser(roles = "SETTLEMENT")
    void placeSellOrderReturns400WhenServiceThrowsException() throws Exception {
        SellOrderRequestDTO request = validRequestBuilder().build();

        when(sellOrderService.placeSellOrder(any()))
                .thenThrow(new SellOrderException("한도를 초과했습니다."));

        mockMvc.perform(post("/api/sell-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("한도를 초과했습니다."));
    }

    @Test
    @DisplayName("매도 주문 접수 시 수량이 0이면 검증 실패로 400을 반환하고 서비스는 호출되지 않는다")
    @WithMockUser(roles = "SETTLEMENT")
    void placeSellOrderReturns400WhenSellQtyIsZero() throws Exception {
        SellOrderRequestDTO request = validRequestBuilder().sellQty(BigDecimal.ZERO).build();

        mockMvc.perform(post("/api/sell-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("매도 수량은 0보다 커야 합니다."));

        verify(sellOrderService, never()).placeSellOrder(any());
    }

    @Test
    @DisplayName("매도 주문 접수 시 출고 상세 ID가 없으면 검증 실패로 400을 반환하고 서비스는 호출되지 않는다")
    @WithMockUser(roles = "SETTLEMENT")
    void placeSellOrderReturns400WhenInboundDetailIdMissing() throws Exception {
        SellOrderRequestDTO request = validRequestBuilder().inboundDetailId(null).build();

        mockMvc.perform(post("/api/sell-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("출고 상세 ID를 입력하세요."));

        verify(sellOrderService, never()).placeSellOrder(any());
    }

    @Test
    @DisplayName("매도 주문 접수 시 인증되지 않은 요청이면 401을 반환하고 서비스는 호출되지 않는다")
    void placeSellOrderReturns401WhenNotAuthenticated() throws Exception {
        SellOrderRequestDTO request = validRequestBuilder().build();

        mockMvc.perform(post("/api/sell-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(sellOrderService, never()).placeSellOrder(any());
    }

    @Test
    @DisplayName("매도 주문 접수 시 SETTLEMENT/ADMIN이 아니면 403을 반환하고 서비스는 호출되지 않는다")
    @WithMockUser(roles = "VIEWER")
    void placeSellOrderReturns403WhenCallerIsNotSettlementOrAdmin() throws Exception {
        SellOrderRequestDTO request = validRequestBuilder().build();

        mockMvc.perform(post("/api/sell-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(sellOrderService, never()).placeSellOrder(any());
    }

    @Test
    @DisplayName("매도 주문 조회 시 존재하면 200과 결과를 반환한다")
    @WithMockUser(roles = "SETTLEMENT")
    void getSellOrderReturns200WithResultWhenExists() throws Exception {
        SellOrderResponseDTO response = SellOrderResponseDTO.builder()
                .orderId(100L)
                .status(SellOrderStatus.RECEIVED)
                .build();

        when(sellOrderService.getSellOrder(100L)).thenReturn(response);

        mockMvc.perform(get("/api/sell-orders/{orderId}", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(100));
    }

    @Test
    @DisplayName("매도 주문 조회 시 존재하지 않으면 404를 반환한다")
    @WithMockUser(roles = "SETTLEMENT")
    void getSellOrderReturns404WhenNotFound() throws Exception {
        when(sellOrderService.getSellOrder(999L))
                .thenThrow(new SellOrderNotFoundException("매도 주문 조회 실패"));

        mockMvc.perform(get("/api/sell-orders/{orderId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("매도 주문 조회 실패"));
    }

    @Test
    @DisplayName("계좌ID로 매도 주문 목록을 조회하면 200과 목록을 반환한다")
    @WithMockUser(roles = "VIEWER")
    void getAllSellOrdersReturns200WithListForAccount() throws Exception {
        SellOrderResponseDTO response = SellOrderResponseDTO.builder()
                .orderId(100L)
                .inboundDetailId(1L)
                .status(SellOrderStatus.RECEIVED)
                .build();

        when(sellOrderService.getSellOrderByAccount(1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/sell-orders").param("accountId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderId").value(100))
                .andExpect(jsonPath("$.data[0].status").value("RECEIVED"));
    }

    @Test
    @DisplayName("계좌에 매도 주문이 없으면 빈 목록을 반환한다")
    @WithMockUser(roles = "VIEWER")
    void getAllSellOrdersReturns200WithEmptyListWhenNoOrders() throws Exception {
        when(sellOrderService.getSellOrderByAccount(999L)).thenReturn(List.of());

        mockMvc.perform(get("/api/sell-orders").param("accountId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("계좌별 매도 주문 목록 조회 시 인증되지 않은 요청이면 401을 반환한다")
    void getAllSellOrdersReturns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/sell-orders").param("accountId", "1"))
                .andExpect(status().isUnauthorized());

        verify(sellOrderService, never()).getSellOrderByAccount(any());
    }
}
