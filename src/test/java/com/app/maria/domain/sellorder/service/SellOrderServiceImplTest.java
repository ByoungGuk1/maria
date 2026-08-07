package com.app.maria.domain.sellorder.service;

import com.app.maria.domain.foreignproduct.dto.ForeignProductDTO;
import com.app.maria.domain.foreignproduct.exception.ForeignProductNotFoundException;
import com.app.maria.domain.foreignproduct.mapper.ForeignProductMapper;
import com.app.maria.domain.inbound.dto.InboundDetailDTO;
import com.app.maria.domain.inbound.exception.InboundNotFoundException;
import com.app.maria.domain.inbound.mapper.InboundMapper;
import com.app.maria.domain.sellorder.dto.SellOrderDTO;
import com.app.maria.domain.sellorder.dto.request.SellOrderRequestDTO;
import com.app.maria.domain.sellorder.dto.response.SellOrderResponseDTO;
import com.app.maria.domain.sellorder.exception.SellOrderException;
import com.app.maria.domain.sellorder.exception.SellOrderNotFoundException;
import com.app.maria.domain.sellorder.mapper.SellOrderMapper;
import com.app.maria.domain.sellorder.type.SellOrderStatus;
import com.app.maria.global.client.exchange.ExchangeRateClient;
import com.app.maria.global.client.kis.KisPriceClient;
import com.app.maria.global.exception.KisPriceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SellOrderServiceImplTest {

    @Mock
    SellOrderMapper sellOrderMapper;

    @Mock
    KisPriceClient kisPriceClient;

    @Mock
    ExchangeRateClient exchangeRateClient;

    @Mock
    InboundMapper inboundMapper;

    @Mock
    ForeignProductMapper foreignProductMapper;

    @Mock
    SellLimitService sellLimitService;

    @InjectMocks
    SellOrderServiceImpl sellOrderService;

    private SellOrderRequestDTO.SellOrderRequestDTOBuilder validRequestBuilder() {
        return SellOrderRequestDTO.builder()
                .inboundDetailId(1L)
                .sellQty(new BigDecimal("10"));
    }

    private InboundDetailDTO validLot() {
        return InboundDetailDTO.builder()
                .inboundDetailId(1L)
                .inboundId(5L)
                .foreignProductId(10L)
                .currentQty(new BigDecimal("50"))
                .purchaseFxRate(new BigDecimal("1300.5"))
                .build();
    }

    private ForeignProductDTO validProduct() {
        return ForeignProductDTO.builder()
                .foreignProductId(10L)
                .market("NAS")
                .ticker("AAPL")
                .currency("USD")
                .build();
    }

    @Test
    @DisplayName("정상 요청이면 잔량을 차감하고 전일종가와 환율을 곱해 RECEIVED 상태로 저장한다")
    void placeSellOrderMultipliesPreviousCloseAndRateAndSavesAsReceivedOnValidRequest() {
        SellOrderRequestDTO request = validRequestBuilder().build();

        when(inboundMapper.selectInboundDetailById(1L)).thenReturn(Optional.of(validLot()));
        when(inboundMapper.decreaseCurrentQty(1L, new BigDecimal("10"))).thenReturn(1);
        when(foreignProductMapper.selectById(10L)).thenReturn(Optional.of(validProduct()));
        when(kisPriceClient.getPreviousClose("NAS", "AAPL")).thenReturn(new BigDecimal("308.91"));
        when(exchangeRateClient.getBaseRate("USD")).thenReturn(new BigDecimal("1433.6"));
        BigDecimal expectedBasePrice = new BigDecimal("308.91").multiply(new BigDecimal("1433.6"));

        SellOrderResponseDTO result = sellOrderService.placeSellOrder(request);

        assertThat(result.getInboundDetailId()).isEqualTo(1L);
        assertThat(result.getSellQty()).isEqualByComparingTo("10");
        assertThat(result.getStatus()).isEqualTo(SellOrderStatus.RECEIVED);
        assertThat(result.getBasePrice()).isEqualByComparingTo(expectedBasePrice);
        assertThat(result.getSettlementFxRate()).isEqualByComparingTo("1433.6");
        assertThat(result.getProcessedAt()).isNull();

        verify(inboundMapper, times(1)).decreaseCurrentQty(1L, new BigDecimal("10"));

        ArgumentCaptor<SellOrderDTO> captor = ArgumentCaptor.forClass(SellOrderDTO.class);
        verify(sellOrderMapper, times(1)).insertSellOrder(captor.capture());
        SellOrderDTO saved = captor.getValue();
        assertThat(saved.getInboundDetailId()).isEqualTo(1L);
        assertThat(saved.getSellQty()).isEqualByComparingTo("10");
        assertThat(saved.getStatus()).isEqualTo(SellOrderStatus.RECEIVED);
        assertThat(saved.getBasePrice()).isEqualByComparingTo(expectedBasePrice);
        assertThat(saved.getSettlementFxRate()).isEqualByComparingTo("1433.6");
    }

    @Test
    @DisplayName("정상 요청이면 sellLimitService에 lot의 inboundId와 매도금액(수량x기준가)을 정확히 넘겨 한도를 검증한다")
    void placeSellOrderCallsSellLimitServiceWithLotInboundIdAndOrderAmount() {
        SellOrderRequestDTO request = validRequestBuilder().build();

        when(inboundMapper.selectInboundDetailById(1L)).thenReturn(Optional.of(validLot()));
        when(inboundMapper.decreaseCurrentQty(1L, new BigDecimal("10"))).thenReturn(1);
        when(foreignProductMapper.selectById(10L)).thenReturn(Optional.of(validProduct()));
        when(kisPriceClient.getPreviousClose("NAS", "AAPL")).thenReturn(new BigDecimal("308.91"));
        when(exchangeRateClient.getBaseRate("USD")).thenReturn(new BigDecimal("1433.6"));
        BigDecimal expectedOrderAmount = new BigDecimal("10")
                .multiply(new BigDecimal("308.91").multiply(new BigDecimal("1433.6")));

        sellOrderService.placeSellOrder(request);

        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(sellLimitService, times(1)).validateSellLimit(eq(5L), amountCaptor.capture());
        assertThat(amountCaptor.getValue()).isEqualByComparingTo(expectedOrderAmount);
    }

    @Test
    @DisplayName("sellLimitService가 한도초과 예외를 던지면 저장하지 않고 그대로 전파한다")
    void placeSellOrderPropagatesExceptionAndSkipsSaveWhenSellLimitExceeded() {
        SellOrderRequestDTO request = validRequestBuilder().build();

        when(inboundMapper.selectInboundDetailById(1L)).thenReturn(Optional.of(validLot()));
        when(inboundMapper.decreaseCurrentQty(1L, new BigDecimal("10"))).thenReturn(1);
        when(foreignProductMapper.selectById(10L)).thenReturn(Optional.of(validProduct()));
        when(kisPriceClient.getPreviousClose("NAS", "AAPL")).thenReturn(new BigDecimal("308.91"));
        when(exchangeRateClient.getBaseRate("USD")).thenReturn(new BigDecimal("1433.6"));
        doThrow(new SellOrderException("매도 한도를 초과했습니다."))
                .when(sellLimitService).validateSellLimit(any(), any());

        assertThatThrownBy(() -> sellOrderService.placeSellOrder(request))
                .isInstanceOf(SellOrderException.class)
                .hasMessage("매도 한도를 초과했습니다.");

        verifyNoInteractions(sellOrderMapper);
    }

    @Test
    @DisplayName("전일종가 조회에 실패하면 예외가 전파되고 저장은 하지 않는다")
    void placeSellOrderPropagatesExceptionAndSkipsSaveWhenPreviousCloseLookupFails() {
        SellOrderRequestDTO request = validRequestBuilder().build();

        when(inboundMapper.selectInboundDetailById(1L)).thenReturn(Optional.of(validLot()));
        when(inboundMapper.decreaseCurrentQty(1L, new BigDecimal("10"))).thenReturn(1);
        when(foreignProductMapper.selectById(10L)).thenReturn(Optional.of(validProduct()));
        when(kisPriceClient.getPreviousClose("NAS", "AAPL"))
                .thenThrow(new KisPriceNotFoundException("전일종가 조회 실패: AAPL"));

        assertThatThrownBy(() -> sellOrderService.placeSellOrder(request))
                .isInstanceOf(KisPriceNotFoundException.class);

        verify(exchangeRateClient, never()).getBaseRate(any());
        verifyNoInteractions(sellOrderMapper);
    }

    @Test
    @DisplayName("수량이 null이면 예외를 던지고 외부 API와 저장소는 호출하지 않는다")
    void placeSellOrderThrowsAndSkipsExternalCallsWhenSellQtyIsNull() {
        SellOrderRequestDTO request = validRequestBuilder().sellQty(null).build();

        assertThatThrownBy(() -> sellOrderService.placeSellOrder(request))
                .isInstanceOf(SellOrderException.class);

        verifyNoInteractions(sellOrderMapper, kisPriceClient, exchangeRateClient, inboundMapper, foreignProductMapper);
    }

    @Test
    @DisplayName("수량이 0이면 예외를 던진다")
    void placeSellOrderThrowsWhenSellQtyIsZero() {
        SellOrderRequestDTO request = validRequestBuilder().sellQty(BigDecimal.ZERO).build();

        assertThatThrownBy(() -> sellOrderService.placeSellOrder(request))
                .isInstanceOf(SellOrderException.class);

        verifyNoInteractions(sellOrderMapper, kisPriceClient, exchangeRateClient, inboundMapper, foreignProductMapper);
    }

    @Test
    @DisplayName("수량이 음수이면 예외를 던진다")
    void placeSellOrderThrowsWhenSellQtyIsNegative() {
        SellOrderRequestDTO request = validRequestBuilder().sellQty(new BigDecimal("-5")).build();

        assertThatThrownBy(() -> sellOrderService.placeSellOrder(request))
                .isInstanceOf(SellOrderException.class);

        verifyNoInteractions(sellOrderMapper, kisPriceClient, exchangeRateClient, inboundMapper, foreignProductMapper);
    }

    @Test
    @DisplayName("입고 상세가 존재하지 않으면 예외를 던지고 차감/외부 API/저장은 하지 않는다")
    void placeSellOrderThrowsWhenInboundDetailNotFound() {
        SellOrderRequestDTO request = validRequestBuilder().build();

        when(inboundMapper.selectInboundDetailById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellOrderService.placeSellOrder(request))
                .isInstanceOf(InboundNotFoundException.class)
                .hasMessage("입고 상세를 찾을 수 없습니다.");

        verify(inboundMapper, never()).decreaseCurrentQty(any(), any());
        verifyNoInteractions(kisPriceClient, exchangeRateClient, sellOrderMapper, foreignProductMapper);
    }

    @Test
    @DisplayName("매도 수량이 잔량을 초과하면 예외를 던지고 차감/외부 API/저장은 하지 않는다")
    void placeSellOrderThrowsWhenSellQtyExceedsCurrentQty() {
        SellOrderRequestDTO request = validRequestBuilder().sellQty(new BigDecimal("100")).build();
        InboundDetailDTO lot = InboundDetailDTO.builder()
                .inboundDetailId(1L)
                .currentQty(new BigDecimal("50"))
                .build();
        when(inboundMapper.selectInboundDetailById(1L)).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> sellOrderService.placeSellOrder(request))
                .isInstanceOf(SellOrderException.class)
                .hasMessage("매도 가능 수량을 초과했습니다.");

        verify(inboundMapper, never()).decreaseCurrentQty(any(), any());
        verifyNoInteractions(kisPriceClient, exchangeRateClient, sellOrderMapper, foreignProductMapper);
    }

    @Test
    @DisplayName("종목 정보를 찾을 수 없으면 예외를 던지고 잔량 차감/외부 API/저장은 하지 않는다")
    void placeSellOrderThrowsWhenForeignProductNotFound() {
        SellOrderRequestDTO request = validRequestBuilder().build();

        when(inboundMapper.selectInboundDetailById(1L)).thenReturn(Optional.of(validLot()));
        when(foreignProductMapper.selectById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellOrderService.placeSellOrder(request))
                .isInstanceOf(ForeignProductNotFoundException.class)
                .hasMessage("종목 정보를 찾을 수 없습니다.");

        verify(inboundMapper, never()).decreaseCurrentQty(any(), any());
        verifyNoInteractions(kisPriceClient, exchangeRateClient, sellOrderMapper);
    }

    @Test
    @DisplayName("동시성 충돌로 차감된 row가 없으면 예외를 던지고 외부 API/저장은 하지 않는다")
    void placeSellOrderThrowsWhenDecreaseCurrentQtyAffectsNoRows() {
        SellOrderRequestDTO request = validRequestBuilder().build();

        when(inboundMapper.selectInboundDetailById(1L)).thenReturn(Optional.of(validLot()));
        when(foreignProductMapper.selectById(10L)).thenReturn(Optional.of(validProduct()));
        when(inboundMapper.decreaseCurrentQty(1L, new BigDecimal("10"))).thenReturn(0);

        assertThatThrownBy(() -> sellOrderService.placeSellOrder(request))
                .isInstanceOf(SellOrderException.class)
                .hasMessage("다른 요청이 먼저 처리되었습니다.");

        verifyNoInteractions(kisPriceClient, exchangeRateClient, sellOrderMapper);
    }

    @Test
    @DisplayName("존재하면 조회 결과를 반환한다")
    void getSellOrderReturnsResultWhenExists() {
        SellOrderDTO saved = SellOrderDTO.builder()
                .orderId(100L)
                .inboundDetailId(1L)
                .sellQty(new BigDecimal("10"))
                .status(SellOrderStatus.RECEIVED)
                .build();
        when(sellOrderMapper.selectSellOrderById(100L)).thenReturn(Optional.of(saved));

        SellOrderResponseDTO result = sellOrderService.getSellOrder(100L);

        assertThat(result.getOrderId()).isEqualTo(100L);
        assertThat(result.getInboundDetailId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(SellOrderStatus.RECEIVED);
    }

    @Test
    @DisplayName("존재하지 않으면 SellOrderNotFoundException을 던진다")
    void getSellOrderThrowsSellOrderNotFoundExceptionWhenNotFound() {
        when(sellOrderMapper.selectSellOrderById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellOrderService.getSellOrder(999L))
                .isInstanceOf(SellOrderNotFoundException.class);
    }
}
