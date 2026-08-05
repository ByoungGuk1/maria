package com.app.maria.domain.inbound.service;

import com.app.maria.domain.inbound.dto.InboundDTO;
import com.app.maria.domain.inbound.dto.InboundDetailDTO;
import com.app.maria.domain.inbound.dto.InboundMinDTO;
import com.app.maria.domain.inbound.dto.response.InboundResponseDTO;
import com.app.maria.domain.inbound.exception.InboundException;
import com.app.maria.domain.inbound.exception.InboundNotFoundException;
import com.app.maria.domain.inbound.mapper.InboundMapper;
import com.app.maria.domain.registrablestock.dto.RegistrableStockResponseDTO;
import com.app.maria.global.response.ApiResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InboundServiceImplTest {

    @Mock
    InboundMapper inboundMapper;

    @Mock
    RestClient restClient;

    @Mock
    RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    RestClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    RestClient.ResponseSpec responseSpec;

    @InjectMocks
    InboundServiceImpl inboundService;

    @Test
    void processInbound_정상요청이면_결과를_반환한다() {
        Long accountId = 1L;
        Long foreignProductId = 1L;
        BigDecimal requestedQty = BigDecimal.valueOf(80);
        BigDecimal currentHoldingAtRequest = BigDecimal.valueOf(90);

        RegistrableStockResponseDTO registrableStock = RegistrableStockResponseDTO.builder()
                .heldQty(BigDecimal.valueOf(100))
                .sourceBroker(null)
                .purchaseDate(LocalDateTime.now())
                .purchasePrice(BigDecimal.valueOf(150.25))
                .purchaseCurrency("USD")
                .purchaseFxRate(BigDecimal.valueOf(1320.5))
                .build();

        ApiResponseDTO<RegistrableStockResponseDTO> apiResponse =
                ApiResponseDTO.of("등록가능 보유수량 조회 성공", registrableStock);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), eq(accountId), eq(foreignProductId)))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(apiResponse);

        InboundDTO inboundDTO = InboundDTO.builder()
                .inboundId(10L)
                .accountId(accountId)
                .requestedQty(requestedQty)
                .currentHoldingAtRequest(currentHoldingAtRequest)
                .approvedQty(BigDecimal.valueOf(80))
                .processedAt(LocalDateTime.now())
                .build();

        doAnswer(invocation -> {
            InboundDTO arg = invocation.getArgument(0);
            arg.setInboundId(10L);
            arg.setProcessedAt(inboundDTO.getProcessedAt());
            return null;
        }).when(inboundMapper).insertInbound(any(InboundDTO.class));

        doAnswer(invocation -> {
            InboundDetailDTO arg = invocation.getArgument(0);
            arg.setInboundDetailId(20L);
            return null;
        }).when(inboundMapper).insertInboundDetail(any(InboundDetailDTO.class));

        doNothing().when(inboundMapper).insertInboundMin(any(InboundMinDTO.class));

        InboundResponseDTO result = inboundService.processInbound(
                accountId, foreignProductId, requestedQty, currentHoldingAtRequest);

        assertThat(result.getApprovedQty()).isEqualByComparingTo(BigDecimal.valueOf(80));
        assertThat(result.getSnapshotQty()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void processInbound_accountId가_null이면_예외를_던진다() {
        assertThatThrownBy(() -> inboundService.processInbound(null, 1L, BigDecimal.TEN, BigDecimal.TEN))
                .isInstanceOf(InboundException.class);

        verifyNoInteractions(inboundMapper, restClient);
    }

    @Test
    void processInbound_foreignProductId가_null이면_예외를_던진다() {
        assertThatThrownBy(() -> inboundService.processInbound(1L, null, BigDecimal.TEN, BigDecimal.TEN))
                .isInstanceOf(InboundException.class);

        verifyNoInteractions(inboundMapper, restClient);
    }

    @Test
    void processInbound_requestedQty가_null이면_예외를_던진다() {
        assertThatThrownBy(() -> inboundService.processInbound(1L, 1L, null, BigDecimal.TEN))
                .isInstanceOf(InboundException.class);

        verifyNoInteractions(inboundMapper, restClient);
    }

    @Test
    void processInbound_REGISTRABLE_STOCK_조회결과가_없으면_NotFoundException을_던진다() {
        Long accountId = 1L;
        Long foreignProductId = 1L;

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), eq(accountId), eq(foreignProductId)))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> inboundService.processInbound(
                accountId, foreignProductId, BigDecimal.TEN, BigDecimal.TEN))
                .isInstanceOf(InboundNotFoundException.class);
    }
}