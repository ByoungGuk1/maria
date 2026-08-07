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
import com.app.maria.global.client.kis.KisExchangeCode;
import com.app.maria.global.client.kis.KisPriceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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
    private final ForeignProductMapper foreignProductMapper;
    private final SellLimitService sellLimitService;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SellOrderResponseDTO placeSellOrder(SellOrderRequestDTO request) {

        if (request.getSellQty() == null || request.getSellQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new SellOrderException("매도 수량은 0보다 커야 합니다.");
        }

        SellOrderDTO dto = request.toSellOrderDTO();
        dto.setStatus(SellOrderStatus.RECEIVED);

        Optional<InboundDetailDTO> lot = inboundMapper.selectInboundDetailById(dto.getInboundDetailId());
        if (lot.isEmpty()) {
            throw new InboundNotFoundException("입고 상세를 찾을 수 없습니다.");
        }
        if (dto.getSellQty().compareTo(lot.get().getCurrentQty()) > 0) {
            throw new SellOrderException("매도 가능 수량을 초과했습니다.");
        }

        ForeignProductDTO product = foreignProductMapper.selectById(lot.get().getForeignProductId())
                .orElseThrow(() -> new ForeignProductNotFoundException("종목 정보를 찾을 수 없습니다."));

        int updateRows = inboundMapper.decreaseCurrentQty(dto.getInboundDetailId(), dto.getSellQty());
        if (updateRows == 0) {
            throw new SellOrderException("다른 요청이 먼저 처리되었습니다.");
        }

        String kisMarketCode = KisExchangeCode.fromMarket(product.getMarket());
        BigDecimal previousClose = kis.getPreviousClose(kisMarketCode, product.getTicker());
        BigDecimal exchangeRate = exchange.getBaseRate(product.getCurrency());
        dto.setBasePrice(previousClose.multiply(exchangeRate));
        dto.setSettlementFxRate(exchangeRate);

        BigDecimal orderAmount = dto.getSellQty().multiply(dto.getBasePrice());
        sellLimitService.validateSellLimit(lot.get().getInboundId(), orderAmount);

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
