package com.app.maria.domain.settlement.service;

import com.app.maria.domain.settlement.dto.KrwExchangeDTO;
import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import com.app.maria.domain.settlement.dto.SettlementItemDTO;
import com.app.maria.domain.settlement.dto.SettlementJoinDTO;
import com.app.maria.domain.settlement.exception.*;
import com.app.maria.domain.settlement.mapper.KrwExchangeMapper;
import com.app.maria.domain.settlement.mapper.SettlementBatchMapper;
import com.app.maria.domain.settlement.mapper.SettlementItemMapper;
import com.app.maria.domain.settlement.mapper.SettlementJoinMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {
  private final KrwExchangeMapper krwExchangeMapper;
  private final SettlementItemMapper settlementItemMapper;
  private final SettlementBatchMapper settlementBatchMapper;
  private final SettlementJoinMapper settlementJoinMapper;

  @Override
  public SettlementBatchDTO executeSettlementBatch() {
    return null;
  }

  @Override
  @Transactional(readOnly = true)
  public List<SettlementBatchDTO> getSettlementBatches() {
    return settlementBatchMapper.selectBatches();
  }

  @Override
  @Transactional(readOnly = true)
  public SettlementBatchDTO getSettlementBatch(Long batchId) {
    return settlementBatchMapper.selectBatchById(batchId).orElseThrow(()->new SettlementBatchNotFoundException("batch id로 배치 조회 실패"));
  }

  @Override
  @Transactional(readOnly = true)
  public SettlementBatchDTO getSettlementBatchByRunId(String runId) {
    return settlementBatchMapper.selectBatchByRunId(runId).orElseThrow(()->new SettlementBatchNotFoundException("run id로 배치 조회 실패"));
  }

  @Override
  @Transactional(readOnly = true)
  public List<SettlementItemDTO> getPendingSettlementItems(Long batchId, Long lastItemId) {
    getSettlementBatch(batchId);
    SettlementItemDTO cursor = SettlementItemDTO.builder()
        .batchId(batchId)
        .itemId(lastItemId)
        .build();
    return settlementItemMapper.selectPendingItems(cursor);
  }

  @Override
  @Transactional(readOnly = true)
  public SettlementJoinDTO getSettlementItem(Long batchId, Long itemId) {
    getSettlementBatch(batchId);
    SettlementJoinDTO cursor = SettlementJoinDTO.builder()
        .batchId(batchId)
        .itemId(itemId)
        .build();
    return settlementJoinMapper.selectItemDetail(cursor).orElseThrow(()->new SettlementItemNotFoundException("item detail 조회 실패"));
  }

  @Override
  @Transactional(readOnly = true)
  public KrwExchangeDTO getKrwExchange(Long exchangeId) {
    return krwExchangeMapper.selectExchangeById(exchangeId).orElseThrow(()->new KrwExchangeNotFoundException("환전 조회 실패"));
  }
}
