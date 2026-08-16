package com.app.maria.domain.inbound.service;

import com.app.maria.domain.account.exception.AccountNotFoundException;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.foreignproduct.dto.ForeignProductDTO;
import com.app.maria.domain.foreignproduct.exception.ForeignProductNotFoundException;
import com.app.maria.domain.foreignproduct.mapper.ForeignProductMapper;
import com.app.maria.domain.inbound.dto.InboundDTO;
import com.app.maria.domain.inbound.dto.InboundDetailDTO;
import com.app.maria.domain.inbound.dto.InboundListDTO;
import com.app.maria.domain.inbound.dto.InboundMinDTO;
import com.app.maria.domain.inbound.dto.InboundPageDTO;
import com.app.maria.domain.inbound.dto.request.InboundRequestDTO;
import com.app.maria.domain.inbound.dto.response.AccountHoldingResponseDTO;
import com.app.maria.domain.inbound.dto.response.InboundResponseDTO;
import com.app.maria.domain.inbound.exception.InboundNotFoundException;
import com.app.maria.domain.inbound.mapper.InboundMapper;
import com.app.maria.domain.registrablestock.dto.RegistrableStockResponseDTO;
import com.app.maria.global.clock.service.BusinessClockService;
import com.app.maria.global.response.ApiResponseDTO;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
@Transactional(rollbackFor = Exception.class)
public class InboundServiceImpl implements InboundService {

    private final InboundMapper inboundMapper;
    private final ForeignProductMapper foreignProductMapper;
    private final AccountMapper accountMapper;
    private final RestClient restClient;
    private final BusinessClockService businessClockService;

    public InboundServiceImpl(
            InboundMapper inboundMapper,
            ForeignProductMapper foreignProductMapper,
            AccountMapper accountMapper,
            @Qualifier("returnSecuritiesRestClient") RestClient restClient,
            BusinessClockService businessClockService) {
        this.inboundMapper = inboundMapper;
        this.foreignProductMapper = foreignProductMapper;
        this.accountMapper = accountMapper;
        this.restClient = restClient;
        this.businessClockService = businessClockService;
    }

    @Override
    public InboundResponseDTO processInbound(InboundRequestDTO request) {

        Long accountId = request.getAccountId();
        Long foreignProductId = request.getForeignProductId();
        BigDecimal requestedQty = request.getRequestedQty();
        BigDecimal currentHoldingAtRequest = request.getCurrentHoldingAtRequest();

        Long customerId =
                accountMapper
                        .selectByAccountId(accountId)
                        .orElseThrow(() -> new AccountNotFoundException("입고 대상 계좌가 존재하지 않습니다."))
                        .getCustomerId();

        String ciHash =
                accountMapper
                        .selectCiHashByCustomerId(customerId)
                        .orElseThrow(
                                () -> new AccountNotFoundException("입고 계좌의 고객 식별정보를 찾을 수 없습니다."));

        ApiResponseDTO<RegistrableStockResponseDTO> apiResponse =
                restClient
                        .get()
                        .uri(
                                "/api/registrable-stocks?ciHash={ciHash}&foreignProductId={foreignProductId}",
                                ciHash,
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
                        .sourceGeneralAccountId(registrableStock.getGeneralAccountId())
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

    @Override
    @Transactional(readOnly = true)
    public List<AccountHoldingResponseDTO> getHoldings(Long accountId) {
        return inboundMapper.selectHoldingsByAccount(accountId).stream()
                .map(
                        holding -> {
                            ForeignProductDTO product =
                                    foreignProductMapper
                                            .selectById(holding.getForeignProductId())
                                            .orElseThrow(
                                                    () ->
                                                            new ForeignProductNotFoundException(
                                                                    "종목 정보를 찾을 수 없습니다."));
                            return new AccountHoldingResponseDTO(product, holding.getCurrentQty());
                        })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InboundPageDTO getInbounds(int page, int size) {
        int offset = page * size;
        List<InboundListDTO> content = inboundMapper.selectInbounds(offset, size);
        long totalElements = inboundMapper.countInbounds();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return InboundPageDTO.builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }
}
