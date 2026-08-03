package com.app.maria.domain.sellorder.api;

import com.app.maria.domain.sellorder.dto.request.SellOrderRequestDTO;
import com.app.maria.domain.sellorder.dto.response.SellOrderResponseDTO;
import com.app.maria.domain.sellorder.exception.SellOrderException;
import com.app.maria.domain.sellorder.exception.SellOrderNotFoundException;
import com.app.maria.domain.sellorder.service.SellOrderService;
import com.app.maria.domain.sellorder.type.SellOrderStatus;
import com.app.maria.global.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SellOrderApi.class)
@Import(SecurityConfig.class)
class SellOrderApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    SellOrderService sellOrderService;

    @Test
    void 매도주문접수_성공시_201과_결과를_반환한다() throws Exception {
        SellOrderRequestDTO request = SellOrderRequestDTO.builder()
                .inboundDetailId(1L)
                .sellQty(new BigDecimal("10"))
                .build();

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
    void 매도주문접수_서비스에서_예외발생시_400을_반환한다() throws Exception {
        SellOrderRequestDTO request = SellOrderRequestDTO.builder()
                .inboundDetailId(1L)
                .sellQty(new BigDecimal("10"))
                .build();

        when(sellOrderService.placeSellOrder(any()))
                .thenThrow(new SellOrderException("한도를 초과했습니다."));

        mockMvc.perform(post("/api/sell-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("한도를 초과했습니다."));
    }

    @Test
    void 매도주문접수_수량이_0이면_검증실패로_400을_반환하고_서비스는_호출되지않는다() throws Exception {
        SellOrderRequestDTO request = SellOrderRequestDTO.builder()
                .inboundDetailId(1L)
                .sellQty(BigDecimal.ZERO)
                .build();

        mockMvc.perform(post("/api/sell-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("매도 수량은 0보다 커야 합니다."));

        verify(sellOrderService, never()).placeSellOrder(any());
    }

    @Test
    void 매도주문접수_출고상세ID가_없으면_검증실패로_400을_반환하고_서비스는_호출되지않는다() throws Exception {
        SellOrderRequestDTO request = SellOrderRequestDTO.builder()
                .inboundDetailId(null)
                .sellQty(new BigDecimal("10"))
                .build();

        mockMvc.perform(post("/api/sell-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("출고 상세 ID를 입력하세요."));

        verify(sellOrderService, never()).placeSellOrder(any());
    }

    @Test
    void 매도주문조회_존재하면_200과_결과를_반환한다() throws Exception {
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
    void 매도주문조회_존재하지않으면_404를_반환한다() throws Exception {
        when(sellOrderService.getSellOrder(999L))
                .thenThrow(new SellOrderNotFoundException("매도 주문 조회 실패"));

        mockMvc.perform(get("/api/sell-orders/{orderId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("매도 주문 조회 실패"));
    }
}