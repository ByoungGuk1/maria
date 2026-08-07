package com.app.maria.domain.sellorder.api;

import com.app.maria.domain.sellorder.dto.request.SellOrderRequestDTO;
import com.app.maria.domain.sellorder.dto.response.SellOrderResponseDTO;
import com.app.maria.domain.sellorder.service.SellOrderService;
import com.app.maria.global.response.ApiResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sell-orders")
public class SellOrderApi {

    private final SellOrderService sellOrderService;

    @PreAuthorize("hasAnyRole('SETTLEMENT', 'ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponseDTO<SellOrderResponseDTO>> placeSellOrder(@Valid @RequestBody SellOrderRequestDTO request) {
        SellOrderResponseDTO responseDTO = sellOrderService.placeSellOrder(request);
        String message = switch(responseDTO.getStatus()) {
            case EXECUTED -> "매도 주문이 체결되었습니다.";
            case REJECTED -> "매도 한도 초과로 거부되었습니다.";
            case RECEIVED -> "매도 주문이 접수되었습니다.";
        };
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDTO.of(message, responseDTO));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponseDTO<SellOrderResponseDTO>> getSellOrder(@PathVariable Long orderId) {
        SellOrderResponseDTO responseDTO = sellOrderService.getSellOrder(orderId);
        return ResponseEntity.ok(ApiResponseDTO.of("매도 주문 조회에 성공하였습니다.", responseDTO));
    }

    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<SellOrderResponseDTO>>> getAllSellOrders(@RequestParam Long accountId) {
        List<SellOrderResponseDTO> list = sellOrderService.getSellOrderByAccount(accountId);
        return ResponseEntity.ok(ApiResponseDTO.of("계좌 매도 주문 조회에 성공하였습니다.", list));
    }


}
