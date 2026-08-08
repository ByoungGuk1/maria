package com.app.maria.domain.account.service;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.exception.AccountNotFoundException;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.account.provider.MydataProvider;
import com.app.maria.domain.account.type.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountMydataSyncService {
  private final AccountMapper accountMapper;
  private final MydataProvider mydataProvider;

  public void create(AccountDTO account) {
    synchronize(account, true);
  }

  public void updateLimit(AccountDTO account) {
    synchronize(account, false);
  }

  @Scheduled(fixedDelayString = "${custom.mydata.ria-sync-delay-ms:60000}")
  public void retryOpenedAccounts() {
    accountMapper.selectAllAccount().stream()
        .filter(account -> account.getStatus() == Status.OPENED)
        .forEach(this::retry);
  }

  private void synchronize(AccountDTO account, boolean create) {
    try {
      String ciHash = accountMapper.selectCiHashByCustomerId(account.getCustomerId())
          .orElseThrow(() -> new AccountNotFoundException("개설할 계좌의 사용자를 찾을 수 없습니다."));
      boolean successful = (create
          ? mydataProvider.createRiaAccount(ciHash, account)
          : mydataProvider.updateRiaLimit(ciHash, account))
          .is2xxSuccessful();
      if (!successful) {
        log.error("MyData RIA 계좌 동기화 실패: accountId={}, operation={}", account.getAccountId(), create ? "CREATE" : "UPDATE_LIMIT");
      }
    } catch (RuntimeException exception) {
      log.error("MyData RIA 계좌 동기화 예외: accountId={}, operation={}",
          account.getAccountId(), create ? "CREATE" : "UPDATE_LIMIT", exception);
    }
  }

  private Optional<Boolean> hasOwnRiaAccount(AccountDTO account) {
    String ciHash = accountMapper.selectCiHashByCustomerId(account.getCustomerId())
        .orElseThrow(() -> new AccountNotFoundException("개설할 계좌의 사용자를 찾을 수 없습니다."));
    try {
      return Optional.of(mydataProvider.hasOwnRiaAccount(ciHash));
    } catch (RuntimeException exception) {
      log.error("MyData RIA 계좌 재동기화 대상 조회 실패: accountId={}", account.getAccountId(), exception);
      return Optional.empty();
    }
  }

  private void retry(AccountDTO account) {
    try {
      Optional<Boolean> ownRiaAccount = hasOwnRiaAccount(account);
      if (ownRiaAccount.isEmpty()) {
        return;
      }
      synchronize(account, !ownRiaAccount.get());
    } catch (RuntimeException exception) {
      log.error("MyData RIA 계좌 재동기화 실패: accountId={}", account.getAccountId(), exception);
    }
  }
}
