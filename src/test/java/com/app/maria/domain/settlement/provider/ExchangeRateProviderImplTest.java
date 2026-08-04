package com.app.maria.domain.settlement.provider;

import com.app.maria.domain.settlement.exception.InvalidSettlementException;
import com.app.maria.global.client.exchange.ExchangeRateClient;
import com.app.maria.global.exception.ExchangeRateNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateProviderImplTest {

  private static final LocalDate SEARCH_DATE = LocalDate.of(2026, 8, 4);

  @Mock
  private ExchangeRateClient exchangeRateClient;

  @InjectMocks
  private ExchangeRateProviderImpl exchangeRateProvider;

  @Test
  @DisplayName("통화와 기준일로 조회한 정상 환율을 반환한다")
  void getFinalRateReturnsRate() {
    BigDecimal expectedRate = new BigDecimal("1433.60");
    when(exchangeRateClient.getBaseRate("USD", SEARCH_DATE))
        .thenReturn(expectedRate);

    BigDecimal actualRate = exchangeRateProvider.getFinalRate("USD", SEARCH_DATE);

    assertThat(actualRate).isSameAs(expectedRate);
    verify(exchangeRateClient).getBaseRate("USD", SEARCH_DATE);
  }

  @Test
  @DisplayName("통화가 null이면 외부 환율을 조회하지 않고 요청 오류를 반환한다")
  void getFinalRateRejectsNullCurrency() {
    assertThatThrownBy(() -> exchangeRateProvider.getFinalRate(null, SEARCH_DATE))
        .isInstanceOf(InvalidSettlementException.class)
        .hasMessage("환율 조회 통화 입력");

    verifyNoInteractions(exchangeRateClient);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t"})
  @DisplayName("통화가 공백이면 외부 환율을 조회하지 않고 요청 오류를 반환한다")
  void getFinalRateRejectsBlankCurrency(String currency) {
    assertThatThrownBy(() -> exchangeRateProvider.getFinalRate(currency, SEARCH_DATE))
        .isInstanceOf(InvalidSettlementException.class)
        .hasMessage("환율 조회 통화 입력");

    verifyNoInteractions(exchangeRateClient);
  }

  @Test
  @DisplayName("기준일이 null이면 외부 환율을 조회하지 않고 요청 오류를 반환한다")
  void getFinalRateRejectsNullSearchDate() {
    assertThatThrownBy(() -> exchangeRateProvider.getFinalRate("USD", null))
        .isInstanceOf(InvalidSettlementException.class)
        .hasMessage("환율 기준일 입력");

    verifyNoInteractions(exchangeRateClient);
  }

  @Test
  @DisplayName("외부 환율 응답이 null이면 조회 실패로 처리한다")
  void getFinalRateRejectsNullRate() {
    when(exchangeRateClient.getBaseRate("USD", SEARCH_DATE)).thenReturn(null);

    assertThatThrownBy(() -> exchangeRateProvider.getFinalRate("USD", SEARCH_DATE))
        .isInstanceOf(ExchangeRateNotFoundException.class)
        .hasMessage("유효하지 않은 환율 응답");
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-0.01"})
  @DisplayName("외부 환율 응답이 0 이하이면 조회 실패로 처리한다")
  void getFinalRateRejectsNonPositiveRate(String rate) {
    when(exchangeRateClient.getBaseRate("USD", SEARCH_DATE))
        .thenReturn(new BigDecimal(rate));

    assertThatThrownBy(() -> exchangeRateProvider.getFinalRate("USD", SEARCH_DATE))
        .isInstanceOf(ExchangeRateNotFoundException.class)
        .hasMessage("유효하지 않은 환율 응답");
  }

  @Test
  @DisplayName("외부 환율 Client의 조회 실패를 그대로 전파한다")
  void getFinalRatePropagatesClientException() {
    ExchangeRateNotFoundException clientException =
        new ExchangeRateNotFoundException("환율 API 응답 오류");
    when(exchangeRateClient.getBaseRate("USD", SEARCH_DATE))
        .thenThrow(clientException);

    assertThatThrownBy(() -> exchangeRateProvider.getFinalRate("USD", SEARCH_DATE))
        .isSameAs(clientException);
  }
}
