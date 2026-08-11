package com.app.maria.domain.dashboard.api;

import com.app.maria.domain.dashboard.dto.response.DashboardSummaryResponseDTO;
import com.app.maria.domain.dashboard.service.DashboardService;
import com.app.maria.global.response.ApiResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/dashboard")
public class DashboardApi {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<ApiResponseDTO<DashboardSummaryResponseDTO>> getDashboard() {
        DashboardSummaryResponseDTO response =
                new DashboardSummaryResponseDTO(dashboardService.getDashboardSummary());
        return ResponseEntity.ok(ApiResponseDTO.of("대시보드 조회 성공", response));
    }
}
