package com.app.maria.domain.tax.batch;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class TaxSnapshotJobLauncher {
    private final JobLauncher jobLauncher;

    @Qualifier("taxSnapshotJob")
    private final Job taxSnapshotJob;

    public JobExecution launch(LocalDateTime calculatedAt) throws JobExecutionException {
        String runId = UUID.randomUUID().toString();
        JobParameters parameters =
                new JobParametersBuilder()
                        .addLocalDateTime("calculatedAt", calculatedAt)
                        .addString("runId", runId)
                        .toJobParameters();

        JobExecution execution = jobLauncher.run(taxSnapshotJob, parameters);
        log.info(
                "세액 스냅샷 Batch 실행. calculatedAt={}, runId={}, status={}",
                calculatedAt,
                runId,
                execution.getStatus());
        return execution;
    }
}
