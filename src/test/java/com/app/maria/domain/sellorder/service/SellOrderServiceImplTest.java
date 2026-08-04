package com.app.maria.domain.sellorder.service;

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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SellOrderServiceImplTest {

    @Mock
    SellOrderMapper sellOrderMapper;

    @Mock
    KisPriceClient kisPriceClient;

    @Mock
    ExchangeRateClient exchangeRateClient;

    @InjectMocks
    SellOrderServiceImpl sellOrderService;

    private SellOrderRequestDTO.SellOrderRequestDTOBuilder validRequestBuilder() {
        return SellOrderRequestDTO.builder()
                .inboundDetailId(1L)
                .sellQty(new BigDecimal("10"))
                .exchangeCode("NAS")
                .ticker("AAPL")
                .currencyUnit("USD");
    }

    @Test
    void placeSellOrder_정상요청이면_전일종가와_환율을_곱해_RECEIVED상태로_저장한다() {
        SellOrderRequestDTO request = validRequestBuilder().build();

        when(kisPriceClient.getPreviousClose("NAS", "AAPL")).thenReturn(new BigDecimal("308.91"));
        when(exchangeRateClient.getBaseRate("USD")).thenReturn(new BigDecimal("1433.6"));
        BigDecimal expectedBasePrice = new BigDecimal("308.91").multiply(new BigDecimal("1433.6"));

        SellOrderResponseDTO result = sellOrderService.placeSellOrder(request);

        assertThat(result.getInboundDetailId()).isEqualTo(1L);
        assertThat(result.getSellQty()).isEqualByComparingTo("10");
        assertThat(result.getStatus()).isEqualTo(SellOrderStatus.RECEIVED);
        assertThat(result.getBasePrice()).isEqualByComparingTo(expectedBasePrice);
        assertThat(result.getPurchaseFxRate()).isNull();
        assertThat(result.getProcessedAt()).isNull();

        ArgumentCaptor<SellOrderDTO> captor = ArgumentCaptor.forClass(SellOrderDTO.class);
        verify(sellOrderMapper, times(1)).insertSellOrder(captor.capture());
        SellOrderDTO saved = captor.getValue();
        assertThat(saved.getInboundDetailId()).isEqualTo(1L);
        assertThat(saved.getSellQty()).isEqualByComparingTo("10");
        assertThat(saved.getStatus()).isEqualTo(SellOrderStatus.RECEIVED);
        assertThat(saved.getBasePrice()).isEqualByComparingTo(expectedBasePrice);
    }

    @Test
    void placeSellOrder_전일종가조회에_실패하면_예외가_전파되고_환율조회와_저장은_하지않는다() {
        SellOrderRequestDTO request = validRequestBuilder().build();

        when(kisPriceClient.getPreviousClose("NAS", "AAPL"))
                .thenThrow(new KisPriceNotFoundException("전일종가 조회 실패: AAPL"));

        assertThatThrownBy(() -> sellOrderService.placeSellOrder(request))
                .isInstanceOf(KisPriceNotFoundException.class);

        verify(exchangeRateClient, never()).getBaseRate(any());
        verifyNoInteractions(sellOrderMapper);
    }

    @Test
    void placeSellOrder_수량이_null이면_예외를_던지고_외부API와_저장소는_호출하지않는다() {
        SellOrderRequestDTO request = validRequestBuilder().sellQty(null).build();

        assertThatThrownBy(() -> sellOrderService.placeSellOrder(request))
                .isInstanceOf(SellOrderException.class);

        verifyNoInteractions(sellOrderMapper, kisPriceClient, exchangeRateClient);
    }

    @Test
    void placeSellOrder_수량이_0이면_예외를_던진다() {
        SellOrderRequestDTO request = validRequestBuilder().sellQty(BigDecimal.ZERO).build();

        assertThatThrownBy(() -> sellOrderService.placeSellOrder(request))
                .isInstanceOf(SellOrderException.class);

        verifyNoInteractions(sellOrderMapper, kisPriceClient, exchangeRateClient);
    }

    @Test
    void placeSellOrder_수량이_음수이면_예외를_던진다() {
        SellOrderRequestDTO request = validRequestBuilder().sellQty(new BigDecimal("-5")).build();

        assertThatThrownBy(() -> sellOrderService.placeSellOrder(request))
                .isInstanceOf(SellOrderException.class);

        verifyNoInteractions(sellOrderMapper, kisPriceClient, exchangeRateClient);
    }

    @Test
    void getSellOrder_존재하면_조회결과를_반환한다() {
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
    void getSellOrder_존재하지않으면_SellOrderNotFoundException을_던진다() {
        when(sellOrderMapper.selectSellOrderById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellOrderService.getSellOrder(999L))
                .isInstanceOf(SellOrderNotFoundException.class);
    }
}
