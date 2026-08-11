package com.app.maria.domain.tax.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.maria.domain.tax.dto.ExternalBuyDTO;
import com.app.maria.domain.tax.dto.SellLotDTO;
import com.app.maria.domain.tax.dto.TaxCalculationResultDTO;
import com.app.maria.domain.tax.dto.TaxRuleDTO;
import static com.app.maria.domain.tax.fixture.TaxFixtures.externalBuy;
import static com.app.maria.domain.tax.fixture.TaxFixtures.lot;
import static com.app.maria.domain.tax.fixture.TaxFixtures.reliefRate;
import static com.app.maria.domain.tax.fixture.TaxFixtures.reliefRates;
import static com.app.maria.domain.tax.fixture.TaxFixtures.rule;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TaxCalculatorTest {

    private final TaxCalculator calculator = new TaxCalculator();

    @Test
    @DisplayName("골든 시나리오 - CLAUDE.md §3 검증 예시와 일치한다")
    void 골든시나리오() {
        List<SellLotDTO> lots = List.of(
                lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"),
                lot(LocalDate.of(2026, 6, 15), "10000000", "100", "1000", "40"),
                lot(LocalDate.of(2026, 9, 20), "10000000", "100", "1000", "40"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates(), List.of());

        assertThat(result.getWeightedSell()).isEqualByComparingTo("43000000");
        assertThat(result.getWeightedGain()).isEqualByComparingTo("27800000");
        assertThat(result.getOriginalGainAmount()).isEqualByComparingTo("32000000");
    }

    @Test
    @DisplayName("구간 경계 - 5/31은 100%, 6/1은 80%")
    void 구간경계_5월말_6월초() {
        TaxCalculationResultDTO may = calculator.calculate(
                List.of(lot(LocalDate.of(2026, 5, 31), "10000000", "100", "1000", "40")), reliefRates(), List.of());
        TaxCalculationResultDTO jun = calculator.calculate(
                List.of(lot(LocalDate.of(2026, 6, 1), "10000000", "100", "1000", "40")), reliefRates(), List.of());

        assertThat(may.getWeightedSell()).isEqualByComparingTo("10000000");
        assertThat(may.getWeightedGain()).isEqualByComparingTo("6000000");

        assertThat(jun.getWeightedSell()).isEqualByComparingTo("8000000");
        assertThat(jun.getWeightedGain()).isEqualByComparingTo("4800000");

        assertThat(may.getOriginalGainAmount()).isEqualByComparingTo("6000000");
        assertThat(jun.getOriginalGainAmount()).isEqualByComparingTo("6000000");
    }

    @Test
    @DisplayName("구간 경계 - 7/31은 80%, 8/1은 50%")
    void 구간경계_7월말_8월초() {
        TaxCalculationResultDTO jul = calculator.calculate(
                List.of(lot(LocalDate.of(2026, 7, 31), "10000000", "100", "1000", "40")), reliefRates(), List.of());
        TaxCalculationResultDTO aug = calculator.calculate(
                List.of(lot(LocalDate.of(2026, 8, 1), "10000000", "100", "1000", "40")), reliefRates(), List.of());

        assertThat(jul.getWeightedSell()).isEqualByComparingTo("8000000");
        assertThat(jul.getWeightedGain()).isEqualByComparingTo("4800000");

        assertThat(aug.getWeightedSell()).isEqualByComparingTo("5000000");
        assertThat(aug.getWeightedGain()).isEqualByComparingTo("3000000");
    }

    @Test
    @DisplayName("과세연도 첫날·마지막날도 가중치를 찾는다")
    void 구간경계_연초_연말() {
        TaxCalculationResultDTO first = calculator.calculate(
                List.of(lot(LocalDate.of(2026, 1, 1), "10000000", "100", "1000", "40")), reliefRates(), List.of());
        TaxCalculationResultDTO last = calculator.calculate(
                List.of(lot(LocalDate.of(2026, 12, 31), "10000000", "100", "1000", "40")), reliefRates(), List.of());

        assertThat(first.getWeightedSell()).isEqualByComparingTo("10000000");
        assertThat(last.getWeightedSell()).isEqualByComparingTo("5000000");
    }

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

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates(), List.of());

        assertThat(result.getWeightedSell()).isEqualByComparingTo(expectedWeightedSell);
        assertThat(result.getWeightedGain()).isEqualByComparingTo(expectedWeightedGain);
        assertThat(result.getOriginalGainAmount()).isEqualByComparingTo("6000000");
    }

    @Test
    @DisplayName("손익통산 - 손실 lot이 이익 lot과 상계된다")
    void 손익통산() {
        List<SellLotDTO> lots = List.of(
                lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"),
                lot(LocalDate.of(2026, 3, 10), "5000000", "100", "1000", "100"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates(), List.of());

        assertThat(result.getOriginalGainAmount()).isEqualByComparingTo("15000000");
        assertThat(result.getWeightedGain()).isEqualByComparingTo("15000000");
        assertThat(result.getWeightedSell()).isEqualByComparingTo("35000000");
    }

    @Test
    @DisplayName("전부 손실이면 양도소득이 음수로 남는다")
    void 전부손실() {
        List<SellLotDTO> lots = List.of(
                lot(LocalDate.of(2026, 3, 10), "5000000", "100", "1000", "100"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates(), List.of());

        assertThat(result.getOriginalGainAmount()).isEqualByComparingTo("-5000000");
        assertThat(result.getWeightedGain()).isEqualByComparingTo("-5000000");
    }

    @Test
    @DisplayName("매도 lot이 없으면 전부 0")
    void 매도없음() {
        TaxCalculationResultDTO result = calculator.calculate(List.of(), reliefRates(), List.of());

        assertThat(result.getWeightedSell()).isEqualByComparingTo("0");
        assertThat(result.getWeightedGain()).isEqualByComparingTo("0");
        assertThat(result.getOriginalGainAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("가중치 구간 밖 날짜면 예외")
    void 가중치없는날짜() {
        List<SellLotDTO> lots = List.of(
                lot(LocalDate.of(2027, 1, 5), "10000000", "100", "1000", "40"));

        assertThatThrownBy(() -> calculator.calculate(lots, reliefRates(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RELIEF_RATE가 아닌 규칙이 섞여 있어도 가중치만 골라 쓴다")
    void 다른규칙이_섞여도_가중치만_사용한다() {
        List<TaxRuleDTO> mixed = List.of(
                rule("DEPOSIT_LIMIT", "50000000", LocalDate.of(2026, 1, 1), LocalDate.of(9999, 12, 31)),
                rule("BASIC_DEDUCTION", "2500000", LocalDate.of(2026, 1, 1), LocalDate.of(9999, 12, 31)),
                rule("TAX_RATE", "0.22", LocalDate.of(2026, 1, 1), LocalDate.of(9999, 12, 31)),
                reliefRate("100", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 31)),
                reliefRate("80", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 31)),
                reliefRate("50", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31)));

        List<SellLotDTO> lots = List.of(
                lot(LocalDate.of(2026, 9, 20), "10000000", "100", "1000", "40"));

        TaxCalculationResultDTO result = calculator.calculate(lots, mixed, List.of());

        assertThat(result.getWeightedSell()).isEqualByComparingTo("5000000");
        assertThat(result.getWeightedGain()).isEqualByComparingTo("3000000");
    }

    @Test
    @DisplayName("RELIEF_RATE가 하나도 없으면 예외")
    void 가중치규칙없음() {
        List<TaxRuleDTO> onlyConstants = List.of(
                rule("BASIC_DEDUCTION", "2500000", LocalDate.of(2026, 1, 1), LocalDate.of(9999, 12, 31)));

        List<SellLotDTO> lots = List.of(
                lot(LocalDate.of(2026, 3, 10), "10000000", "100", "1000", "40"));

        assertThatThrownBy(() -> calculator.calculate(lots, onlyConstants, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("가중치 목록이 비어 있으면 예외")
    void 가중치목록없음() {
        List<SellLotDTO> lots = List.of(
                lot(LocalDate.of(2026, 3, 10), "10000000", "100", "1000", "40"));

        assertThatThrownBy(() -> calculator.calculate(lots, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("골든 시나리오 - 외부 순매수까지 §3 검증 예시와 일치한다")
    void 골든시나리오_외부순매수() {
        List<SellLotDTO> lots = List.of(
                lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"),
                lot(LocalDate.of(2026, 6, 15), "10000000", "100", "1000", "40"),
                lot(LocalDate.of(2026, 9, 20), "10000000", "100", "1000", "40"));

        List<ExternalBuyDTO> external = List.of(
                externalBuy(LocalDate.of(2026, 6, 15), "20000000"),
                externalBuy(LocalDate.of(2026, 9, 20), "-10000000"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates(), external);

        assertThat(result.getWeightedExternalAmount()).isEqualByComparingTo("11000000");
        assertThat(result.getWeightedSell()).isEqualByComparingTo("43000000");
        assertThat(result.getWeightedGain()).isEqualByComparingTo("27800000");
        assertThat(result.getOriginalGainAmount()).isEqualByComparingTo("32000000");
    }

    @Test
    @DisplayName("외부 거래가 없으면 순매수는 0")
    void 외부거래_없음() {
        List<SellLotDTO> lots = List.of(lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates(), List.of());

        assertThat(result.getWeightedExternalAmount()).isEqualByComparingTo("0");
    }

    @ParameterizedTest(name = "{0} 매수 1,000만 → 가중 {1}")
    @CsvSource({
            "2026-01-01, 10000000",
            "2026-05-31, 10000000",
            "2026-06-01,  8000000",
            "2026-07-31,  8000000",
            "2026-08-01,  5000000",
            "2026-12-31,  5000000"})
    @DisplayName("외부 순매수도 거래일별 구간 가중치가 그대로 적용된다")
    void 외부_구간가중치(String tradeDate, String expected) {
        List<SellLotDTO> lots = List.of(lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"));
        List<ExternalBuyDTO> external = List.of(externalBuy(LocalDate.parse(tradeDate), "10000000"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates(), external);

        assertThat(result.getWeightedExternalAmount()).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("매수·매도가 섞이면 각 거래일 가중치를 적용해 상계한다")
    void 외부_매수매도_상계() {
        List<SellLotDTO> lots = List.of(lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"));
        List<ExternalBuyDTO> external = List.of(
                externalBuy(LocalDate.of(2026, 3, 10), "10000000"),
                externalBuy(LocalDate.of(2026, 6, 15), "10000000"),
                externalBuy(LocalDate.of(2026, 9, 20), "-10000000"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates(), external);

        assertThat(result.getWeightedExternalAmount()).isEqualByComparingTo("13000000");
    }

    @Test
    @DisplayName("외부에서 순매도면 조정할 순매수가 없으므로 0으로 본다")
    void 외부_순매도는_0() {
        List<SellLotDTO> lots = List.of(lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"));
        List<ExternalBuyDTO> external = List.of(
                externalBuy(LocalDate.of(2026, 3, 10), "20000000"),
                externalBuy(LocalDate.of(2026, 3, 10), "-30000000"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates(), external);

        assertThat(result.getWeightedExternalAmount()).isEqualByComparingTo("0");
        assertThat(result.getWeightedExternalAmount().signum()).isZero();
    }

    @Test
    @DisplayName("매수·매도가 정확히 상쇄되면 0")
    void 외부_완전상쇄() {
        List<SellLotDTO> lots = List.of(lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"));
        List<ExternalBuyDTO> external = List.of(
                externalBuy(LocalDate.of(2026, 6, 15), "10000000"),
                externalBuy(LocalDate.of(2026, 6, 15), "-10000000"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates(), external);

        assertThat(result.getWeightedExternalAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("금액은 소수점 2자리로 반올림된다")
    void 외부_금액_스케일() {
        List<SellLotDTO> lots = List.of(lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"));
        List<ExternalBuyDTO> external = List.of(externalBuy(LocalDate.of(2026, 9, 20), "1000000.005"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates(), external);

        assertThat(result.getWeightedExternalAmount()).isEqualByComparingTo("500000.00");
        assertThat(result.getWeightedExternalAmount().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("매도 lot이 없어도 외부 순매수는 독립적으로 집계된다")
    void 외부_매도없어도_집계() {
        List<ExternalBuyDTO> external = List.of(externalBuy(LocalDate.of(2026, 3, 10), "10000000"));

        TaxCalculationResultDTO result = calculator.calculate(List.of(), reliefRates(), external);

        assertThat(result.getWeightedSell()).isEqualByComparingTo("0");
        assertThat(result.getWeightedExternalAmount()).isEqualByComparingTo("10000000");
    }

    @Test
    @DisplayName("외부 거래일이 가중치 구간 밖이면 예외")
    void 외부_가중치없는날짜() {
        List<SellLotDTO> lots = List.of(lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"));
        List<ExternalBuyDTO> external = List.of(externalBuy(LocalDate.of(2027, 1, 5), "10000000"));

        assertThatThrownBy(() -> calculator.calculate(lots, reliefRates(), external))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RELIEF_RATE");
    }

    @Test
    @DisplayName("골든 시나리오 - 조정비율이 §3 검증 예시(74.4%)와 일치한다")
    void 조정비율_골든시나리오() {
        List<SellLotDTO> lots = List.of(
                lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"),
                lot(LocalDate.of(2026, 6, 15), "10000000", "100", "1000", "40"),
                lot(LocalDate.of(2026, 9, 20), "10000000", "100", "1000", "40"));
        List<ExternalBuyDTO> external = List.of(
                externalBuy(LocalDate.of(2026, 6, 15), "20000000"),
                externalBuy(LocalDate.of(2026, 9, 20), "-10000000"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates(), external);

        assertThat(result.getAdjustRatio()).isEqualByComparingTo("0.7442");
    }

    @Test
    @DisplayName("외부 순매수가 없으면 조정비율은 1")
    void 조정비율_외부없음() {
        List<SellLotDTO> lots = List.of(lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates(), List.of());

        assertThat(result.getAdjustRatio()).isEqualByComparingTo("1.0000");
    }

    @Test
    @DisplayName("외부 순매도만 있어도 조정비율은 1 - 순매수가 0으로 잘리기 때문")
    void 조정비율_외부순매도() {
        List<SellLotDTO> lots = List.of(lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"));
        List<ExternalBuyDTO> external = List.of(externalBuy(LocalDate.of(2026, 3, 10), "-10000000"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates(), external);

        assertThat(result.getAdjustRatio()).isEqualByComparingTo("1.0000");
    }

    @Test
    @DisplayName("외부 순매수가 가중매도금액과 같으면 조정비율은 0")
    void 조정비율_경계_동일() {
        List<SellLotDTO> lots = List.of(lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"));
        List<ExternalBuyDTO> external = List.of(externalBuy(LocalDate.of(2026, 3, 10), "30000000"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates(), external);

        assertThat(result.getWeightedSell()).isEqualByComparingTo("30000000");
        assertThat(result.getWeightedExternalAmount()).isEqualByComparingTo("30000000");
        assertThat(result.getAdjustRatio()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("외부 순매수가 가중매도금액보다 크면 음수가 아니라 0이 된다")
    void 조정비율_초과분은_0으로_잘린다() {
        List<SellLotDTO> lots = List.of(lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"));
        List<ExternalBuyDTO> external = List.of(externalBuy(LocalDate.of(2026, 3, 10), "50000000"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates(), external);

        assertThat(result.getAdjustRatio()).isEqualByComparingTo("0.0000");
        assertThat(result.getAdjustRatio().signum()).isNotNegative();
    }

    @Test
    @DisplayName("매도가 없으면 0으로 나누지 않고 조정비율 0을 반환한다")
    void 조정비율_매도없음() {
        List<ExternalBuyDTO> external = List.of(externalBuy(LocalDate.of(2026, 3, 10), "10000000"));

        TaxCalculationResultDTO result = calculator.calculate(List.of(), reliefRates(), external);

        assertThat(result.getWeightedSell()).isEqualByComparingTo("0");
        assertThat(result.getAdjustRatio()).isEqualByComparingTo("0.0000");
    }

    @ParameterizedTest(name = "가중매도 4,300만 / 외부 {0} → {1}")
    @CsvSource({
            "        0, 1.0000",
            " 10750000, 0.7500",
            " 21500000, 0.5000",
            " 43000000, 0.0000",
            "  1000000, 0.9767"})
    @DisplayName("조정비율 = 1 - (외부 순매수 / 가중매도금액)")
    void 조정비율_산식(String externalAmount, String expected) {
        List<SellLotDTO> lots = List.of(
                lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"),
                lot(LocalDate.of(2026, 6, 15), "10000000", "100", "1000", "40"),
                lot(LocalDate.of(2026, 9, 20), "10000000", "100", "1000", "40"));
        List<ExternalBuyDTO> external = List.of(externalBuy(LocalDate.of(2026, 3, 10), externalAmount));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates(), external);

        assertThat(result.getWeightedSell()).isEqualByComparingTo("43000000");
        assertThat(result.getAdjustRatio()).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("조정비율은 tax_calculation.ratio(DECIMAL(7,4))에 맞춰 소수점 4자리로 확정된다")
    void 조정비율_스케일() {
        List<SellLotDTO> lots = List.of(lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"));
        List<ExternalBuyDTO> external = List.of(externalBuy(LocalDate.of(2026, 3, 10), "10000000"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates(), external);

        assertThat(result.getAdjustRatio().scale()).isEqualTo(4);
        assertThat(result.getAdjustRatio()).isEqualByComparingTo("0.6667");
    }
}
