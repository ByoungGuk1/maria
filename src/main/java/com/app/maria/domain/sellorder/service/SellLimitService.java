package com.app.maria.domain.sellorder.service;

import java.math.BigDecimal;

public interface SellLimitService {

    public void validateSellLimit(Long inboundId, BigDecimal orderAmount);

}
