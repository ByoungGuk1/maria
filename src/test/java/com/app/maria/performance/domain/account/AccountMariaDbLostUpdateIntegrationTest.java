package com.app.maria.performance.domain.account;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.account.type.Status;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("mariadb-integration")
@EnabledIfSystemProperty(named = "settlement.mariadb.enabled", matches = "true")
class AccountMariaDbLostUpdateIntegrationTest {
  private static final long CUSTOMER_ID = 901L;
  private static final BigDecimal INITIAL_LIMIT = new BigDecimal("30000000");
  private static PooledDataSource dataSource;
  private static SqlSessionFactory sqlSessionFactory;

  @BeforeAll
  static void setUp() throws Exception {
    Properties properties = new Properties();
    properties.setProperty("db.driver", "org.mariadb.jdbc.Driver");
    properties.setProperty("db.url", required("settlement.mariadb.url"));
    properties.setProperty("db.username", required("settlement.mariadb.username"));
    properties.setProperty("db.password", required("settlement.mariadb.password"));
    try (Reader reader = Resources.getResourceAsReader("mybatis-settlement-mariadb-test-config.xml")) {
      sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader, properties);
    }
    dataSource = (PooledDataSource) sqlSessionFactory.getConfiguration().getEnvironment().getDataSource();
    resetFixture();
  }

  @AfterAll
  static void tearDown() {
    if (dataSource != null) dataSource.forceCloseAll();
  }

  @Test
  void compareAndSetBlocksLostUpdateForStaleLimit() throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> results = List.of(
          executor.submit(() -> updateLimit(start, ready, new BigDecimal("35000000"))),
          executor.submit(() -> updateLimit(start, ready, new BigDecimal("40000000")))
      );
      ready.await();
      start.countDown();

      int successCount = results.get(0).get() + results.get(1).get();
      AccountDTO account = selectAccount();

      assertThat(successCount).isEqualTo(1);
      assertThat(account.getLimitAmount()).isIn(new BigDecimal("35000000"), new BigDecimal("40000000"));
    } finally {
      executor.shutdownNow();
    }
  }

  private static int updateLimit(CountDownLatch start, CountDownLatch ready, BigDecimal newLimit) throws Exception {
    try (SqlSession session = sqlSessionFactory.openSession(false)) {
      AccountMapper mapper = session.getMapper(AccountMapper.class);
      ready.countDown();
      start.await();
      int affected = mapper.updateLimit(901L, Status.OPENED, INITIAL_LIMIT, newLimit);
      if (affected == 1) session.commit(); else session.rollback();
      return affected;
    }
  }

  private static AccountDTO selectAccount() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      return session.getMapper(AccountMapper.class).selectByCustomerId(CUSTOMER_ID).orElseThrow();
    }
  }

  private static void resetFixture() throws Exception {
    try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
      statement.executeUpdate("DELETE FROM account WHERE customer_id = " + CUSTOMER_ID);
      statement.executeUpdate("DELETE FROM customer WHERE customer_id = " + CUSTOMER_ID);
      statement.executeUpdate("INSERT INTO customer (customer_id, name, birth_date, investor_type, ci_hash) VALUES (" + CUSTOMER_ID + ", 'CAS테스트', '1990-01-01', 'NEUTRAL', 'account-cas-901')");
      statement.executeUpdate("INSERT INTO account (account_id, customer_id, status, opened_at, account_no, limit_amount, amount) VALUES (901, " + CUSTOMER_ID + ", 'OPENED', '2026-08-09 09:00:00', '9000000901', 30000000, 0)");
    }
  }

  private static String required(String name) {
    String value = System.getProperty(name);
    if (value == null || value.isBlank()) throw new IllegalStateException(name + " 시스템 속성이 필요합니다.");
    return value;
  }
}
