package com.app.maria.domain.settlement.component;

import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import com.app.maria.domain.settlement.dto.SettlementItemDTO;
import com.app.maria.domain.settlement.exception.SettlementStateConflictException;
import com.app.maria.domain.settlement.mapper.SettlementBatchMapper;
import com.app.maria.domain.settlement.mapper.SettlementItemMapper;
import com.app.maria.domain.settlement.type.BatchStatus;
import com.app.maria.domain.settlement.type.SettlementFailureCode;
import com.app.maria.domain.settlement.type.SettlementItemResult;
import com.app.maria.global.clock.service.BusinessClockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SettlementBatchStatusUpdater {
  private final SettlementBatchMapper settlementBatchMapper;
  private final SettlementItemMapper settlementItemMapper;
  private final BusinessClockService systemClock;

  @Transactional(transactionManager = "transactionManager", propagation = Propagation.REQUIRES_NEW)
  public void markFailed(Long batchId, String failureMessage) {
    String limitedMessage = limitMessage(failureMessage);
    settlementItemMapper.markPendingItemsFailed(SettlementItemDTO.builder()
        .batchId(batchId)
        .result(SettlementItemResult.FAILED)
        .processedAt(systemClock.now())
        .failureCode(SettlementFailureCode.UNKNOWN_ERROR)
        .failureMessage(limitedMessage)
        .build());

    SettlementBatchDTO command = SettlementBatchDTO.builder()
            .batchId(batchId)
            .status(BatchStatus.FAILED)
            .failureMessage(limitedMessage)
            .build();

    int affectedRows = settlementBatchMapper.updateBatchStatus(command);

    if (affectedRows != 1) {
      throw new SettlementStateConflictException("Batch 실패 상태 변경 실패 batchId=" + batchId);
    }
  }

  private String limitMessage(String failureMessage) {
    if (failureMessage == null || failureMessage.isBlank()) {
      return "확정산 Batch 실행 중 알 수 없는 오류가 발생했습니다.";
    }
    return failureMessage.substring(0, Math.min(failureMessage.length(), 500));
  }
}
