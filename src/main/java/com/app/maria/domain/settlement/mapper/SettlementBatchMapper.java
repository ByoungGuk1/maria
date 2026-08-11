package com.app.maria.domain.settlement.mapper;

import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SettlementBatchMapper {

    List<SettlementBatchDTO> selectBatches();

    Optional<SettlementBatchDTO> selectBatchById(Long batchId);

    int insertBatch(SettlementBatchDTO batch);

    Optional<SettlementBatchDTO> selectBatchByRunId(String runId);

    Optional<SettlementBatchDTO> selectRunningBatchByBusinessDate(LocalDate businessDate);

    int countRunningBatch(SettlementBatchDTO batch);

    int updateBatchStatus(SettlementBatchDTO batch);
}
