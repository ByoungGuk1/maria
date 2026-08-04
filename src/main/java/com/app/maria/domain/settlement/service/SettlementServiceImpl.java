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
    validateLongPositive(batchId,"batchId");
    return settlementBatchMapper.selectBatchById(batchId).orElseThrow(()->new SettlementBatchNotFoundException("batch id로 배치 조회 실패"));
  }

  @Override
  @Transactional(readOnly = true)
  public SettlementBatchDTO getSettlementBatchByRunId(String runId) {
    if(runId == null || runId.isBlank()){
      throw new InvalidSettlementException("runId - 요청 값 오류");
    }
    return settlementBatchMapper.selectBatchByRunId(runId).orElseThrow(()->new SettlementBatchNotFoundException("run id로 배치 조회 실패"));
  }

  @Override
  @Transactional(readOnly = true)
  public List<SettlementItemDTO> getPendingSettlementItems(Long batchId, Long lastItemId) {
    getSettlementBatch(batchId);
    lastItemId = lastItemId == null ? 0 : lastItemId;
    if(lastItemId < 0){
      throw new InvalidSettlementException("lastItemId - 요청 값 오류");
    }
    SettlementItemDTO cursor = SettlementItemDTO.builder()
        .batchId(batchId)
        .itemId(lastItemId)
        .build();
    return settlementItemMapper.selectPendingItems(cursor);
  }

  @Override
  @Transactional(readOnly = true)
  public SettlementJoinDTO getSettlementItem(Long batchId, Long itemId) {
    validateLongPositive(itemId, "itemId");
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
    validateLongPositive(exchangeId, "exchangeId");
    return krwExchangeMapper.selectExchangeById(exchangeId).orElseThrow(()->new KrwExchangeNotFoundException("환전 조회 실패"));
  }

  private void validateLongPositive(Long value, String fieldName) {
    if (value == null) {
      throw new InvalidSettlementException(fieldName + " - 요청 값 오류");
    }
    if (value.compareTo(0L) <= 0) {
      throw new InvalidSettlementException(fieldName + "은 0보다 커야 합니다.");
    }
  }
}
