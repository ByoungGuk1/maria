package com.app.maria.domain.settlement.mapper;

import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import com.app.maria.domain.settlement.dto.SettlementItemDTO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SettlementItemMapper {

    int insertItemsForTargets(SettlementBatchDTO batch);

    List<SettlementItemDTO> selectPendingItems(SettlementItemDTO cursor);

    int updateItemResult(SettlementItemDTO item);

    int countPendingItems(Long batchId);

    int countFailedItems(Long batchId);
}
