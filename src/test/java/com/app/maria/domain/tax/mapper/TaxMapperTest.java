package com.app.maria.domain.tax.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.maria.domain.tax.dto.SellLotDTO;
import com.app.maria.domain.tax.dto.TaxRuleDTO;
import com.app.maria.domain.tax.fixture.TaxTestFixture;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
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

class TaxMapperTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long OTHER_ACCOUNT_ID = 2L;
    private static final int TAX_YEAR = 2026;
    private static final LocalDateTime CALC_BASE = LocalDateTime.of(2026, 12, 31, 23, 59, 59);

    private static PooledDataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;
    private static TaxTestFixture fixture;

    private SqlSession sqlSession;
    private TaxMapper taxMapper;

    @BeforeAll
    static void configureMyBatis() throws IOException {
        try (Reader reader = Resources.getResourceAsReader("mybatis-tax-test-config.xml")) {
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
        }
        dataSource = (PooledDataSource) sqlSessionFactory
                .getConfiguration()
                .getEnvironment()
                .getDataSource();
        fixture = new TaxTestFixture(dataSource);
    }

    @BeforeEach
    void setUpDatabase() throws SQLException {
        fixture.resetSchema();
        sqlSession = sqlSessionFactory.openSession(true);
        taxMapper = sqlSession.getMapper(TaxMapper.class);
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
    @DisplayName("규칙은 종류·기간 제한 없이 전량 조회된다")
    void findTaxRules_전량조회() {
        fixture.insertSeedTaxRules();

        List<TaxRuleDTO> rules = taxMapper.findTaxRules();

        assertThat(rules).hasSize(7);
        assertThat(rules).extracting(TaxRuleDTO::getRuleType)
                .containsExactlyInAnyOrder(
                        "DEPOSIT_LIMIT", "HOLDING_PERIOD",
                        "RELIEF_RATE", "RELIEF_RATE", "RELIEF_RATE",
                        "BASIC_DEDUCTION", "TAX_RATE");
    }

    @Test
    @DisplayName("valid_to가 9999-12-31인 규칙도 누락 없이 조회된다")
    void findTaxRules_열린구간규칙도_조회된다() {
        fixture.insertSeedTaxRules();

        List<TaxRuleDTO> rules = taxMapper.findTaxRules();

        assertThat(rules)
                .filteredOn(rule -> "BASIC_DEDUCTION".equals(rule.getRuleType()))
                .singleElement()
                .satisfies(rule -> {
                    assertThat(rule.getRuleValue()).isEqualByComparingTo("2500000");
                    assertThat(rule.getValidTo()).isEqualTo(LocalDate.of(9999, 12, 31));
                });

        assertThat(rules)
                .filteredOn(rule -> "TAX_RATE".equals(rule.getRuleType()))
                .singleElement()
                .satisfies(rule -> assertThat(rule.getRuleValue()).isEqualByComparingTo("0.22"));
    }

    @Test
    @DisplayName("규칙의 모든 컬럼이 DTO 필드로 매핑된다")
    void findTaxRules_컬럼매핑() {
        fixture.insertSeedTaxRules();

        TaxRuleDTO rule = taxMapper.findTaxRules().stream()
                .filter(r -> "RELIEF_RATE".equals(r.getRuleType())
                        && r.getValidFrom().equals(LocalDate.of(2026, 1, 1)))
                .findFirst()
                .orElseThrow();

        assertThat(rule.getRuleId()).isNotNull();
        assertThat(rule.getRuleType()).isEqualTo("RELIEF_RATE");
        assertThat(rule.getRuleValue()).isEqualByComparingTo("100");
        assertThat(rule.getValidFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(rule.getValidTo()).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    @DisplayName("확정산된 매도 lot의 모든 컬럼이 DTO 필드로 매핑된다")
    void findLots_컬럼매핑() {
        Long lotId = fixture.insertLot(ACCOUNT_ID, "150.0000", "1300.0000", "100.0000");
        Long orderId = fixture.insertSellOrder(lotId, "EXECUTED", LocalDateTime.of(2026, 3, 10, 10, 0), "100.0000");
        fixture.insertKrwExchange(ACCOUNT_ID, orderId, "FINALIZED", "24000000.00");

        List<SellLotDTO> lots = taxMapper.findFinalizedLotsByAccountAndYear(ACCOUNT_ID, TAX_YEAR, CALC_BASE);

        assertThat(lots).singleElement().satisfies(lot -> {
            assertThat(lot.getOrderId()).isEqualTo(orderId);
            assertThat(lot.getInboundDetailId()).isEqualTo(lotId);
            assertThat(lot.getSellAt()).isEqualTo(LocalDate.of(2026, 3, 10));
            assertThat(lot.getFinalAmount()).isEqualByComparingTo("24000000");
            assertThat(lot.getSellQty()).isEqualByComparingTo("100");
            assertThat(lot.getPurchasePrice()).isEqualByComparingTo("150");
            assertThat(lot.getPurchaseFxRate()).isEqualByComparingTo("1300");
        });
    }

    @Test
    @DisplayName("가정산(PROVISIONAL) 건은 조회되지 않는다")
    void findLots_가정산_제외() {
        Long lotId = fixture.insertLot(ACCOUNT_ID, "150.0000", "1300.0000", "100.0000");
        Long orderId = fixture.insertSellOrder(lotId, "EXECUTED", LocalDateTime.of(2026, 3, 10, 10, 0), "100.0000");
        fixture.insertKrwExchange(ACCOUNT_ID, orderId, "PROVISIONAL", "23760000.00");

        assertThat(taxMapper.findFinalizedLotsByAccountAndYear(ACCOUNT_ID, TAX_YEAR, CALC_BASE)).isEmpty();
    }

    @Test
    @DisplayName("EXECUTED가 아닌 매도 주문은 조회되지 않는다")
    void findLots_미체결_제외() {
        Long lotId = fixture.insertLot(ACCOUNT_ID, "150.0000", "1300.0000", "100.0000");
        Long orderId = fixture.insertSellOrder(lotId, "RECEIVED", LocalDateTime.of(2026, 3, 10, 10, 0), "100.0000");
        fixture.insertKrwExchange(ACCOUNT_ID, orderId, "FINALIZED", "24000000.00");

        assertThat(taxMapper.findFinalizedLotsByAccountAndYear(ACCOUNT_ID, TAX_YEAR, CALC_BASE)).isEmpty();
    }

    @Test
    @DisplayName("다른 계좌의 매도는 조회되지 않는다")
    void findLots_타계좌_제외() {
        Long lotId = fixture.insertLot(OTHER_ACCOUNT_ID, "150.0000", "1300.0000", "100.0000");
        Long orderId = fixture.insertSellOrder(lotId, "EXECUTED", LocalDateTime.of(2026, 3, 10, 10, 0), "100.0000");
        fixture.insertKrwExchange(OTHER_ACCOUNT_ID, orderId, "FINALIZED", "24000000.00");

        assertThat(taxMapper.findFinalizedLotsByAccountAndYear(ACCOUNT_ID, TAX_YEAR, CALC_BASE)).isEmpty();
    }

    @Test
    @DisplayName("과세연도가 다른 매도는 조회되지 않는다")
    void findLots_타연도_제외() {
        Long lotId = fixture.insertLot(ACCOUNT_ID, "150.0000", "1300.0000", "100.0000");
        Long orderId = fixture.insertSellOrder(lotId, "EXECUTED", LocalDateTime.of(2025, 12, 31, 10, 0), "100.0000");
        fixture.insertKrwExchange(ACCOUNT_ID, orderId, "FINALIZED", "24000000.00");

        assertThat(taxMapper.findFinalizedLotsByAccountAndYear(ACCOUNT_ID, TAX_YEAR, CALC_BASE)).isEmpty();
    }

    @Test
    @DisplayName("업무 기준시각 이후의 매도는 조회되지 않는다")
    void findLots_기준시각_이후_제외() {
        Long lotId = fixture.insertLot(ACCOUNT_ID, "150.0000", "1300.0000", "100.0000");
        Long orderId = fixture.insertSellOrder(lotId, "EXECUTED", LocalDateTime.of(2026, 9, 20, 10, 0), "100.0000");
        fixture.insertKrwExchange(ACCOUNT_ID, orderId, "FINALIZED", "24000000.00");

        LocalDateTime before = LocalDateTime.of(2026, 6, 30, 0, 0);

        assertThat(taxMapper.findFinalizedLotsByAccountAndYear(ACCOUNT_ID, TAX_YEAR, before)).isEmpty();
        assertThat(taxMapper.findFinalizedLotsByAccountAndYear(ACCOUNT_ID, TAX_YEAR, CALC_BASE)).hasSize(1);
    }

    @Test
    @DisplayName("한 lot을 여러 번 나눠 판 경우 매도 건마다 각각 조회된다")
    void findLots_동일lot_다건매도() {
        Long lotId = fixture.insertLot(ACCOUNT_ID, "150.0000", "1300.0000", "100.0000");
        Long first = fixture.insertSellOrder(lotId, "EXECUTED", LocalDateTime.of(2026, 3, 10, 10, 0), "40.0000");
        Long second = fixture.insertSellOrder(lotId, "EXECUTED", LocalDateTime.of(2026, 9, 20, 10, 0), "60.0000");
        fixture.insertKrwExchange(ACCOUNT_ID, first, "FINALIZED", "10000000.00");
        fixture.insertKrwExchange(ACCOUNT_ID, second, "FINALIZED", "15000000.00");

        List<SellLotDTO> lots = taxMapper.findFinalizedLotsByAccountAndYear(ACCOUNT_ID, TAX_YEAR, CALC_BASE);

        assertThat(lots).hasSize(2);
        assertThat(lots).extracting(SellLotDTO::getInboundDetailId).containsOnly(lotId);
        assertThat(lots).extracting(SellLotDTO::getSellQty)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("40"), new BigDecimal("60"));
    }

    @Test
    @DisplayName("매도 이력이 없으면 빈 목록을 반환한다")
    void findLots_없으면_빈목록() {
        assertThat(taxMapper.findFinalizedLotsByAccountAndYear(ACCOUNT_ID, TAX_YEAR, CALC_BASE)).isEmpty();
    }
}
