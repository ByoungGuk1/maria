package com.app.maria.domain.tax.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.maria.domain.tax.dto.SellLotDTO;
import com.app.maria.domain.tax.dto.TaxCalculationResultDTO;
import com.app.maria.domain.tax.dto.TaxRuleDTO;
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

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates());

        assertThat(result.getWeightedSell()).isEqualByComparingTo("43000000");
        assertThat(result.getWeightedGain()).isEqualByComparingTo("27800000");
        assertThat(result.getOriginalGainAmount()).isEqualByComparingTo("32000000");
    }

    @Test
    @DisplayName("구간 경계 - 5/31은 100%, 6/1은 80%")
    void 구간경계_5월말_6월초() {
        TaxCalculationResultDTO may = calculator.calculate(
                List.of(lot(LocalDate.of(2026, 5, 31), "10000000", "100", "1000", "40")), reliefRates());
        TaxCalculationResultDTO jun = calculator.calculate(
                List.of(lot(LocalDate.of(2026, 6, 1), "10000000", "100", "1000", "40")), reliefRates());

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
        assertThat(result.getOriginalGainAmount()).isEqualByComparingTo("6000000");
    }

    @Test
    @DisplayName("손익통산 - 손실 lot이 이익 lot과 상계된다")
    void 손익통산() {
        List<SellLotDTO> lots = List.of(
                lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100"),
                lot(LocalDate.of(2026, 3, 10), "5000000", "100", "1000", "100"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates());

        assertThat(result.getOriginalGainAmount()).isEqualByComparingTo("15000000");
        assertThat(result.getWeightedGain()).isEqualByComparingTo("15000000");
        assertThat(result.getWeightedSell()).isEqualByComparingTo("35000000");
    }

    @Test
    @DisplayName("전부 손실이면 양도소득이 음수로 남는다")
    void 전부손실() {
        List<SellLotDTO> lots = List.of(
                lot(LocalDate.of(2026, 3, 10), "5000000", "100", "1000", "100"));

        TaxCalculationResultDTO result = calculator.calculate(lots, reliefRates());

        assertThat(result.getOriginalGainAmount()).isEqualByComparingTo("-5000000");
        assertThat(result.getWeightedGain()).isEqualByComparingTo("-5000000");
    }

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

        TaxCalculationResultDTO result = calculator.calculate(lots, mixed);

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

        assertThatThrownBy(() -> calculator.calculate(lots, onlyConstants))
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
