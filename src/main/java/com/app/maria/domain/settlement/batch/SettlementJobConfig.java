package com.app.maria.domain.settlement.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class SettlementJobConfig {

  @Bean
  public Job settlementJob(
      JobRepository jobRepository,
      Step settlementStep,
      SettlementJobListener listener
  ) {
    return new JobBuilder("settlementJob", jobRepository)
        .start(settlementStep)
        .listener(listener)
        .build();
  }

  @Bean
  public Step settlementStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      SettlementBatchTasklet tasklet
  ) {
    return new StepBuilder("settlementStep", jobRepository)
        .tasklet(tasklet, transactionManager)
        .build();
  }
}
