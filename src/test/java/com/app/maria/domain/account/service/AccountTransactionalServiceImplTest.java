package com.app.maria.domain.account.service;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.exception.InvalidAccountRequestException;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.account.type.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountTransactionalServiceImplTest {
  private static final Long CUSTOMER_ID = 1L;
  private static final Long ACCOUNT_ID = 10L;
  private static final BigDecimal LIMIT = BigDecimal.valueOf(30_000_000L);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 10, 30);

  @Mock private AccountMapper accountMapper;
  @Mock private AccountLogService accountLogService;
  @InjectMocks private AccountTransactionalServiceImpl service;

  @Test
  void updateLimitRejectsAmountBelowOwnUsedAndReservedAmount() {
    when(accountMapper.selectByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(account(Status.OPENED, LIMIT)));
    when(accountMapper.selectOwnUsedAndReservedAmount(ACCOUNT_ID)).thenReturn(BigDecimal.valueOf(20_000_000L));

    assertThatThrownBy(() -> service.updateLimit(CUSTOMER_ID, BigDecimal.valueOf(10_000_000L), NOW))
        .isInstanceOf(InvalidAccountRequestException.class)
        .hasMessage("이미 사용한 매도한도보다 낮게 설정할 수 없습니다.");

    verify(accountMapper, never()).updateLimit(
        anyLong(),
        any(Status.class),
        any(BigDecimal.class),
        any(BigDecimal.class)
    );
  }

  @Test
  void approveUsesValidatedLimitAsOptimisticLockCondition() {
    AccountDTO applied = account(Status.APPLIED, BigDecimal.valueOf(40_000_000L));
    AccountDTO opened = account(Status.OPENED, LIMIT);
    when(accountMapper.selectByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(applied), Optional.of(opened));
    when(accountMapper.approve(any(AccountDTO.class))).thenReturn(1);

    service.approve(ACCOUNT_ID, LIMIT, NOW);

    ArgumentCaptor<AccountDTO> captor = ArgumentCaptor.forClass(AccountDTO.class);
    verify(accountMapper).approve(captor.capture());
    assertThat(captor.getValue().getLimitAmount()).isEqualByComparingTo(LIMIT);
  }

  @Test
  void approveReportsConflictWhenValidatedLimitChangedBeforeUpdate() {
    when(accountMapper.selectByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(account(Status.APPLIED, LIMIT)));
    when(accountMapper.approve(any(AccountDTO.class))).thenReturn(0);

    assertThatThrownBy(() -> service.approve(ACCOUNT_ID, LIMIT, NOW))
        .isInstanceOf(InvalidAccountRequestException.class)
        .hasMessage("심사 도중 계좌 한도가 변경되었습니다. 다시 심사하세요.");
  }

  private AccountDTO account(Status status, BigDecimal limit) {
    return AccountDTO.builder()
        .accountId(ACCOUNT_ID)
        .customerId(CUSTOMER_ID)
        .status(status)
        .limitAmount(limit)
        .build();
  }
}
