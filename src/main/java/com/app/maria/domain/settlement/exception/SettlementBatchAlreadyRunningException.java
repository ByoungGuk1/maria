package com.app.maria.domain.settlement.exception;

public class SettlementBatchAlreadyRunningException extends SettlementException {
  public SettlementBatchAlreadyRunningException(String message) {
    super(message);
  }
}
