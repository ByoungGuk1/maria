package com.app.maria.domain.settlement.mapper;

import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SettlementBatchMapper {

  List<SettlementBatchDTO> selectBatches();

  Optional<SettlementBatchDTO> selectBatchById(Long batchId);

  int insertBatch(SettlementBatchDTO batch);

  Optional<SettlementBatchDTO> selectBatchByRunId(String runId);

  int countRunningBatch(SettlementBatchDTO batch);

  int updateBatchStatus(SettlementBatchDTO batch);
}
