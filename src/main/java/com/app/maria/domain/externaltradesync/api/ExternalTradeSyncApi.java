package com.app.maria.domain.externaltradesync.api;

import com.app.maria.domain.externaltradesync.launcher.ExternalTradeSyncLauncher;
import com.app.maria.global.response.ApiResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/external-trade-sync")
public class ExternalTradeSyncApi {

    private final ExternalTradeSyncLauncher externalTradeSyncLauncher;

    @PreAuthorize("hasAnyRole('SETTLEMENT', 'ADMIN')")
    @PostMapping("/jobs")
    public ResponseEntity<ApiResponseDTO<Void>> executeSync() {
        externalTradeSyncLauncher.launch();
        return ResponseEntity.accepted().body(ApiResponseDTO.of("외부 순매수 동기화가 실행되었습니다."));
    }
}
