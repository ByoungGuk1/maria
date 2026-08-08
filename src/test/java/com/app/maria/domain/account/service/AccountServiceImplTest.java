package com.app.maria.domain.account.service;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.dto.request.AccountRequestDTO;
import com.app.maria.domain.account.dto.response.AccountResponseDTO;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.account.provider.MydataProvider;
import com.app.maria.domain.account.type.Status;
import com.app.maria.global.clock.service.BusinessClockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceImplTest {
  private static final Long CUSTOMER_ID = 1L;
  private static final Long ACCOUNT_ID = 10L;
  private static final BigDecimal LIMIT = BigDecimal.valueOf(30_000_000L);
  private static final BigDecimal CHANGED_LIMIT = BigDecimal.valueOf(40_000_000L);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 10, 30);

  @Mock private AccountMapper accountMapper;
  @Mock private MydataProvider mydataProvider;
  @Mock private AccountLogService accountLogService;
  @Mock private BusinessClockService businessClockService;
  @Mock private AccountTransactionalService accountTransactionalService;
  @Mock private AccountMydataSyncService accountMydataSyncService;

  @InjectMocks private AccountServiceImpl accountService;

  @BeforeEach
  void setUp() {
    when(accountMapper.existsCustomerById(CUSTOMER_ID)).thenReturn(true);
    when(accountMapper.selectCiHashByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of("ci-hash"));
    when(mydataProvider.getExternalUsedLimit("ci-hash")).thenReturn(BigDecimal.ZERO);
    when(businessClockService.now()).thenReturn(NOW);
  }

  @Test
  void updateLimitDoesNotSyncMydataForAppliedAccount() {
    when(accountTransactionalService.updateLimit(CUSTOMER_ID, CHANGED_LIMIT, NOW))
        .thenReturn(account(Status.APPLIED, CHANGED_LIMIT));

    AccountResponseDTO result = accountService.updateAccountLimit(request(CHANGED_LIMIT));

    assertThat(result.getStatus()).isEqualTo(Status.APPLIED);
    verify(accountMydataSyncService, never()).updateLimit(any());
  }

  @Test
  void updateLimitSyncsOnlyLimitForOpenedAccount() {
    AccountDTO updated = account(Status.OPENED, CHANGED_LIMIT);
    when(accountTransactionalService.updateLimit(CUSTOMER_ID, CHANGED_LIMIT, NOW)).thenReturn(updated);

    accountService.updateAccountLimit(request(CHANGED_LIMIT));

    verify(accountMydataSyncService).updateLimit(updated);
    verify(accountMydataSyncService, never()).create(any());
  }

  @Test
  void applyUsesSingleBusinessClockSnapshotAndCreatesMydataForOpenedAccount() {
    AccountDTO opened = account(Status.OPENED, LIMIT);
    when(accountTransactionalService.apply(any(AccountDTO.class), eq(NOW), any())).thenReturn(opened);

    accountService.applyAccount(request(LIMIT));

    verify(businessClockService).now();
    verify(accountMydataSyncService).create(opened);
  }

  @Test
  void approvePassesValidatedLimitAndCreatesMydataAccount() {
    AccountDTO applied = account(Status.APPLIED, LIMIT);
    AccountDTO opened = account(Status.OPENED, LIMIT);
    when(accountMapper.selectByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(applied));
    when(accountTransactionalService.approve(ACCOUNT_ID, LIMIT, NOW)).thenReturn(opened);

    accountService.approveAccount(ACCOUNT_ID);

    verify(accountTransactionalService).approve(ACCOUNT_ID, LIMIT, NOW);
    verify(accountMydataSyncService).create(opened);
  }

  @Test
  void approveAllowsPendingAccountOutsideApplicationPeriod() {
    LocalDateTime afterApplicationPeriod = LocalDateTime.of(2027, 1, 1, 10, 0);
    AccountDTO applied = account(Status.APPLIED, LIMIT);
    AccountDTO opened = account(Status.OPENED, LIMIT);
    when(businessClockService.now()).thenReturn(afterApplicationPeriod);
    when(accountMapper.selectByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(applied));
    when(accountTransactionalService.approve(ACCOUNT_ID, LIMIT, afterApplicationPeriod)).thenReturn(opened);

    accountService.approveAccount(ACCOUNT_ID);

    verify(accountTransactionalService).approve(ACCOUNT_ID, LIMIT, afterApplicationPeriod);
  }

  private AccountRequestDTO request(BigDecimal limit) {
    return AccountRequestDTO.builder().customerId(CUSTOMER_ID).limitAmount(limit).build();
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
