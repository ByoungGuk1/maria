package com.app.maria.domain.sellorder.service;

import com.app.maria.domain.sellorder.exception.SellOrderException;
import com.app.maria.domain.sellorder.mapper.SellLimitMapper;
import com.app.maria.global.client.mydata.MydataClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class SellLimitServiceImpl implements SellLimitService {

    private final SellLimitMapper sellLimitMapper;
    private final MydataClient mydataClient;

    @Override
    public void validateSellLimit(Long inboundId, BigDecimal orderAmount) {
        Long accountId = sellLimitMapper.selectAccountByInboundId(inboundId)
                .orElseThrow(() -> new SellOrderException("계좌 정보를 찾을 수 없습니다."));

        BigDecimal limitAmount = sellLimitMapper.selectAccountLimitForUpdate(accountId)
                .orElseThrow(() -> new SellOrderException("계좌 한도 정보를 찾을 수 없습니다."));

        BigDecimal finalizedSum = sellLimitMapper.sumFinalizedExchangeAmount(accountId);

        BigDecimal pendingSum = sellLimitMapper.sumPendingSellOrderAmount(accountId);

        String ciHash = sellLimitMapper.selectCiHashByAccountId(accountId)
                .orElseThrow(() ->  new SellOrderException("고객 정보를 확인할 수 없습니다."));

        BigDecimal externalSum = mydataClient.getExternalSellTotal(ciHash);

        BigDecimal totalAfterThisOrder = finalizedSum.add(pendingSum).add(externalSum).add(orderAmount);
        if (totalAfterThisOrder.compareTo(limitAmount) > 0) {
            throw new SellOrderException("매도 한도를 초과했습니다.");
        }
    }

}
