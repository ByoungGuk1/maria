package com.app.maria.domain.settlement.service;

import com.app.maria.domain.settlement.dto.KrwExchangeDTO;
import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import com.app.maria.domain.settlement.dto.SettlementItemDTO;
import com.app.maria.domain.settlement.dto.SettlementJoinDTO;
import com.app.maria.domain.settlement.exception.InvalidSettlementException;
import com.app.maria.domain.settlement.exception.KrwExchangeNotFoundException;
import com.app.maria.domain.settlement.exception.SettlementBatchNotFoundException;
import com.app.maria.domain.settlement.exception.SettlementItemNotFoundException;
import com.app.maria.domain.settlement.mapper.KrwExchangeMapper;
import com.app.maria.domain.settlement.mapper.SettlementBatchMapper;
import com.app.maria.domain.settlement.mapper.SettlementItemMapper;
import com.app.maria.domain.settlement.mapper.SettlementJoinMapper;
import com.app.maria.domain.settlement.type.BatchStatus;
import com.app.maria.domain.settlement.type.SettlementStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceImplTest {

  private static final Long BATCH_ID = 1L;
  private static final Long ITEM_ID = 10L;
  private static final Long EXCHANGE_ID = 100L;
  private static final String RUN_ID = "run-1";

  @Mock
  private KrwExchangeMapper krwExchangeMapper;

  @Mock
  private SettlementItemMapper settlementItemMapper;

  @Mock
  private SettlementBatchMapper settlementBatchMapper;

  @Mock
  private SettlementJoinMapper settlementJoinMapper;

  @InjectMocks
  private SettlementServiceImpl settlementService;

  @Test
  @DisplayName("확정산 Batch 목록을 Mapper 조회 결과 그대로 반환한다")
  void getSettlementBatchesReturnsMapperResult() {
    List<SettlementBatchDTO> batches = List.of(batch());
    when(settlementBatchMapper.selectBatches()).thenReturn(batches);

    List<SettlementBatchDTO> result = settlementService.getSettlementBatches();

    assertThat(result).isSameAs(batches);
    verify(settlementBatchMapper).selectBatches();
  }

  @Test
  @DisplayName("batchId에 해당하는 Batch를 반환한다")
  void getSettlementBatchReturnsBatch() {
    SettlementBatchDTO batch = batch();
    when(settlementBatchMapper.selectBatchById(BATCH_ID)).thenReturn(Optional.of(batch));

    SettlementBatchDTO result = settlementService.getSettlementBatch(BATCH_ID);

    assertThat(result).isSameAs(batch);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = {0L, -1L})
  @DisplayName("batchId가 null 또는 양수가 아니면 조회를 차단한다")
  void getSettlementBatchRejectsInvalidBatchId(Long batchId) {
    assertThatThrownBy(() -> settlementService.getSettlementBatch(batchId))
        .isInstanceOf(InvalidSettlementException.class)
        .hasMessage("batchId - 요청 값 오류");

    verifyNoInteractions(settlementBatchMapper);
  }

  @Test
  @DisplayName("batchId에 해당하는 Batch가 없으면 NotFound 예외를 반환한다")
  void getSettlementBatchThrowsWhenBatchDoesNotExist() {
    when(settlementBatchMapper.selectBatchById(BATCH_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> settlementService.getSettlementBatch(BATCH_ID))
        .isInstanceOf(SettlementBatchNotFoundException.class)
        .hasMessage("batch id로 배치 조회 실패");
  }

  @Test
  @DisplayName("runId에 해당하는 Batch를 반환한다")
  void getSettlementBatchByRunIdReturnsBatch() {
    SettlementBatchDTO batch = batch();
    when(settlementBatchMapper.selectBatchByRunId(RUN_ID)).thenReturn(Optional.of(batch));

    SettlementBatchDTO result = settlementService.getSettlementBatchByRunId(RUN_ID);

    assertThat(result).isSameAs(batch);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "\t"})
  @DisplayName("runId가 null 또는 blank이면 조회를 차단한다")
  void getSettlementBatchByRunIdRejectsInvalidRunId(String runId) {
    assertThatThrownBy(() -> settlementService.getSettlementBatchByRunId(runId))
        .isInstanceOf(InvalidSettlementException.class)
        .hasMessage("runId - 요청 값 오류");

    verifyNoInteractions(settlementBatchMapper);
  }

  @Test
  @DisplayName("runId에 해당하는 Batch가 없으면 NotFound 예외를 반환한다")
  void getSettlementBatchByRunIdThrowsWhenBatchDoesNotExist() {
    when(settlementBatchMapper.selectBatchByRunId(RUN_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> settlementService.getSettlementBatchByRunId(RUN_ID))
        .isInstanceOf(SettlementBatchNotFoundException.class)
        .hasMessage("run id로 배치 조회 실패");
  }

  @Test
  @DisplayName("대기 Item 조회 cursor가 null이면 itemId 0으로 조회한다")
  void getPendingSettlementItemsUsesZeroWhenCursorIsNull() {
    SettlementBatchDTO batch = batch();
    List<SettlementItemDTO> items = List.of(item());
    when(settlementBatchMapper.selectBatchById(BATCH_ID)).thenReturn(Optional.of(batch));
    when(settlementItemMapper.selectPendingItems(any(SettlementItemDTO.class)))
        .thenReturn(items);

    List<SettlementItemDTO> result = settlementService.getPendingSettlementItems(BATCH_ID, null);

    assertThat(result).isSameAs(items);
    ArgumentCaptor<SettlementItemDTO> cursorCaptor =
        ArgumentCaptor.forClass(SettlementItemDTO.class);
    verify(settlementItemMapper).selectPendingItems(cursorCaptor.capture());
    assertThat(cursorCaptor.getValue().getBatchId()).isEqualTo(BATCH_ID);
    assertThat(cursorCaptor.getValue().getItemId()).isZero();
  }

  @Test
  @DisplayName("대기 Item 조회 cursor가 음수이면 Item Mapper를 호출하지 않는다")
  void getPendingSettlementItemsRejectsNegativeCursor() {
    when(settlementBatchMapper.selectBatchById(BATCH_ID)).thenReturn(Optional.of(batch()));

    assertThatThrownBy(() -> settlementService.getPendingSettlementItems(BATCH_ID, -1L))
        .isInstanceOf(InvalidSettlementException.class)
        .hasMessage("lastItemId - 요청 값 오류");

    verify(settlementItemMapper, never()).selectPendingItems(any());
  }

  @Test
  @DisplayName("Batch가 없으면 대기 Item Mapper를 호출하지 않는다")
  void getPendingSettlementItemsChecksBatchBeforeItems() {
    when(settlementBatchMapper.selectBatchById(BATCH_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> settlementService.getPendingSettlementItems(BATCH_ID, 0L))
        .isInstanceOf(SettlementBatchNotFoundException.class);

    verify(settlementItemMapper, never()).selectPendingItems(any());
  }

  @Test
  @DisplayName("batchId와 itemId가 일치하는 Item 상세를 반환한다")
  void getSettlementItemReturnsItemDetail() {
    SettlementJoinDTO detail = itemDetail();
    when(settlementBatchMapper.selectBatchById(BATCH_ID)).thenReturn(Optional.of(batch()));
    when(settlementJoinMapper.selectItemDetail(any(SettlementJoinDTO.class)))
        .thenReturn(Optional.of(detail));

    SettlementJoinDTO result = settlementService.getSettlementItem(BATCH_ID, ITEM_ID);

    assertThat(result).isSameAs(detail);
    ArgumentCaptor<SettlementJoinDTO> queryCaptor =
        ArgumentCaptor.forClass(SettlementJoinDTO.class);
    verify(settlementJoinMapper).selectItemDetail(queryCaptor.capture());
    assertThat(queryCaptor.getValue().getBatchId()).isEqualTo(BATCH_ID);
    assertThat(queryCaptor.getValue().getItemId()).isEqualTo(ITEM_ID);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = {0L, -1L})
  @DisplayName("itemId가 null 또는 양수가 아니면 상세 조회를 차단한다")
  void getSettlementItemRejectsInvalidItemId(Long itemId) {
    assertThatThrownBy(() -> settlementService.getSettlementItem(BATCH_ID, itemId))
        .isInstanceOf(InvalidSettlementException.class)
        .hasMessage("itemId - 요청 값 오류");

    verifyNoInteractions(settlementBatchMapper, settlementJoinMapper);
  }

  @Test
  @DisplayName("Batch가 없으면 Item 상세 Mapper를 호출하지 않는다")
  void getSettlementItemChecksBatchBeforeDetail() {
    when(settlementBatchMapper.selectBatchById(BATCH_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> settlementService.getSettlementItem(BATCH_ID, ITEM_ID))
        .isInstanceOf(SettlementBatchNotFoundException.class);

    verify(settlementJoinMapper, never()).selectItemDetail(any());
  }

  @Test
  @DisplayName("batchId와 itemId에 해당하는 상세가 없으면 NotFound 예외를 반환한다")
  void getSettlementItemThrowsWhenDetailDoesNotExist() {
    when(settlementBatchMapper.selectBatchById(BATCH_ID)).thenReturn(Optional.of(batch()));
    when(settlementJoinMapper.selectItemDetail(any(SettlementJoinDTO.class)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> settlementService.getSettlementItem(BATCH_ID, ITEM_ID))
        .isInstanceOf(SettlementItemNotFoundException.class)
        .hasMessage("item detail 조회 실패");
  }

  @Test
  @DisplayName("exchangeId에 해당하는 원화 환전을 반환한다")
  void getKrwExchangeReturnsExchange() {
    KrwExchangeDTO exchange = exchange();
    when(krwExchangeMapper.selectExchangeById(EXCHANGE_ID)).thenReturn(Optional.of(exchange));

    KrwExchangeDTO result = settlementService.getKrwExchange(EXCHANGE_ID);

    assertThat(result).isSameAs(exchange);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = {0L, -1L})
  @DisplayName("exchangeId가 null 또는 양수가 아니면 환전 조회를 차단한다")
  void getKrwExchangeRejectsInvalidExchangeId(Long exchangeId) {
    assertThatThrownBy(() -> settlementService.getKrwExchange(exchangeId))
        .isInstanceOf(InvalidSettlementException.class)
        .hasMessage("exchangeId - 요청 값 오류");

    verifyNoInteractions(krwExchangeMapper);
  }

  @Test
  @DisplayName("exchangeId에 해당하는 환전이 없으면 NotFound 예외를 반환한다")
  void getKrwExchangeThrowsWhenExchangeDoesNotExist() {
    when(krwExchangeMapper.selectExchangeById(EXCHANGE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> settlementService.getKrwExchange(EXCHANGE_ID))
        .isInstanceOf(KrwExchangeNotFoundException.class)
        .hasMessage("환전 조회 실패");
  }

  private SettlementBatchDTO batch() {
    return SettlementBatchDTO.builder()
        .batchId(BATCH_ID)
        .runId(RUN_ID)
        .status(BatchStatus.RUNNING)
        .build();
  }

  private SettlementItemDTO item() {
    return SettlementItemDTO.builder()
        .itemId(ITEM_ID)
        .batchId(BATCH_ID)
        .exchangeId(EXCHANGE_ID)
        .build();
  }

  private SettlementJoinDTO itemDetail() {
    return SettlementJoinDTO.builder()
        .itemId(ITEM_ID)
        .batchId(BATCH_ID)
        .exchangeId(EXCHANGE_ID)
        .build();
  }

  private KrwExchangeDTO exchange() {
    return KrwExchangeDTO.builder()
        .exchangeId(EXCHANGE_ID)
        .accountId(1L)
        .settlementStatus(SettlementStatus.PROVISIONAL)
        .build();
  }
}
