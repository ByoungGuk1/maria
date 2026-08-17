package com.app.maria.domain.sellorder.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.maria.domain.sellorder.dto.SellOrderDTO;
import com.app.maria.domain.sellorder.type.SellOrderStatus;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

class SellOrderMapperTest {

    private static PooledDataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession sqlSession;
    private SellOrderMapper sellOrderMapper;

    @BeforeAll
    static void configureMyBatis() throws IOException {
        try (Reader reader = Resources.getResourceAsReader("mybatis-sellorder-test-config.xml")) {
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
        sellOrderMapper = sqlSession.getMapper(SellOrderMapper.class);
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

    // ---- insertSellOrder / selectSellOrderById ----

    @Test
    @DisplayName("insert 후 useGeneratedKeys로 orderId가 실제로 채워진다")
    void insertSellOrderPopulatesGeneratedOrderId() throws SQLException {
        Long inboundDetailId = insertInboundChain(1L);
        SellOrderDTO dto =
                newSellOrder(
                        1L,
                        10L,
                        inboundDetailId,
                        "10",
                        "308.91",
                        SellOrderStatus.RECEIVED,
                        "1433.6");

        sellOrderMapper.insertSellOrder(dto);

        assertThat(dto.getOrderId()).isNotNull();
    }

    @Test
    @DisplayName("저장한 모든 필드(account_id/foreign_product_id 포함)가 조회 시 정확히 그대로 돌아온다")
    void selectSellOrderByIdRoundTripsAllFieldsExactly() throws SQLException {
        Long inboundDetailId = insertInboundChain(1L);
        SellOrderDTO dto =
                newSellOrder(
                        1L,
                        10L,
                        inboundDetailId,
                        "12.5",
                        "100.1234",
                        SellOrderStatus.EXECUTED,
                        "1350.5");
        sellOrderMapper.insertSellOrder(dto);

        Optional<SellOrderDTO> result = sellOrderMapper.selectSellOrderById(dto.getOrderId());

        assertThat(result).isPresent();
        SellOrderDTO found = result.get();
        assertThat(found.getAccountId()).isEqualTo(1L);
        assertThat(found.getForeignProductId()).isEqualTo(10L);
        assertThat(found.getInboundDetailId()).isEqualTo(inboundDetailId);
        assertThat(found.getSellQty()).isEqualByComparingTo("12.5");
        assertThat(found.getBasePrice()).isEqualByComparingTo("100.1234");
        assertThat(found.getStatus()).isEqualTo(SellOrderStatus.EXECUTED);
        assertThat(found.getSettlementFxRate()).isEqualByComparingTo("1350.5");
    }

    @Test
    @DisplayName("settlement_fx_rate가 null이어도 예외 없이 null로 조회된다")
    void selectSellOrderByIdReturnsNullSettlementFxRateWhenNotSet() throws SQLException {
        Long inboundDetailId = insertInboundChain(1L);
        SellOrderDTO dto =
                newSellOrder(1L, 10L, inboundDetailId, "5", "50", SellOrderStatus.RECEIVED, null);
        sellOrderMapper.insertSellOrder(dto);

        Optional<SellOrderDTO> result = sellOrderMapper.selectSellOrderById(dto.getOrderId());

        assertThat(result).isPresent();
        assertThat(result.get().getSettlementFxRate()).isNull();
    }

    @Test
    @DisplayName(
            "REJECTED 건은 inbound_detail_id가 null이어도 저장/조회가 되고, account_id/foreign_product_id는 그대로 남는다")
    void insertSellOrderAllowsNullInboundDetailIdForRejectedOrder() throws SQLException {
        SellOrderDTO dto =
                newSellOrder(1L, 10L, null, "100", "308.91", SellOrderStatus.REJECTED, null);

        sellOrderMapper.insertSellOrder(dto);
        Optional<SellOrderDTO> result = sellOrderMapper.selectSellOrderById(dto.getOrderId());

        assertThat(result).isPresent();
        assertThat(result.get().getInboundDetailId()).isNull();
        assertThat(result.get().getAccountId()).isEqualTo(1L);
        assertThat(result.get().getForeignProductId()).isEqualTo(10L);
        assertThat(result.get().getStatus()).isEqualTo(SellOrderStatus.REJECTED);
    }

    @Test
    @DisplayName("존재하지 않는 orderId면 빈 Optional을 반환한다")
    void selectSellOrderByIdReturnsEmptyWhenNotFound() {
        Optional<SellOrderDTO> result = sellOrderMapper.selectSellOrderById(999L);

        assertThat(result).isEmpty();
    }

    // ---- selectSellOrdersByAccountId ----

    @Test
    @DisplayName("계좌의 매도 주문만 반환하고 다른 계좌의 매도 주문은 섞이지 않는다")
    void selectSellOrdersByAccountIdDoesNotLeakOrdersFromOtherAccounts() throws SQLException {
        Long inboundDetailForAccountA = insertInboundChain(100L);
        Long inboundDetailForAccountB = insertInboundChain(200L);

        SellOrderDTO orderForA =
                newSellOrder(
                        100L,
                        10L,
                        inboundDetailForAccountA,
                        "1",
                        "10",
                        SellOrderStatus.RECEIVED,
                        "1000");
        sellOrderMapper.insertSellOrder(orderForA);
        SellOrderDTO orderForB =
                newSellOrder(
                        200L,
                        10L,
                        inboundDetailForAccountB,
                        "2",
                        "20",
                        SellOrderStatus.RECEIVED,
                        "1000");
        sellOrderMapper.insertSellOrder(orderForB);

        List<SellOrderDTO> result = sellOrderMapper.selectSellOrdersByAccountId(100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrderId()).isEqualTo(orderForA.getOrderId());
    }

    @Test
    @DisplayName("같은 계좌에 매도 주문이 여러 건이면 중복/누락 없이 전부 반환한다")
    void selectSellOrdersByAccountIdReturnsAllOrdersForSameAccountWithoutDuplicates()
            throws SQLException {
        Long accountId = 300L;
        Long inboundDetail1 = insertInboundChain(accountId);
        Long inboundDetail2 = insertInboundChainUnderExistingInbound(accountId);

        SellOrderDTO order1 =
                newSellOrder(
                        accountId,
                        10L,
                        inboundDetail1,
                        "1",
                        "10",
                        SellOrderStatus.RECEIVED,
                        "1000");
        sellOrderMapper.insertSellOrder(order1);
        SellOrderDTO order2 =
                newSellOrder(
                        accountId,
                        10L,
                        inboundDetail2,
                        "2",
                        "20",
                        SellOrderStatus.RECEIVED,
                        "1000");
        sellOrderMapper.insertSellOrder(order2);

        List<SellOrderDTO> result = sellOrderMapper.selectSellOrdersByAccountId(accountId);

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(SellOrderDTO::getOrderId)
                .containsExactlyInAnyOrder(order1.getOrderId(), order2.getOrderId());
    }

    @Test
    @DisplayName("매도 주문이 없는 계좌는 빈 목록을 반환한다")
    void selectSellOrdersByAccountIdReturnsEmptyListWhenNoOrdersExist() throws SQLException {
        insertInboundChain(400L);

        List<SellOrderDTO> result = sellOrderMapper.selectSellOrdersByAccountId(400L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("REJECTED로 inbound_detail_id 없이 저장된 건도 계좌별 조회에 포함된다 (조인이 아니라 account_id 직접 필터라서)")
    void selectSellOrdersByAccountIdIncludesRejectedOrdersWithoutInboundDetail()
            throws SQLException {
        Long accountId = 500L;
        Long inboundDetailId = insertInboundChain(accountId);
        SellOrderDTO executed =
                newSellOrder(
                        accountId,
                        10L,
                        inboundDetailId,
                        "1",
                        "10",
                        SellOrderStatus.EXECUTED,
                        "1000");
        sellOrderMapper.insertSellOrder(executed);
        SellOrderDTO rejected =
                newSellOrder(accountId, 10L, null, "5", "10", SellOrderStatus.REJECTED, null);
        sellOrderMapper.insertSellOrder(rejected);

        List<SellOrderDTO> result = sellOrderMapper.selectSellOrdersByAccountId(accountId);

        assertThat(result)
                .extracting(SellOrderDTO::getOrderId)
                .containsExactlyInAnyOrder(executed.getOrderId(), rejected.getOrderId());
    }

    // ---- selectSellOrdersByInboundDetailIds ----

    @Test
    @DisplayName("여러 inboundDetailId를 한번에 조회하면 각각의 매도 이력을 전부 반환한다")
    void selectSellOrdersByInboundDetailIdsReturnsOrdersForGivenDetailIds() throws SQLException {
        Long detail1 = insertInboundChain(600L);
        Long detail2 = insertInboundChain(700L);
        SellOrderDTO order1 =
                newSellOrder(600L, 10L, detail1, "1", "10", SellOrderStatus.EXECUTED, "1000");
        sellOrderMapper.insertSellOrder(order1);
        SellOrderDTO order2 =
                newSellOrder(700L, 10L, detail2, "2", "20", SellOrderStatus.RECEIVED, "1000");
        sellOrderMapper.insertSellOrder(order2);

        List<SellOrderDTO> result =
                sellOrderMapper.selectSellOrdersByInboundDetailIds(List.of(detail1, detail2));

        assertThat(result)
                .extracting(SellOrderDTO::getOrderId)
                .containsExactlyInAnyOrder(order1.getOrderId(), order2.getOrderId());
    }

    @Test
    @DisplayName("조회 대상에 없는 inboundDetailId의 매도 이력은 섞이지 않는다")
    void selectSellOrdersByInboundDetailIdsExcludesOrdersForOtherDetailIds() throws SQLException {
        Long includedDetail = insertInboundChain(800L);
        Long excludedDetail = insertInboundChain(900L);
        SellOrderDTO includedOrder =
                newSellOrder(
                        800L, 10L, includedDetail, "1", "10", SellOrderStatus.EXECUTED, "1000");
        sellOrderMapper.insertSellOrder(includedOrder);
        SellOrderDTO excludedOrder =
                newSellOrder(
                        900L, 10L, excludedDetail, "2", "20", SellOrderStatus.EXECUTED, "1000");
        sellOrderMapper.insertSellOrder(excludedOrder);

        List<SellOrderDTO> result =
                sellOrderMapper.selectSellOrdersByInboundDetailIds(List.of(includedDetail));

        assertThat(result)
                .extracting(SellOrderDTO::getOrderId)
                .containsExactly(includedOrder.getOrderId());
    }

    @Test
    @DisplayName("매도 이력이 없는 inboundDetailId면 빈 목록을 반환한다")
    void selectSellOrdersByInboundDetailIdsReturnsEmptyListWhenNoMatch() throws SQLException {
        Long detail = insertInboundChain(1000L);

        List<SellOrderDTO> result =
                sellOrderMapper.selectSellOrdersByInboundDetailIds(List.of(detail));

        assertThat(result).isEmpty();
    }

    private SellOrderDTO newSellOrder(
            Long accountId,
            Long foreignProductId,
            Long inboundDetailId,
            String sellQty,
            String basePrice,
            SellOrderStatus status,
            String settlementFxRate) {
        return SellOrderDTO.builder()
                .accountId(accountId)
                .foreignProductId(foreignProductId)
                .inboundDetailId(inboundDetailId)
                .sellQty(new BigDecimal(sellQty))
                .basePrice(new BigDecimal(basePrice))
                .status(status)
                .settlementFxRate(
                        settlementFxRate == null ? null : new BigDecimal(settlementFxRate))
                .processedAt(LocalDateTime.of(2026, 8, 6, 10, 0))
                .build();
    }

    /** inbound 1건 + inbound_detail 1건을 새로 만들어 그 inbound_detail_id를 반환한다. */
    private Long insertInboundChain(Long accountId) throws SQLException {
        Long inboundId = insertInbound(accountId);
        return insertInboundDetail(inboundId);
    }

    /** 이미 있는 계좌의 inbound에 inbound_detail만 하나 더 추가해서 그 id를 반환한다 (같은 계좌, lot만 여러 개인 케이스). */
    private Long insertInboundChainUnderExistingInbound(Long accountId) throws SQLException {
        Long inboundId = insertInbound(accountId);
        return insertInboundDetail(inboundId);
    }

    private Long insertInbound(Long accountId) throws SQLException {
        String sql = "INSERT INTO inbound (account_id) VALUES (?)";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, accountId);
            statement.executeUpdate();
            var keys = statement.getGeneratedKeys();
            keys.next();
            return keys.getLong(1);
        }
    }

    private Long insertInboundDetail(Long inboundId) throws SQLException {
        String sql =
                "INSERT INTO inbound_detail (inbound_id, foreign_product_id, purchase_date, purchase_price, purchase_currency, purchase_fx_rate, qty, current_qty) "
                        + "VALUES (?, 1, CURRENT_TIMESTAMP, 100, 'USD', 1300, 10, 10)";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, inboundId);
            statement.executeUpdate();
            var keys = statement.getGeneratedKeys();
            keys.next();
            return keys.getLong(1);
        }
    }

    private void resetSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute(
                    """
                    CREATE TABLE inbound (
                        inbound_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        account_id BIGINT NOT NULL
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE inbound_detail (
                        inbound_detail_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        inbound_id BIGINT NOT NULL,
                        foreign_product_id BIGINT NOT NULL,
                        purchase_date DATETIME NOT NULL,
                        purchase_price DECIMAL(15,4) NOT NULL,
                        purchase_currency VARCHAR(10) NOT NULL,
                        purchase_fx_rate DECIMAL(15,4) NOT NULL,
                        qty DECIMAL(15,4) NOT NULL,
                        current_qty DECIMAL(15,4) NOT NULL
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE sell_order (
                        order_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        account_id BIGINT NOT NULL,
                        foreign_product_id BIGINT NOT NULL,
                        inbound_detail_id BIGINT NULL,
                        sell_qty DECIMAL(15,4) NOT NULL,
                        base_price DECIMAL(15,4) NOT NULL,
                        processed_at DATETIME NULL,
                        status VARCHAR(10) NOT NULL,
                        settlement_fx_rate DECIMAL(15,4) NULL
                    )
                    """);
        }
    }
}
