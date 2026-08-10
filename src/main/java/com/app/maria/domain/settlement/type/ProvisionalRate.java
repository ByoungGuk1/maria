package com.app.maria.domain.settlement.type;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public enum ProvisionalRate {
  PROVISIONAL_RATE(new BigDecimal("0.99"));

  private final BigDecimal value;

  ProvisionalRate(BigDecimal value) {
    this.value = value;
  }
}
