package com.app.maria.domain.settlement.mapper;

import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface SettlementBatchMapper {

  int insertBatch(SettlementBatchDTO batch);

  Optional<SettlementBatchDTO> selectBatchByRunId(String runId);

  int updateBatchStatus(SettlementBatchDTO batch);
}
