package com.app.maria.domain.tax.service;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.exception.AccountNotFoundException;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.account.type.BenefitType;
import com.app.maria.domain.tax.dto.ExternalBuyDTO;
import com.app.maria.domain.tax.dto.SellLotDTO;
import com.app.maria.domain.tax.dto.TaxCalculationDTO;
import com.app.maria.domain.tax.dto.TaxCalculationResultDTO;
import com.app.maria.domain.tax.dto.TaxRuleDTO;
import com.app.maria.domain.tax.dto.response.TaxCalculationPreviewResponseDTO;
import com.app.maria.domain.tax.dto.response.TaxCalculationSaveResponseDTO;
import com.app.maria.domain.tax.dto.response.TaxSnapshotResponseDTO;
import com.app.maria.domain.tax.exception.TaxCalculationAlreadyExistsException;
import com.app.maria.domain.tax.mapper.TaxMapper;
import com.app.maria.domain.tax.mapper.TaxSnapshotMapper;
import com.app.maria.domain.tax.type.TaxBasisType;
import com.app.maria.global.clock.service.BusinessClockService;
import com.app.maria.global.config.properties.RiaTaxProperties;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
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
    private final TaxSnapshotMapper taxSnapshotMapper;

    @Override
    @Transactional(readOnly = true)
    public TaxCalculationPreviewResponseDTO taxCalculate(Long accountId) {
        return TaxCalculationPreviewResponseDTO.of(accountId, calculateFor(findAccount(accountId)));
    }

    @Override
    @Transactional
    public TaxCalculationSaveResponseDTO calculateAndSave(Long accountId) {
        AccountDTO account = findAccount(accountId);
        TaxBasisType basisType = resolveBasisType(account);

        TaxCalculationDTO taxCalculationDTO =
                TaxCalculationDTO.of(
                        accountId, basisType, clockService.now(), calculateFor(account));

        try {
            taxMapper.insertCalculation(taxCalculationDTO);
        } catch (DuplicateKeyException e) {
            throw new TaxCalculationAlreadyExistsException(
                    "이미 " + basisType + " 계산이 저장되었습니다. accountId=" + accountId);
        }

        return TaxCalculationSaveResponseDTO.of(taxCalculationDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxSnapshotResponseDTO> findSnapshots(List<Long> accountIds) {
        return taxSnapshotMapper.selectByAccountIds(accountIds).stream()
                .map(TaxSnapshotResponseDTO::of)
                .toList();
    }

    private TaxBasisType resolveBasisType(AccountDTO account) {
        Long accountId = account.getAccountId();

        if (!taxMapper.existsByAccountAndBasis(accountId, TaxBasisType.FINAL_REPORT)) {
            return TaxBasisType.FINAL_REPORT;
        }
        if (account.getBenefit() != BenefitType.IMPOSSIBLE) {
            throw new TaxCalculationAlreadyExistsException(
                    "이미 확정신고된 계좌입니다. accountId=" + accountId);
        }
        if (taxMapper.existsByAccountAndBasis(accountId, TaxBasisType.EARLY_WITHDRAWAL_CLAWBACK)) {
            throw new TaxCalculationAlreadyExistsException(
                    "이미 조기인출 정정이 처리된 계좌입니다. accountId=" + accountId);
        }
        return TaxBasisType.EARLY_WITHDRAWAL_CLAWBACK;
    }

    private AccountDTO findAccount(Long accountId) {
        return accountMapper
                .selectByAccountId(accountId)
                .orElseThrow(() -> new AccountNotFoundException("계좌가 없습니다."));
    }

    private TaxCalculationResultDTO calculateFor(AccountDTO account) {
        int taxYear = riaTaxProperties.getYear();
        List<Long> accountIds = List.of(account.getAccountId());
        LocalDateTime now = clockService.now();

        List<SellLotDTO> sellLots =
                taxMapper.findFinalizedLotsByAccountIdsAndYear(accountIds, taxYear, now);
        List<TaxRuleDTO> taxRules = taxMapper.findTaxRules();
        List<ExternalBuyDTO> externalTrades =
                taxMapper.findExternalBuysByAccountIdsAndYear(accountIds, taxYear, now);

        return taxCalculator.calculate(
                sellLots,
                taxRules,
                externalTrades,
                BenefitType.isReliefExcluded(account.getBenefit()));
    }
}
