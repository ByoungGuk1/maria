package com.app.maria.domain.account.service;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.exception.AccountNotFoundException;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.account.provider.MydataProvider;
import com.app.maria.domain.account.type.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountMydataSyncService {
  private static final String RETRY_SCHEDULE_KEY = "mydata:ria:retry:schedule";
  private static final String RETRY_TASK_KEY_PREFIX = "mydata:ria:retry:task:";
  private static final String RETRY_LOCK_KEY_PREFIX = "mydata:ria:retry:lock:";
  private static final int RETRY_BATCH_SIZE = 100;
  private static final long[] RETRY_DELAYS_MILLIS = {60_000L, 300_000L, 900_000L, 3_600_000L};
  private static final Duration RETRY_LOCK_TTL = Duration.ofMinutes(5);

  private final AccountMapper accountMapper;
  private final MydataProvider mydataProvider;
  private final StringRedisTemplate redisTemplate;

  public void create(AccountDTO account) {
    synchronize(account, SyncOperation.CREATE);
  }

  public void updateLimit(AccountDTO account) {
    synchronize(account, SyncOperation.UPDATE_LIMIT);
  }

  @Scheduled(fixedDelayString = "${custom.mydata.ria-sync-delay-ms:60000}")
  public void retryOpenedAccounts() {
    Set<String> dueAccountIds = redisTemplate.opsForZSet().rangeByScore( RETRY_SCHEDULE_KEY, Double.NEGATIVE_INFINITY, System.currentTimeMillis(), 0, RETRY_BATCH_SIZE);
    if (dueAccountIds == null || dueAccountIds.isEmpty()) {
      return;
    }
    dueAccountIds.forEach(this::claimAndRetry);
  }

  private void claimAndRetry(String accountIdValue) {
    String lockKey = RETRY_LOCK_KEY_PREFIX + accountIdValue;
    if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, "processing", RETRY_LOCK_TTL))) {
      return;
    }
    try {
      retry(Long.parseLong(accountIdValue));
    } finally {
      redisTemplate.delete(lockKey);
    }
  }

  private void synchronize(AccountDTO account, SyncOperation operation) {
    try {
      String ciHash = accountMapper.selectCiHashByCustomerId(account.getCustomerId()).orElseThrow(() -> new AccountNotFoundException("개설할 계좌의 사용자를 찾을 수 없습니다."));
      boolean successful = (operation == SyncOperation.CREATE ? mydataProvider.createRiaAccount(ciHash, account) : mydataProvider.updateRiaLimit(ciHash, account))
          .is2xxSuccessful();
      if (!successful) {
        scheduleRetry(account.getAccountId(), operation);
        log.error("MyData RIA 계좌 동기화 실패: accountId={}, operation={}", account.getAccountId(), operation);
        return;
      }
      clearRetry(account.getAccountId());
    } catch (RuntimeException exception) {
      log.error("MyData RIA 계좌 동기화 예외: accountId={}, operation={}", account.getAccountId(), operation, exception);
      scheduleRetry(account.getAccountId(), operation);
    }
  }

  private void retry(Long accountId) {
    try {
      AccountDTO account = accountMapper.selectByAccountId(accountId).orElse(null);
      if (account == null || account.getStatus() != Status.OPENED) {
        clearRetry(accountId);
        return;
      }
      String ciHash = accountMapper.selectCiHashByCustomerId(account.getCustomerId())
          .orElseThrow(() -> new AccountNotFoundException("개설할 계좌의 사용자를 찾을 수 없습니다."));
      SyncOperation operation = mydataProvider.hasOwnRiaAccount(ciHash) ? SyncOperation.UPDATE_LIMIT : SyncOperation.CREATE;
      synchronize(account, operation);
    } catch (RuntimeException exception) {
      log.error("MyData RIA 계좌 재동기화 실패: accountId={}", accountId, exception);
      scheduleRetry(accountId, SyncOperation.UPDATE_LIMIT);
    }
  }

  private void scheduleRetry(Long accountId, SyncOperation operation) {
    String taskKey = RETRY_TASK_KEY_PREFIX + accountId;
    int attemptCount = parseAttemptCount(redisTemplate.opsForValue().get(taskKey)) + 1;
    redisTemplate.opsForValue().set(taskKey, operation.name() + ":" + attemptCount);
    long nextRetryAt = System.currentTimeMillis() + retryDelay(attemptCount);
    redisTemplate.opsForZSet().add(RETRY_SCHEDULE_KEY, accountId.toString(), nextRetryAt);
  }

  private void clearRetry(Long accountId) {
    redisTemplate.delete(RETRY_TASK_KEY_PREFIX + accountId);
    redisTemplate.opsForZSet().remove(RETRY_SCHEDULE_KEY, accountId.toString());
  }

  private int parseAttemptCount(String task) {
    if (task == null || !task.contains(":")) {
      return 0;
    }
    try {
      return Integer.parseInt(task.substring(task.indexOf(':') + 1));
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private long retryDelay(int attemptCount) {
    return RETRY_DELAYS_MILLIS[Math.min(attemptCount - 1, RETRY_DELAYS_MILLIS.length - 1)];
  }

  private enum SyncOperation {
    CREATE,
    UPDATE_LIMIT
  }
}
