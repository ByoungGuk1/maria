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
import com.app.maria.domain.settlement.mapper.SettlementBatchGuardMapper;
import com.app.maria.domain.settlement.type.BatchStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {
  private final KrwExchangeMapper krwExchangeMapper;
  private final SettlementItemMapper settlementItemMapper;
  private final SettlementBatchMapper settlementBatchMapper;
  private final SettlementJoinMapper settlementJoinMapper;
  private final SettlementBatchGuardMapper settlementBatchGuardMapper;
  private final PlatformTransactionManager transactionManager;

  @Override
  public SettlementBatchDTO executeSettlementBatch() {
    LocalDateTime executedAt = LocalDateTime.now();
    LocalDate businessDate = executedAt.toLocalDate();

    SettlementBatchDTO batch = new TransactionTemplate(transactionManager).execute(status -> {
      settlementBatchGuardMapper.ensureGuard(businessDate);

      if (settlementBatchGuardMapper.selectGuardForUpdate(businessDate).isEmpty()) {
        throw new SettlementBatchLockException("확정산 Batch 업무일 잠금 획득 실패");
      }

      if (settlementBatchMapper.selectRunningBatchByBusinessDate(businessDate).isPresent()) {
        throw new SettlementBatchAlreadyRunningException("동일 업무일의 확정산 Batch가 이미 실행 중");
      }

      SettlementBatchDTO newBatch = SettlementBatchDTO.builder()
          .executedAt(executedAt)
          .status(BatchStatus.RUNNING)
          .runId(UUID.randomUUID().toString())
          .build();

      if (settlementBatchMapper.insertBatch(newBatch) != 1 || newBatch.getBatchId() == null) {
        throw new SettlementStateConflictException("확정산 Batch 생성에 실패했습니다.");
      }

      settlementItemMapper.insertItemsForTargets(newBatch);
      return newBatch;
    });

    if (batch == null) {
      throw new SettlementStateConflictException("확정산 Batch 트랜잭션 처리에 실패했습니다.");
    }
    return batch;
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
