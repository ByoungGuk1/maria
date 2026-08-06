package com.app.maria.domain.settlement.batch;

import com.app.maria.domain.settlement.component.SettlementBatchStatusUpdater;
import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import com.app.maria.domain.settlement.exception.SettlementStateConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SettlementBatchLauncher {

  private final JobLauncher jobLauncher;
  private final Job settlementJob;
  private final SettlementBatchStatusUpdater statusUpdater;

  @Async("settlementBatchTaskExecutor")
  public void launch(SettlementBatchDTO batch) {
    JobParameters parameters = new JobParametersBuilder()
        .addLong("batchId", batch.getBatchId())
        .addString("runId", batch.getRunId())
        .toJobParameters();
    try {
      jobLauncher.run(settlementJob, parameters);
    } catch (Exception e) {
      statusUpdater.markFailed(batch.getBatchId());
      throw new SettlementStateConflictException("확정산 Batch 실행 실패 : batchId="+batch.getBatchId());
    }
  }
}
