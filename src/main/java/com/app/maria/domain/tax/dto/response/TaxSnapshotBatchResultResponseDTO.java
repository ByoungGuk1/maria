package com.app.maria.domain.tax.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;

@Getter
@Builder
@AllArgsConstructor
public class TaxSnapshotBatchResultResponseDTO {
    private Long jobExecutionId;
    private BatchStatus status;

    public static TaxSnapshotBatchResultResponseDTO of(JobExecution execution) {
        return TaxSnapshotBatchResultResponseDTO.builder()
                .jobExecutionId(execution.getId())
                .status(execution.getStatus())
                .build();
    }
}
