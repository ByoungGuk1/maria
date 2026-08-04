package com.app.maria.domain.sellorder.service;

import com.app.maria.domain.sellorder.dto.request.SellOrderRequestDTO;
import com.app.maria.domain.sellorder.dto.response.SellOrderResponseDTO;

public interface SellOrderService {

    // 매도 주문 접수
    public SellOrderResponseDTO placeSellOrder(SellOrderRequestDTO request);

    // 단건 조회
    public SellOrderResponseDTO getSellOrder(Long orderId);

}
