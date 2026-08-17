package com.app.maria.domain.tax.scheduler;

import com.app.maria.domain.tax.batch.TaxSnapshotJobLauncher;
import com.app.maria.global.clock.service.BusinessClockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class TaxSnapshotSchedule {
    private final TaxSnapshotJobLauncher taxSnapshotJobLauncher;
    private final BusinessClockService clockService;

    /** 확정산(00:00)·myData 동기화(01:00)가 끝난 뒤에 돌아야 그날 데이터가 반영된다. */
    @Scheduled(
            cron = "${custom.tax.snapshot-cron:0 0 2 * * *}",
            zone = "${custom.tax.zone:Asia/Seoul}")
    public void executeDailyTaxSnapshotSchedule() {
        try {
            taxSnapshotJobLauncher.launch(clockService.now());
        } catch (Exception e) {
            log.error("세액 스냅샷 Batch 실행 실패.", e);
        }
    }
}
