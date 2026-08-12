package com.app.maria.domain.inbound.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.app.maria.domain.foreignproduct.dto.ForeignProductDTO;
import com.app.maria.domain.foreignproduct.exception.ForeignProductNotFoundException;
import com.app.maria.domain.foreignproduct.mapper.ForeignProductMapper;
import com.app.maria.domain.inbound.dto.InboundHoldingDTO;
import com.app.maria.domain.inbound.dto.request.InboundRequestDTO;
import com.app.maria.domain.inbound.dto.response.AccountHoldingResponseDTO;
import com.app.maria.domain.inbound.dto.response.InboundResponseDTO;
import com.app.maria.domain.inbound.exception.InboundNotFoundException;
import com.app.maria.domain.inbound.mapper.InboundMapper;
import com.app.maria.domain.registrablestock.dto.RegistrableStockResponseDTO;
import com.app.maria.global.clock.service.BusinessClockService;
import com.app.maria.global.response.ApiResponseDTO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 10, 0);

    @Mock private InboundMapper inboundMapper;

    @Mock private ForeignProductMapper foreignProductMapper;

    @Mock private RestClient restClient;

    @Mock private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock private RestClient.RequestHeadersSpec requestHeadersSpec;

    @Mock private RestClient.ResponseSpec responseSpec;

    @Mock private BusinessClockService businessClockService;

    @InjectMocks private InboundServiceImpl inboundService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUpRestClientChain() {
        lenient().when(restClient.get()).thenReturn(requestHeadersUriSpec);
        lenient()
                .when(
                        requestHeadersUriSpec.uri(
                                eq(
                                        "/api/registrable-stocks?generalAccountId={accountId}&foreignProductId={foreignProductId}"),
                                eq(ACCOUNT_ID),
                                eq(FOREIGN_PRODUCT_ID)))
                .thenReturn(requestHeadersSpec);
        lenient().when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        lenient().when(businessClockService.now()).thenReturn(NOW);
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

    @Test
    void getHoldingsEnrichesEachHoldingWithProductInfo() {
        when(inboundMapper.selectHoldingsByAccount(ACCOUNT_ID))
                .thenReturn(List.of(holding(FOREIGN_PRODUCT_ID, BigDecimal.valueOf(50))));
        when(foreignProductMapper.selectById(FOREIGN_PRODUCT_ID))
                .thenReturn(Optional.of(foreignProduct(FOREIGN_PRODUCT_ID)));

        List<AccountHoldingResponseDTO> result = inboundService.getHoldings(ACCOUNT_ID);

        assertThat(result).hasSize(1);
        AccountHoldingResponseDTO first = result.get(0);
        assertThat(first.getForeignProductId()).isEqualTo(FOREIGN_PRODUCT_ID);
        assertThat(first.getTicker()).isEqualTo("AAPL");
        assertThat(first.getName()).isEqualTo("Apple Inc.");
        assertThat(first.getMarket()).isEqualTo("NAS");
        assertThat(first.getCurrency()).isEqualTo("USD");
        assertThat(first.getType()).isEqualTo("FOREIGN_STOCK");
        assertThat(first.getCurrentQty()).isEqualByComparingTo(BigDecimal.valueOf(50));
    }

    @Test
    void getHoldingsReturnsOneEntryPerHoldingInMapperOrder() {
        when(inboundMapper.selectHoldingsByAccount(ACCOUNT_ID))
                .thenReturn(
                        List.of(
                                holding(1L, BigDecimal.valueOf(10)),
                                holding(2L, BigDecimal.valueOf(20))));
        when(foreignProductMapper.selectById(1L)).thenReturn(Optional.of(foreignProduct(1L)));
        when(foreignProductMapper.selectById(2L)).thenReturn(Optional.of(foreignProduct(2L)));

        List<AccountHoldingResponseDTO> result = inboundService.getHoldings(ACCOUNT_ID);

        assertThat(result)
                .extracting(AccountHoldingResponseDTO::getForeignProductId)
                .containsExactly(1L, 2L);
    }

    @Test
    void getHoldingsReturnsEmptyListWhenAccountHasNoHoldings() {
        when(inboundMapper.selectHoldingsByAccount(ACCOUNT_ID)).thenReturn(List.of());

        List<AccountHoldingResponseDTO> result = inboundService.getHoldings(ACCOUNT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void getHoldingsThrowsWhenHeldProductNoLongerExists() {
        when(inboundMapper.selectHoldingsByAccount(ACCOUNT_ID))
                .thenReturn(List.of(holding(FOREIGN_PRODUCT_ID, BigDecimal.valueOf(50))));
        when(foreignProductMapper.selectById(FOREIGN_PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inboundService.getHoldings(ACCOUNT_ID))
                .isInstanceOf(ForeignProductNotFoundException.class)
                .hasMessage("종목 정보를 찾을 수 없습니다.");
    }

    private InboundHoldingDTO holding(Long foreignProductId, BigDecimal currentQty) {
        return InboundHoldingDTO.builder()
                .foreignProductId(foreignProductId)
                .currentQty(currentQty)
                .build();
    }

    private ForeignProductDTO foreignProduct(Long foreignProductId) {
        return ForeignProductDTO.builder()
                .foreignProductId(foreignProductId)
                .ticker("AAPL")
                .name("Apple Inc.")
                .market("NAS")
                .currency("USD")
                .type("FOREIGN_STOCK")
                .build();
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
