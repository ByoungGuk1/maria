package com.app.maria.domain.tax.scheduler;

import com.app.maria.global.clock.service.BusinessClockService;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class TaxSnapshotSchedule {
    private final JobLauncher jobLauncher;
    private final BusinessClockService clockService;

    @Qualifier("taxSnapshotJob")
    private final Job taxSnapshotJob;

    /** 확정산(00:00)·myData 동기화(01:00)가 끝난 뒤에 돌아야 그날 데이터가 반영된다. */
    @Scheduled(
            cron = "${custom.tax.snapshot-cron:0 0 2 * * *}",
            zone = "${custom.tax.zone:Asia/Seoul}")
    public void executeDailyTaxSnapshotSchedule() {
        LocalDateTime calculatedAt = clockService.now();
        String runId = UUID.randomUUID().toString();

        JobParameters parameters =
                new JobParametersBuilder()
                        .addLocalDateTime("calculatedAt", calculatedAt)
                        .addString("runId", runId)
                        .toJobParameters();
        try {
            jobLauncher.run(taxSnapshotJob, parameters);
            log.info("세액 스냅샷 Batch 실행완료. calculatedAt={}, runId={}", calculatedAt, runId);
        } catch (Exception e) {
            log.error("세액 스냅샷 Batch 실행 실패. runId={}", runId, e);
        }
    }
}
