package com.app.maria.domain.inbound.service;

import com.app.maria.domain.inbound.dto.InboundDTO;
import com.app.maria.domain.inbound.dto.InboundDetailDTO;
import com.app.maria.domain.inbound.dto.InboundMinDTO;
import com.app.maria.domain.inbound.dto.response.InboundResponseDTO;
import com.app.maria.domain.inbound.exception.InboundException;
import com.app.maria.domain.inbound.exception.InboundNotFoundException;
import com.app.maria.domain.inbound.mapper.InboundMapper;
import com.app.maria.domain.registrablestock.dto.RegistrableStockResponseDTO;
import com.app.maria.global.response.ApiResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class InboundServiceImpl implements InboundService {

    private final InboundMapper inboundMapper;
    private final RestClient restClient;

    @Override
    public InboundResponseDTO processInbound(
            Long accountId,
            Long foreignProductId,
            BigDecimal requestedQty,
            BigDecimal currentHoldingAtRequest) {

        if (accountId == null) {
            throw new InboundException("accountId는 필수입니다.");
        }
        if (foreignProductId == null) {
            throw new InboundException("foreignProductId는 필수입니다.");
        }
        if (requestedQty == null) {
            throw new InboundException("requestedQty는 필수입니다.");
        }

        ApiResponseDTO<RegistrableStockResponseDTO> apiResponse = restClient.get()
                .uri("/api/registrable-stocks?generalAccountId={accountId}&foreignProductId={foreignProductId}", accountId, foreignProductId)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponseDTO<RegistrableStockResponseDTO>>() {
                });

        if (apiResponse == null || apiResponse.getData() == null) {
            throw new InboundNotFoundException("등록가능 보유수량 조회 실패");
        }

        RegistrableStockResponseDTO registrableStock = apiResponse.getData();
        BigDecimal snapshotQty = registrableStock.getHeldQty();

        BigDecimal approvedQty = requestedQty
                .min(currentHoldingAtRequest)
                .min(snapshotQty);

        InboundDTO inboundDTO = InboundDTO.builder()
                .accountId(accountId)
                .requestedQty(requestedQty)
                .currentHoldingAtRequest(currentHoldingAtRequest)
                .approvedQty(approvedQty)
                .processedAt(LocalDateTime.now())
                .build();
        inboundMapper.insertInbound(inboundDTO);

        InboundDetailDTO inboundDetailDTO = InboundDetailDTO.builder()
                .inboundId(inboundDTO.getInboundId())
                .foreignProductId(foreignProductId)
                .qty(approvedQty.longValue())
                .currentQty(approvedQty.longValue())
                .purchaseDate(registrableStock.getPurchaseDate())
                .purchasePrice(registrableStock.getPurchasePrice())
                .purchaseCurrency(registrableStock.getPurchaseCurrency())
                .purchaseFxRate(registrableStock.getPurchaseFxRate())
                .sourceBroker(registrableStock.getSourceBroker())
                .sourceGeneralAccountId(registrableStock.getSourceBroker() == null ? accountId : null)
                .build();
        inboundMapper.insertInboundDetail(inboundDetailDTO);

        InboundMinDTO inboundMinDTO = InboundMinDTO.builder()
                .inboundDetailId(inboundDetailDTO.getInboundDetailId())
                .requestedQty(requestedQty)
                .approvedQty(approvedQty)
                .snapshotQty(snapshotQty)
                .build();
        inboundMapper.insertInboundMin(inboundMinDTO);

        return InboundResponseDTO.builder()
                .inboundId(inboundDTO.getInboundId())
                .requestedQty(requestedQty)
                .snapshotQty(snapshotQty)
                .currentHoldingAtRequest(currentHoldingAtRequest)
                .approvedQty(approvedQty)
                .processedAt(inboundDTO.getProcessedAt())
                .build();
    }
}