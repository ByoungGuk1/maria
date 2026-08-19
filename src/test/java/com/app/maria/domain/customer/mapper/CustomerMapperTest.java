package com.app.maria.domain.customer.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.maria.domain.customer.dto.CustomerCiHashDTO;
import com.app.maria.domain.customer.dto.CustomerSearchDTO;
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

    @Test
    @DisplayName("이름 앞부분이 일치하고 계좌가 없는 고객만 최신순으로 조회한다")
    void searchEligibleCustomersByNameReturnsOnlyCustomersWithoutAccount() throws SQLException {
        insertSearchCustomer(
                1L, "김리아", "1990-01-02", "010-1111-2222", "STABLE", "2026-08-15 09:00:00");
        insertSearchCustomer(
                2L, "김리아", "1991-02-03", "010-3333-4444", "ACTIVE", "2026-08-16 09:00:00");
        insertSearchCustomer(
                3L, "김마리아", "1992-03-04", "010-5555-6666", "NEUTRAL", "2026-08-17 09:00:00");
        insertSearchCustomer(
                4L, "이리아", "1993-04-05", "010-7777-8888", "STABLE", "2026-08-18 09:00:00");
        insertAccount(2L, "REJECTED");

        List<CustomerSearchDTO> result = customerMapper.searchEligibleCustomersByName("김", 10);

        assertThat(result).extracting(CustomerSearchDTO::getCustomerId).containsExactly(3L, 1L);
        assertThat(result.get(0).getName()).isEqualTo("김마리아");
        assertThat(result.get(0).getPhone()).isEqualTo("010-5555-6666");
    }

    @Test
    @DisplayName("정확히 일치하는 이름은 최신 가입 고객보다 먼저 노출하고 조회 개수를 제한한다")
    void searchEligibleCustomersByNamePrioritizesExactNameAndLimitsResults() throws SQLException {
        insertSearchCustomer(
                1L, "홍길", "1990-01-02", "010-1111-2222", "STABLE", "2026-08-15 09:00:00");
        insertSearchCustomer(
                2L, "홍길동", "1991-02-03", "010-3333-4444", "ACTIVE", "2026-08-17 09:00:00");
        insertSearchCustomer(
                3L, "홍길순", "1992-03-04", "010-5555-6666", "NEUTRAL", "2026-08-16 09:00:00");

        List<CustomerSearchDTO> result = customerMapper.searchEligibleCustomersByName("홍길", 2);

        assertThat(result).extracting(CustomerSearchDTO::getCustomerId).containsExactly(1L, 2L);
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

    private void insertSearchCustomer(
            Long customerId,
            String name,
            String birthDate,
            String phone,
            String investorType,
            String createdAt)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO customer (customer_id, name, birth_date, phone, investor_type, ci_hash, created_at) VALUES ("
                            + customerId
                            + ", '"
                            + name
                            + "', '"
                            + birthDate
                            + "', '"
                            + phone
                            + "', '"
                            + investorType
                            + "', 'search-ci-"
                            + customerId
                            + "', '"
                            + createdAt
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
                        name VARCHAR(50),
                        birth_date DATE,
                        phone VARCHAR(20),
                        investor_type VARCHAR(20),
                        ci_hash VARCHAR(64) NOT NULL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
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
