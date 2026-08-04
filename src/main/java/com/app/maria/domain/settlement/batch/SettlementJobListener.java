package com.app.maria.domain.settlement.batch;

import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import com.app.maria.domain.settlement.mapper.SettlementBatchMapper;
import com.app.maria.domain.settlement.type.BatchStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SettlementJobListener implements JobExecutionListener {

  private final SettlementBatchMapper settlementBatchMapper;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void afterJob(JobExecution jobExecution) {
    if (jobExecution.getStatus()
        == org.springframework.batch.core.BatchStatus.COMPLETED) {
      return;
    }

    Long batchId = jobExecution.getJobParameters().getLong("batchId");
    if (batchId == null) {
      return;
    }

    settlementBatchMapper.selectBatchById(batchId).ifPresent(batch -> {
      if (batch.getStatus() == BatchStatus.RUNNING) {
        batch.setStatus(BatchStatus.FAILED);
        settlementBatchMapper.updateBatchStatus(batch);
      }
    });
  }
}
