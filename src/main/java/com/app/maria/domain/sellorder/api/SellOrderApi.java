package com.app.maria.domain.sellorder.api;

import com.app.maria.domain.sellorder.dto.request.SellOrderRequestDTO;
import com.app.maria.domain.sellorder.dto.response.SellOrderResponseDTO;
import com.app.maria.domain.sellorder.service.SellOrderService;
import com.app.maria.global.response.ApiResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sell-orders")
public class SellOrderApi {

    private final SellOrderService sellOrderService;

    @PostMapping
    public ResponseEntity<ApiResponseDTO<SellOrderResponseDTO>> placeSellOrder(@Valid @RequestBody SellOrderRequestDTO request) {
        SellOrderResponseDTO responseDTO = sellOrderService.placeSellOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDTO.of("매도 주문이 완료되었습니다.", responseDTO));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponseDTO<SellOrderResponseDTO>> getSellOrder(@PathVariable Long orderId) {
        SellOrderResponseDTO responseDTO = sellOrderService.getSellOrder(orderId);
        return ResponseEntity.ok(ApiResponseDTO.of("매도 주문 조회에 성공하였습니다.", responseDTO));
    }


}
