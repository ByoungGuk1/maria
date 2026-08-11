package com.app.maria.domain.sellorder.service;

import java.math.BigDecimal;

public interface SellLimitService {

    boolean isWithinSellLimit(Long accountId, BigDecimal orderAmount);
}
