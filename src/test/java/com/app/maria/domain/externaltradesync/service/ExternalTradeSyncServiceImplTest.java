package com.app.maria.domain.externaltradesync.service;

import com.app.maria.domain.customer.dto.CustomerCiHashDTO;
import com.app.maria.domain.customer.mapper.CustomerMapper;
import com.app.maria.domain.externaltradesync.dto.ExternalTradeSyncCursorDTO;
import com.app.maria.domain.externaltradesync.mapper.ExternalTradeSyncCursorMapper;
import com.app.maria.domain.externaltradesync.dto.response.MydataTradeResponseDTO;
import com.app.maria.domain.externaltradesync.dto.request.MydataTradeRequestDTO;
import com.app.maria.domain.targetproduct.dto.TargetProductJudgementDTO;
import com.app.maria.domain.targetproduct.mapper.TargetProductMapper;
import com.app.maria.domain.targetproduct.service.TargetProductService;
import com.app.maria.global.client.mydatatrade.MydataTradeClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalTradeSyncServiceImplTest {

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private ExternalTradeSyncCursorMapper cursorMapper;

    @Mock
    private MydataTradeClient mydataTradeClient;

    @Mock
    private TargetProductMapper targetProductMapper;

    @Mock
    private TargetProductService targetProductService;

    @InjectMocks
    private ExternalTradeSyncServiceImpl externalTradeSyncService;

    private static CustomerCiHashDTO customer(Long customerId, String ciHash) {
        return CustomerCiHashDTO.builder().customerId(customerId).ciHash(ciHash).build();
    }

    private static MydataTradeResponseDTO trade(Long tradeId, String stockType, String fundCode, LocalDate tradeDate) {
        return MydataTradeResponseDTO.builder()
                .tradeId(tradeId)
                .ciHash("ci-1")
                .brokerName("증권사A")
                .tradeType("BUY")
                .stockType(stockType)
                .qty(BigDecimal.TEN)
                .tradeDate(tradeDate)
                .amount(BigDecimal.valueOf(1_000_000))
                .fundCode(fundCode)
                .build();
    }

    @Test
    @DisplayName("동기화 대상 고객이 없으면 mydata 조회, 판정, 커서 갱신 모두 발생하지 않는다")
    void syncAllDoesNothingWhenNoCustomers() {
        when(customerMapper.selectActiveRiaCustomers()).thenReturn(List.of());

        externalTradeSyncService.syncAll();

        verifyNoInteractions(mydataTradeClient, targetProductService, cursorMapper);
    }

    @Test
    @DisplayName("커서가 없는 고객(첫 동기화)은 fromDate 없이 mydata를 조회한다")
    void firstSyncQueriesWithNullFromDate() {
        when(customerMapper.selectActiveRiaCustomers()).thenReturn(List.of(customer(1L, "ci-1")));
        when(cursorMapper.selectByCustomerId(1L)).thenReturn(Optional.empty());
        when(mydataTradeClient.getTrades(any())).thenReturn(List.of());

        externalTradeSyncService.syncAll();

        ArgumentCaptor<MydataTradeRequestDTO> captor = ArgumentCaptor.forClass(MydataTradeRequestDTO.class);
        verify(mydataTradeClient).getTrades(captor.capture());
        assertThat(captor.getValue().getCiHash()).isEqualTo("ci-1");
        assertThat(captor.getValue().getFromDate()).isNull();
    }

    @Test
    @DisplayName("커서가 있는 고객은 마지막 동기화 거래일을 fromDate로 사용해 조회한다")
    void resumedSyncQueriesWithLastSyncedTradeDate() {
        LocalDate lastSynced = LocalDate.of(2026, 3, 1);
        when(customerMapper.selectActiveRiaCustomers()).thenReturn(List.of(customer(1L, "ci-1")));
        when(cursorMapper.selectByCustomerId(1L)).thenReturn(Optional.of(
                ExternalTradeSyncCursorDTO.builder().customerId(1L).lastSyncedTradeDate(lastSynced).build()));
        when(mydataTradeClient.getTrades(any())).thenReturn(List.of());

        externalTradeSyncService.syncAll();

        ArgumentCaptor<MydataTradeRequestDTO> captor = ArgumentCaptor.forClass(MydataTradeRequestDTO.class);
        verify(mydataTradeClient).getTrades(captor.capture());
        assertThat(captor.getValue().getFromDate()).isEqualTo(lastSynced);
    }

    @Test
    @DisplayName("신규 거래는 각 거래 객체 그대로 judge()에 전달된다")
    void newTradesAreJudgedWithTheirOwnArguments() {
        MydataTradeResponseDTO foreignStock = trade(100L, "FOREIGN_STOCK", null, LocalDate.of(2026, 3, 5));
        MydataTradeResponseDTO fund = trade(101L, "FUND", "448630", LocalDate.of(2026, 3, 10));

        when(customerMapper.selectActiveRiaCustomers()).thenReturn(List.of(customer(1L, "ci-1")));
        when(cursorMapper.selectByCustomerId(1L)).thenReturn(Optional.empty());
        when(mydataTradeClient.getTrades(any())).thenReturn(List.of(foreignStock, fund));
        when(targetProductMapper.existsByMydataTradeId(anyLong())).thenReturn(false);

        externalTradeSyncService.syncAll();

        verify(targetProductService).judge(foreignStock);
        verify(targetProductService).judge(fund);
    }

    @Test
    @DisplayName("이미 판정된 거래(mydata_trade_id 존재)는 judge()를 다시 호출하지 않는다")
    void alreadyJudgedTradeIsSkippedWithoutCallingJudge() {
        MydataTradeResponseDTO alreadyJudged = trade(100L, "FOREIGN_STOCK", null, LocalDate.of(2026, 3, 5));
        MydataTradeResponseDTO newTrade = trade(101L, "ETF", null, LocalDate.of(2026, 3, 6));

        when(customerMapper.selectActiveRiaCustomers()).thenReturn(List.of(customer(1L, "ci-1")));
        when(cursorMapper.selectByCustomerId(1L)).thenReturn(Optional.empty());
        when(mydataTradeClient.getTrades(any())).thenReturn(List.of(alreadyJudged, newTrade));
        when(targetProductMapper.existsByMydataTradeId(100L)).thenReturn(true);
        when(targetProductMapper.existsByMydataTradeId(101L)).thenReturn(false);

        externalTradeSyncService.syncAll();

        verify(targetProductService).judge(newTrade);
        verifyNoMoreInteractions(targetProductService);
    }

    @Test
    @DisplayName("한 거래의 판정이 실패해도 나머지 거래는 계속 처리된다")
    void oneTradeFailureDoesNotStopProcessingOtherTrades() {
        MydataTradeResponseDTO failing = trade(100L, "FUND", "BADCODE", LocalDate.of(2026, 3, 5));
        MydataTradeResponseDTO succeeding = trade(101L, "FOREIGN_STOCK", null, LocalDate.of(2026, 3, 6));

        when(customerMapper.selectActiveRiaCustomers()).thenReturn(List.of(customer(1L, "ci-1")));
        when(cursorMapper.selectByCustomerId(1L)).thenReturn(Optional.empty());
        when(mydataTradeClient.getTrades(any())).thenReturn(List.of(failing, succeeding));
        when(targetProductMapper.existsByMydataTradeId(anyLong())).thenReturn(false);
        when(targetProductService.judge(failing))
                .thenThrow(new RuntimeException("mydata 펀드 조회 실패"));

        externalTradeSyncService.syncAll();

        verify(targetProductService).judge(succeeding);
    }

    @Test
    @DisplayName("고객별 커서를 여러 거래 중 가장 늦은 거래일로 갱신한다")
    void cursorAdvancesToTheLatestTradeDateAmongMultipleTrades() {
        MydataTradeResponseDTO t1 = trade(100L, "FOREIGN_STOCK", null, LocalDate.of(2026, 3, 5));
        MydataTradeResponseDTO t2 = trade(101L, "ETF", null, LocalDate.of(2026, 3, 20));
        MydataTradeResponseDTO t3 = trade(102L, "ETN", null, LocalDate.of(2026, 3, 10));

        when(customerMapper.selectActiveRiaCustomers()).thenReturn(List.of(customer(1L, "ci-1")));
        when(cursorMapper.selectByCustomerId(1L)).thenReturn(Optional.empty());
        // 실제 mydata 조회는 trade_date 오름차순으로 오므로(MydataTradeMapper의 ORDER BY trade_date),
        // 테스트 픽스처도 그 순서(3/5 -> 3/10 -> 3/20)에 맞춰 구성한다.
        when(mydataTradeClient.getTrades(any())).thenReturn(List.of(t1, t3, t2));
        when(targetProductMapper.existsByMydataTradeId(anyLong())).thenReturn(false);

        externalTradeSyncService.syncAll();

        ArgumentCaptor<ExternalTradeSyncCursorDTO> captor = ArgumentCaptor.forClass(ExternalTradeSyncCursorDTO.class);
        verify(cursorMapper).upsertCursor(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isEqualTo(1L);
        assertThat(captor.getValue().getLastSyncedTradeDate()).isEqualTo(LocalDate.of(2026, 3, 20));
    }

    @Test
    @DisplayName("mydata가 거래를 날짜순이 아니라 뒤섞어서 반환해도, 실제 날짜 기준 최댓값으로 커서가 잡힌다")
    void cursorAdvancesToTheLatestTradeDateEvenWhenMydataReturnsTradesOutOfOrder() {
        MydataTradeResponseDTO latest = trade(100L, "FOREIGN_STOCK", null, LocalDate.of(2026, 3, 20));
        MydataTradeResponseDTO earliest = trade(101L, "ETF", null, LocalDate.of(2026, 3, 5));
        MydataTradeResponseDTO middle = trade(102L, "ETN", null, LocalDate.of(2026, 3, 10));

        when(customerMapper.selectActiveRiaCustomers()).thenReturn(List.of(customer(1L, "ci-1")));
        when(cursorMapper.selectByCustomerId(1L)).thenReturn(Optional.empty());
        // mydata가 날짜 역순으로 반환하는 상황을 재현 (리스트 마지막 원소가 최댓값이 아님)
        when(mydataTradeClient.getTrades(any())).thenReturn(List.of(latest, earliest, middle));
        when(targetProductMapper.existsByMydataTradeId(anyLong())).thenReturn(false);

        externalTradeSyncService.syncAll();

        ArgumentCaptor<ExternalTradeSyncCursorDTO> captor = ArgumentCaptor.forClass(ExternalTradeSyncCursorDTO.class);
        verify(cursorMapper).upsertCursor(captor.capture());
        assertThat(captor.getValue().getLastSyncedTradeDate()).isEqualTo(LocalDate.of(2026, 3, 20));
    }

    @Test
    @DisplayName("뒤섞인 순서로 와도 날짜 기준 실패 지점 이전까지만 커서가 전진한다")
    void cursorStopsAtDateOrderFailurePointEvenWhenMydataReturnsTradesOutOfOrder() {
        MydataTradeResponseDTO late = trade(100L, "FOREIGN_STOCK", null, LocalDate.of(2026, 3, 20));
        MydataTradeResponseDTO failingMiddle = trade(101L, "FUND", "BADCODE", LocalDate.of(2026, 3, 10));
        MydataTradeResponseDTO early = trade(102L, "ETF", null, LocalDate.of(2026, 3, 5));

        when(customerMapper.selectActiveRiaCustomers()).thenReturn(List.of(customer(1L, "ci-1")));
        when(cursorMapper.selectByCustomerId(1L)).thenReturn(Optional.empty());
        // 리스트 순서(late -> failingMiddle -> early)는 날짜 순서와 정반대
        when(mydataTradeClient.getTrades(any())).thenReturn(List.of(late, failingMiddle, early));
        when(targetProductMapper.existsByMydataTradeId(anyLong())).thenReturn(false);
        when(targetProductService.judge(late))
                .thenReturn(TargetProductJudgementDTO.builder().build());
        when(targetProductService.judge(failingMiddle))
                .thenThrow(new RuntimeException("mydata 펀드 조회 실패"));
        when(targetProductService.judge(early))
                .thenReturn(TargetProductJudgementDTO.builder().build());

        externalTradeSyncService.syncAll();

        // 날짜순 정렬 후 처리 순서는 early(3/5) -> failingMiddle(3/10, 실패) -> late(3/20)
        // 실패 직전인 3/5에서 커서가 멈춰야 하며, 리스트상 첫 원소였던 3/20으로 잘못 잡히면 안 된다
        ArgumentCaptor<ExternalTradeSyncCursorDTO> captor = ArgumentCaptor.forClass(ExternalTradeSyncCursorDTO.class);
        verify(cursorMapper).upsertCursor(captor.capture());
        assertThat(captor.getValue().getLastSyncedTradeDate()).isEqualTo(LocalDate.of(2026, 3, 5));
    }

    @Test
    @DisplayName("유일한 거래가 판정에 실패하면 재시도할 수 있도록 커서를 만들지 않는다")
    void cursorDoesNotAdvancePastAFailingTradeToAllowRetry() {
        MydataTradeResponseDTO failing = trade(100L, "FUND", "BADCODE", LocalDate.of(2026, 3, 20));

        when(customerMapper.selectActiveRiaCustomers()).thenReturn(List.of(customer(1L, "ci-1")));
        when(cursorMapper.selectByCustomerId(1L)).thenReturn(Optional.empty());
        when(mydataTradeClient.getTrades(any())).thenReturn(List.of(failing));
        when(targetProductMapper.existsByMydataTradeId(100L)).thenReturn(false);
        when(targetProductService.judge(failing))
                .thenThrow(new RuntimeException("mydata 펀드 조회 실패"));

        externalTradeSyncService.syncAll();

        // 첫 동기화(fromDate=null)에서 유일한 거래가 실패했으므로 커서를 저장할 근거가 없다
        verify(cursorMapper, never()).upsertCursor(any());
    }

    @Test
    @DisplayName("성공-실패-성공 순서로 거래가 와도 커서는 실패한 거래 이전 지점에서 멈춘다")
    void cursorStopsAtFirstFailureEvenIfLaterTradesSucceed() {
        MydataTradeResponseDTO success1 = trade(100L, "FOREIGN_STOCK", null, LocalDate.of(2026, 3, 5));
        MydataTradeResponseDTO failing = trade(101L, "FUND", "BADCODE", LocalDate.of(2026, 3, 10));
        MydataTradeResponseDTO success2 = trade(102L, "ETF", null, LocalDate.of(2026, 3, 20));

        when(customerMapper.selectActiveRiaCustomers()).thenReturn(List.of(customer(1L, "ci-1")));
        when(cursorMapper.selectByCustomerId(1L)).thenReturn(Optional.empty());
        when(mydataTradeClient.getTrades(any())).thenReturn(List.of(success1, failing, success2));
        when(targetProductMapper.existsByMydataTradeId(anyLong())).thenReturn(false);
        when(targetProductService.judge(success1))
                .thenReturn(TargetProductJudgementDTO.builder().build());
        when(targetProductService.judge(failing))
                .thenThrow(new RuntimeException("mydata 펀드 조회 실패"));
        when(targetProductService.judge(success2))
                .thenReturn(TargetProductJudgementDTO.builder().build());

        externalTradeSyncService.syncAll();

        // 실패한 거래(3/10) 이후의 성공(3/20)은 커서에 반영되지 않고, 실패 직전(3/5)에서 멈춘다
        ArgumentCaptor<ExternalTradeSyncCursorDTO> captor = ArgumentCaptor.forClass(ExternalTradeSyncCursorDTO.class);
        verify(cursorMapper).upsertCursor(captor.capture());
        assertThat(captor.getValue().getLastSyncedTradeDate()).isEqualTo(LocalDate.of(2026, 3, 5));

        // 세 거래 모두 스킵되지 않고 judge 시도는 이루어졌다
        verify(targetProductService).judge(success1);
        verify(targetProductService).judge(failing);
        verify(targetProductService).judge(success2);
    }

    @Test
    @DisplayName("신규 거래가 없고 기존 커서도 없으면 커서를 생성하지 않는다")
    void cursorIsNotUpsertedWhenNoTradesAndNoExistingCursor() {
        when(customerMapper.selectActiveRiaCustomers()).thenReturn(List.of(customer(1L, "ci-1")));
        when(cursorMapper.selectByCustomerId(1L)).thenReturn(Optional.empty());
        when(mydataTradeClient.getTrades(any())).thenReturn(List.of());

        externalTradeSyncService.syncAll();

        verify(cursorMapper, never()).upsertCursor(any());
        verifyNoInteractions(targetProductService);
    }

    @Test
    @DisplayName("신규 거래가 없어도 기존 커서가 있으면 동일한 값으로 커서를 다시 저장한다")
    void cursorIsReupsertedWithSameValueWhenNoNewTrades() {
        LocalDate lastSynced = LocalDate.of(2026, 3, 1);
        when(customerMapper.selectActiveRiaCustomers()).thenReturn(List.of(customer(1L, "ci-1")));
        when(cursorMapper.selectByCustomerId(1L)).thenReturn(Optional.of(
                ExternalTradeSyncCursorDTO.builder().customerId(1L).lastSyncedTradeDate(lastSynced).build()));
        when(mydataTradeClient.getTrades(any())).thenReturn(List.of());

        externalTradeSyncService.syncAll();

        ArgumentCaptor<ExternalTradeSyncCursorDTO> captor = ArgumentCaptor.forClass(ExternalTradeSyncCursorDTO.class);
        verify(cursorMapper).upsertCursor(captor.capture());
        assertThat(captor.getValue().getLastSyncedTradeDate()).isEqualTo(lastSynced);
    }

    @Test
    @DisplayName("고객이 여러 명이면 고객마다 각각 mydata를 조회하고 서로의 결과에 영향을 주지 않는다")
    void syncsMultipleCustomersIndependently() {
        MydataTradeResponseDTO customer1Trade = trade(100L, "FOREIGN_STOCK", null, LocalDate.of(2026, 3, 5));
        MydataTradeResponseDTO customer2Trade = trade(200L, "ETF", null, LocalDate.of(2026, 4, 1));

        when(customerMapper.selectActiveRiaCustomers())
                .thenReturn(List.of(customer(1L, "ci-1"), customer(2L, "ci-2")));
        when(cursorMapper.selectByCustomerId(1L)).thenReturn(Optional.empty());
        when(cursorMapper.selectByCustomerId(2L)).thenReturn(Optional.empty());
        // customerMapper가 고객을 1번→2번 순서로 반환하고 syncAll()도 그 순서로 순회하므로,
        // getTrades() 호출 순서에 맞춰 결과를 순차적으로 반환하도록 스텁한다.
        when(mydataTradeClient.getTrades(any()))
                .thenReturn(List.of(customer1Trade))
                .thenReturn(List.of(customer2Trade));
        when(targetProductMapper.existsByMydataTradeId(anyLong())).thenReturn(false);

        externalTradeSyncService.syncAll();

        verify(targetProductService).judge(customer1Trade);
        verify(targetProductService).judge(customer2Trade);
        verify(mydataTradeClient, times(2)).getTrades(any());

        ArgumentCaptor<ExternalTradeSyncCursorDTO> captor = ArgumentCaptor.forClass(ExternalTradeSyncCursorDTO.class);
        verify(cursorMapper, times(2)).upsertCursor(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ExternalTradeSyncCursorDTO::getCustomerId, ExternalTradeSyncCursorDTO::getLastSyncedTradeDate)
                .containsExactlyInAnyOrder(
                        tuple(1L, LocalDate.of(2026, 3, 5)),
                        tuple(2L, LocalDate.of(2026, 4, 1))
                );
    }
}