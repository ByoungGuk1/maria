package com.app.maria.domain.inbound.service;

import com.app.maria.domain.inbound.dto.InboundDTO;
import com.app.maria.domain.inbound.dto.InboundDetailDTO;
import com.app.maria.domain.inbound.dto.InboundMinDTO;
import com.app.maria.domain.inbound.dto.request.InboundRequestDTO;
import com.app.maria.domain.inbound.dto.response.InboundResponseDTO;
import com.app.maria.domain.inbound.exception.InboundNotFoundException;
import com.app.maria.domain.inbound.mapper.InboundMapper;
import com.app.maria.domain.registrablestock.dto.RegistrableStockResponseDTO;
import com.app.maria.global.clock.service.BusinessClockService;
import com.app.maria.global.response.ApiResponseDTO;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class InboundServiceImpl implements InboundService {

    private final InboundMapper inboundMapper;
    private final RestClient restClient;
    private final BusinessClockService businessClockService;

    @Override
    public InboundResponseDTO processInbound(InboundRequestDTO request) {

        Long accountId = request.getAccountId();
        Long foreignProductId = request.getForeignProductId();
        BigDecimal requestedQty = request.getRequestedQty();
        BigDecimal currentHoldingAtRequest = request.getCurrentHoldingAtRequest();

        ApiResponseDTO<RegistrableStockResponseDTO> apiResponse =
                restClient
                        .get()
                        .uri(
                                "/api/registrable-stocks?generalAccountId={accountId}&foreignProductId={foreignProductId}",
                                accountId,
                                foreignProductId)
                        .retrieve()
                        .body(
                                new ParameterizedTypeReference<
                                        ApiResponseDTO<RegistrableStockResponseDTO>>() {});

        if (apiResponse == null || apiResponse.getData() == null) {
            throw new InboundNotFoundException("등록가능 보유수량 조회 실패");
        }

        RegistrableStockResponseDTO registrableStock = apiResponse.getData();
        BigDecimal snapshotQty = registrableStock.getHeldQty();

        BigDecimal alreadyApprovedQty =
                inboundMapper.sumApprovedQtyByAccountAndProduct(accountId, foreignProductId);
        BigDecimal availableQty = snapshotQty.subtract(alreadyApprovedQty).max(BigDecimal.ZERO);

        BigDecimal approvedQty = requestedQty.min(availableQty);
        if (currentHoldingAtRequest != null) {
            approvedQty = approvedQty.min(currentHoldingAtRequest);
        }

        InboundDTO inboundDTO =
                InboundDTO.builder()
                        .accountId(accountId)
                        .requestedQty(requestedQty)
                        .currentHoldingAtRequest(currentHoldingAtRequest)
                        .approvedQty(approvedQty)
                        .processedAt(businessClockService.now())
                        .build();
        inboundMapper.insertInbound(inboundDTO);

        InboundDetailDTO inboundDetailDTO =
                InboundDetailDTO.builder()
                        .inboundId(inboundDTO.getInboundId())
                        .foreignProductId(foreignProductId)
                        .qty(approvedQty)
                        .currentQty(approvedQty)
                        .purchaseDate(registrableStock.getPurchaseDate())
                        .purchasePrice(registrableStock.getPurchasePrice())
                        .purchaseCurrency(registrableStock.getPurchaseCurrency())
                        .purchaseFxRate(registrableStock.getPurchaseFxRate())
                        .sourceBroker(registrableStock.getSourceBroker())
                        .sourceGeneralAccountId(
                                registrableStock.getSourceBroker() == null ? accountId : null)
                        .build();
        inboundMapper.insertInboundDetail(inboundDetailDTO);

        InboundMinDTO inboundMinDTO =
                InboundMinDTO.of(
                        inboundDetailDTO.getInboundDetailId(),
                        requestedQty,
                        approvedQty,
                        snapshotQty);
        inboundMapper.insertInboundMin(inboundMinDTO);

        return InboundResponseDTO.of(inboundDTO, snapshotQty);
    }
}
