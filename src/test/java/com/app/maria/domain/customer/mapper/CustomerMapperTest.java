package com.app.maria.domain.customer.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.maria.domain.customer.dto.CustomerCiHashDTO;
import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
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

class CustomerMapperTest {

    private static PooledDataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;

    private SqlSession sqlSession;
    private CustomerMapper customerMapper;

    @BeforeAll
    static void configureMyBatis() throws IOException {
        try (Reader reader = Resources.getResourceAsReader("mybatis-customer-test-config.xml")) {
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
        }
        dataSource =
                (PooledDataSource)
                        sqlSessionFactory.getConfiguration().getEnvironment().getDataSource();
    }

    @BeforeEach
    void setUpDatabase() throws SQLException {
        resetSchema();
        sqlSession = sqlSessionFactory.openSession(true);
        customerMapper = sqlSession.getMapper(CustomerMapper.class);
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
    @DisplayName("OPENED 상태 계좌를 가진 고객만 조회된다")
    void selectActiveRiaCustomersReturnsOnlyCustomersWithOpenedAccount() throws SQLException {
        insertCustomer(1L, "ci-1");
        insertAccount(1L, "OPENED");
        insertCustomer(2L, "ci-2");
        insertAccount(2L, "APPLIED");

        List<CustomerCiHashDTO> result = customerMapper.selectActiveRiaCustomers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCustomerId()).isEqualTo(1L);
        assertThat(result.get(0).getCiHash()).isEqualTo("ci-1");
    }

    @Test
    @DisplayName("CLOSED, REJECTED, CLOSURE_REQUESTED 상태 계좌를 가진 고객은 제외된다")
    void selectActiveRiaCustomersExcludesNonOpenedStatuses() throws SQLException {
        insertCustomer(1L, "ci-1");
        insertAccount(1L, "CLOSED");
        insertCustomer(2L, "ci-2");
        insertAccount(2L, "REJECTED");
        insertCustomer(3L, "ci-3");
        insertAccount(3L, "CLOSURE_REQUESTED");

        List<CustomerCiHashDTO> result = customerMapper.selectActiveRiaCustomers();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("계좌가 아예 없는 고객은 조회되지 않는다")
    void selectActiveRiaCustomersExcludesCustomersWithoutAnyAccount() throws SQLException {
        insertCustomer(1L, "ci-1");

        List<CustomerCiHashDTO> result = customerMapper.selectActiveRiaCustomers();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("OPENED 계좌를 가진 고객이 여러 명이면 모두 조회된다")
    void selectActiveRiaCustomersReturnsAllCustomersWithOpenedAccounts() throws SQLException {
        insertCustomer(1L, "ci-1");
        insertAccount(1L, "OPENED");
        insertCustomer(2L, "ci-2");
        insertAccount(2L, "OPENED");

        List<CustomerCiHashDTO> result = customerMapper.selectActiveRiaCustomers();

        assertThat(result)
                .extracting(CustomerCiHashDTO::getCiHash)
                .containsExactlyInAnyOrder("ci-1", "ci-2");
    }

    private void insertCustomer(Long customerId, String ciHash) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO customer (customer_id, ci_hash) VALUES ("
                            + customerId
                            + ", '"
                            + ciHash
                            + "')");
        }
    }

    private void insertAccount(Long customerId, String status) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO account (customer_id, status) VALUES ("
                            + customerId
                            + ", '"
                            + status
                            + "')");
        }
    }

    private void resetSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute(
                    """
                    CREATE TABLE customer (
                        customer_id BIGINT PRIMARY KEY,
                        ci_hash VARCHAR(64) NOT NULL
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE account (
                        account_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        customer_id BIGINT NOT NULL,
                        status VARCHAR(20) NOT NULL
                    )
                    """);
        }
    }
}
