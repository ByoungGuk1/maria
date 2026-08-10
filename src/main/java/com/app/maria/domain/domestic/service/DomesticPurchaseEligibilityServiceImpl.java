package com.app.maria.domain.domestic.service;

import com.app.maria.domain.domestic.dto.DomesticProductDTO;
import com.app.maria.domain.domestic.exception.DomesticProductNotFoundException;
import com.app.maria.domain.domestic.mapper.DomesticProductMapper;
import com.app.maria.domain.domestic.type.Type;
import com.app.maria.global.clock.service.BusinessClockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DomesticPurchaseEligibilityServiceImpl implements DomesticPurchaseEligibilityService {

    private static final int DOMESTIC_STOCK_RATIO_THRESHOLD = 80;
    private static final int INCEPTION_GRACE_PERIOD_MONTHS = 1;

    private final DomesticProductMapper domesticProductMapper;
    private final BusinessClockService businessClockService;

    @Override
    public boolean isPurchasable(Long domesticProductId) {
        DomesticProductDTO product = domesticProductMapper.selectById(domesticProductId)
                .orElseThrow(() -> new DomesticProductNotFoundException("종목 정보를 찾을 수 없습니다."));

        if (product.getType() == Type.STOCK) {
            return true;
        }

        LocalDate today = businessClockService.now().toLocalDate();
        return isDomesticStockRatioMet(product) && isInceptionPeriodMet(product, today);
    }

    private boolean isDomesticStockRatioMet(DomesticProductDTO product) {
        return product.getDomesticStockRatio() != null
                && product.getDomesticStockRatio().compareTo(BigDecimal.valueOf(DOMESTIC_STOCK_RATIO_THRESHOLD)) >= 0;
    }

    private boolean isInceptionPeriodMet(DomesticProductDTO product, LocalDate today) {
        return product.getInceptionDate() != null
                && !product.getInceptionDate().isAfter(today.minusMonths(INCEPTION_GRACE_PERIOD_MONTHS));
    }
}
