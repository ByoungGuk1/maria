package com.app.maria.domain.withdrawal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.maria.domain.withdrawal.dto.LeftAmountDTO;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;

class WithdrawalMapperTest {

    private static PooledDataSource dataSource;
    private static SqlSessionFactory factory;
    private SqlSession session;
    private WithdrawalMapper mapper;

    @BeforeAll
    static void configureMyBatis() throws Exception {
        dataSource =
                new PooledDataSource(
                        "org.h2.Driver",
                        "jdbc:h2:mem:withdrawal_mapper_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                        "sa",
                        "");
        Configuration configuration =
                new Configuration(
                        new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(WithdrawalMapper.class);
        try (InputStream xml =
                Resources.getResourceAsStream("mappers/withdrawal/withdrawalMapper.xml")) {
            new XMLMapperBuilder(
                            xml,
                            configuration,
                            "mappers/withdrawal/withdrawalMapper.xml",
                            configuration.getSqlFragments())
                    .parse();
        }
        factory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void setUp() throws Exception {
        resetSchemaAndData();
        session = factory.openSession(true);
        mapper = session.getMapper(WithdrawalMapper.class);
    }

    @AfterEach
    void closeSession() {
        if (session != null) session.close();
    }

    @AfterAll
    static void closePool() {
        if (dataSource != null) dataSource.forceCloseAll();
    }

    @Test
    void selectsOnlyAvailableSourcesInFinalAtAndExchangeIdOrder() {
        List<LeftAmountDTO> result = mapper.selectAvailableLeftAmountsByAccountId(1L);

        assertThat(result)
                .extracting(LeftAmountDTO::getLeftAmountId)
                .containsExactly(102L, 106L, 101L);
        assertThat(result).extracting(LeftAmountDTO::getExchangeId).containsExactly(2L, 6L, 1L);
        assertThat(result)
                .extracting(LeftAmountDTO::getCurAmount)
                .containsExactly(
                        new BigDecimal("200"), new BigDecimal("50"), new BigDecimal("100"));
        assertThat(result).allSatisfy(value -> assertThat(value.getFinalAt()).isNotNull());
    }

    @Test
    void sumsOnlyImmatureAllocationsForRequestedWithdrawal() {
        assertThat(mapper.selectImmatureAllocatedAmountByWithdrawalId(10L))
                .isEqualByComparingTo("400");
        assertThat(mapper.selectImmatureAllocatedAmountByWithdrawalId(99L)).isZero();
    }

    private static void resetSchemaAndData() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute(
                    """
                    CREATE TABLE krw_exchange (
                        exchange_id BIGINT PRIMARY KEY,
                        account_id BIGINT NOT NULL,
                        settlement_status VARCHAR(12) NOT NULL,
                        final_at DATETIME NULL
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE left_amount (
                        left_amount_id BIGINT PRIMARY KEY,
                        exchange_id BIGINT NOT NULL,
                        cur_amount DECIMAL(15, 0) NOT NULL
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE withdrawal_allocation (
                        allocation_id BIGINT PRIMARY KEY,
                        withdrawal_id BIGINT NOT NULL,
                        left_amount_id BIGINT NULL,
                        allocated_amount DECIMAL(15, 0) NOT NULL,
                        withdrawal_at DATETIME NOT NULL,
                        type VARCHAR(40) NOT NULL
                    )
                    """);
            statement.execute(
                    """
                    INSERT INTO krw_exchange VALUES
                        (1, 1, 'FINALIZED', TIMESTAMP '2025-01-02 09:00:00'),
                        (2, 1, 'FINALIZED', TIMESTAMP '2025-01-01 09:00:00'),
                        (3, 2, 'FINALIZED', TIMESTAMP '2024-01-01 09:00:00'),
                        (4, 1, 'PROVISIONAL', TIMESTAMP '2024-01-01 09:00:00'),
                        (5, 1, 'FINALIZED', NULL),
                        (6, 1, 'FINALIZED', TIMESTAMP '2025-01-01 09:00:00'),
                        (7, 1, 'FINALIZED', TIMESTAMP '2024-01-01 09:00:00')
                    """);
            statement.execute(
                    """
                    INSERT INTO left_amount VALUES
                        (101, 1, 100), (102, 2, 200), (103, 3, 300),
                        (104, 4, 400), (105, 5, 500), (106, 6, 50),
                        (107, 7, 0)
                    """);
            statement.execute(
                    """
                    INSERT INTO withdrawal_allocation VALUES
                        (1, 10, NULL, 100, TIMESTAMP '2026-08-01 09:00:00', 'EARNINGS_ONLY'),
                        (2, 10, 101, 300, TIMESTAMP '2026-08-01 09:00:00', 'MATURED_PRINCIPAL_INCLUDED'),
                        (3, 10, 102, 250, TIMESTAMP '2026-08-01 09:00:00', 'IMMATURE_PRINCIPAL_INCLUDED'),
                        (4, 10, 106, 150, TIMESTAMP '2026-08-01 09:00:00', 'IMMATURE_PRINCIPAL_INCLUDED'),
                        (5, 11, 103, 999, TIMESTAMP '2026-08-01 09:00:00', 'IMMATURE_PRINCIPAL_INCLUDED')
                    """);
        }
    }
}
