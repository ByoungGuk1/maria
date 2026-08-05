package com.app.maria.domain.settlement.mapper;

import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import com.app.maria.domain.settlement.dto.SettlementItemDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SettlementItemMapper {

  int insertItemsForTargets(SettlementBatchDTO batch);

  List<SettlementItemDTO> selectPendingItems(SettlementItemDTO cursor);

  int updateItemResult(SettlementItemDTO item);

  int countPendingItems(Long batchId);

  int countFailedItems(Long batchId);
}
