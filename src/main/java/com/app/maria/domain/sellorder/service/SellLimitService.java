package com.app.maria.domain.sellorder.service;

import java.math.BigDecimal;

public interface SellLimitService {

    public boolean isWithinSellLimit(Long inboundId, BigDecimal orderAmount);

}
