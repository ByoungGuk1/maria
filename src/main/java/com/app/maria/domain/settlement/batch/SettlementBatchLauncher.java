package com.app.maria.domain.settlement.batch;

import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import com.app.maria.domain.settlement.mapper.SettlementBatchMapper;
import com.app.maria.domain.settlement.type.BatchStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SettlementBatchLauncher {

  private final JobLauncher jobLauncher;
  private final Job settlementJob;
  private final SettlementBatchMapper settlementBatchMapper;

  @Async("settlementBatchTaskExecutor")
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void launch(SettlementBatchDTO batch) {
    JobParameters parameters = new JobParametersBuilder()
        .addLong("batchId", batch.getBatchId())
        .addString("runId", batch.getRunId())
        .toJobParameters();

    try {
      jobLauncher.run(settlementJob, parameters);
    } catch (Exception e) {
      batch.setStatus(BatchStatus.FAILED);
      settlementBatchMapper.updateBatchStatus(batch);
    }
  }
}
