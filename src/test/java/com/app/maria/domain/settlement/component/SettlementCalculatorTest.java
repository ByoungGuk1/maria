package com.app.maria.domain.settlement.component;

import com.app.maria.domain.settlement.exception.SettlementCalculationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementCalculatorTest {

  private final SettlementCalculator calculator = new SettlementCalculator();

  @ParameterizedTest(name = "{0}")
  @MethodSource("exchangeRateScenarios")
  @DisplayName("확정환율 상승·하락·동일 시 최종 원화금액을 계산한다")
  void calculateFinalAmountAppliesFinalRate(
      String scenario,
      String provisionalAmount,
      String purchaseFxRate,
      String finalRate,
      String expectedAmount
  ) {
    BigDecimal result = calculator.calculateFinalAmount(
        new BigDecimal(provisionalAmount),
        new BigDecimal(purchaseFxRate),
        new BigDecimal(finalRate)
    );

    assertThat(result).isEqualByComparingTo(expectedAmount);
    assertThat(result.scale()).isEqualTo(2);
  }

  @Test
  @DisplayName("나눗셈이 반복소수여도 외화 scale 8을 적용해 최종금액을 계산한다")
  void calculateFinalAmountHandlesRepeatingDecimal() {
    BigDecimal result = calculator.calculateFinalAmount(
        new BigDecimal("100"),
        new BigDecimal("3"),
        new BigDecimal("2")
    );

    assertThat(result).isEqualByComparingTo("67.34");
    assertThat(result.scale()).isEqualTo(2);
  }

  @Test
  @DisplayName("최종 원화금액은 소수점 셋째 자리에서 HALF_UP 반올림한다")
  void calculateFinalAmountRoundsHalfUp() {
    BigDecimal result = calculator.calculateFinalAmount(
        new BigDecimal("1.005"),
        BigDecimal.ONE,
        BigDecimal.ONE
    );

    assertThat(result).isEqualByComparingTo("1.02");
    assertThat(result.scale()).isEqualTo(2);
  }

  @ParameterizedTest(name = "{0}이 null이면 계산을 차단한다")
  @MethodSource("nullInputs")
  @DisplayName("계산 입력값이 null이면 SettlementCalculationException을 반환한다")
  void calculateFinalAmountRejectsNullInput(
      String fieldName,
      BigDecimal provisionalAmount,
      BigDecimal purchaseFxRate,
      BigDecimal finalRate
  ) {
    assertThatThrownBy(() -> calculator.calculateFinalAmount(
        provisionalAmount,
        purchaseFxRate,
        finalRate
    )).isInstanceOf(SettlementCalculationException.class)
        .hasMessage(fieldName + " - 요청 값 오류");
  }

  @ParameterizedTest(name = "{0}={1}이면 계산을 차단한다")
  @MethodSource("nonPositiveInputs")
  @DisplayName("계산 입력값이 0 이하이면 SettlementCalculationException을 반환한다")
  void calculateFinalAmountRejectsNonPositiveInput(
      String fieldName,
      BigDecimal invalidValue,
      BigDecimal provisionalAmount,
      BigDecimal purchaseFxRate,
      BigDecimal finalRate
  ) {
    assertThatThrownBy(() -> calculator.calculateFinalAmount(
        provisionalAmount,
        purchaseFxRate,
        finalRate
    )).isInstanceOf(SettlementCalculationException.class)
        .hasMessage(fieldName + "은 0보다 커야 합니다.");
  }

  private static Stream<Arguments> exchangeRateScenarios() {
    return Stream.of(
        Arguments.of("확정환율 상승", "2700000.00", "1350.0000", "1400.000000", "2828282.83"),
        Arguments.of("확정환율 하락", "2700000.00", "1350.0000", "1300.000000", "2626262.63"),
        Arguments.of("확정환율 동일", "2700000.00", "1350.0000", "1350.000000", "2727272.73")
    );
  }

  private static Stream<Arguments> nullInputs() {
    return Stream.of(
        Arguments.of("provisionalAmount", null, BigDecimal.ONE, BigDecimal.ONE),
        Arguments.of("purchaseFxRate", BigDecimal.ONE, null, BigDecimal.ONE),
        Arguments.of("finalRate", BigDecimal.ONE, BigDecimal.ONE, null)
    );
  }

  private static Stream<Arguments> nonPositiveInputs() {
    return Stream.of(
        invalidInput("provisionalAmount", BigDecimal.ZERO),
        invalidInput("provisionalAmount", BigDecimal.ONE.negate()),
        invalidInput("purchaseFxRate", BigDecimal.ZERO),
        invalidInput("purchaseFxRate", BigDecimal.ONE.negate()),
        invalidInput("finalRate", BigDecimal.ZERO),
        invalidInput("finalRate", BigDecimal.ONE.negate())
    );
  }

  private static Arguments invalidInput(String fieldName, BigDecimal invalidValue) {
    BigDecimal provisionalAmount = BigDecimal.ONE;
    BigDecimal purchaseFxRate = BigDecimal.ONE;
    BigDecimal finalRate = BigDecimal.ONE;

    if ("provisionalAmount".equals(fieldName)) {
      provisionalAmount = invalidValue;
    } else if ("purchaseFxRate".equals(fieldName)) {
      purchaseFxRate = invalidValue;
    } else {
      finalRate = invalidValue;
    }

    return Arguments.of(
        fieldName,
        invalidValue,
        provisionalAmount,
        purchaseFxRate,
        finalRate
    );
  }
}
