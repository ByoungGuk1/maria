package com.app.maria.domain.account.mapper;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.dto.AccountStatusLogDTO;
import com.app.maria.domain.account.type.Status;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountMapperTest {

  private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 2, 10, 0);
  private static final BigDecimal DEFAULT_LIMIT = BigDecimal.valueOf(30_000_000L);

  private static PooledDataSource dataSource;
  private static SqlSessionFactory sqlSessionFactory;

  private SqlSession sqlSession;
  private AccountMapper accountMapper;
  private AccountStatusLogMapper accountStatusLogMapper;

  @BeforeAll
  static void configureMyBatis() throws IOException {
    try (Reader reader = Resources.getResourceAsReader("mybatis-account-test-config.xml")) {
      sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
    }
    dataSource = (PooledDataSource) sqlSessionFactory
        .getConfiguration()
        .getEnvironment()
        .getDataSource();
  }

  @BeforeEach
  void setUpDatabase() throws SQLException {
    resetSchema();
    sqlSession = sqlSessionFactory.openSession(true);
    accountMapper = sqlSession.getMapper(AccountMapper.class);
    accountStatusLogMapper = sqlSession.getMapper(AccountStatusLogMapper.class);
  }

  @AfterEach
  void closeSession() {
    if (sqlSession != null) {
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
  @DisplayName("고객과 고객 계좌 존재 여부를 구분해 조회한다")
  void existsQueriesReturnExpectedValues() {
    assertThat(accountMapper.existsCustomerById(1L)).isTrue();
    assertThat(accountMapper.existsCustomerById(999L)).isFalse();
    assertThat(accountMapper.existsByCustomerId(1L)).isFalse();

    insertApplication(1L, DEFAULT_LIMIT);

    assertThat(accountMapper.existsByCustomerId(1L)).isTrue();
  }

  @Test
  @DisplayName("신규 신청은 APPLIED 상태와 계좌 기본값으로 저장한다")
  void insertApplicationStoresAppliedAccount() {
    int affectedRows = accountMapper.insertApplication(application(1L, DEFAULT_LIMIT));

    assertThat(affectedRows).isOne();
    AccountDTO savedAccount = accountMapper.selectByCustomerId(1L).orElseThrow();
    assertThat(savedAccount.getAccountId()).isNotNull();
    assertThat(savedAccount.getCustomerId()).isEqualTo(1L);
    assertThat(savedAccount.getStatus()).isEqualTo(Status.APPLIED);
    assertThat(savedAccount.getAccountNo()).isNull();
    assertThat(savedAccount.getOpenedAt()).isNull();
    assertThat(savedAccount.getCreatedAt()).isEqualTo(CREATED_AT);
    assertThat(savedAccount.getLimitAmount()).isEqualByComparingTo(DEFAULT_LIMIT);
    assertThat(savedAccount.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(savedAccount.getBenefit()).isNull();
  }

  @Test
  @DisplayName("동일 고객의 두 번째 계좌는 DB unique 제약조건으로 차단한다")
  void insertApplicationRejectsDuplicateCustomer() {
    insertApplication(1L, DEFAULT_LIMIT);

    assertThatThrownBy(() -> accountMapper.insertApplication(
        application(1L, BigDecimal.valueOf(20_000_000L))
    )).isInstanceOf(PersistenceException.class);
  }

  @Test
  @DisplayName("승인은 APPLIED 계좌에 계좌번호와 개설일을 한 번만 설정한다")
  void approveUpdatesOnlyAppliedAccount() {
    Long accountId = insertApplication(1L, DEFAULT_LIMIT);
    LocalDateTime openedAt = CREATED_AT.plusMinutes(1);
    BigDecimal accountNo = BigDecimal.valueOf(1_234_567_890L);
    AccountDTO approval = AccountDTO.builder()
        .accountId(accountId)
        .accountNo(accountNo)
        .openedAt(openedAt)
        .build();

    assertThat(accountMapper.approve(approval)).isOne();
    assertThat(accountMapper.approve(approval)).isZero();

    AccountDTO openedAccount = accountMapper.selectByAccountId(accountId).orElseThrow();
    assertThat(openedAccount.getStatus()).isEqualTo(Status.OPENED);
    assertThat(openedAccount.getAccountNo()).isEqualByComparingTo(accountNo);
    assertThat(openedAccount.getOpenedAt()).isEqualTo(openedAt);
  }

  @Test
  @DisplayName("APPLIED 계좌를 반려하고 변경된 한도로 재신청한다")
  void rejectAndReapplyAccount() {
    Long accountId = insertApplication(1L, DEFAULT_LIMIT);
    AccountDTO target = AccountDTO.builder().accountId(accountId).build();

    assertThat(accountMapper.reject(target)).isOne();
    assertThat(accountMapper.reject(target)).isZero();
    assertThat(accountMapper.selectByAccountId(accountId).orElseThrow().getStatus())
        .isEqualTo(Status.REJECTED);

    BigDecimal changedLimit = BigDecimal.valueOf(20_000_000L);
    AccountDTO reapplication = AccountDTO.builder()
        .accountId(accountId)
        .limitAmount(changedLimit)
        .build();

    assertThat(accountMapper.reapply(reapplication)).isOne();

    AccountDTO reappliedAccount = accountMapper.selectByAccountId(accountId).orElseThrow();
    assertThat(reappliedAccount.getStatus()).isEqualTo(Status.APPLIED);
    assertThat(reappliedAccount.getLimitAmount()).isEqualByComparingTo(changedLimit);
    assertThat(reappliedAccount.getAccountNo()).isNull();
    assertThat(reappliedAccount.getOpenedAt()).isNull();
  }

  @Test
  @DisplayName("이미 OPENED인 계좌는 재신청할 수 없다")
  void reapplyDoesNotUpdateOpenedAccount() {
    Long accountId = insertApplication(1L, DEFAULT_LIMIT);
    openAccount(accountId, BigDecimal.valueOf(1_234_567_890L));

    AccountDTO reapplication = AccountDTO.builder()
        .accountId(accountId)
        .limitAmount(BigDecimal.valueOf(10_000_000L))
        .build();

    assertThat(accountMapper.reapply(reapplication)).isZero();
    assertThat(accountMapper.selectByAccountId(accountId).orElseThrow().getStatus())
        .isEqualTo(Status.OPENED);
  }

  @Test
  @DisplayName("APPLIED 계좌의 한도 변경은 재신청 Mapper로 처리하지 않는다")
  void reapplyDoesNotUpdateAppliedAccount() {
    Long accountId = insertApplication(1L, DEFAULT_LIMIT);
    AccountDTO reapplication = AccountDTO.builder()
        .accountId(accountId)
        .limitAmount(BigDecimal.valueOf(20_000_000L))
        .build();

    assertThat(accountMapper.reapply(reapplication)).isZero();
    assertThat(accountMapper.selectByAccountId(accountId).orElseThrow().getLimitAmount())
        .isEqualByComparingTo(DEFAULT_LIMIT);
  }

  @Test
  @DisplayName("한도 조건부 UPDATE는 APPLIED와 OPENED 상태에서 현재 한도가 일치할 때만 성공한다")
  void updateLimitUpdatesOnlyAllowedStatusWithExpectedValue() {
    Long appliedAccountId = insertApplication(1L, DEFAULT_LIMIT);
    Long openedAccountId = insertApplication(2L, DEFAULT_LIMIT);
    Long rejectedAccountId = insertApplication(3L, DEFAULT_LIMIT);
    openAccount(openedAccountId, BigDecimal.valueOf(1_234_567_890L));
    accountMapper.reject(AccountDTO.builder().accountId(rejectedAccountId).build());
    BigDecimal changedLimit = BigDecimal.valueOf(40_000_000L);

    assertThat(accountMapper.updateLimit(
        appliedAccountId,
        Status.APPLIED,
        DEFAULT_LIMIT,
        changedLimit
    )).isOne();
    assertThat(accountMapper.updateLimit(
        openedAccountId,
        Status.OPENED,
        DEFAULT_LIMIT,
        changedLimit
    )).isOne();
    assertThat(accountMapper.updateLimit(
        rejectedAccountId,
        Status.REJECTED,
        DEFAULT_LIMIT,
        changedLimit
    )).isZero();
    assertThat(accountMapper.updateLimit(
        appliedAccountId,
        Status.APPLIED,
        DEFAULT_LIMIT,
        BigDecimal.valueOf(45_000_000L)
    )).isZero();

    assertThat(accountMapper.selectByAccountId(appliedAccountId).orElseThrow().getLimitAmount())
        .isEqualByComparingTo(changedLimit);
    assertThat(accountMapper.selectByAccountId(openedAccountId).orElseThrow().getLimitAmount())
        .isEqualByComparingTo(changedLimit);
    assertThat(accountMapper.selectByAccountId(rejectedAccountId).orElseThrow().getLimitAmount())
        .isEqualByComparingTo(DEFAULT_LIMIT);
  }

  @Test
  @DisplayName("관리자 오버라이드는 REJECTED 계좌만 OPENED로 변경한다")
  void overrideOpensOnlyRejectedAccount() {
    Long rejectedAccountId = insertApplication(1L, DEFAULT_LIMIT);
    Long appliedAccountId = insertApplication(2L, DEFAULT_LIMIT);
    accountMapper.reject(AccountDTO.builder().accountId(rejectedAccountId).build());

    LocalDateTime openedAt = CREATED_AT.plusMinutes(5);
    AccountDTO rejectedOverride = AccountDTO.builder()
        .accountId(rejectedAccountId)
        .accountNo(BigDecimal.valueOf(2_345_678_901L))
        .openedAt(openedAt)
        .build();
    AccountDTO appliedOverride = AccountDTO.builder()
        .accountId(appliedAccountId)
        .accountNo(BigDecimal.valueOf(3_456_789_012L))
        .openedAt(openedAt)
        .build();

    assertThat(accountMapper.overrideToOpened(rejectedOverride)).isOne();
    assertThat(accountMapper.overrideToOpened(appliedOverride)).isZero();

    AccountDTO openedAccount = accountMapper.selectByAccountId(rejectedAccountId).orElseThrow();
    assertThat(openedAccount.getStatus()).isEqualTo(Status.OPENED);
    assertThat(openedAccount.getAccountNo()).isEqualByComparingTo(rejectedOverride.getAccountNo());
    assertThat(openedAccount.getOpenedAt()).isEqualTo(openedAt);
    assertThat(accountMapper.selectByAccountId(appliedAccountId).orElseThrow().getStatus())
        .isEqualTo(Status.APPLIED);
  }

  @Test
  @DisplayName("같은 계좌번호 발급은 DB unique 제약조건으로 차단한다")
  void approveRejectsDuplicateAccountNumber() {
    Long firstAccountId = insertApplication(1L, DEFAULT_LIMIT);
    Long secondAccountId = insertApplication(2L, DEFAULT_LIMIT);
    BigDecimal duplicatedAccountNo = BigDecimal.valueOf(1_234_567_890L);

    openAccount(firstAccountId, duplicatedAccountNo);

    AccountDTO secondApproval = AccountDTO.builder()
        .accountId(secondAccountId)
        .accountNo(duplicatedAccountNo)
        .openedAt(CREATED_AT.plusMinutes(2))
        .build();

    assertThatThrownBy(() -> accountMapper.approve(secondApproval))
        .isInstanceOf(PersistenceException.class);
  }

  @Test
  @DisplayName("전체 계좌를 status 내림차순으로 조회한다")
  void selectAllAccountsOrdersByStatusDescending() {
    Long openedAccountId = insertApplication(1L, DEFAULT_LIMIT);
    Long rejectedAccountId = insertApplication(2L, DEFAULT_LIMIT);
    insertApplication(3L, DEFAULT_LIMIT);
    openAccount(openedAccountId, BigDecimal.valueOf(1_234_567_890L));
    accountMapper.reject(AccountDTO.builder().accountId(rejectedAccountId).build());

    List<AccountDTO> result = accountMapper.selectAllAccount();

    assertThat(result).hasSize(3);
    assertThat(result).extracting(AccountDTO::getStatus)
        .containsExactly(Status.REJECTED, Status.OPENED, Status.APPLIED);
  }

  @Test
  @DisplayName("상태 이력을 시간순으로 조회하고 최신 신청 시각을 반환한다")
  void statusLogMapperStoresAndSelectsHistory() {
    Long accountId = insertApplication(1L, DEFAULT_LIMIT);
    LocalDateTime firstAppliedAt = CREATED_AT;
    LocalDateTime rejectedAt = CREATED_AT.plusMinutes(1);
    LocalDateTime reappliedAt = CREATED_AT.plusMinutes(2);

    assertThat(accountStatusLogMapper.insertLog(statusLog(
        accountId, null, Status.APPLIED, firstAppliedAt, "최초 개설 신청"
    ))).isOne();
    assertThat(accountStatusLogMapper.insertLog(statusLog(
        accountId, Status.APPLIED, Status.REJECTED, rejectedAt, "서류 확인 필요"
    ))).isOne();
    assertThat(accountStatusLogMapper.insertLog(statusLog(
        accountId, Status.REJECTED, Status.APPLIED, reappliedAt, "사용자 계좌 개설 재신청"
    ))).isOne();
    LocalDateTime limitChangedAt = CREATED_AT.plusMinutes(3);
    assertThat(accountStatusLogMapper.insertLog(statusLog(
        accountId,
        Status.APPLIED,
        Status.APPLIED,
        limitChangedAt,
        "LIMIT_CHANGE_V1|source=MYPAGE|from=30000000|to=40000000"
    ))).isOne();

    List<AccountStatusLogDTO> logs = accountStatusLogMapper.selectByAccountId(accountId);

    assertThat(logs).hasSize(3);
    assertThat(logs).extracting(AccountStatusLogDTO::getNewStatus)
        .containsExactly(Status.APPLIED, Status.REJECTED, Status.APPLIED);
    assertThat(logs).extracting(AccountStatusLogDTO::getReason)
        .containsExactly("최초 개설 신청", "서류 확인 필요", "사용자 계좌 개설 재신청");

    AccountStatusLogDTO latestLog = accountStatusLogMapper
        .selectLatestByAccountId(accountId)
        .orElseThrow();
    assertThat(latestLog.getPrevStatus()).isEqualTo(Status.REJECTED);
    assertThat(latestLog.getNewStatus()).isEqualTo(Status.APPLIED);
    assertThat(latestLog.getChangedAt()).isEqualTo(reappliedAt);
    assertThat(accountStatusLogMapper.selectLatestApplicationAt(accountId))
        .isEqualTo(reappliedAt);

    List<AccountStatusLogDTO> limitChanges =
        accountStatusLogMapper.selectLimitChangesByAccountId(accountId);
    assertThat(limitChanges).hasSize(1);
    assertThat(limitChanges.get(0).getPrevStatus()).isEqualTo(Status.APPLIED);
    assertThat(limitChanges.get(0).getNewStatus()).isEqualTo(Status.APPLIED);
    assertThat(limitChanges.get(0).getChangedAt()).isEqualTo(limitChangedAt);
    assertThat(limitChanges.get(0).getReason())
        .isEqualTo("LIMIT_CHANGE_V1|source=MYPAGE|from=30000000|to=40000000");
  }

  private void resetSchema() throws SQLException {
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement()) {
      statement.execute("DROP ALL OBJECTS");
      statement.execute("""
          CREATE TABLE customer (
              customer_id BIGINT PRIMARY KEY AUTO_INCREMENT,
              name VARCHAR(50) NOT NULL,
              birth_date DATE NOT NULL,
              phone VARCHAR(20),
              investor_type VARCHAR(20) NOT NULL,
              ci_hash VARCHAR(64) NOT NULL,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
          )
          """);
      statement.execute("""
          CREATE TABLE account (
              account_id BIGINT PRIMARY KEY AUTO_INCREMENT,
              customer_id BIGINT NOT NULL,
              account_no DECIMAL(10, 0),
              status VARCHAR(20) NOT NULL,
              opened_at DATETIME,
              created_at DATETIME NOT NULL,
              limit_amount DECIMAL(15, 0) NOT NULL,
              amount DECIMAL(15, 0) NOT NULL DEFAULT 0,
              benefit VARCHAR(20),
              CONSTRAINT uk_account_customer UNIQUE (customer_id),
              CONSTRAINT uk_account_no UNIQUE (account_no),
              CONSTRAINT chk_account_status CHECK (
                  status IN ('APPLIED', 'OPENED', 'REJECTED', 'CLOSURE_REQUESTED', 'CLOSED')
              ),
              CONSTRAINT chk_account_limit_amount CHECK (
                  limit_amount > 0 AND limit_amount <= 50000000
              ),
              CONSTRAINT fk_account_customer FOREIGN KEY (customer_id)
                  REFERENCES customer(customer_id)
          )
          """);
      statement.execute("""
          CREATE TABLE account_status_log (
              log_id BIGINT PRIMARY KEY AUTO_INCREMENT,
              account_id BIGINT NOT NULL,
              prev_status VARCHAR(20),
              new_status VARCHAR(20) NOT NULL,
              changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              reason VARCHAR(200),
              CONSTRAINT fk_account_status_log_account FOREIGN KEY (account_id)
                  REFERENCES account(account_id)
          )
          """);
      statement.execute("""
          INSERT INTO customer (
              customer_id, name, birth_date, phone, investor_type, ci_hash
          ) VALUES
              (1, '홍길동', DATE '1999-03-23', '010-1111-1111', '안정형', 'customer-1'),
              (2, '김리아', DATE '1990-01-01', '010-2222-2222', '위험중립형', 'customer-2'),
              (3, '이투자', DATE '1985-05-15', '010-3333-3333', '적극투자형', 'customer-3')
          """);
    }
  }

  private Long insertApplication(Long customerId, BigDecimal limitAmount) {
    assertThat(accountMapper.insertApplication(application(customerId, limitAmount))).isOne();
    return accountMapper.selectByCustomerId(customerId).orElseThrow().getAccountId();
  }

  private AccountDTO application(Long customerId, BigDecimal limitAmount) {
    return AccountDTO.builder()
        .customerId(customerId)
        .createdAt(CREATED_AT)
        .limitAmount(limitAmount)
        .build();
  }

  private void openAccount(Long accountId, BigDecimal accountNo) {
    AccountDTO approval = AccountDTO.builder()
        .accountId(accountId)
        .accountNo(accountNo)
        .openedAt(CREATED_AT.plusMinutes(1))
        .build();
    assertThat(accountMapper.approve(approval)).isOne();
  }

  private AccountStatusLogDTO statusLog(
      Long accountId,
      Status prevStatus,
      Status newStatus,
      LocalDateTime changedAt,
      String reason
  ) {
    return AccountStatusLogDTO.builder()
        .accountId(accountId)
        .prevStatus(prevStatus)
        .newStatus(newStatus)
        .changedAt(changedAt)
        .reason(reason)
        .build();
  }
}
