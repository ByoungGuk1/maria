package com.app.maria.domain.targetproduct.service;

import com.app.maria.domain.mydatafund.dto.MydataFundResponseDTO;
import com.app.maria.domain.targetproduct.dto.TargetProductJudgementDTO;
import com.app.maria.domain.targetproduct.mapper.TargetProductMapper;
import com.app.maria.global.client.mydatafund.MydataFundClient;
import com.app.maria.global.clock.service.BusinessClockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class TargetProductServiceImpl implements TargetProductService {

    private static final int FOREIGN_STOCK_RATIO_THRESHOLD = 60;
    private static final int INCEPTION_GRACE_PERIOD_MONTHS = 1;

    private final TargetProductMapper targetProductMapper;
    private final MydataFundClient mydataFundClient;
    private final BusinessClockService businessClockService;

    @Override
    public TargetProductJudgementDTO judge(Long mydataTradeId, String stockType, String fundCode) {

        boolean isTarget;
        BigDecimal foreignStockRatio = null;
        LocalDate inceptionDate = null;
        String fundName = null;
        LocalDateTime now = businessClockService.now();
        LocalDate today = now.toLocalDate();

        if ("FUND".equals(stockType)) {
            MydataFundResponseDTO fund = mydataFundClient.getFund(fundCode);

            if (fund.getForeignStockRatio() != null) {
                foreignStockRatio = fund.getForeignStockRatio();
            }
            inceptionDate = fund.getInceptionDate();
            fundName = fund.getFundName();

            isTarget = isForeignStockRatioMet(fund) && isInceptionPeriodMet(fund, today);
        } else {
            // FOREIGN_STOCK / ETF / ETN: 비중요건 없이 전부 대상
            isTarget = true;
        }

        TargetProductJudgementDTO dto = new TargetProductJudgementDTO();
        dto.setMydataTradeId(mydataTradeId);
        dto.setFundCode(fundCode);
        dto.setFundName(fundName);
        dto.setIsTarget(isTarget);
        dto.setForeignStockRatio(foreignStockRatio);
        dto.setInceptionDate(inceptionDate);
        dto.setJudgedAt(now);

        targetProductMapper.insertJudgement(dto);

        return dto;
    }

    private boolean isForeignStockRatioMet(MydataFundResponseDTO fund) {
        return fund.getForeignStockRatio() != null
                && fund.getForeignStockRatio().compareTo(BigDecimal.valueOf(FOREIGN_STOCK_RATIO_THRESHOLD)) >= 0;
    }

    private boolean isInceptionPeriodMet(MydataFundResponseDTO fund, LocalDate today) {
        return fund.getInceptionDate() != null
                && !fund.getInceptionDate().isAfter(today.minusMonths(INCEPTION_GRACE_PERIOD_MONTHS));
    }
}
