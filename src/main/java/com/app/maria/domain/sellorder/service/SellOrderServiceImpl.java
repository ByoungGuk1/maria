package com.app.maria.domain.sellorder.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class SellOrderServiceImpl implements SellOrderService{

    private final SellOrderMapper sellOrderMapper;
    private final KisPriceClient kis;
    private final ExchangeRateClient exchange;
    private final InboundMapper inboundMapper;

    @Override
    public SellOrderResponseDTO placeSellOrder(SellOrderRequestDTO request) {

        if (request.getSellQty() == null || request.getSellQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new SellOrderException("매도 수량은 0보다 커야 합니다.");
        }

        Optional<InboundDetailDTO> lot = inboundMapper.selectInboundDetailById(request.getInboundDetailId());
        if (lot.isEmpty()) {
            throw new InboundNotFoundException("입고 상세를 찾을 수 없습니다.");
        }
        if (request.getSellQty().compareTo(lot.get().getCurrentQty()) > 0) {
            throw new SellOrderException("매도 가능 수량을 초과했습니다.");
        }

        int updateRows = inboundMapper.decreaseCurrentQty(request.getInboundDetailId(), request.getSellQty());
        if (updateRows == 0) {
            throw new SellOrderException("다른 요청이 먼저 처리되었습니다.");
        }

        BigDecimal previousClose = kis.getPreviousClose(request.getExchangeCode(), request.getTicker());
        BigDecimal exchangeRate = exchange.getBaseRate(request.getCurrencyUnit());
        BigDecimal basePrice = previousClose.multiply(exchangeRate);

        SellOrderDTO dto = new SellOrderDTO();
        dto.setInboundDetailId(request.getInboundDetailId());
        dto.setSellQty(request.getSellQty());
        dto.setStatus(SellOrderStatus.RECEIVED);
        dto.setBasePrice(basePrice);
        dto.setSettlementFxRate(exchangeRate);
        sellOrderMapper.insertSellOrder(dto);

        return new SellOrderResponseDTO(dto);
    }

    @Override
    @Transactional(readOnly = true)
    public SellOrderResponseDTO getSellOrder(Long orderId) {
        SellOrderDTO dto =  sellOrderMapper.selectSellOrderById(orderId).orElseThrow(() -> new SellOrderNotFoundException("매도 주문 조회 실패"));
        return new SellOrderResponseDTO(dto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SellOrderResponseDTO> getSellOrderByAccount(Long accountId) {
        List<SellOrderDTO> orders = sellOrderMapper.selectSellOrdersByAccountId(accountId);
        return orders.stream()
                .map(SellOrderResponseDTO::new)
                .toList();
    }

}
