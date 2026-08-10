package com.app.maria.domain.externaltradesync.launcher;

import com.app.maria.domain.externaltradesync.service.ExternalTradeSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalTradeSyncLauncher {

    private final ExternalTradeSyncService externalTradeSyncService;

    @Async("externalTradeSyncTaskExecutor")
    public void launch() {
        try {
            externalTradeSyncService.syncAll();
        } catch (Exception e) {
            log.error("외부 순매수 동기화 실행 중 예외 발생", e);
        }
    }
}
