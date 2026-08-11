package com.app.maria.domain.targetproduct.mapper;

import com.app.maria.domain.targetproduct.dto.TargetProductJudgementDTO;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TargetProductMapperTest {

    private static PooledDataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;

    private SqlSession sqlSession;
    private TargetProductMapper targetProductMapper;

    @BeforeAll
    static void configureMyBatis() throws IOException {
        try (Reader reader = Resources.getResourceAsReader("mybatis-targetproduct-test-config.xml")) {
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
        targetProductMapper = sqlSession.getMapper(TargetProductMapper.class);
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

    private static TargetProductJudgementDTO.TargetProductJudgementDTOBuilder baseBuilder(Long mydataTradeId) {
        return TargetProductJudgementDTO.builder()
                .mydataTradeId(mydataTradeId)
                .ciHash("ci-1")
                .stockType("FOREIGN_STOCK")
                .isTarget(true)
                .tradeType("BUY")
                .amount(new BigDecimal("1000000.00"))
                .tradeDate(LocalDate.of(2026, 3, 5))
                .netBuyAmount(new BigDecimal("1000000.00"))
                .judgedAt(LocalDateTime.of(2026, 8, 7, 3, 0));
    }

    @Test
    @DisplayName("판별 결과를 저장하면 저장된 행을 확인할 수 있다")
    void insertJudgementSavesRow() throws SQLException {
        TargetProductJudgementDTO dto = baseBuilder(1L)
                .stockType("FUND")
                .fundCode("448630")
                .fundName("TIGER 미국배당다우존스")
                .foreignStockRatio(new BigDecimal("72.50"))
                .inceptionDate(LocalDate.of(2023, 5, 10))
                .build();

        targetProductMapper.insertJudgement(dto);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT mydata_trade_id, ci_hash, stock_type, fund_code, fund_name, is_target, foreign_stock_ratio, inception_date, " +
                             "trade_type, amount, trade_date, net_buy_amount FROM target_product_judgement WHERE mydata_trade_id = 1")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getLong("mydata_trade_id")).isEqualTo(1L);
            assertThat(resultSet.getString("ci_hash")).isEqualTo("ci-1");
            assertThat(resultSet.getString("stock_type")).isEqualTo("FUND");
            assertThat(resultSet.getString("fund_code")).isEqualTo("448630");
            assertThat(resultSet.getString("fund_name")).isEqualTo("TIGER 미국배당다우존스");
            assertThat(resultSet.getBoolean("is_target")).isTrue();
            assertThat(resultSet.getBigDecimal("foreign_stock_ratio")).isEqualByComparingTo("72.50");
            assertThat(resultSet.getDate("inception_date").toLocalDate()).isEqualTo(LocalDate.of(2023, 5, 10));
            assertThat(resultSet.getString("trade_type")).isEqualTo("BUY");
            assertThat(resultSet.getBigDecimal("amount")).isEqualByComparingTo("1000000.00");
            assertThat(resultSet.getDate("trade_date").toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 5));
            assertThat(resultSet.getBigDecimal("net_buy_amount")).isEqualByComparingTo("1000000.00");
        }
    }

    @Test
    @DisplayName("SELL 거래는 음수로 부호처리된 net_buy_amount가 그대로 저장된다")
    void insertJudgementSavesNegativeNetBuyAmountForSellTrade() throws SQLException {
        TargetProductJudgementDTO dto = baseBuilder(11L)
                .tradeType("SELL")
                .amount(new BigDecimal("300000.00"))
                .netBuyAmount(new BigDecimal("-300000.00"))
                .build();

        targetProductMapper.insertJudgement(dto);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT trade_type, amount, net_buy_amount FROM target_product_judgement WHERE mydata_trade_id = 11")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("trade_type")).isEqualTo("SELL");
            assertThat(resultSet.getBigDecimal("amount")).isEqualByComparingTo("300000.00");
            assertThat(resultSet.getBigDecimal("net_buy_amount")).isEqualByComparingTo("-300000.00");
        }
    }

    @Test
    @DisplayName("FOREIGN_STOCK 등 비FUND 판정은 fund_code와 비중 정보 없이 저장된다")
    void insertJudgementSavesRowWithoutFundInfoForNonFund() throws SQLException {
        TargetProductJudgementDTO dto = baseBuilder(2L)
                .ticker("AAPL")
                .fundCode(null)
                .foreignStockRatio(null)
                .inceptionDate(null)
                .build();

        targetProductMapper.insertJudgement(dto);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT stock_type, ticker, fund_code, foreign_stock_ratio FROM target_product_judgement WHERE mydata_trade_id = 2")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("stock_type")).isEqualTo("FOREIGN_STOCK");
            assertThat(resultSet.getString("ticker")).isEqualTo("AAPL");
            assertThat(resultSet.getString("fund_code")).isNull();
            assertThat(resultSet.getBigDecimal("foreign_stock_ratio")).isNull();
        }
    }

    @Test
    @DisplayName("동일 mydataTradeId는 두 번 저장할 수 없다")
    void mydataTradeIdMustBeUnique() {
        TargetProductJudgementDTO first = baseBuilder(3L).build();
        targetProductMapper.insertJudgement(first);

        TargetProductJudgementDTO duplicate = baseBuilder(3L).isTarget(false).build();

        assertThatThrownBy(() -> targetProductMapper.insertJudgement(duplicate))
                .isInstanceOf(org.apache.ibatis.exceptions.PersistenceException.class);
    }

    @Test
    @DisplayName("판정이 저장된 mydataTradeId는 존재 여부 조회에서 true를 반환한다")
    void existsByMydataTradeIdReturnsTrueWhenJudgementExists() {
        TargetProductJudgementDTO dto = baseBuilder(10L).build();
        targetProductMapper.insertJudgement(dto);

        boolean exists = targetProductMapper.existsByMydataTradeId(10L);

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("판정이 저장되지 않은 mydataTradeId는 존재 여부 조회에서 false를 반환한다")
    void existsByMydataTradeIdReturnsFalseWhenJudgementDoesNotExist() {
        boolean exists = targetProductMapper.existsByMydataTradeId(999L);

        assertThat(exists).isFalse();
    }

    private void resetSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("""
                    CREATE TABLE target_product_judgement (
                        judgement_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        mydata_trade_id BIGINT NOT NULL,
                        ci_hash VARCHAR(64) NOT NULL,
                        stock_type VARCHAR(15) NOT NULL,
                        fund_code VARCHAR(12),
                        fund_name VARCHAR(100),
                        ticker VARCHAR(20),
                        is_target BOOLEAN NOT NULL,
                        foreign_stock_ratio DECIMAL(5, 2),
                        inception_date DATE,
                        judged_at DATETIME NOT NULL,
                        trade_type VARCHAR(15) NOT NULL,
                        amount DECIMAL(15, 2) NOT NULL,
                        trade_date DATE NOT NULL,
                        net_buy_amount DECIMAL(15, 2) NOT NULL,
                        CONSTRAINT uq_target_product__trade UNIQUE (mydata_trade_id)
                    )
                    """);
        }
    }
}