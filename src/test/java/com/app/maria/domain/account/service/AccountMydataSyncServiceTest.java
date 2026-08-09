package com.app.maria.domain.account.service;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.account.provider.MydataProvider;
import com.app.maria.domain.account.type.Status;
import com.app.maria.global.exception.MydataApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountMydataSyncServiceTest {
  private static final Long CUSTOMER_ID = 1L;
  private static final String CI_HASH = "ci-hash";

  @Mock private AccountMapper accountMapper;
  @Mock private MydataProvider mydataProvider;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @Mock private ZSetOperations<String, String> zSetOperations;
  @InjectMocks private AccountMydataSyncService service;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
  }

  @Test
  void createSendsInitialCumulativeSellPayloadThroughCreateOperation() {
    AccountDTO account = openedAccount();
    when(accountMapper.selectCiHashByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(CI_HASH));
    when(mydataProvider.createRiaAccount(CI_HASH, account)).thenReturn(HttpStatus.OK);

    service.create(account);

    verify(mydataProvider).createRiaAccount(CI_HASH, account);
    verify(mydataProvider, never()).updateRiaLimit(CI_HASH, account);
  }

  @Test
  void updateLimitUsesUpdateOperation() {
    AccountDTO account = openedAccount();
    when(accountMapper.selectCiHashByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(CI_HASH));
    when(mydataProvider.updateRiaLimit(CI_HASH, account)).thenReturn(HttpStatus.OK);

    service.updateLimit(account);

    verify(mydataProvider).updateRiaLimit(CI_HASH, account);
    verify(mydataProvider, never()).createRiaAccount(CI_HASH, account);
  }

  @Test
  void retryCreatesMissingAccountAndUpdatesExistingAccount() {
    AccountDTO missing = openedAccount();
    missing.setAccountId(10L);
    AccountDTO existing = openedAccount();
    existing.setAccountId(11L);
    when(zSetOperations.rangeByScore(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong()))
        .thenReturn(Set.of("10", "11"));
    when(valueOperations.setIfAbsent(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
    when(accountMapper.selectByAccountId(10L)).thenReturn(Optional.of(missing));
    when(accountMapper.selectByAccountId(11L)).thenReturn(Optional.of(existing));
    when(accountMapper.selectCiHashByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(CI_HASH));
    when(mydataProvider.hasOwnRiaAccount(CI_HASH)).thenReturn(false, true);
    when(mydataProvider.createRiaAccount(CI_HASH, missing)).thenReturn(HttpStatus.OK);
    when(mydataProvider.updateRiaLimit(CI_HASH, existing)).thenReturn(HttpStatus.OK);

    service.retryOpenedAccounts();

    verify(mydataProvider).createRiaAccount(CI_HASH, missing);
    verify(mydataProvider).updateRiaLimit(CI_HASH, existing);
  }

  @Test
  void retryDoesNotCreateOrUpdateWhenMydataLookupFails() {
    AccountDTO account = openedAccount();
    account.setAccountId(10L);
    when(zSetOperations.rangeByScore(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong()))
        .thenReturn(Set.of("10"));
    when(valueOperations.setIfAbsent(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
    when(accountMapper.selectByAccountId(10L)).thenReturn(Optional.of(account));
    when(accountMapper.selectCiHashByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(CI_HASH));
    when(mydataProvider.hasOwnRiaAccount(CI_HASH))
        .thenThrow(new MydataApiException("조회 실패", null));

    service.retryOpenedAccounts();

    verify(mydataProvider, never()).createRiaAccount(CI_HASH, account);
    verify(mydataProvider, never()).updateRiaLimit(CI_HASH, account);
  }

  private AccountDTO openedAccount() {
    return AccountDTO.builder()
        .customerId(CUSTOMER_ID)
        .status(Status.OPENED)
        .limitAmount(BigDecimal.valueOf(30_000_000L))
        .build();
  }
}
