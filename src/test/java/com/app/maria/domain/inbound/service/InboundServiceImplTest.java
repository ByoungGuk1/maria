package com.app.maria.domain.inbound.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.exception.AccountNotFoundException;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.foreignproduct.dto.ForeignProductDTO;
import com.app.maria.domain.foreignproduct.exception.ForeignProductNotFoundException;
import com.app.maria.domain.foreignproduct.mapper.ForeignProductMapper;
import com.app.maria.domain.foreignproduct.type.ForeignProductType;
import com.app.maria.domain.inbound.dto.InboundDetailDTO;
import com.app.maria.domain.inbound.dto.InboundHoldingDTO;
import com.app.maria.domain.inbound.dto.InboundListDTO;
import com.app.maria.domain.inbound.dto.InboundPageDTO;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class InboundServiceImplTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long CUSTOMER_ID = 5177L;
    private static final String CI_HASH = "ci-hash-5177";
    private static final Long FOREIGN_PRODUCT_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 10, 0);

    @Mock private InboundMapper inboundMapper;

    @Mock private ForeignProductMapper foreignProductMapper;

    @Mock private AccountMapper accountMapper;

    @Mock private RestClient restClient;

    @Mock private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock private RestClient.RequestHeadersSpec requestHeadersSpec;

    @Mock private RestClient.ResponseSpec responseSpec;

    @Mock private BusinessClockService businessClockService;

    @InjectMocks private InboundServiceImpl inboundService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUpRestClientChain() {
        lenient()
                .when(accountMapper.selectByAccountId(ACCOUNT_ID))
                .thenReturn(
                        Optional.of(
                                AccountDTO.builder()
                                        .accountId(ACCOUNT_ID)
                                        .customerId(CUSTOMER_ID)
                                        .build()));
        lenient()
                .when(accountMapper.selectCiHashByCustomerId(CUSTOMER_ID))
                .thenReturn(Optional.of(CI_HASH));
        lenient().when(restClient.get()).thenReturn(requestHeadersUriSpec);
        lenient()
                .when(
                        requestHeadersUriSpec.uri(
                                eq(
                                        "/api/registrable-stocks?ciHash={ciHash}&foreignProductId={foreignProductId}"),
                                eq(CI_HASH),
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
    void processInboundThrowsAccountNotFoundWhenAccountDoesNotExist() {
        when(accountMapper.selectByAccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                inboundService.processInbound(
                                        request(BigDecimal.valueOf(80), BigDecimal.valueOf(90))))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("입고 대상 계좌가 존재하지 않습니다.");
    }

    @Test
    void processInboundThrowsAccountNotFoundWhenCiHashMissing() {
        when(accountMapper.selectCiHashByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                inboundService.processInbound(
                                        request(BigDecimal.valueOf(80), BigDecimal.valueOf(90))))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("입고 계좌의 고객 식별정보를 찾을 수 없습니다.");
    }

    @Test
    void processInboundResolvesCiHashFromAccountBeforeCallingRegistrableStockApi() {
        stubRegistrableStock(BigDecimal.valueOf(100));
        when(inboundMapper.sumApprovedQtyByAccountAndProduct(ACCOUNT_ID, FOREIGN_PRODUCT_ID))
                .thenReturn(BigDecimal.ZERO);

        inboundService.processInbound(request(BigDecimal.valueOf(80), BigDecimal.valueOf(90)));

        verify(accountMapper).selectByAccountId(ACCOUNT_ID);
        verify(accountMapper).selectCiHashByCustomerId(CUSTOMER_ID);
        verify(requestHeadersUriSpec)
                .uri(
                        "/api/registrable-stocks?ciHash={ciHash}&foreignProductId={foreignProductId}",
                        CI_HASH,
                        FOREIGN_PRODUCT_ID);
    }

    @Test
    void processInboundSetsSourceGeneralAccountIdFromRegistrableStockResponse() {
        stubRegistrableStock(BigDecimal.valueOf(100), 42L);
        when(inboundMapper.sumApprovedQtyByAccountAndProduct(ACCOUNT_ID, FOREIGN_PRODUCT_ID))
                .thenReturn(BigDecimal.ZERO);
        ArgumentCaptor<InboundDetailDTO> captor = ArgumentCaptor.forClass(InboundDetailDTO.class);

        inboundService.processInbound(request(BigDecimal.valueOf(80), BigDecimal.valueOf(90)));

        verify(inboundMapper).insertInboundDetail(captor.capture());
        // 증권사 registrable-stock 응답의 generalAccountId를 그대로 써야 함
        // (예전엔 여기 RIA account_id가 잘못 들어갔었음 - 회귀 방지용 테스트)
        assertThat(captor.getValue().getSourceGeneralAccountId()).isEqualTo(42L);
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
        assertThat(first.getType()).isEqualTo(ForeignProductType.FOREIGN_STOCK);
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

    @Test
    void getInboundsCalculatesOffsetAndDelegatesToMapper() {
        List<InboundListDTO> expected = List.of(InboundListDTO.builder().inboundId(1L).build());
        when(inboundMapper.selectInbounds(20, 10)).thenReturn(expected);
        when(inboundMapper.countInbounds()).thenReturn(25);

        InboundPageDTO result = inboundService.getInbounds(2, 10);

        verify(inboundMapper).selectInbounds(20, 10);
        assertThat(result.getContent()).isEqualTo(expected);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(10);
    }

    @Test
    void getInboundsCalculatesTotalPagesFromTotalElements() {
        when(inboundMapper.selectInbounds(0, 20)).thenReturn(List.of());
        when(inboundMapper.countInbounds()).thenReturn(45);

        InboundPageDTO result = inboundService.getInbounds(0, 20);

        assertThat(result.getTotalElements()).isEqualTo(45);
        assertThat(result.getTotalPages()).isEqualTo(3);
    }

    @Test
    void getInboundsReturnsZeroTotalPagesWhenNoRows() {
        when(inboundMapper.selectInbounds(0, 20)).thenReturn(List.of());
        when(inboundMapper.countInbounds()).thenReturn(0);

        InboundPageDTO result = inboundService.getInbounds(0, 20);

        assertThat(result.getTotalElements()).isEqualTo(0);
        assertThat(result.getTotalPages()).isEqualTo(0);
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
                .foreignProductType(ForeignProductType.FOREIGN_STOCK)
                .build();
    }

    private void stubRegistrableStock(BigDecimal heldQty) {
        stubRegistrableStock(heldQty, null);
    }

    @SuppressWarnings("unchecked")
    private void stubRegistrableStock(BigDecimal heldQty, Long generalAccountId) {
        RegistrableStockResponseDTO registrableStock =
                RegistrableStockResponseDTO.builder()
                        .generalAccountId(generalAccountId)
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
