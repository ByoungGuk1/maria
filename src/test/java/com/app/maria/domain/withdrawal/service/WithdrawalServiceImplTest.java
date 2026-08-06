package com.app.maria.domain.withdrawal.service;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.exception.AccountNotFoundException;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.account.type.Status;
import com.app.maria.domain.withdrawal.dto.LeftAmountDTO;
import com.app.maria.domain.withdrawal.dto.WithdrawalAllocationDTO;
import com.app.maria.domain.withdrawal.dto.request.WithdrawalRequestDTO;
import com.app.maria.domain.withdrawal.exception.WithdrawalNotAllowedException;
import com.app.maria.domain.withdrawal.mapper.WithdrawalMapper;
import com.app.maria.domain.withdrawal.type.WithdrawalType;
import com.app.maria.global.clock.service.BusinessClockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceImplTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 10, 0);

    @Mock BusinessClockService businessClockService;
    @Mock AccountMapper accountMapper;
    @Mock WithdrawalMapper withdrawalMapper;
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
    void oneSecondBeforeOneYear_isNotMatured() {
        prepareOpenedAccount(List.of(leftAmount(
                12L, "300", NOW.minusYears(1).plusSeconds(1))));

        assertThat(withdrawalService.withdraw(request("200"))).isEmpty();
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

    private void prepareOpenedAccount(List<LeftAmountDTO> leftAmounts) {
        when(accountMapper.selectByAccountIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(Status.OPENED)));
        when(withdrawalMapper.selectAvailableLeftAmountsByAccountId(ACCOUNT_ID))
                .thenReturn(leftAmounts);
        when(businessClockService.now()).thenReturn(NOW);
    }

    private static AccountDTO account(Status status) {
        return AccountDTO.builder().accountId(ACCOUNT_ID).status(status).build();
    }

    private static WithdrawalRequestDTO request(String amount) {
        return WithdrawalRequestDTO.builder()
                .accountId(ACCOUNT_ID)
                .requestedAmount(new BigDecimal(amount))
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
