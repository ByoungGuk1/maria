package com.app.maria.domain.inbound.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.maria.domain.inbound.dto.response.InboundResponseDTO;
import com.app.maria.domain.inbound.exception.InboundNotFoundException;
import com.app.maria.domain.inbound.service.InboundService;
import com.app.maria.global.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InboundApiTest {

    private InboundService inboundService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        inboundService = mock(InboundService.class);
        mockMvc =
                MockMvcBuilders.standaloneSetup(new InboundApi(inboundService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void processInboundAcceptsValidRequest() throws Exception {
        InboundResponseDTO response =
                InboundResponseDTO.builder()
                        .inboundId(10L)
                        .requestedQty(BigDecimal.valueOf(80))
                        .snapshotQty(BigDecimal.valueOf(100))
                        .currentHoldingAtRequest(BigDecimal.valueOf(90))
                        .approvedQty(BigDecimal.valueOf(80))
                        .processedAt(LocalDateTime.now())
                        .build();
        when(inboundService.processInbound(any())).thenReturn(response);

        mockMvc.perform(
                        post("/api/inbounds")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                {
                  "accountId": 1,
                  "foreignProductId": 1,
                  "requestedQty": 80,
                  "currentHoldingAtRequest": 90
                }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("입고대상 수량 계산 성공"))
                .andExpect(jsonPath("$.data.approvedQty").value(80));

        verify(inboundService).processInbound(any());
    }

    @Test
    void processInboundRejectsMissingAccountId() throws Exception {
        mockMvc.perform(
                        post("/api/inbounds")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                {
                  "foreignProductId": 1,
                  "requestedQty": 80
                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("accountId는 필수입니다."));

        verify(inboundService, never()).processInbound(any());
    }

    @Test
    void processInboundRejectsMissingForeignProductId() throws Exception {
        mockMvc.perform(
                        post("/api/inbounds")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                {
                  "accountId": 1,
                  "requestedQty": 80
                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("foreignProductId는 필수입니다."));

        verify(inboundService, never()).processInbound(any());
    }

    @Test
    void processInboundRejectsMissingRequestedQty() throws Exception {
        mockMvc.perform(
                        post("/api/inbounds")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                {
                  "accountId": 1,
                  "foreignProductId": 1
                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("requestedQty는 필수입니다."));

        verify(inboundService, never()).processInbound(any());
    }

    @Test
    void processInboundAcceptsMissingCurrentHoldingAtRequest() throws Exception {
        InboundResponseDTO response =
                InboundResponseDTO.builder()
                        .inboundId(10L)
                        .requestedQty(BigDecimal.valueOf(80))
                        .snapshotQty(BigDecimal.valueOf(100))
                        .approvedQty(BigDecimal.valueOf(80))
                        .processedAt(LocalDateTime.now())
                        .build();
        when(inboundService.processInbound(any())).thenReturn(response);

        mockMvc.perform(
                        post("/api/inbounds")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                {
                  "accountId": 1,
                  "foreignProductId": 1,
                  "requestedQty": 80
                }
                """))
                .andExpect(status().isOk());

        verify(inboundService).processInbound(any());
    }

    @Test
    void processInboundReturnsNotFoundWhenRegistrableStockMissing() throws Exception {
        when(inboundService.processInbound(any()))
                .thenThrow(new InboundNotFoundException("등록가능 보유수량 조회 실패"));

        mockMvc.perform(
                        post("/api/inbounds")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                {
                  "accountId": 1,
                  "foreignProductId": 1,
                  "requestedQty": 80
                }
                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("등록가능 보유수량 조회 실패"));
    }
}
