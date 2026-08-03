package com.app.maria.domain.sellorder.service;

import com.app.maria.domain.sellorder.dto.SellOrderDTO;
import com.app.maria.domain.sellorder.dto.request.SellOrderRequestDTO;
import com.app.maria.domain.sellorder.dto.response.SellOrderResponseDTO;
import com.app.maria.domain.sellorder.exception.SellOrderException;
import com.app.maria.domain.sellorder.exception.SellOrderNotFoundException;
import com.app.maria.domain.sellorder.mapper.SellOrderMapper;
import com.app.maria.domain.sellorder.type.SellOrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class SellOrderServiceImpl implements SellOrderService{

    private final SellOrderMapper sellOrderMapper;

    @Override
    public SellOrderResponseDTO placeSellOrder(SellOrderRequestDTO request) {

        if (request.getSellQty() == null || request.getSellQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new SellOrderException("매도 수량은 0보다 커야 합니다.");
        }

        SellOrderDTO dto = SellOrderDTO.builder()
                .inboundDetailId(request.getInboundDetailId())
                .sellQty(request.getSellQty())
                .status(SellOrderStatus.RECEIVED)
                .basePrice(null)  // C2 연동 후 계산값으로 대체
                .purchaseFxRate(null) // C2 연동 후 계산값으로 대체
                .processedAt(null)  // system_clock 기반 clock 컴포넌트 연동
                .build();
        sellOrderMapper.insertSellOrder(dto);
        return new SellOrderResponseDTO(dto);
    }

    @Override
    @Transactional(readOnly = true)
    public SellOrderResponseDTO getSellOrder(Long orderId) {
        SellOrderDTO dto =  sellOrderMapper.selectSellOrderById(orderId).orElseThrow(() -> new SellOrderNotFoundException("매도 주문 조회 실패"));
        return new SellOrderResponseDTO(dto);
    }

}
