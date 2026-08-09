package com.app.maria.domain.tax.service;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.exception.AccountNotFoundException;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.tax.dto.SellLotDTO;
import com.app.maria.domain.tax.dto.TaxRuleDTO;
import com.app.maria.domain.tax.dto.response.TaxCalculationResponseDTO;
import com.app.maria.domain.tax.mapper.TaxMapper;
import com.app.maria.global.clock.service.BusinessClockService;
import com.app.maria.global.config.properties.RiaTaxProperties;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaxCalculationServiceImpl implements TaxCalculationService {
    private final TaxMapper taxMapper;
    private final AccountMapper accountMapper;
    private final BusinessClockService clockService;
    private final RiaTaxProperties riaTaxProperties;
    private final TaxCalculator taxCalculator;

    @Override
    @Transactional(readOnly = true)
    public TaxCalculationResponseDTO taxCalculate(Long accountId) {
        AccountDTO account = accountMapper.selectByAccountId(accountId)
                .orElseThrow(() -> new AccountNotFoundException("계좌가 없습니다."));

        List<SellLotDTO> lots = taxMapper.findFinalizedLotsByAccountAndYear(accountId,
                riaTaxProperties.getTaxYear(), clockService.now());

        List<TaxRuleDTO> rules = taxMapper.findTaxRules();

        return TaxCalculationResponseDTO.of(accountId,taxCalculator.calculate(lots,rules));
    }
}
