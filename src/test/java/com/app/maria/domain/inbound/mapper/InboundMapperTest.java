package com.app.maria.domain.inbound.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.maria.domain.inbound.dto.InboundDTO;
import com.app.maria.domain.inbound.dto.InboundDetailDTO;
import com.app.maria.domain.inbound.dto.InboundHoldingDTO;
import com.app.maria.domain.inbound.dto.InboundListDTO;
import com.app.maria.domain.inbound.dto.InboundMinDTO;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
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

class InboundMapperTest {

    private static final LocalDateTime PURCHASE_DATE = LocalDateTime.of(2025, 6, 1, 0, 0);
    private static final BigDecimal PURCHASE_PRICE = BigDecimal.valueOf(150.25);
    private static final BigDecimal PURCHASE_FX_RATE = BigDecimal.valueOf(1320.5);

    private static PooledDataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;

    private SqlSession sqlSession;
    private InboundMapper inboundMapper;

    @BeforeAll
    static void configureMyBatis() throws IOException {
        try (Reader reader = Resources.getResourceAsReader("mybatis-inbound-test-config.xml")) {
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
        inboundMapper = sqlSession.getMapper(InboundMapper.class);
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
    @DisplayName("승인 이력이 전혀 없으면 0을 반환한다")
    void sumReturnsZeroWhenNoApprovalHistoryExists() {
        BigDecimal sum = inboundMapper.sumApprovedQtyByAccountAndProduct(1L, 1L);

        assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("동일 계좌·동일 상품의 승인수량을 모두 합산한다")
    void sumAggregatesAllApprovalsForSameAccountAndProduct() {
        insertApprovedInbound(1L, 1L, BigDecimal.valueOf(60));
        insertApprovedInbound(1L, 1L, BigDecimal.valueOf(25));

        BigDecimal sum = inboundMapper.sumApprovedQtyByAccountAndProduct(1L, 1L);

        assertThat(sum).isEqualByComparingTo(BigDecimal.valueOf(85));
    }

    @Test
    @DisplayName("다른 상품(foreignProductId)의 승인수량은 합산에서 제외한다")
    void sumExcludesApprovalsForDifferentProduct() {
        insertApprovedInbound(1L, 1L, BigDecimal.valueOf(60));
        insertApprovedInbound(1L, 2L, BigDecimal.valueOf(999));

        BigDecimal sum = inboundMapper.sumApprovedQtyByAccountAndProduct(1L, 1L);

        assertThat(sum).isEqualByComparingTo(BigDecimal.valueOf(60));
    }

    @Test
    @DisplayName("다른 계좌(accountId)의 승인수량은 합산에서 제외한다")
    void sumExcludesApprovalsForDifferentAccount() {
        insertApprovedInbound(1L, 1L, BigDecimal.valueOf(60));
        insertApprovedInbound(2L, 1L, BigDecimal.valueOf(999));

        BigDecimal sum = inboundMapper.sumApprovedQtyByAccountAndProduct(1L, 1L);

        assertThat(sum).isEqualByComparingTo(BigDecimal.valueOf(60));
    }

    @Test
    @DisplayName("이후 매도로 current_qty가 줄어도, 최초 승인수량(qty) 기준으로 합산한다")
    void sumUsesOriginalApprovedQtyNotRemainingQty() {
        Long inboundDetailId = insertApprovedInbound(1L, 1L, BigDecimal.valueOf(60));
        reduceCurrentQty(inboundDetailId, BigDecimal.valueOf(10));

        BigDecimal sum = inboundMapper.sumApprovedQtyByAccountAndProduct(1L, 1L);

        assertThat(sum).isEqualByComparingTo(BigDecimal.valueOf(60));
    }

    @Test
    @DisplayName("두 번째 입고 신청의 가용수량은 기준일 보유수량에서 기존 승인수량을 뺀 값과 일치한다")
    void availableQtyReflectsPreviouslyApprovedQty() {
        BigDecimal snapshotQty = BigDecimal.valueOf(100);
        insertApprovedInbound(1L, 1L, BigDecimal.valueOf(60));

        BigDecimal alreadyApprovedQty = inboundMapper.sumApprovedQtyByAccountAndProduct(1L, 1L);
        BigDecimal availableQty = snapshotQty.subtract(alreadyApprovedQty).max(BigDecimal.ZERO);

        assertThat(alreadyApprovedQty).isEqualByComparingTo(BigDecimal.valueOf(60));
        assertThat(availableQty).isEqualByComparingTo(BigDecimal.valueOf(40));
    }

    // ---- selectFifoLots ----

    @Test
    @DisplayName("여러 lot이 있으면 purchase_date 오래된 순으로 반환한다")
    void selectFifoLotsReturnsLotsOrderedByPurchaseDateAscending() {
        Long newer =
                insertApprovedInbound(1L, 1L, BigDecimal.valueOf(30), PURCHASE_DATE.plusMonths(2));
        Long oldest = insertApprovedInbound(1L, 1L, BigDecimal.valueOf(10), PURCHASE_DATE);
        Long middle =
                insertApprovedInbound(1L, 1L, BigDecimal.valueOf(20), PURCHASE_DATE.plusMonths(1));

        List<InboundDetailDTO> lots = inboundMapper.selectFifoLots(1L, 1L);

        assertThat(lots)
                .extracting(InboundDetailDTO::getInboundDetailId)
                .containsExactly(oldest, middle, newer);
    }

    @Test
    @DisplayName("current_qty가 0인 lot은 제외한다")
    void selectFifoLotsExcludesLotsWithZeroCurrentQty() {
        Long depleted = insertApprovedInbound(1L, 1L, BigDecimal.valueOf(10), PURCHASE_DATE);
        reduceCurrentQty(depleted, BigDecimal.ZERO);
        Long remaining =
                insertApprovedInbound(1L, 1L, BigDecimal.valueOf(20), PURCHASE_DATE.plusMonths(1));

        List<InboundDetailDTO> lots = inboundMapper.selectFifoLots(1L, 1L);

        assertThat(lots)
                .extracting(InboundDetailDTO::getInboundDetailId)
                .containsExactly(remaining);
    }

    @Test
    @DisplayName("다른 계좌·다른 종목의 lot은 제외한다")
    void selectFifoLotsExcludesOtherAccountsAndProducts() {
        insertApprovedInbound(2L, 1L, BigDecimal.valueOf(10), PURCHASE_DATE);
        insertApprovedInbound(1L, 2L, BigDecimal.valueOf(10), PURCHASE_DATE);
        Long matching = insertApprovedInbound(1L, 1L, BigDecimal.valueOf(10), PURCHASE_DATE);

        List<InboundDetailDTO> lots = inboundMapper.selectFifoLots(1L, 1L);

        assertThat(lots).extracting(InboundDetailDTO::getInboundDetailId).containsExactly(matching);
    }

    @Test
    @DisplayName("해당 계좌·종목의 lot이 없으면 빈 목록을 반환한다")
    void selectFifoLotsReturnsEmptyListWhenNoLotsExist() {
        List<InboundDetailDTO> lots = inboundMapper.selectFifoLots(1L, 1L);

        assertThat(lots).isEmpty();
    }

    // ---- selectHoldingsByAccount ----

    @Test
    @DisplayName("동일 종목의 여러 lot은 수량을 합산해 한 건으로 반환한다")
    void selectHoldingsByAccountSumsQtyAcrossLotsOfSameProduct() {
        insertApprovedInbound(1L, 1L, BigDecimal.valueOf(30));
        insertApprovedInbound(1L, 1L, BigDecimal.valueOf(20));

        List<InboundHoldingDTO> holdings = inboundMapper.selectHoldingsByAccount(1L);

        assertThat(holdings).hasSize(1);
        assertThat(holdings.get(0).getForeignProductId()).isEqualTo(1L);
        assertThat(holdings.get(0).getCurrentQty()).isEqualByComparingTo(BigDecimal.valueOf(50));
    }

    @Test
    @DisplayName("종목이 다르면 각각 별도 행으로 반환한다")
    void selectHoldingsByAccountGroupsByDistinctProduct() {
        insertApprovedInbound(1L, 1L, BigDecimal.valueOf(30));
        insertApprovedInbound(1L, 2L, BigDecimal.valueOf(10));

        List<InboundHoldingDTO> holdings = inboundMapper.selectHoldingsByAccount(1L);

        assertThat(holdings)
                .extracting(InboundHoldingDTO::getForeignProductId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("current_qty가 0인 lot만 있는 종목은 결과에서 제외한다")
    void selectHoldingsByAccountExcludesProductWithOnlyZeroCurrentQtyLots() {
        Long depleted = insertApprovedInbound(1L, 1L, BigDecimal.valueOf(30));
        reduceCurrentQty(depleted, BigDecimal.ZERO);
        insertApprovedInbound(1L, 2L, BigDecimal.valueOf(10));

        List<InboundHoldingDTO> holdings = inboundMapper.selectHoldingsByAccount(1L);

        assertThat(holdings).extracting(InboundHoldingDTO::getForeignProductId).containsExactly(2L);
    }

    @Test
    @DisplayName("다른 계좌의 보유종목은 섞이지 않는다")
    void selectHoldingsByAccountExcludesOtherAccounts() {
        insertApprovedInbound(1L, 1L, BigDecimal.valueOf(30));
        insertApprovedInbound(2L, 1L, BigDecimal.valueOf(999));

        List<InboundHoldingDTO> holdings = inboundMapper.selectHoldingsByAccount(1L);

        assertThat(holdings).hasSize(1);
        assertThat(holdings.get(0).getCurrentQty()).isEqualByComparingTo(BigDecimal.valueOf(30));
    }

    @Test
    @DisplayName("보유종목이 없으면 빈 목록을 반환한다")
    void selectHoldingsByAccountReturnsEmptyListWhenNoHoldingsExist() {
        List<InboundHoldingDTO> holdings = inboundMapper.selectHoldingsByAccount(1L);

        assertThat(holdings).isEmpty();
    }

    // ---- selectInbounds ----

    @Test
    @DisplayName("입고 이력을 고객명·종목정보와 함께 처리일시 최신순으로 조회한다")
    void selectInboundsReturnsListOrderedByProcessedAtDesc() throws SQLException {
        insertCustomer(1L, "홍길동");
        insertAccount(1L, 1L, "1234567890");
        insertForeignProduct(1L, "AAPL", "Apple Inc.");
        insertFullInbound(
                1L,
                1L,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(90),
                BigDecimal.valueOf(80),
                BigDecimal.valueOf(80),
                LocalDateTime.of(2026, 3, 5, 9, 0));
        insertFullInbound(
                1L,
                1L,
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(50),
                LocalDateTime.of(2026, 3, 6, 9, 0));

        List<InboundListDTO> result = inboundMapper.selectInbounds(0, 20);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProcessedAt()).isEqualTo(LocalDateTime.of(2026, 3, 6, 9, 0));
        assertThat(result.get(1).getProcessedAt()).isEqualTo(LocalDateTime.of(2026, 3, 5, 9, 0));
        assertThat(result.get(0).getCustomerName()).isEqualTo("홍길동");
        assertThat(result.get(0).getTicker()).isEqualTo("AAPL");
        assertThat(result.get(0).getProductName()).isEqualTo("Apple Inc.");
        assertThat(result.get(0).getAccountNo()).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("잔여 가능 수량은 12.23 기준수량에서 동일 계좌·종목의 누적 승인수량을 뺀 값이다")
    void selectInboundsReturnsRemainingQtyBasedOnCumulativeApprovals() throws SQLException {
        insertCustomer(1L, "홍길동");
        insertAccount(1L, 1L, "1234567890");
        insertForeignProduct(1L, "AAPL", "Apple Inc.");
        insertFullInbound(
                1L,
                1L,
                BigDecimal.valueOf(30),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(30),
                LocalDateTime.of(2026, 3, 5, 9, 0));
        insertFullInbound(
                1L,
                1L,
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(70),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(20),
                LocalDateTime.of(2026, 3, 6, 9, 0));

        List<InboundListDTO> result = inboundMapper.selectInbounds(0, 20);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRemainingQty()).isEqualByComparingTo("50");
        assertThat(result.get(1).getRemainingQty()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("3-way MIN 계산 근거(신청수량/기준일수량/현재보유수량/승인수량)를 그대로 반환한다")
    void selectInboundsReturnsThreeWayMinFields() throws SQLException {
        insertCustomer(1L, "홍길동");
        insertAccount(1L, 1L, "1234567890");
        insertForeignProduct(1L, "AAPL", "Apple Inc.");
        insertFullInbound(
                1L,
                1L,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(90),
                BigDecimal.valueOf(80),
                BigDecimal.valueOf(80),
                LocalDateTime.of(2026, 3, 5, 9, 0));

        List<InboundListDTO> result = inboundMapper.selectInbounds(0, 20);

        assertThat(result).hasSize(1);
        InboundListDTO dto = result.get(0);
        assertThat(dto.getRequestedQty()).isEqualByComparingTo("100");
        assertThat(dto.getCurrentHoldingAtRequest()).isEqualByComparingTo("90");
        assertThat(dto.getSnapshotQty()).isEqualByComparingTo("80");
        assertThat(dto.getApprovedQty()).isEqualByComparingTo("80");
    }

    @Test
    @DisplayName("입고 이력이 없으면 빈 목록을 반환한다")
    void selectInboundsReturnsEmptyListWhenNoInboundsExist() {
        List<InboundListDTO> result = inboundMapper.selectInbounds(0, 20);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("size만큼만 조회하고, offset을 지정하면 그만큼 건너뛴 뒤부터 조회한다")
    void selectInboundsAppliesOffsetAndSizeForPagination() throws SQLException {
        insertCustomer(1L, "홍길동");
        insertAccount(1L, 1L, "1234567890");
        insertForeignProduct(1L, "AAPL", "Apple Inc.");
        insertFullInbound(
                1L,
                1L,
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(10),
                LocalDateTime.of(2026, 3, 5, 9, 0));
        insertFullInbound(
                1L,
                1L,
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(20),
                LocalDateTime.of(2026, 3, 6, 9, 0));
        insertFullInbound(
                1L,
                1L,
                BigDecimal.valueOf(30),
                BigDecimal.valueOf(30),
                BigDecimal.valueOf(30),
                BigDecimal.valueOf(30),
                LocalDateTime.of(2026, 3, 7, 9, 0));

        List<InboundListDTO> firstPage = inboundMapper.selectInbounds(0, 2);
        List<InboundListDTO> secondPage = inboundMapper.selectInbounds(2, 2);

        assertThat(firstPage).hasSize(2);
        assertThat(firstPage.get(0).getProcessedAt()).isEqualTo(LocalDateTime.of(2026, 3, 7, 9, 0));
        assertThat(firstPage.get(1).getProcessedAt()).isEqualTo(LocalDateTime.of(2026, 3, 6, 9, 0));
        assertThat(secondPage).hasSize(1);
        assertThat(secondPage.get(0).getProcessedAt())
                .isEqualTo(LocalDateTime.of(2026, 3, 5, 9, 0));
    }

    @Test
    @DisplayName("countInbounds는 전체 입고 이력 건수를 반환한다")
    void countInboundsReturnsTotalRowCount() throws SQLException {
        insertCustomer(1L, "홍길동");
        insertAccount(1L, 1L, "1234567890");
        insertForeignProduct(1L, "AAPL", "Apple Inc.");
        insertFullInbound(
                1L,
                1L,
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(10),
                LocalDateTime.of(2026, 3, 5, 9, 0));
        insertFullInbound(
                1L,
                1L,
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(20),
                LocalDateTime.of(2026, 3, 6, 9, 0));

        int count = inboundMapper.countInbounds();

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("countInbounds는 입고 이력이 없으면 0을 반환한다")
    void countInboundsReturnsZeroWhenNoInboundsExist() {
        int count = inboundMapper.countInbounds();

        assertThat(count).isEqualTo(0);
    }

    private void insertCustomer(Long customerId, String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO customer (customer_id, name) VALUES ("
                            + customerId
                            + ", '"
                            + name
                            + "')");
        }
    }

    private void insertAccount(Long accountId, Long customerId, String accountNo)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO account (account_id, customer_id, account_no) VALUES ("
                            + accountId
                            + ", "
                            + customerId
                            + ", '"
                            + accountNo
                            + "')");
        }
    }

    private void insertForeignProduct(Long foreignProductId, String ticker, String name)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO foreign_product (foreign_product_id, ticker, name) VALUES ("
                            + foreignProductId
                            + ", '"
                            + ticker
                            + "', '"
                            + name
                            + "')");
        }
    }

    private Long insertFullInbound(
            Long accountId,
            Long foreignProductId,
            BigDecimal requestedQty,
            BigDecimal currentHoldingAtRequest,
            BigDecimal snapshotQty,
            BigDecimal approvedQty,
            LocalDateTime processedAt) {
        InboundDTO inboundDTO =
                InboundDTO.builder()
                        .accountId(accountId)
                        .requestedQty(requestedQty)
                        .currentHoldingAtRequest(currentHoldingAtRequest)
                        .approvedQty(approvedQty)
                        .processedAt(processedAt)
                        .build();
        inboundMapper.insertInbound(inboundDTO);

        InboundDetailDTO inboundDetailDTO =
                InboundDetailDTO.builder()
                        .inboundId(inboundDTO.getInboundId())
                        .foreignProductId(foreignProductId)
                        .qty(approvedQty)
                        .currentQty(approvedQty)
                        .purchaseDate(PURCHASE_DATE)
                        .purchasePrice(PURCHASE_PRICE)
                        .purchaseCurrency("USD")
                        .purchaseFxRate(PURCHASE_FX_RATE)
                        .sourceGeneralAccountId(accountId)
                        .build();
        inboundMapper.insertInboundDetail(inboundDetailDTO);

        InboundMinDTO inboundMinDTO =
                InboundMinDTO.of(
                        inboundDetailDTO.getInboundDetailId(),
                        requestedQty,
                        approvedQty,
                        snapshotQty);
        inboundMapper.insertInboundMin(inboundMinDTO);

        return inboundDTO.getInboundId();
    }

    private void resetSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute(
                    """
          CREATE TABLE inbound (
              inbound_id BIGINT PRIMARY KEY AUTO_INCREMENT,
              account_id BIGINT NOT NULL,
              requested_qty DECIMAL(15, 4) NOT NULL,
              current_holding_at_request DECIMAL(15, 4),
              approved_qty DECIMAL(15, 4) NOT NULL,
              processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
          )
          """);
            statement.execute(
                    """
          CREATE TABLE inbound_detail (
              inbound_detail_id BIGINT PRIMARY KEY AUTO_INCREMENT,
              inbound_id BIGINT NOT NULL,
              foreign_product_id BIGINT NOT NULL,
              source_broker VARCHAR(50),
              qty DECIMAL(15, 4) NOT NULL,
              current_qty DECIMAL(15, 4) NOT NULL,
              purchase_date DATETIME NOT NULL,
              purchase_price DECIMAL(15, 4) NOT NULL,
              purchase_currency VARCHAR(10) NOT NULL,
              purchase_fx_rate DECIMAL(15, 4) NOT NULL,
              source_general_account_id BIGINT,
              recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              CONSTRAINT fk_inbound_detail_inbound FOREIGN KEY (inbound_id)
                  REFERENCES inbound(inbound_id)
          )
          """);
            statement.execute(
                    """
          CREATE TABLE inbound_min (
              inbound_min_id BIGINT PRIMARY KEY AUTO_INCREMENT,
              inbound_detail_id BIGINT NOT NULL,
              requested_qty DECIMAL(15, 4) NOT NULL,
              approved_qty DECIMAL(15, 4) NOT NULL,
              snapshot_qty DECIMAL(15, 4) NOT NULL
          )
          """);
            statement.execute(
                    """
          CREATE TABLE customer (
              customer_id BIGINT PRIMARY KEY AUTO_INCREMENT,
              name VARCHAR(50) NOT NULL
          )
          """);
            statement.execute(
                    """
          CREATE TABLE account (
              account_id BIGINT PRIMARY KEY AUTO_INCREMENT,
              customer_id BIGINT NOT NULL,
              account_no VARCHAR(10)
          )
          """);
            statement.execute(
                    """
          CREATE TABLE foreign_product (
              foreign_product_id BIGINT PRIMARY KEY AUTO_INCREMENT,
              ticker VARCHAR(20) NOT NULL,
              name VARCHAR(100) NOT NULL
          )
          """);
        }
    }

    private Long insertApprovedInbound(
            Long accountId, Long foreignProductId, BigDecimal approvedQty) {
        return insertApprovedInbound(accountId, foreignProductId, approvedQty, PURCHASE_DATE);
    }

    private Long insertApprovedInbound(
            Long accountId,
            Long foreignProductId,
            BigDecimal approvedQty,
            LocalDateTime purchaseDate) {
        InboundDTO inboundDTO =
                InboundDTO.builder()
                        .accountId(accountId)
                        .requestedQty(approvedQty)
                        .currentHoldingAtRequest(approvedQty)
                        .approvedQty(approvedQty)
                        .processedAt(PURCHASE_DATE)
                        .build();
        inboundMapper.insertInbound(inboundDTO);

        InboundDetailDTO inboundDetailDTO =
                InboundDetailDTO.builder()
                        .inboundId(inboundDTO.getInboundId())
                        .foreignProductId(foreignProductId)
                        .qty(approvedQty)
                        .currentQty(approvedQty)
                        .purchaseDate(purchaseDate)
                        .purchasePrice(PURCHASE_PRICE)
                        .purchaseCurrency("USD")
                        .purchaseFxRate(PURCHASE_FX_RATE)
                        .sourceGeneralAccountId(accountId)
                        .build();
        inboundMapper.insertInboundDetail(inboundDetailDTO);

        return inboundDetailDTO.getInboundDetailId();
    }

    private void reduceCurrentQty(Long inboundDetailId, BigDecimal newCurrentQty) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "UPDATE inbound_detail SET current_qty = ? WHERE inbound_detail_id = ?")) {
            statement.setBigDecimal(1, newCurrentQty);
            statement.setLong(2, inboundDetailId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
