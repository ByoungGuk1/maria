package com.app.maria.domain.targetproduct.api;

import com.app.maria.domain.targetproduct.dto.response.TargetProductJudgementListResponseDTO;
import com.app.maria.domain.targetproduct.service.TargetProductService;
import com.app.maria.global.response.ApiResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/target-products")
@PreAuthorize("hasAnyRole('ADMIN', 'SETTLEMENT', 'REVIEWER', 'VIEWER')")
public class TargetProductApi {

    private final TargetProductService targetProductService;

    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<TargetProductJudgementListResponseDTO>>> getRecentJudgements() {
        List<TargetProductJudgementListResponseDTO> result = targetProductService.getRecentJudgements()
                .stream()
                .map(TargetProductJudgementListResponseDTO::new)
                .toList();
        return ResponseEntity.ok(ApiResponseDTO.of("외부 순매수 판정 목록 조회 성공", result));
    }
}
