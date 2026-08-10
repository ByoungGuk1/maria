package com.app.maria.domain.settlement.component;

import com.app.maria.domain.settlement.exception.SettlementCalculationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProvisionalExchangeCalculatorTest {
  private final ProvisionalExchangeCalculator calculator = new ProvisionalExchangeCalculator();

  @Test
  void calculatesProvisionalAmountWithOnePercentAdjustment() {
    BigDecimal provisionalAmount = calculator.calculate(new BigDecimal("10"), new BigDecimal("270000"));

    assertThat(provisionalAmount)
        .isEqualByComparingTo("2673000");
    assertThat(provisionalAmount.scale()).isZero();
  }

  @Test
  void roundsProvisionalAmountToKrwUnitUsingHalfUp() {
    BigDecimal provisionalAmount = calculator.calculate(new BigDecimal("1"), new BigDecimal("100.51"));

    assertThat(provisionalAmount)
        .isEqualByComparingTo("100");
    assertThat(provisionalAmount.scale()).isZero();
  }

  @Test
  void rejectsMissingOrNonPositiveInputs() {
    assertThatThrownBy(() -> calculator.calculate(null, BigDecimal.ONE))
        .isInstanceOf(SettlementCalculationException.class);
    assertThatThrownBy(() -> calculator.calculate(BigDecimal.ZERO, BigDecimal.ONE))
        .isInstanceOf(SettlementCalculationException.class);
    assertThatThrownBy(() -> calculator.calculate(BigDecimal.ONE, BigDecimal.ZERO))
        .isInstanceOf(SettlementCalculationException.class);
  }
}
