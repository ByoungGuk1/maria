package com.app.maria.domain.tax.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// 이슈 #161 - 세액 스냅샷 배치는 실제 Spring Batch(JobLauncher)로 돌기 때문에
// 별도 이력 테이블 없이 Spring Batch가 이미 쌓아둔 실행 메타데이터(JobExplorer)를 그대로 읽어 보여준다.
@Getter
@Builder
@AllArgsConstructor
public class TaxBatchHistoryDTO {
    private Long jobExecutionId;
    private String runId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private String exitStatus;
    private long readCount;
    private long writeCount;
    private long skipCount;
}
