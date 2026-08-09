package com.app.maria.performance.domain.settlement;

import com.app.maria.domain.sellorder.type.SellOrderStatus;
import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import com.app.maria.domain.settlement.dto.SettlementItemDTO;
import com.app.maria.domain.settlement.dto.SettlementJoinDTO;
import com.app.maria.domain.settlement.mapper.SettlementBatchMapper;
import com.app.maria.domain.settlement.mapper.SettlementBatchGuardMapper;
import com.app.maria.domain.settlement.mapper.SettlementItemMapper;
import com.app.maria.domain.settlement.mapper.SettlementJoinMapper;
import com.app.maria.domain.settlement.mapper.KrwExchangeMapper;
import com.app.maria.domain.settlement.component.SettlementCalculator;
import com.app.maria.domain.settlement.component.SettlementTransactionExecutor;
import com.app.maria.domain.settlement.type.BatchStatus;
import com.app.maria.domain.settlement.type.SettlementItemResult;
import com.app.maria.domain.settlement.type.SettlementStatus;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("mariadb-integration")
@EnabledIfSystemProperty(named = "settlement.mariadb.enabled", matches = "true")
class SettlementMariaDbMapperIntegrationTest {

  private static PooledDataSource dataSource;
  private static SqlSessionFactory sqlSessionFactory;

  private SqlSession sqlSession;
  private SettlementBatchMapper settlementBatchMapper;
  private SettlementItemMapper settlementItemMapper;
  private SettlementJoinMapper settlementJoinMapper;
  private KrwExchangeMapper krwExchangeMapper;

