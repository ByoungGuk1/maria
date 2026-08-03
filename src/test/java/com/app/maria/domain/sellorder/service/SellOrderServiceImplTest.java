package com.app.maria.domain.sellorder.service;

import com.app.maria.domain.sellorder.dto.SellOrderDTO;
import com.app.maria.domain.sellorder.dto.request.SellOrderRequestDTO;
import com.app.maria.domain.sellorder.dto.response.SellOrderResponseDTO;
import com.app.maria.domain.sellorder.exception.SellOrderException;
import com.app.maria.domain.sellorder.exception.SellOrderNotFoundException;
import com.app.maria.domain.sellorder.mapper.SellOrderMapper;
import com.app.maria.domain.sellorder.type.SellOrderStatus;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SellOrderServiceImplTest {

    @Mock
    SellOrderMapper sellOrderMapper;

    @InjectMocks
    SellOrderServiceImpl sellOrderService;

    @Test
    void placeSellOrder_정상요청이면_RECEIVED상태로_저장한다() {
        SellOrderRequestDTO request = SellOrderRequestDTO.builder()
                .inboundDetailId(1L)
                .sellQty(new BigDecimal("10"))
                .build();

        SellOrderResponseDTO result = sellOrderService.placeSellOrder(request);

        assertThat(result.getInboundDetailId()).isEqualTo(1L);
        assertThat(result.getSellQty()).isEqualByComparingTo("10");
        assertThat(result.getStatus()).isEqualTo(SellOrderStatus.RECEIVED);
        assertThat(result.getBasePrice()).isNull();
        assertThat(result.getPurchaseFxRate()).isNull();
        assertThat(result.getProcessedAt()).isNull();

        ArgumentCaptor<SellOrderDTO> captor = ArgumentCaptor.forClass(SellOrderDTO.class);
        verify(sellOrderMapper, times(1)).insertSellOrder(captor.capture());
        SellOrderDTO saved = captor.getValue();
        assertThat(saved.getInboundDetailId()).isEqualTo(1L);
        assertThat(saved.getSellQty()).isEqualByComparingTo("10");
        assertThat(saved.getStatus()).isEqualTo(SellOrderStatus.RECEIVED);
    }

    @Test
    void placeSellOrder_수량이_null이면_예외를_던지고_저장하지않는다() {
        SellOrderRequestDTO request = SellOrderRequestDTO.builder()
                .inboundDetailId(1L)
                .sellQty(null)
                .build();

        assertThatThrownBy(() -> sellOrderService.placeSellOrder(request))
                .isInstanceOf(SellOrderException.class);
        verifyNoInteractions(sellOrderMapper);
    }

    @Test
    void placeSellOrder_수량이_0이면_예외를_던진다() {
        SellOrderRequestDTO request = SellOrderRequestDTO.builder()
                .inboundDetailId(1L)
                .sellQty(BigDecimal.ZERO)
                .build();

        assertThatThrownBy(() -> sellOrderService.placeSellOrder(request))
                .isInstanceOf(SellOrderException.class);
        verifyNoInteractions(sellOrderMapper);
    }

    @Test
    void placeSellOrder_수량이_음수이면_예외를_던진다() {
        SellOrderRequestDTO request = SellOrderRequestDTO.builder()
                .inboundDetailId(1L)
                .sellQty(new BigDecimal("-5"))
                .build();

        assertThatThrownBy(() -> sellOrderService.placeSellOrder(request))
                .isInstanceOf(SellOrderException.class);
        verifyNoInteractions(sellOrderMapper);
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