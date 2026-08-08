package com.app.maria.domain.tax.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.maria.domain.tax.dto.SellLotDTO;
import com.app.maria.domain.tax.dto.TaxCalculationResultDTO;
import com.app.maria.domain.tax.dto.TaxRuleDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * [F2] RIA 내 매도금액·양도소득 가중합산 검증.
 *
 * DB/외부 API를 타지 않고 값만 주입한다. 계산기가 순수 함수라 가능한 구조.
 * 기대값 출처: CLAUDE.md §3 검증 예시 — 규정을 손으로 계산한 값이지 코드에서 뽑은 값이 아니다.
 * 테스트가 깨지면 기대값을 고치지 말고 계산 로직이 규정과 어긋났는지 먼저 확인할 것.
 */
class
TaxCalculatorTest {

    private final TaxCalculator calculator = new TaxCalculator();

    // ----------------------------------------------------------------
    // 픽스처
    // ----------------------------------------------------------------

    /** tax_rule 시드와 동일한 구간 가중치. 퍼센트로 저장되므로 100/80/50을 넣는다. */
    private List<TaxRuleDTO> reliefRates() {
        return List.of(
                rule("100", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 31)),
                rule("80", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 31)),
                rule("50", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31)));
    }

    /** TaxRuleDTO 생성자 인자 순서가 (ruleId, ruleValue, validTo, validFrom)이므로 여기서 감싼다. */
    private TaxRuleDTO rule(String value, LocalDate validFrom, LocalDate validTo) {
        return new TaxRuleDTO(null, new BigDecimal(value), validTo, validFrom);
    }

    /** 취득원가 = purchasePrice × purchaseFxRate × sellQty, 양도소득 = finalAmount − 취득원가 */
    private SellLotDTO lot(LocalDate sellAt, String finalAmount,
                           String purchasePrice, String purchaseFxRate, String sellQty) {
        return SellLotDTO.builder()
                .sellAt(sellAt)
                .finalAmount(new BigDecimal(finalAmount))
                .purchasePrice(new BigDecimal(purchasePrice))
                .purchaseFxRate(new BigDecimal(purchaseFxRate))
                .sellQty(new BigDecimal(sellQty))
                .build();
    }

    // ----------------------------------------------------------------
    // 골든 시나리오
    // ----------------------------------------------------------------

    @Test
    @DisplayName("골든 시나리오 - CLAUDE.md §3 검증 예시와 일치한다")
    void 골든시나리오() {
        // 1~5월 : 매도 3,000만 − 취득 1,000만 = 양도소득 2,000만, 가중치 1.0
        // 6~7월 : 매도 1,000만 − 취득   400만 = 양도소득   600만, 가중치 0.8
        // 8~12월: 매도 1,000만 − 취득   400만 = 양도소득   600만, 가중치 0.5
        List<SellLotDTO> lots = List.of(
                lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"),
                lot(LocalDate.of(2026, 6, 15), "10000000", "100", "1000", "40"),
                lot(LocalDate.of(2026, 9, 20), "10000000", "100", "1000", "40"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates());

        // 3000×1.0 + 1000×0.8 + 1000×0.5 = 4,300만 (조정비율의 분모)
        assertThat(result.getWeightedSell()).isEqualByComparingTo("43000000");
        // 2000×1.0 + 600×0.8 + 600×0.5 = 2,780만 (조정전공제액)
        assertThat(result.getWeightedGain()).isEqualByComparingTo("27800000");
        // 2000 + 600 + 600 = 3,200만 — 가중치를 곱하지 않은 원금액(F6 과세표준용)
        assertThat(result.getOriginalGainAmount()).isEqualByComparingTo("32000000");
    }

    // ----------------------------------------------------------------
    // 구간 경계
    // ----------------------------------------------------------------

    @Test
    @DisplayName("구간 경계 - 5/31은 100%, 6/1은 80%")
    void 구간경계_5월말_6월초() {
        // 두 lot 모두 매도 1,000만 / 취득 400만 → 양도소득 600만. 날짜만 다르다.
        TaxCalculationResultDTO may = calculator.calculate(
                List.of(lot(LocalDate.of(2026, 5, 31), "10000000", "100", "1000", "40")), reliefRates());
        TaxCalculationResultDTO jun = calculator.calculate(
                List.of(lot(LocalDate.of(2026, 6, 1), "10000000", "100", "1000", "40")), reliefRates());

        assertThat(may.getWeightedSell()).isEqualByComparingTo("10000000");
        assertThat(may.getWeightedGain()).isEqualByComparingTo("6000000");

        assertThat(jun.getWeightedSell()).isEqualByComparingTo("8000000");
        assertThat(jun.getWeightedGain()).isEqualByComparingTo("4800000");

        // 비가중 양도소득은 구간과 무관하게 동일해야 한다
        assertThat(may.getOriginalGainAmount()).isEqualByComparingTo("6000000");
        assertThat(jun.getOriginalGainAmount()).isEqualByComparingTo("6000000");
    }

    @Test
    @DisplayName("구간 경계 - 7/31은 80%, 8/1은 50%")
    void 구간경계_7월말_8월초() {
        TaxCalculationResultDTO jul = calculator.calculate(
                List.of(lot(LocalDate.of(2026, 7, 31), "10000000", "100", "1000", "40")), reliefRates());
        TaxCalculationResultDTO aug = calculator.calculate(
                List.of(lot(LocalDate.of(2026, 8, 1), "10000000", "100", "1000", "40")), reliefRates());

        assertThat(jul.getWeightedSell()).isEqualByComparingTo("8000000");
        assertThat(jul.getWeightedGain()).isEqualByComparingTo("4800000");

        assertThat(aug.getWeightedSell()).isEqualByComparingTo("5000000");
        assertThat(aug.getWeightedGain()).isEqualByComparingTo("3000000");
    }

    @Test
    @DisplayName("과세연도 첫날·마지막날도 가중치를 찾는다")
    void 구간경계_연초_연말() {
        TaxCalculationResultDTO first = calculator.calculate(
                List.of(lot(LocalDate.of(2026, 1, 1), "10000000", "100", "1000", "40")), reliefRates());
        TaxCalculationResultDTO last = calculator.calculate(
                List.of(lot(LocalDate.of(2026, 12, 31), "10000000", "100", "1000", "40")), reliefRates());

        assertThat(first.getWeightedSell()).isEqualByComparingTo("10000000");
        assertThat(last.getWeightedSell()).isEqualByComparingTo("5000000");
    }

    /**
     * customer_seed.sql의 RELIEF_RATE 3행을 그대로 넣고, 매도결제일별로 올바른 구간이 붙는지 확인한다.
     *   ('RELIEF_RATE',100.0000,'2026-01-01','2026-05-31')
     *   ('RELIEF_RATE', 80.0000,'2026-06-01','2026-07-31')
     *   ('RELIEF_RATE', 50.0000,'2026-08-01','2026-12-31')
     * 매도금액 1,000만 / 취득 400만(=100×1000×40) 고정이므로 양도소득은 항상 600만이다.
     */
    @ParameterizedTest(name = "{0} 매도 → 가중치 {3}")
    @CsvSource({
            "2026-01-01, 10000000, 6000000, 100%",
            "2026-02-14, 10000000, 6000000, 100%",
            "2026-03-10, 10000000, 6000000, 100%",
            "2026-05-30, 10000000, 6000000, 100%",
            "2026-05-31, 10000000, 6000000, 100%",
            "2026-06-01,  8000000, 4800000, 80%",
            "2026-06-15,  8000000, 4800000, 80%",
            "2026-07-01,  8000000, 4800000, 80%",
            "2026-07-31,  8000000, 4800000, 80%",
            "2026-08-01,  5000000, 3000000, 50%",
            "2026-09-20,  5000000, 3000000, 50%",
            "2026-11-11,  5000000, 3000000, 50%",
            "2026-12-31,  5000000, 3000000, 50%",
    })
    @DisplayName("시드의 RELIEF_RATE 구간이 매도결제일별로 정확히 적용된다")
    void 매도일별_구간가중치(String sellDate, String expectedWeightedSell,
                     String expectedWeightedGain, String weightLabel) {

        List<SellLotDTO> lots = List.of(
                lot(LocalDate.parse(sellDate), "10000000", "100", "1000", "40"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates());

        assertThat(result.getWeightedSell()).isEqualByComparingTo(expectedWeightedSell);
        assertThat(result.getWeightedGain()).isEqualByComparingTo(expectedWeightedGain);
        // 비가중 양도소득은 구간과 무관하게 항상 600만
        assertThat(result.getOriginalGainAmount()).isEqualByComparingTo("6000000");
    }

    // ----------------------------------------------------------------
    // 손익통산
    // ----------------------------------------------------------------

    @Test
    @DisplayName("손익통산 - 손실 lot이 이익 lot과 상계된다")
    void 손익통산() {
        List<SellLotDTO> lots = List.of(
                lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"),  // 취득 1,000만 → +2,000만
                lot(LocalDate.of(2026, 3, 10), "5000000", "100", "1000", "100"));  // 취득 1,000만 → −500만

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates());

        // 손실을 0으로 자르면 2,000만이 나온다. 상계돼야 1,500만.
        assertThat(result.getOriginalGainAmount()).isEqualByComparingTo("15000000");
        assertThat(result.getWeightedGain()).isEqualByComparingTo("15000000");
        assertThat(result.getWeightedSell()).isEqualByComparingTo("35000000");
    }

    @Test
    @DisplayName("전부 손실이면 양도소득이 음수로 남는다")
    void 전부손실() {
        List<SellLotDTO> lots = List.of(
                lot(LocalDate.of(2026, 3, 10), "5000000", "100", "1000", "100"));  // 취득 1,000만 → −500만

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates());

        // F2는 음수를 그대로 넘긴다. 0으로 자르는 건 F5/F6의 책임.
        assertThat(result.getOriginalGainAmount()).isEqualByComparingTo("-5000000");
        assertThat(result.getWeightedGain()).isEqualByComparingTo("-5000000");
    }

    // ----------------------------------------------------------------
    // 예외 상황
    // ----------------------------------------------------------------

    @Test
    @DisplayName("매도 lot이 없으면 전부 0")
    void 매도없음() {
        TaxCalculationResultDTO result = calculator.calculate(List.of(), reliefRates());

        assertThat(result.getWeightedSell()).isEqualByComparingTo("0");
        assertThat(result.getWeightedGain()).isEqualByComparingTo("0");
        assertThat(result.getOriginalGainAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("가중치 구간 밖 날짜면 예외")
    void 가중치없는날짜() {
        List<SellLotDTO> lots = List.of(
                lot(LocalDate.of(2027, 1, 5), "10000000", "100", "1000", "40"));

        assertThatThrownBy(() -> calculator.calculate(lots, reliefRates()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("가중치 목록이 비어 있으면 예외")
    void 가중치목록없음() {
        List<SellLotDTO> lots = List.of(
                lot(LocalDate.of(2026, 3, 10), "10000000", "100", "1000", "40"));

        assertThatThrownBy(() -> calculator.calculate(lots, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
