package com.app.maria.domain.account.service;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.exception.AccountNotFoundException;
import com.app.maria.domain.account.infra.RedisMydataSyncTaskRepository;
import com.app.maria.domain.account.infra.RedisMydataSyncTaskRepository.ClaimedTask;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.account.provider.MydataProvider;
import com.app.maria.domain.account.type.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountMydataSyncService {
    private static final int RETRY_BATCH_SIZE = 100;

    private final AccountMapper accountMapper;
    private final MydataProvider mydataProvider;
    private final RedisMydataSyncTaskRepository taskRepository;

    public void create(AccountDTO account) {
        taskRepository.enqueue(account.getAccountId(), SyncOperation.CREATE.name());
    }

    public void updateLimit(AccountDTO account) {
        taskRepository.enqueue(account.getAccountId(), SyncOperation.UPDATE_LIMIT.name());
    }

    @Scheduled(fixedDelayString = "${custom.mydata.ria-sync-delay-ms:1000}")
    public void retryOpenedAccounts() {
        taskRepository.claimDueTasks(RETRY_BATCH_SIZE).forEach(this::process);
    }

    private void process(ClaimedTask task) {
        try {
            retry(task);
        } finally {
            taskRepository.release(task);
        }
    }

    private void retry(ClaimedTask task) {
        try {
            AccountDTO account = accountMapper.selectByAccountId(task.accountId()).orElse(null);
            if (account == null || account.getStatus() != Status.OPENED) {
                taskRepository.complete(task);
                return;
            }

            String ciHash =
                    accountMapper
                            .selectCiHashByCustomerId(account.getCustomerId())
                            .orElseThrow(
                                    () -> new AccountNotFoundException("개설할 계좌의 사용자를 찾을 수 없습니다."));
            SyncOperation operation =
                    mydataProvider.hasOwnRiaAccount(ciHash)
                            ? SyncOperation.UPDATE_LIMIT
                            : SyncOperation.CREATE;
            synchronize(account, ciHash, operation, task);
        } catch (RuntimeException exception) {
            log.error("MyData RIA 계좌 재동기화 실패: accountId={}", task.accountId(), exception);
            taskRepository.reschedule(task, SyncOperation.UPDATE_LIMIT.name());
        }
    }

    private void synchronize(
            AccountDTO account, String ciHash, SyncOperation operation, ClaimedTask task) {
        try {
            boolean successful =
                    (operation == SyncOperation.CREATE
                                    ? mydataProvider.createRiaAccount(ciHash, account)
                                    : mydataProvider.updateRiaLimit(ciHash, account))
                            .is2xxSuccessful();
            if (successful) {
                taskRepository.complete(task);
                return;
            }
            taskRepository.reschedule(task, operation.name());
            log.error(
                    "MyData RIA 계좌 동기화 실패: accountId={}, operation={}",
                    account.getAccountId(),
                    operation);
        } catch (RuntimeException exception) {
            log.error(
                    "MyData RIA 계좌 동기화 예외: accountId={}, operation={}",
                    account.getAccountId(),
                    operation,
                    exception);
            taskRepository.reschedule(task, operation.name());
        }
    }

    private enum SyncOperation {
        CREATE,
        UPDATE_LIMIT
    }
}
