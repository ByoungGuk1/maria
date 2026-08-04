package com.app.maria.domain.settlement.provider;

import com.app.maria.domain.settlement.exception.InvalidSettlementException;
import com.app.maria.global.client.exchange.ExchangeRateClient;
import com.app.maria.global.exception.ExchangeRateNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class ExchangeRateProviderImpl implements ExchangeRateProvider {

  private static final int MAX_LOOKBACK_DAYS = 7;

  private final ExchangeRateClient exchangeRateClient;

  public ExchangeRateProviderImpl(@Qualifier("settlementExchangeRateClient")ExchangeRateClient exchangeRateClient) {
    this.exchangeRateClient = exchangeRateClient;
  }

  @Override
  public BigDecimal getFinalRate(String currency, LocalDate searchDate) {
    validateCurrency(currency);
    validateSearchDate(searchDate);

    ExchangeRateNotFoundException lastNotFound = null;
    LocalDate lookupDate = searchDate;

    for (int elapsedDays = 0; elapsedDays <= MAX_LOOKBACK_DAYS; elapsedDays++) {
      BigDecimal finalRate;
      try {
        finalRate = exchangeRateClient.getBaseRate(currency, lookupDate);
      } catch (ExchangeRateNotFoundException e) {
        lastNotFound = e;
        lookupDate = lookupDate.minusDays(1);
        continue;
      } catch (ResourceAccessException e) {
        throw new ExchangeRateNotFoundException("환율 API 연결 또는 응답 시간 초과", e);
      } catch (RestClientException e) {
        throw new ExchangeRateNotFoundException("환율 API 호출 실패", e);
      }
      return validateFinalRate(finalRate);
    }

    throw lastNotFound;
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
