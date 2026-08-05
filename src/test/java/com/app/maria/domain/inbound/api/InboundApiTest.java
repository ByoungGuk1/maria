package com.app.maria.domain.inbound.api;

import com.app.maria.domain.inbound.dto.response.InboundResponseDTO;
import com.app.maria.domain.inbound.exception.InboundException;
import com.app.maria.domain.inbound.exception.InboundNotFoundException;
import com.app.maria.domain.inbound.service.InboundService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InboundApi.class)
@AutoConfigureMockMvc(addFilters = false)
class InboundApiTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    InboundService inboundService;

    @Test
    void processInbound_정상요청이면_200을_반환한다() throws Exception {
        InboundResponseDTO responseDTO = InboundResponseDTO.builder()
                .inboundId(10L)
                .requestedQty(BigDecimal.valueOf(80))
                .snapshotQty(BigDecimal.valueOf(100))
                .currentHoldingAtRequest(BigDecimal.valueOf(90))
                .approvedQty(BigDecimal.valueOf(80))
                .build();

        when(inboundService.processInbound(1L, 1L, BigDecimal.valueOf(80), BigDecimal.valueOf(90)))
                .thenReturn(responseDTO);

        mockMvc.perform(post("/api/inbounds")
                        .param("accountId", "1")
                        .param("foreignProductId", "1")
                        .param("requestedQty", "80")
                        .param("currentHoldingAtRequest", "90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvedQty").value(80));
    }

    @Test
    void processInbound_필수값이_없으면_400을_반환한다() throws Exception {
        when(inboundService.processInbound(any(), any(), any(), any()))
                .thenThrow(new InboundException("accountId는 필수입니다."));

        mockMvc.perform(post("/api/inbounds")
                        .param("accountId", "1")
                        .param("foreignProductId", "1")
                        .param("requestedQty", "80")
                        .param("currentHoldingAtRequest", "90"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processInbound_REGISTRABLE_STOCK_조회결과가_없으면_404를_반환한다() throws Exception {
        when(inboundService.processInbound(any(), any(), any(), any()))
                .thenThrow(new InboundNotFoundException("등록가능 보유수량 조회 실패"));

        mockMvc.perform(post("/api/inbounds")
                        .param("accountId", "1")
                        .param("foreignProductId", "1")
                        .param("requestedQty", "80")
                        .param("currentHoldingAtRequest", "90"))
                .andExpect(status().isNotFound());
    }
}