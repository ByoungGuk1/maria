package com.app.maria.domain.settlement.service;

import com.app.maria.domain.settlement.dto.KrwExchangeDTO;
import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import com.app.maria.domain.settlement.dto.SettlementItemDTO;
import com.app.maria.domain.settlement.dto.SettlementJoinDTO;

import java.util.List;

public interface SettlementService {

  SettlementBatchDTO executeSettlementBatch();

  List<SettlementBatchDTO> getSettlementBatches();

  SettlementBatchDTO getSettlementBatch(Long batchId);

  SettlementBatchDTO getSettlementBatchByRunId(String runId);

  List<SettlementItemDTO> getPendingSettlementItems(Long batchId, Long lastItemId);

  SettlementJoinDTO getSettlementItem(Long batchId, Long itemId);

  KrwExchangeDTO getKrwExchange(Long exchangeId);
}
