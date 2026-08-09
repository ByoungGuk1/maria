package com.app.maria.domain.withdrawal.service;

import com.app.maria.domain.account.dto.AccountBenefitLogDTO;
import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.exception.AccountException;
import com.app.maria.domain.account.exception.AccountNotFoundException;
import com.app.maria.domain.account.mapper.AccountBenefitLogMapper;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.account.type.BenefitType;
import com.app.maria.domain.account.type.Status;
import com.app.maria.domain.withdrawal.dto.LeftAmountDTO;
import com.app.maria.domain.withdrawal.dto.WithdrawalAllocationDTO;
import com.app.maria.domain.withdrawal.dto.request.WithdrawalRequestDTO;
import com.app.maria.domain.withdrawal.exception.EarlyWithdrawalConsentRequiredException;
import com.app.maria.domain.withdrawal.exception.InsufficientWithdrawalAmountException;
import com.app.maria.domain.withdrawal.exception.WithdrawalNotAllowedException;
import com.app.maria.domain.withdrawal.mapper.WithdrawalMapper;
import com.app.maria.domain.withdrawal.type.WithdrawalType;
import com.app.maria.global.clock.service.BusinessClockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceImplTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 10, 0);

    @Mock BusinessClockService businessClockService;
    @Mock AccountMapper accountMapper;
    @Mock WithdrawalMapper withdrawalMapper;
    @Mock AccountBenefitLogMapper accountBenefitLogMapper;
    @InjectMocks WithdrawalServiceImpl withdrawalService;

    @Test
    void accountNotFound_stopsBeforeLoadingWithdrawalSources() {
        when(accountMapper.selectByAccountIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> withdrawalService.withdraw(request("100")))
                .isInstanceOf(AccountNotFoundException.class);
        verifyNoInteractions(withdrawalMapper, businessClockService);
    }

    @Test
    void nonOpenedAccount_stopsBeforeFifoCalculation() {
        when(accountMapper.selectByAccountIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(Status.APPLIED)));

        assertThatThrownBy(() -> withdrawalService.withdraw(request("100")))
                .isInstanceOf(WithdrawalNotAllowedException.class);
        verifyNoInteractions(withdrawalMapper, businessClockService);
    }

    @Test
    void exactlyOneYearAfterFinalAt_isMatured() {
        prepareOpenedAccount(List.of(leftAmount(11L, "300", NOW.minusYears(1))));

        List<WithdrawalAllocationDTO> result = withdrawalService.withdraw(request("200"));

        assertThat(result).singleElement().satisfies(allocation -> {
            assertThat(allocation.getLeftAmountId()).isEqualTo(11L);
            assertThat(allocation.getAllocatedAmount()).isEqualByComparingTo("200");
            assertThat(allocation.getWithdrawalAt()).isEqualTo(NOW);
            assertThat(allocation.getType())
                    .isEqualTo(WithdrawalType.MATURED_PRINCIPAL_INCLUDED);
        });
    }

    @Test
    void oneSecondBeforeOneYear_requiresEarlyWithdrawalConsent() {
        prepareOpenedAccount(List.of(leftAmount(
                12L, "300", NOW.minusYears(1).plusSeconds(1))));

        assertThatThrownBy(() -> withdrawalService.withdraw(request("200")))
                .isInstanceOf(EarlyWithdrawalConsentRequiredException.class);

        verify(accountMapper, never()).updateBenefitToImpossible(ACCOUNT_ID);
        verifyNoInteractions(accountBenefitLogMapper);
    }

    @Test
    void agreedEarlyWithdrawal_allocatesImmaturePrincipalFifoAndSavesBenefitLog() {
        prepareOpenedAccount("1200", BenefitType.POSSIBLE, List.of(
                leftAmount(61L, "300", NOW.minusYears(2)),
                leftAmount(62L, "400", NOW.minusMonths(11)),
                leftAmount(63L, "500", NOW.minusMonths(6))
        ));
        when(accountMapper.updateBenefitToImpossible(ACCOUNT_ID)).thenReturn(1);
        when(accountBenefitLogMapper.insertLog(org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);

        List<WithdrawalAllocationDTO> result =
                withdrawalService.withdraw(request("800", true));

        assertThat(result).extracting(WithdrawalAllocationDTO::getLeftAmountId)
                .containsExactly(61L, 62L, 63L);
        assertThat(result).extracting(WithdrawalAllocationDTO::getAllocatedAmount)
                .containsExactly(
                        new BigDecimal("300"),
                        new BigDecimal("400"),
                        new BigDecimal("100")
                );
        assertThat(result).extracting(WithdrawalAllocationDTO::getType)
                .containsExactly(
                        WithdrawalType.MATURED_PRINCIPAL_INCLUDED,
                        WithdrawalType.IMMATURE_PRINCIPAL_INCLUDED,
                        WithdrawalType.IMMATURE_PRINCIPAL_INCLUDED
                );

        ArgumentCaptor<AccountBenefitLogDTO> logCaptor =
                ArgumentCaptor.forClass(AccountBenefitLogDTO.class);
        verify(accountBenefitLogMapper).insertLog(logCaptor.capture());

        assertThat(logCaptor.getValue()).satisfies(log -> {
            assertThat(log.getBenefitId()).isNull();
            assertThat(log.getAccountId()).isEqualTo(ACCOUNT_ID);
            assertThat(log.getPrevStatus()).isEqualTo(BenefitType.POSSIBLE);
            assertThat(log.getNewStatus()).isEqualTo(BenefitType.IMPOSSIBLE);
            assertThat(log.getChangedAt()).isEqualTo(NOW);
            assertThat(log.getReason()).isEqualTo("조기인출로 인한 세제혜택 취소");
        });
    }

    @Test
    void benefitAlreadyImpossible_doesNotSaveDuplicateBenefitLog() {
        prepareOpenedAccount("300", BenefitType.IMPOSSIBLE, List.of(
                leftAmount(71L, "300", NOW.minusMonths(3))
        ));
        when(accountMapper.updateBenefitToImpossible(ACCOUNT_ID)).thenReturn(0);

        List<WithdrawalAllocationDTO> result =
                withdrawalService.withdraw(request("200", true));

        assertThat(result).singleElement().satisfies(allocation -> {
            assertThat(allocation.getLeftAmountId()).isEqualTo(71L);
            assertThat(allocation.getAllocatedAmount()).isEqualByComparingTo("200");
            assertThat(allocation.getType())
                    .isEqualTo(WithdrawalType.IMMATURE_PRINCIPAL_INCLUDED);
        });
        verifyNoInteractions(accountBenefitLogMapper);
    }

    @Test
    void benefitLogInsertFailure_abortsEarlyWithdrawal() {
        prepareOpenedAccount("300", BenefitType.REDUCED, List.of(
                leftAmount(81L, "300", NOW.minusMonths(3))
        ));
        when(accountMapper.updateBenefitToImpossible(ACCOUNT_ID)).thenReturn(1);
        when(accountBenefitLogMapper.insertLog(org.mockito.ArgumentMatchers.any()))
                .thenReturn(0);

        assertThatThrownBy(() -> withdrawalService.withdraw(request("200", true)))
                .isInstanceOf(AccountException.class)
                .hasMessage("ACCOUNT_BENEFIT_LOG저장에 실패했습니다.");
    }

    @Test
    void multipleSources_areAllocatedInFifoOrderWithPartialLastSource() {
        prepareOpenedAccount(List.of(
                leftAmount(21L, "300", NOW.minusYears(2)),
                leftAmount(22L, "500", NOW.minusYears(1).minusDays(1))
        ));

        List<WithdrawalAllocationDTO> result = withdrawalService.withdraw(request("700"));

        assertThat(result).extracting(WithdrawalAllocationDTO::getLeftAmountId)
                .containsExactly(21L, 22L);
        assertThat(result).extracting(WithdrawalAllocationDTO::getAllocatedAmount)
                .containsExactly(new BigDecimal("300"), new BigDecimal("400"));

        InOrder order = inOrder(accountMapper, withdrawalMapper, businessClockService);
        order.verify(accountMapper).selectByAccountIdForUpdate(ACCOUNT_ID);
        order.verify(withdrawalMapper).selectAvailableLeftAmountsByAccountId(ACCOUNT_ID);
        order.verify(businessClockService).now();
    }

    @Test
    void requestWithinEarnings_isAllocatedWithoutAPrincipalSource() {
        prepareOpenedAccount("1000", List.of(
                leftAmount(31L, "700", NOW.minusYears(2))
        ));

        List<WithdrawalAllocationDTO> result = withdrawalService.withdraw(request("200"));

        assertThat(result).singleElement().satisfies(allocation -> {
            assertThat(allocation.getLeftAmountId()).isNull();
            assertThat(allocation.getAllocatedAmount()).isEqualByComparingTo("200");
            assertThat(allocation.getType()).isEqualTo(WithdrawalType.EARNINGS_ONLY);
            assertThat(allocation.getWithdrawalAt()).isEqualTo(NOW);
        });
    }

    @Test
    void requestExceedingEarnings_allocatesEarningsThenMaturedPrincipal() {
        prepareOpenedAccount("1000", List.of(
                leftAmount(41L, "300", NOW.minusYears(2)),
                leftAmount(42L, "500", NOW.minusYears(1).minusDays(1))
        ));

        List<WithdrawalAllocationDTO> result = withdrawalService.withdraw(request("450"));

        assertThat(result).extracting(WithdrawalAllocationDTO::getLeftAmountId)
                .containsExactly(null, 41L);
        assertThat(result).extracting(WithdrawalAllocationDTO::getAllocatedAmount)
                .containsExactly(new BigDecimal("200"), new BigDecimal("250"));
        assertThat(result).extracting(WithdrawalAllocationDTO::getType)
                .containsExactly(
                        WithdrawalType.EARNINGS_ONLY,
                        WithdrawalType.MATURED_PRINCIPAL_INCLUDED
                );
    }

    @Test
    void negativeCalculatedEarnings_isTreatedAsZero() {
        prepareOpenedAccount("500", List.of(
                leftAmount(51L, "600", NOW.minusYears(2))
        ));

        List<WithdrawalAllocationDTO> result = withdrawalService.withdraw(request("100"));

        assertThat(result).singleElement().satisfies(allocation -> {
            assertThat(allocation.getLeftAmountId()).isEqualTo(51L);
            assertThat(allocation.getAllocatedAmount()).isEqualByComparingTo("100");
            assertThat(allocation.getType())
                    .isEqualTo(WithdrawalType.MATURED_PRINCIPAL_INCLUDED);
        });
    }

    @Test
    void requestExceedingAccountAmount_isRejectedBeforeLoadingSources() {
        when(accountMapper.selectByAccountIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(Status.OPENED, "500")));

        assertThatThrownBy(() -> withdrawalService.withdraw(request("501")))
                .isInstanceOf(InsufficientWithdrawalAmountException.class);
        verifyNoInteractions(withdrawalMapper, businessClockService);
    }

    @Test
    void zeroAmountRequest_isRejectedBeforeLoadingSources() {
        when(accountMapper.selectByAccountIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(Status.OPENED, "500")));

        assertThatThrownBy(() -> withdrawalService.withdraw(request("0")))
                .isInstanceOf(WithdrawalNotAllowedException.class);
        verifyNoInteractions(withdrawalMapper, businessClockService);
    }

    private void prepareOpenedAccount(List<LeftAmountDTO> leftAmounts) {
        BigDecimal principal = leftAmounts.stream()
                .map(LeftAmountDTO::getCurAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        prepareOpenedAccount(principal.toPlainString(), leftAmounts);
    }

    private void prepareOpenedAccount(
            String accountAmount,
            List<LeftAmountDTO> leftAmounts
    ) {
        prepareOpenedAccount(accountAmount, BenefitType.POSSIBLE, leftAmounts);
    }

    private void prepareOpenedAccount(
            String accountAmount,
            BenefitType benefit,
            List<LeftAmountDTO> leftAmounts
    ) {
        when(accountMapper.selectByAccountIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(Status.OPENED, accountAmount, benefit)));
        when(withdrawalMapper.selectAvailableLeftAmountsByAccountId(ACCOUNT_ID))
                .thenReturn(leftAmounts);
        when(businessClockService.now()).thenReturn(NOW);
    }

    private static AccountDTO account(Status status) {
        return account(status, "0");
    }

    private static AccountDTO account(Status status, String amount) {
        return account(status, amount, BenefitType.POSSIBLE);
    }

    private static AccountDTO account(
            Status status,
            String amount,
            BenefitType benefit
    ) {
        return AccountDTO.builder()
                .accountId(ACCOUNT_ID)
                .status(status)
                .amount(new BigDecimal(amount))
                .benefit(benefit)
                .build();
    }

    private static WithdrawalRequestDTO request(String amount) {
        return request(amount, false);
    }

    private static WithdrawalRequestDTO request(String amount, boolean earlyWithdrawalAgreed) {
        return WithdrawalRequestDTO.builder()
                .accountId(ACCOUNT_ID)
                .requestedAmount(new BigDecimal(amount))
                .earlyWithdrawalAgreed(earlyWithdrawalAgreed)
                .build();
    }

    private static LeftAmountDTO leftAmount(Long id, String amount, LocalDateTime finalAt) {
        return LeftAmountDTO.builder()
                .leftAmountId(id)
                .exchangeId(id + 100L)
                .curAmount(new BigDecimal(amount))
                .finalAt(finalAt)
                .build();
    }
}
