package com.app.maria.domain.sellorder.mapper;

import org.apache.ibatis.datasource.pooled.PooledDataSource;
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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SellLimitMapperTest {

    private static PooledDataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession sqlSession;
    private SellLimitMapper sellLimitMapper;

    @BeforeAll
    static void configureMyBatis() throws IOException {
        try (Reader reader = Resources.getResourceAsReader("mybatis-selllimit-test-config.xml")) {
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
        sellLimitMapper = sqlSession.getMapper(SellLimitMapper.class);
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

    // ---- selectAccountLimitForUpdate ----

    @Test
    @DisplayName("계좌의 limit_amount를 정확히 그 계좌 것으로 조회한다 (다른 계좌 값과 안 섞임)")
    void selectAccountLimitForUpdateReturnsThisAccountsLimitNotAnotherAccounts() throws SQLException {
        Long customerA = insertCustomer("ci-hash-a");
        Long customerB = insertCustomer("ci-hash-b");
        Long accountA = insertAccount(customerA, "30000000");
        insertAccount(customerB, "45000000");

        Optional<BigDecimal> result = sellLimitMapper.selectAccountLimitForUpdate(accountA);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo("30000000");
    }

    @Test
    @DisplayName("존재하지 않는 계좌면 빈 Optional을 반환한다")
    void selectAccountLimitForUpdateReturnsEmptyWhenAccountDoesNotExist() {
        Optional<BigDecimal> result = sellLimitMapper.selectAccountLimitForUpdate(999L);

        assertThat(result).isEmpty();
    }

    // ---- sumFinalizedExchangeAmount ----

    @Test
    @DisplayName("확정산(krw_exchange) 이력이 하나도 없으면 null이 아니라 0을 반환한다")
    void sumFinalizedExchangeAmountReturnsZeroNotNullWhenNoExchangesExist() throws SQLException {
        Long customerId = insertCustomer("ci-hash-none");
        Long accountId = insertAccount(customerId, "10000000");

        BigDecimal result = sellLimitMapper.sumFinalizedExchangeAmount(accountId);

        assertThat(result).isNotNull();
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("PROVISIONAL 상태 건은 제외하고 FINALIZED 건만 더한다")
    void sumFinalizedExchangeAmountExcludesProvisionalRows() throws SQLException {
        Long customerId = insertCustomer("ci-hash-mixed");
        Long accountId = insertAccount(customerId, "10000000");
        insertExchange(accountId, "500", "FINALIZED");
        insertExchange(accountId, "9999999", "PROVISIONAL");

        BigDecimal result = sellLimitMapper.sumFinalizedExchangeAmount(accountId);

        assertThat(result).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("같은 계좌의 FINALIZED 건 여러 개를 합산한다")
    void sumFinalizedExchangeAmountSumsMultipleFinalizedRows() throws SQLException {
        Long customerId = insertCustomer("ci-hash-multi");
        Long accountId = insertAccount(customerId, "10000000");
        insertExchange(accountId, "1000000", "FINALIZED");
        insertExchange(accountId, "2500000", "FINALIZED");

        BigDecimal result = sellLimitMapper.sumFinalizedExchangeAmount(accountId);

        assertThat(result).isEqualByComparingTo("3500000");
    }

    @Test
    @DisplayName("다른 계좌의 FINALIZED 금액은 이 계좌 합계에 안 섞인다")
    void sumFinalizedExchangeAmountDoesNotLeakAmountsFromOtherAccounts() throws SQLException {
        Long customerA = insertCustomer("ci-hash-x");
        Long customerB = insertCustomer("ci-hash-y");
        Long accountA = insertAccount(customerA, "10000000");
        Long accountB = insertAccount(customerB, "10000000");
        insertExchange(accountA, "700", "FINALIZED");
        insertExchange(accountB, "88888888", "FINALIZED");

        BigDecimal result = sellLimitMapper.sumFinalizedExchangeAmount(accountA);

        assertThat(result).isEqualByComparingTo("700");
    }

    // ---- selectCiHashByAccountId ----

    @Test
    @DisplayName("account_id로 customer를 조인해 정확히 그 고객의 ci_hash를 반환한다")
    void selectCiHashByAccountIdReturnsMatchingCustomersCiHashNotAnothers() throws SQLException {
        Long customerA = insertCustomer("ci-hash-owner-a");
        Long customerB = insertCustomer("ci-hash-owner-b");
        Long accountA = insertAccount(customerA, "10000000");
        insertAccount(customerB, "10000000");

        Optional<String> result = sellLimitMapper.selectCiHashByAccountId(accountA);

        assertThat(result).contains("ci-hash-owner-a");
    }

    @Test
    @DisplayName("존재하지 않는 계좌면 빈 Optional을 반환한다")
    void selectCiHashByAccountIdReturnsEmptyWhenAccountDoesNotExist() {
        Optional<String> result = sellLimitMapper.selectCiHashByAccountId(999L);

        assertThat(result).isEmpty();
    }

    private void resetSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("""
                    CREATE TABLE customer (
                        customer_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        ci_hash VARCHAR(64) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE account (
                        account_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        customer_id BIGINT NOT NULL,
                        limit_amount DECIMAL(15,0) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE krw_exchange (
                        exchange_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        account_id BIGINT NOT NULL,
                        final_amount DECIMAL(15,0),
                        settlement_status VARCHAR(12) NOT NULL
                    )
                    """);
        }
    }

    private Long insertCustomer(String ciHash) throws SQLException {
        String sql = "INSERT INTO customer (ci_hash) VALUES (?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, ciHash);
            statement.executeUpdate();
            var keys = statement.getGeneratedKeys();
            keys.next();
            return keys.getLong(1);
        }
    }

    private Long insertAccount(Long customerId, String limitAmount) throws SQLException {
        String sql = "INSERT INTO account (customer_id, limit_amount) VALUES (?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, customerId);
            statement.setBigDecimal(2, new BigDecimal(limitAmount));
            statement.executeUpdate();
            var keys = statement.getGeneratedKeys();
            keys.next();
            return keys.getLong(1);
        }
    }

    private void insertExchange(Long accountId, String finalAmount, String settlementStatus) throws SQLException {
        String sql = "INSERT INTO krw_exchange (account_id, final_amount, settlement_status) VALUES (?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, accountId);
            statement.setBigDecimal(2, new BigDecimal(finalAmount));
            statement.setString(3, settlementStatus);
            statement.executeUpdate();
        }
    }
}
