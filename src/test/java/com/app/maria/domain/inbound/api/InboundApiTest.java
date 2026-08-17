package com.app.maria.domain.inbound.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.maria.domain.foreignproduct.type.ForeignProductType;
import com.app.maria.domain.inbound.dto.InboundListDTO;
import com.app.maria.domain.inbound.dto.InboundPageDTO;
import com.app.maria.domain.inbound.dto.response.AccountHoldingResponseDTO;
import com.app.maria.domain.inbound.dto.response.InboundResponseDTO;
import com.app.maria.domain.inbound.exception.InboundNotFoundException;
import com.app.maria.domain.inbound.service.InboundService;
import com.app.maria.global.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

    @Test
    void getHoldingsReturnsAccountHoldings() throws Exception {
        AccountHoldingResponseDTO holding =
                AccountHoldingResponseDTO.builder()
                        .foreignProductId(1L)
                        .ticker("AAPL")
                        .name("Apple Inc.")
                        .market("NAS")
                        .currency("USD")
                        .type(ForeignProductType.FOREIGN_STOCK)
                        .currentQty(BigDecimal.valueOf(50))
                        .build();
        when(inboundService.getHoldings(1L)).thenReturn(List.of(holding));

        mockMvc.perform(get("/api/inbounds/holdings").param("accountId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("계좌 보유종목 조회 성공"))
                .andExpect(jsonPath("$.data[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.data[0].currentQty").value(50));

        verify(inboundService).getHoldings(1L);
    }

    @Test
    void getHoldingsReturnsEmptyListWhenAccountHasNoHoldings() throws Exception {
        when(inboundService.getHoldings(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/inbounds/holdings").param("accountId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getHoldingsRejectsMissingAccountId() throws Exception {
        mockMvc.perform(get("/api/inbounds/holdings")).andExpect(status().isBadRequest());

        verify(inboundService, never()).getHoldings(anyLong());
    }

    @Test
    void getInboundsReturnsPagedResultAsJsonWithDefaultPageAndSize() throws Exception {
        InboundListDTO item =
                InboundListDTO.builder()
                        .inboundId(1L)
                        .accountNo("1234567890")
                        .customerName("홍길동")
                        .ticker("AAPL")
                        .productName("Apple Inc.")
                        .requestedQty(BigDecimal.valueOf(100))
                        .currentHoldingAtRequest(BigDecimal.valueOf(90))
                        .snapshotQty(BigDecimal.valueOf(80))
                        .approvedQty(BigDecimal.valueOf(80))
                        .remainingQty(BigDecimal.valueOf(0))
                        .processedAt(LocalDateTime.of(2026, 3, 5, 9, 0))
                        .build();
        InboundPageDTO page =
                InboundPageDTO.builder()
                        .content(List.of(item))
                        .page(0)
                        .size(20)
                        .totalElements(1)
                        .totalPages(1)
                        .build();
        when(inboundService.getInbounds(0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/inbounds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("입고 이력 목록 조회 성공"))
                .andExpect(jsonPath("$.data.content[0].accountNo").value("1234567890"))
                .andExpect(jsonPath("$.data.content[0].customerName").value("홍길동"))
                .andExpect(jsonPath("$.data.content[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.data.content[0].approvedQty").value(80))
                .andExpect(jsonPath("$.data.content[0].remainingQty").value(0))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    void getInboundsPassesPageAndSizeQueryParamsToService() throws Exception {
        InboundPageDTO page =
                InboundPageDTO.builder()
                        .content(List.of())
                        .page(2)
                        .size(5)
                        .totalElements(11)
                        .totalPages(3)
                        .build();
        when(inboundService.getInbounds(2, 5)).thenReturn(page);

        mockMvc.perform(get("/api/inbounds").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(3));
    }

    @Test
    void getInboundsReturnsEmptyListWhenNoInboundsExist() throws Exception {
        InboundPageDTO page =
                InboundPageDTO.builder()
                        .content(List.of())
                        .page(0)
                        .size(20)
                        .totalElements(0)
                        .totalPages(0)
                        .build();
        when(inboundService.getInbounds(0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/inbounds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }
}
