package com.app.maria.domain.settlement.component;

import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import com.app.maria.domain.settlement.exception.SettlementStateConflictException;
import com.app.maria.domain.settlement.mapper.SettlementBatchMapper;
import com.app.maria.domain.settlement.type.BatchStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SettlementBatchStatusUpdater {
  private final SettlementBatchMapper settlementBatchMapper;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailed(Long batchId) {
    SettlementBatchDTO command = SettlementBatchDTO.builder()
            .batchId(batchId)
            .status(BatchStatus.FAILED)
            .build();

    int affectedRows = settlementBatchMapper.updateBatchStatus(command);

    if (affectedRows != 1) {
      throw new SettlementStateConflictException("Batch 실패 상태 변경 실패 batchId=" + batchId);
    }
  }
}
