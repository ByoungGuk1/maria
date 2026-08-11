package com.app.maria.domain.inbound.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.app.maria.domain.inbound.dto.request.InboundRequestDTO;
import com.app.maria.domain.inbound.dto.response.InboundResponseDTO;
import com.app.maria.domain.inbound.exception.InboundNotFoundException;
import com.app.maria.domain.inbound.mapper.InboundMapper;
import com.app.maria.domain.registrablestock.dto.RegistrableStockResponseDTO;
import com.app.maria.global.clock.service.BusinessClockService;
import com.app.maria.global.response.ApiResponseDTO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class InboundServiceImplTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long FOREIGN_PRODUCT_ID = 1L;

    @Mock private InboundMapper inboundMapper;

    @Mock private RestClient restClient;

    @Mock private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock private RestClient.RequestHeadersSpec requestHeadersSpec;

    @Mock private RestClient.ResponseSpec responseSpec;

    @Mock private BusinessClockService businessClockService;

    @InjectMocks private InboundServiceImpl inboundService;

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 5, 10, 0);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUpRestClientChain() {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(
                        eq(
                                "/api/registrable-stocks?generalAccountId={accountId}&foreignProductId={foreignProductId}"),
                        eq(ACCOUNT_ID),
                        eq(FOREIGN_PRODUCT_ID)))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        lenient().when(businessClockService.now()).thenReturn(FIXED_NOW);
    }

    @Test
    void processInboundApprovesRequestedQtyWhenWithinAllLimits() {
        stubRegistrableStock(BigDecimal.valueOf(100));
        when(inboundMapper.sumApprovedQtyByAccountAndProduct(ACCOUNT_ID, FOREIGN_PRODUCT_ID))
                .thenReturn(BigDecimal.ZERO);

        InboundResponseDTO result =
                inboundService.processInbound(
                        request(BigDecimal.valueOf(80), BigDecimal.valueOf(90)));

        assertThat(result.getApprovedQty()).isEqualByComparingTo(BigDecimal.valueOf(80));
        assertThat(result.getSnapshotQty()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void processInboundCapsApprovedQtyBySnapshotQtyWhenRequestedExceedsSnapshot() {
        stubRegistrableStock(BigDecimal.valueOf(50));
        when(inboundMapper.sumApprovedQtyByAccountAndProduct(ACCOUNT_ID, FOREIGN_PRODUCT_ID))
                .thenReturn(BigDecimal.ZERO);

        InboundResponseDTO result =
                inboundService.processInbound(
                        request(BigDecimal.valueOf(80), BigDecimal.valueOf(90)));

        assertThat(result.getApprovedQty()).isEqualByComparingTo(BigDecimal.valueOf(50));
    }

    @Test
    void processInboundCapsApprovedQtyByCurrentHoldingWhenLowerThanOtherLimits() {
        stubRegistrableStock(BigDecimal.valueOf(100));
        when(inboundMapper.sumApprovedQtyByAccountAndProduct(ACCOUNT_ID, FOREIGN_PRODUCT_ID))
                .thenReturn(BigDecimal.ZERO);

        InboundResponseDTO result =
                inboundService.processInbound(
                        request(BigDecimal.valueOf(80), BigDecimal.valueOf(30)));

        assertThat(result.getApprovedQty()).isEqualByComparingTo(BigDecimal.valueOf(30));
    }

    @Test
    void processInboundReducesAvailableQtyByPreviouslyApprovedQty() {
        stubRegistrableStock(BigDecimal.valueOf(100));
        when(inboundMapper.sumApprovedQtyByAccountAndProduct(ACCOUNT_ID, FOREIGN_PRODUCT_ID))
                .thenReturn(BigDecimal.valueOf(60));

        InboundResponseDTO result =
                inboundService.processInbound(
                        request(BigDecimal.valueOf(50), BigDecimal.valueOf(90)));

        // availableQty = 100 - 60 = 40, requestedQty(50)와 currentHolding(90)보다 작음
        assertThat(result.getApprovedQty()).isEqualByComparingTo(BigDecimal.valueOf(40));
    }

    @Test
    void processInboundApprovesZeroWhenPreviouslyApprovedQtyReachesSnapshot() {
        stubRegistrableStock(BigDecimal.valueOf(100));
        when(inboundMapper.sumApprovedQtyByAccountAndProduct(ACCOUNT_ID, FOREIGN_PRODUCT_ID))
                .thenReturn(BigDecimal.valueOf(100));

        InboundResponseDTO result =
                inboundService.processInbound(
                        request(BigDecimal.valueOf(50), BigDecimal.valueOf(90)));

        assertThat(result.getApprovedQty()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @SuppressWarnings("unchecked")
    void processInboundThrowsNotFoundWhenApiResponseIsNull() {
        when(responseSpec.body(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(null);

        assertThatThrownBy(
                        () ->
                                inboundService.processInbound(
                                        request(BigDecimal.valueOf(80), BigDecimal.valueOf(90))))
                .isInstanceOf(InboundNotFoundException.class)
                .hasMessage("등록가능 보유수량 조회 실패");
    }

    @Test
    @SuppressWarnings("unchecked")
    void processInboundThrowsNotFoundWhenApiResponseDataIsNull() {
        ApiResponseDTO<RegistrableStockResponseDTO> apiResponse = ApiResponseDTO.of("조회 실패", null);
        when(responseSpec.body(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(apiResponse);

        assertThatThrownBy(
                        () ->
                                inboundService.processInbound(
                                        request(BigDecimal.valueOf(80), BigDecimal.valueOf(90))))
                .isInstanceOf(InboundNotFoundException.class)
                .hasMessage("등록가능 보유수량 조회 실패");
    }

    @SuppressWarnings("unchecked")
    private void stubRegistrableStock(BigDecimal heldQty) {
        RegistrableStockResponseDTO registrableStock =
                RegistrableStockResponseDTO.builder()
                        .heldQty(heldQty)
                        .sourceBroker(null)
                        .purchaseDate(LocalDateTime.now())
                        .purchasePrice(BigDecimal.valueOf(150.25))
                        .purchaseCurrency("USD")
                        .purchaseFxRate(BigDecimal.valueOf(1320.5))
                        .build();
        ApiResponseDTO<RegistrableStockResponseDTO> apiResponse =
                ApiResponseDTO.of("등록가능 보유수량 조회 성공", registrableStock);
        when(responseSpec.body(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(apiResponse);
    }

    private InboundRequestDTO request(BigDecimal requestedQty, BigDecimal currentHoldingAtRequest) {
        return InboundRequestDTO.builder()
                .accountId(ACCOUNT_ID)
                .foreignProductId(FOREIGN_PRODUCT_ID)
                .requestedQty(requestedQty)
                .currentHoldingAtRequest(currentHoldingAtRequest)
                .build();
    }
}
