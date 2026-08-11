package com.app.maria.domain.tax.service;

import static com.app.maria.domain.tax.fixture.TaxFixtures.externalBuy;
import static com.app.maria.domain.tax.fixture.TaxFixtures.lot;
import static com.app.maria.domain.tax.fixture.TaxFixtures.reliefRates;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.exception.AccountNotFoundException;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.tax.dto.ExternalBuyDTO;
import com.app.maria.domain.tax.dto.SellLotDTO;
import com.app.maria.domain.tax.dto.TaxRuleDTO;
import com.app.maria.domain.tax.dto.response.TaxCalculationResponseDTO;
import com.app.maria.domain.tax.mapper.TaxMapper;
import com.app.maria.global.clock.service.BusinessClockService;
import com.app.maria.global.config.properties.RiaTaxProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaxCalculationServiceImplTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final int TAX_YEAR = 2026;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 9, 0);

    @Mock TaxMapper taxMapper;

    @Mock AccountMapper accountMapper;

    @Mock BusinessClockService clockService;

    @Mock RiaTaxProperties riaTaxProperties;

    @Spy TaxCalculator taxCalculator = new TaxCalculator();

    @InjectMocks TaxCalculationServiceImpl taxCalculationService;

    @Test
    @DisplayName("조회한 lot과 규칙을 계산기에 넘겨 결과를 응답으로 감싼다")
    void 정상_계산() {
        stubAccount();
        stubTaxYearAndClock();
        when(taxMapper.findFinalizedLotsByAccountAndYear(ACCOUNT_ID, TAX_YEAR, NOW))
                .thenReturn(
                        List.of(lot(LocalDate.of(2026, 3, 10), "30000000", "100", "1000", "100")));
        when(taxMapper.findTaxRules()).thenReturn(reliefRates());
        when(taxMapper.findExternalBuysByAccountAndYear(ACCOUNT_ID, TAX_YEAR))
                .thenReturn(List.of(externalBuy(LocalDate.of(2026, 6, 15), "10000000")));

        TaxCalculationResponseDTO response = taxCalculationService.taxCalculate(ACCOUNT_ID);

        assertThat(response.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(response.getTaxCalculationResultDTO().getWeightedSell())
                .isEqualByComparingTo("30000000");
        assertThat(response.getTaxCalculationResultDTO().getWeightedGain())
                .isEqualByComparingTo("20000000");
        assertThat(response.getTaxCalculationResultDTO().getOriginalGainAmount())
                .isEqualByComparingTo("20000000");
        assertThat(response.getTaxCalculationResultDTO().getWeightedExternalAmount())
                .isEqualByComparingTo("8000000");
        assertThat(response.getTaxCalculationResultDTO().getAdjustRatio())
                .isEqualByComparingTo("0.7333");
    }

    @Test
    @DisplayName("계좌가 없으면 예외를 던지고 이후 조회를 하지 않는다")
    void 계좌없음() {
        when(accountMapper.selectByAccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taxCalculationService.taxCalculate(ACCOUNT_ID))
                .isInstanceOf(AccountNotFoundException.class);

        verifyNoInteractions(taxMapper, taxCalculator);
        verify(clockService, never()).now();
    }

    @Test
    @DisplayName("과세연도와 업무 기준시각을 lot 조회 조건으로 그대로 전달한다")
    void 조회조건_전달() {
        stubAccount();
        stubTaxYearAndClock();
        when(taxMapper.findFinalizedLotsByAccountAndYear(anyLong(), anyInt(), any()))
                .thenReturn(List.of());
        when(taxMapper.findTaxRules()).thenReturn(reliefRates());
        when(taxMapper.findExternalBuysByAccountAndYear(anyLong(), anyInt())).thenReturn(List.of());

        taxCalculationService.taxCalculate(ACCOUNT_ID);

        verify(taxMapper).findFinalizedLotsByAccountAndYear(ACCOUNT_ID, TAX_YEAR, NOW);
        verify(taxMapper).findExternalBuysByAccountAndYear(ACCOUNT_ID, TAX_YEAR);
    }

    @Test
    @DisplayName("조회 결과를 가공 없이 계산기에 전달한다")
    void 계산기_인자_전달() {
        stubAccount();
        stubTaxYearAndClock();
        List<SellLotDTO> lots =
                List.of(lot(LocalDate.of(2026, 6, 15), "10000000", "100", "1000", "40"));
        List<TaxRuleDTO> rules = reliefRates();
        List<ExternalBuyDTO> external = List.of(externalBuy(LocalDate.of(2026, 3, 10), "5000000"));
        when(taxMapper.findFinalizedLotsByAccountAndYear(ACCOUNT_ID, TAX_YEAR, NOW))
                .thenReturn(lots);
        when(taxMapper.findTaxRules()).thenReturn(rules);
        when(taxMapper.findExternalBuysByAccountAndYear(ACCOUNT_ID, TAX_YEAR)).thenReturn(external);

        taxCalculationService.taxCalculate(ACCOUNT_ID);

        ArgumentCaptor<List<SellLotDTO>> lotCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<TaxRuleDTO>> ruleCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<ExternalBuyDTO>> externalCaptor = ArgumentCaptor.forClass(List.class);
        verify(taxCalculator)
                .calculate(lotCaptor.capture(), ruleCaptor.capture(), externalCaptor.capture());

        assertThat(lotCaptor.getValue()).isSameAs(lots);
        assertThat(ruleCaptor.getValue()).isSameAs(rules);
        assertThat(externalCaptor.getValue()).isSameAs(external);
    }

    @Test
    @DisplayName("매도 이력이 없으면 전부 0으로 응답한다")
    void 매도없음() {
        stubAccount();
        stubTaxYearAndClock();
        when(taxMapper.findFinalizedLotsByAccountAndYear(ACCOUNT_ID, TAX_YEAR, NOW))
                .thenReturn(List.of());
        when(taxMapper.findTaxRules()).thenReturn(reliefRates());
        when(taxMapper.findExternalBuysByAccountAndYear(ACCOUNT_ID, TAX_YEAR))
                .thenReturn(List.of());

        TaxCalculationResponseDTO response = taxCalculationService.taxCalculate(ACCOUNT_ID);

        assertThat(response.getTaxCalculationResultDTO().getWeightedSell())
                .isEqualByComparingTo("0");
        assertThat(response.getTaxCalculationResultDTO().getWeightedGain())
                .isEqualByComparingTo("0");
        assertThat(response.getTaxCalculationResultDTO().getOriginalGainAmount())
                .isEqualByComparingTo("0");
        assertThat(response.getTaxCalculationResultDTO().getWeightedExternalAmount())
                .isEqualByComparingTo("0");
    }

    private void stubAccount() {
        when(accountMapper.selectByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(new AccountDTO()));
    }

    private void stubTaxYearAndClock() {
        when(riaTaxProperties.getTaxYear()).thenReturn(TAX_YEAR);
        when(clockService.now()).thenReturn(NOW);
    }
}
