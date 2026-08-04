package com.app.maria.domain.settlement.provider;

import com.app.maria.domain.settlement.exception.InvalidSettlementException;
import com.app.maria.global.client.exchange.ExchangeRateClient;
import com.app.maria.global.exception.ExchangeRateNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class ExchangeRateProviderImpl implements ExchangeRateProvider {
  private final ExchangeRateClient exchangeRateClient;

  @Override
  public BigDecimal getFinalRate(String currency, LocalDate searchDate) {
    validateCurrency(currency);
    validateSearchDate(searchDate);

    BigDecimal finalRate = exchangeRateClient.getBaseRate(currency, searchDate);

    return validateFinalRate(finalRate);
  }

  private void validateCurrency(String currency) {
    if (currency == null || currency.isBlank()) {
      throw new InvalidSettlementException("환율 조회 통화 입력");
    }
  }

  private void validateSearchDate(LocalDate searchDate) {
    if (searchDate == null) {
      throw new InvalidSettlementException("환율 기준일 입력");
    }
  }

  private BigDecimal validateFinalRate(BigDecimal finalRate) {
    if (finalRate == null || finalRate.signum() <= 0) {
      throw new ExchangeRateNotFoundException("유효하지 않은 환율 응답");
    }
    return finalRate;
  }
}