  @BeforeAll
  static void configureMyBatis() throws Exception {
    Properties properties = new Properties();
    properties.setProperty("db.driver", "org.mariadb.jdbc.Driver");
    properties.setProperty("db.url", requiredProperty("settlement.mariadb.url"));
    properties.setProperty("db.username", requiredProperty("settlement.mariadb.username"));
    properties.setProperty("db.password", requiredProperty("settlement.mariadb.password"));

    try (Reader reader = Resources.getResourceAsReader("mybatis-settlement-mariadb-test-config.xml")) {
      sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader, properties);
    }
    dataSource = (PooledDataSource) sqlSessionFactory.getConfiguration()
        .getEnvironment().getDataSource();
  }

  @BeforeEach
  void setUp() throws SQLException {
    clearSettlementFixture();
    seedProvisionalTarget();
    sqlSession = sqlSessionFactory.openSession(false);
    settlementBatchMapper = sqlSession.getMapper(SettlementBatchMapper.class);
    settlementItemMapper = sqlSession.getMapper(SettlementItemMapper.class);
    settlementJoinMapper = sqlSession.getMapper(SettlementJoinMapper.class);
    krwExchangeMapper = sqlSession.getMapper(KrwExchangeMapper.class);
  }

  @AfterEach
  void closeSession() {
    if (sqlSession != null) {
      sqlSession.rollback();
      sqlSession.close();
    }
  }

  @AfterAll
  static void closeDataSource() {
    if (dataSource != null) {
      dataSource.forceCloseAll();
    }
  }

  @Test
  void createsSnapshotAndReadsSettlementFxRateFromOperationalMariaDbDdl() {
    SettlementBatchDTO batch = SettlementBatchDTO.builder()
        .executedAt(LocalDateTime.of(2026, 8, 3, 9, 0))
        .status(BatchStatus.RUNNING)
        .runId("mariadb-it-run-1")
        .build();
    assertThat(settlementBatchMapper.insertBatch(batch)).isOne();
    assertThat(settlementItemMapper.insertItemsForTargets(batch)).isOne();

    List<SettlementItemDTO> items = settlementItemMapper.selectPendingItems(
        SettlementItemDTO.builder().batchId(batch.getBatchId()).itemId(0L).build()
    );
    assertThat(items).hasSize(1);

    SettlementJoinDTO target = settlementJoinMapper.selectTargetByItemId(
        SettlementJoinDTO.builder().batchId(batch.getBatchId()).itemId(items.get(0).getItemId()).build()
    ).orElseThrow();

    assertThat(target.getSettlementFxRate()).isEqualByComparingTo("1350.0000");
    assertThat(target.getSettlementStatus()).isEqualTo(SettlementStatus.PROVISIONAL);
    assertThat(target.getSellOrderStatus()).isEqualTo(SellOrderStatus.EXECUTED);
    assertThat(target.getPurchaseCurrency()).isEqualTo("USD");
  }

  @Test
  void retryCreatesOnlyFailedItemsAndCompletesFromLatestResults() throws SQLException {
    SettlementBatchDTO batch = SettlementBatchDTO.builder()
        .executedAt(LocalDateTime.of(2026, 8, 3, 9, 0))
        .status(BatchStatus.RUNNING).runId("mariadb-it-retry-1").build();
    settlementBatchMapper.insertBatch(batch);
    settlementItemMapper.insertItemsForTargets(batch);
    SettlementItemDTO failed = settlementItemMapper.selectPendingItems(
        SettlementItemDTO.builder().batchId(batch.getBatchId()).itemId(0L).build()).get(0);
    failed.setResult(SettlementItemResult.FAILED);
    failed.setProcessedAt(LocalDateTime.of(2026, 8, 3, 9, 1));
    assertThat(settlementItemMapper.updateItemResult(failed)).isOne();
    batch.setStatus(BatchStatus.FAILED);
    assertThat(settlementBatchMapper.updateBatchStatus(batch)).isOne();

    assertThat(settlementItemMapper.insertRetryItemsForFailedBatch(batch.getBatchId())).isOne();
    SettlementBatchDTO retry = SettlementBatchDTO.builder().batchId(batch.getBatchId())
        .runId("mariadb-it-retry-2").build();
    assertThat(settlementBatchMapper.markBatchRetryRunning(retry)).isOne();
    List<SettlementItemDTO> pending = settlementItemMapper.selectPendingItems(
        SettlementItemDTO.builder().batchId(batch.getBatchId()).itemId(failed.getItemId()).build());
    assertThat(pending).hasSize(1);
    pending.get(0).setResult(SettlementItemResult.SUCCESS);
    pending.get(0).setProcessedAt(LocalDateTime.of(2026, 8, 3, 9, 2));
    assertThat(settlementItemMapper.updateItemResult(pending.get(0))).isOne();
    assertThat(settlementBatchMapper.refreshBatchStatusAfterRetry(batch.getBatchId())).isOne();

    SettlementBatchDTO completed = settlementBatchMapper.selectBatchById(batch.getBatchId()).orElseThrow();
    assertThat(completed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(completed.getRunId()).isEqualTo("mariadb-it-retry-2");
    assertThat(completed.getSuccessCount()).isEqualTo(1);
    assertThat(completed.getFailedCount()).isZero();
  }

  @Test
  void batchRetryCreatesHistoryOnlyForLatestFailedItem() throws SQLException {
    addProvisionalTargetsTwoAndThree();
    SettlementBatchDTO batch = SettlementBatchDTO.builder().executedAt(LocalDateTime.of(2026, 8, 3, 9, 0))
        .status(BatchStatus.RUNNING).runId("batch-retry-before").build();
    settlementBatchMapper.insertBatch(batch);
    assertThat(settlementItemMapper.insertItemsForTargets(batch)).isEqualTo(3);
    List<SettlementItemDTO> items = settlementItemMapper.selectPendingItems(
        SettlementItemDTO.builder().batchId(batch.getBatchId()).itemId(0L).build());
    for (SettlementItemDTO item : items) {
      item.setResult(item.getExchangeId().equals(2L) ? SettlementItemResult.FAILED : SettlementItemResult.SUCCESS);
      item.setProcessedAt(LocalDateTime.of(2026, 8, 3, 9, 1));
      assertThat(settlementItemMapper.updateItemResult(item)).isOne();
    }
    batch.setStatus(BatchStatus.FAILED);
    assertThat(settlementBatchMapper.updateBatchStatus(batch)).isOne();

    assertThat(settlementItemMapper.insertRetryItemsForFailedBatch(batch.getBatchId())).isOne();
    assertThat(settlementItemMapper.insertRetryItemsForFailedBatch(batch.getBatchId())).isZero();
    SettlementBatchDTO retry = SettlementBatchDTO.builder().batchId(batch.getBatchId()).runId("batch-retry-after").build();
    assertThat(settlementBatchMapper.markBatchRetryRunning(retry)).isOne();
    SettlementItemDTO retryItem = settlementItemMapper.selectPendingItems(
        SettlementItemDTO.builder().batchId(batch.getBatchId()).itemId(0L).build()).get(0);
    assertThat(retryItem.getExchangeId()).isEqualTo(2L);
    retryItem.setResult(SettlementItemResult.SUCCESS);
    retryItem.setProcessedAt(LocalDateTime.of(2026, 8, 3, 9, 2));
    assertThat(settlementItemMapper.updateItemResult(retryItem)).isOne();
    settlementBatchMapper.refreshBatchStatusAfterRetry(batch.getBatchId());

    SettlementBatchDTO completed = settlementBatchMapper.selectBatchById(batch.getBatchId()).orElseThrow();
    assertThat(completed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(completed.getTotalCount()).isEqualTo(3);
    assertThat(completed.getSuccessCount()).isEqualTo(3);
    assertThat(completed.getFailedCount()).isZero();
    assertThat(completed.getRunId()).isEqualTo("batch-retry-after");
  }

  @Test
  void concurrentBusinessDateGuardCreatesOnlyOneBatch() throws Exception {
    LocalDate businessDate = LocalDate.of(2026, 8, 4);
    CountDownLatch ready = new CountDownLatch(10);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(10);
    try {
      List<Future<Boolean>> results = java.util.stream.IntStream.range(0, 10)
          .mapToObj(index -> pool.submit(() -> createBatchForDate(businessDate, ready, start, index)))
          .toList();
      ready.await(); start.countDown();
      long created = 0;
      for (Future<Boolean> result : results) if (result.get()) created++;
      try (SqlSession session = sqlSessionFactory.openSession()) {
        assertThat(created).isEqualTo(1);
        assertThat(session.getMapper(SettlementBatchMapper.class).selectBatchByBusinessDate(businessDate)).isPresent();
      }
    } finally { pool.shutdownNow(); }
  }

  @Test
  void concurrentRetryForSameItemCreatesOnlyOnePendingHistory() throws Exception {
    SettlementBatchDTO batch = SettlementBatchDTO.builder().executedAt(LocalDateTime.of(2026, 8, 3, 9, 0))
        .status(BatchStatus.RUNNING).runId("ct2-before").build();
    settlementBatchMapper.insertBatch(batch);
    settlementItemMapper.insertItemsForTargets(batch);
    SettlementItemDTO failed = settlementItemMapper.selectPendingItems(
        SettlementItemDTO.builder().batchId(batch.getBatchId()).itemId(0L).build()).get(0);
    failed.setResult(SettlementItemResult.FAILED); failed.setProcessedAt(LocalDateTime.of(2026, 8, 3, 9, 1));
    settlementItemMapper.updateItemResult(failed);
    batch.setStatus(BatchStatus.FAILED); settlementBatchMapper.updateBatchStatus(batch);
    sqlSession.commit();

    CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> results = List.of(
          pool.submit(() -> createRetryWithLocks(batch.getBatchId(), failed.getItemId(), ready, start)),
          pool.submit(() -> createRetryWithLocks(batch.getBatchId(), failed.getItemId(), ready, start)));
      ready.await(); start.countDown();
      assertThat(results.get(0).get() + results.get(1).get()).isEqualTo(1);
      assertThat(settlementItemMapper.selectPendingItems(
          SettlementItemDTO.builder().batchId(batch.getBatchId()).itemId(0L).build())).hasSize(1);
    } finally { pool.shutdownNow(); }
  }

  @Test
  void concurrentFailureTransitionUpdatesRunningBatchOnlyOnce() throws Exception {
    SettlementBatchDTO batch = SettlementBatchDTO.builder().executedAt(LocalDateTime.of(2026, 8, 5, 9, 0))
        .status(BatchStatus.RUNNING).runId("ct3-running").build();
    settlementBatchMapper.insertBatch(batch); sqlSession.commit();
    CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> results = List.of(
          pool.submit(() -> markFailed(batch.getBatchId(), ready, start)),
          pool.submit(() -> markFailed(batch.getBatchId(), ready, start)));
      ready.await(); start.countDown();
      assertThat(results.get(0).get() + results.get(1).get()).isEqualTo(1);
      assertThat(settlementBatchMapper.selectBatchById(batch.getBatchId()).orElseThrow().getStatus())
          .isEqualTo(BatchStatus.FAILED);
    } finally { pool.shutdownNow(); }
  }

  @Test
  void retryExecutorFinalizesOnceAndCreatesOneLeftAmount() throws Exception {
    SettlementBatchDTO batch = SettlementBatchDTO.builder().executedAt(LocalDateTime.of(2026, 8, 3, 9, 0))
        .status(BatchStatus.RUNNING).runId("amount-before").build();
    settlementBatchMapper.insertBatch(batch); settlementItemMapper.insertItemsForTargets(batch);
    SettlementItemDTO failed = settlementItemMapper.selectPendingItems(
        SettlementItemDTO.builder().batchId(batch.getBatchId()).itemId(0L).build()).get(0);
    failed.setResult(SettlementItemResult.FAILED); failed.setProcessedAt(LocalDateTime.of(2026, 8, 3, 9, 1));
    settlementItemMapper.updateItemResult(failed); batch.setStatus(BatchStatus.FAILED); settlementBatchMapper.updateBatchStatus(batch);
    settlementItemMapper.insertRetryItemsForFailedBatch(batch.getBatchId());
    settlementBatchMapper.markBatchRetryRunning(SettlementBatchDTO.builder().batchId(batch.getBatchId()).runId("amount-after").build());
    SettlementItemDTO retry = settlementItemMapper.selectPendingItems(SettlementItemDTO.builder().batchId(batch.getBatchId()).itemId(0L).build()).get(0);
    SettlementJoinDTO target = settlementJoinMapper.selectTargetByItemId(SettlementJoinDTO.builder().batchId(batch.getBatchId()).itemId(retry.getItemId()).build()).orElseThrow();
    BigDecimal before = krwExchangeMapper.selectAccountAmountForUpdate(1L).orElseThrow();
    SettlementTransactionExecutor executor = new SettlementTransactionExecutor(krwExchangeMapper, settlementItemMapper,
        new SettlementCalculator(), () -> LocalDateTime.of(2026, 8, 3, 9, 2));
    executor.execute(target, new BigDecimal("1400")); sqlSession.commit();
    BigDecimal after = krwExchangeMapper.selectAccountAmountForUpdate(1L).orElseThrow();
    assertThat(krwExchangeMapper.selectExchangeById(1L).orElseThrow().getSettlementStatus()).isEqualTo(SettlementStatus.FINALIZED);
    assertThat(after).isNotEqualByComparingTo(before);
    assertThat(count("SELECT COUNT(*) FROM left_amount WHERE exchange_id = 1")).isEqualTo(1);
  }

  private static boolean createBatchForDate(LocalDate date, CountDownLatch ready, CountDownLatch start, int index) throws Exception {
    try (SqlSession session = sqlSessionFactory.openSession(false)) {
      SettlementBatchGuardMapper guard = session.getMapper(SettlementBatchGuardMapper.class);
      SettlementBatchMapper batches = session.getMapper(SettlementBatchMapper.class);
      ready.countDown(); start.await();
      guard.ensureGuard(date);
      guard.selectGuardForUpdate(date).orElseThrow();
      if (batches.selectBatchByBusinessDate(date).isPresent()) { session.commit(); return false; }
      SettlementBatchDTO batch = SettlementBatchDTO.builder().executedAt(date.atStartOfDay())
          .status(BatchStatus.RUNNING).runId("ct1-" + index).build();
      batches.insertBatch(batch); session.commit(); return true;
    }
  }

  private static int createRetryWithLocks(Long batchId, Long itemId, CountDownLatch ready, CountDownLatch start) throws Exception {
    try (SqlSession session = sqlSessionFactory.openSession(false)) {
      SettlementBatchMapper batches = session.getMapper(SettlementBatchMapper.class);
      SettlementItemMapper items = session.getMapper(SettlementItemMapper.class);
      ready.countDown(); start.await();
      SettlementBatchDTO batch = batches.selectBatchByIdForUpdate(batchId).orElseThrow();
      if (batch.getStatus() != BatchStatus.FAILED) { session.rollback(); return 0; }
      SettlementItemDTO failed = items.selectItemByIdForUpdate(itemId).orElseThrow();
      items.selectItemsByExchangeIdForUpdate(failed.getExchangeId());
      if (items.existsPendingItemByExchangeId(failed.getExchangeId())) { session.rollback(); return 0; }
      SettlementItemDTO command = SettlementItemDTO.builder().batchId(batchId).itemId(itemId).build();
      int created = items.insertRetryItem(command);
      session.commit(); return created;
    }
  }

  private static int markFailed(Long batchId, CountDownLatch ready, CountDownLatch start) throws Exception {
    try (SqlSession session = sqlSessionFactory.openSession(false)) {
      ready.countDown(); start.await();
      int affected = session.getMapper(SettlementBatchMapper.class).updateBatchStatus(
          SettlementBatchDTO.builder().batchId(batchId).status(BatchStatus.FAILED).failureMessage("ct3").build());
      session.commit(); return affected;
    }
  }

  private int count(String sql) throws SQLException {
    try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement(); var rs = statement.executeQuery(sql)) {
      rs.next(); return rs.getInt(1);
    }
  }

  private static String requiredProperty(String name) {
    String value = System.getProperty(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("MariaDB 통합 테스트 시스템 속성이 필요합니다. " + name);
    }
    return value;
  }

  private static void clearSettlementFixture() throws SQLException {
    try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
      statement.executeUpdate("DELETE FROM settlement_item");
      statement.executeUpdate("DELETE FROM settlement_batch");
      statement.executeUpdate("DELETE FROM left_amount");
      statement.executeUpdate("DELETE FROM krw_exchange");
      statement.executeUpdate("DELETE FROM sell_order");
      statement.executeUpdate("DELETE FROM inbound_detail");
      statement.executeUpdate("DELETE FROM inbound");
      statement.executeUpdate("DELETE FROM foreign_product");
      statement.executeUpdate("DELETE FROM account");
      statement.executeUpdate("DELETE FROM customer");
    }
  }

  private static void seedProvisionalTarget() throws SQLException {
    try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
      statement.executeUpdate("""
          INSERT INTO customer (customer_id, name, birth_date, investor_type, ci_hash)
          VALUES (1, '통합테스트고객', '1990-01-01', 'NEUTRAL', 'mariadb-integration-customer')
          """);
      statement.executeUpdate("""
          INSERT INTO account (account_id, customer_id, status, opened_at, account_no, limit_amount, amount)
          VALUES (1, 1, 'OPENED', '2026-08-01 09:00:00', '1234567890', 50000000, 2700000)
          """);
      statement.executeUpdate("""
          INSERT INTO foreign_product (foreign_product_id, ticker, name, market, currency, type)
          VALUES (1, 'AAPL', 'Apple', 'NASDAQ', 'USD', 'FOREIGN_STOCK')
          """);
      statement.executeUpdate("""
          INSERT INTO inbound (inbound_id, account_id, requested_qty, approved_qty, processed_at)
          VALUES (1, 1, 10, 10, '2026-08-01 09:00:00')
          """);
      statement.executeUpdate("""
          INSERT INTO inbound_detail (
              inbound_detail_id, inbound_id, foreign_product_id, recorded_at, purchase_date,
              purchase_price, purchase_currency, purchase_fx_rate, qty, current_qty
          ) VALUES (1, 1, 1, '2026-08-01 09:00:00', '2026-08-01 09:00:00',
                    200, 'USD', 1300, 10, 10)
          """);
      statement.executeUpdate("""
          INSERT INTO sell_order (
              order_id, inbound_detail_id, sell_qty, base_price, processed_at, status, settlement_fx_rate
          ) VALUES (1, 1, 10, 2700000, '2026-08-02 15:30:00', 'EXECUTED', 1350)
          """);
      statement.executeUpdate("""
          INSERT INTO krw_exchange (
              exchange_id, account_id, order_id, provisional_amount, provisional_at, settlement_status
          ) VALUES (1, 1, 1, 2700000, '2026-08-02 15:30:00', 'PROVISIONAL')
          """);
    }
  }

  private static void addProvisionalTargetsTwoAndThree() throws SQLException {
    try (Connection connection = dataSource.getConnection(); Statement s = connection.createStatement()) {
      s.executeUpdate("INSERT INTO foreign_product (foreign_product_id,ticker,name,market,currency,type) VALUES (2,'MSFT','Microsoft','NASDAQ','USD','FOREIGN_STOCK'),(3,'NVDA','Nvidia','NASDAQ','USD','FOREIGN_STOCK')");
      s.executeUpdate("INSERT INTO inbound (inbound_id,account_id,requested_qty,approved_qty,processed_at) VALUES (2,1,10,10,'2026-08-01 09:00:00'),(3,1,10,10,'2026-08-01 09:00:00')");
      s.executeUpdate("INSERT INTO inbound_detail (inbound_detail_id,inbound_id,foreign_product_id,recorded_at,purchase_date,purchase_price,purchase_currency,purchase_fx_rate,qty,current_qty) VALUES (2,2,2,'2026-08-01','2026-08-01',200,'USD',1300,10,10),(3,3,3,'2026-08-01','2026-08-01',200,'USD',1300,10,10)");
      s.executeUpdate("INSERT INTO sell_order (order_id,inbound_detail_id,sell_qty,base_price,processed_at,status,settlement_fx_rate) VALUES (2,2,10,2700000,'2026-08-02 15:30:00','EXECUTED',1350),(3,3,10,2700000,'2026-08-02 15:30:00','EXECUTED',1350)");
      s.executeUpdate("INSERT INTO krw_exchange (exchange_id,account_id,order_id,provisional_amount,provisional_at,settlement_status) VALUES (2,1,2,2700000,'2026-08-02 15:30:00','PROVISIONAL'),(3,1,3,2700000,'2026-08-02 15:30:00','PROVISIONAL')");
    }
  }
}
