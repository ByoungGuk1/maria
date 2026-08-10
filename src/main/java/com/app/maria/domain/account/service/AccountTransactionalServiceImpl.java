package com.app.maria.domain.account.service;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.dto.request.AccountReapplyRequestDTO;
import com.app.maria.domain.account.exception.AccountException;
import com.app.maria.domain.account.exception.AccountNotFoundException;
import com.app.maria.domain.account.exception.DuplicateAccountException;
import com.app.maria.domain.account.exception.InvalidAccountRequestException;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.account.type.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class AccountTransactionalServiceImpl implements AccountTransactionalService {
  private static final int ACCOUNT_NO_RETRY_LIMIT = 5;
  private static final long ACCOUNT_NO_MIN = 1_000_000_000L;
  private static final long ACCOUNT_NO_MAX_EXCLUSIVE = 10_000_000_000L;
  private final AccountMapper accountMapper;
  private final AccountLogService accountLogService;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AccountDTO updateLimit(Long customerId, BigDecimal expectedCurrentLimit, BigDecimal newLimit, LocalDateTime changedAt) {
    AccountDTO account = findByCustomer(customerId);
    if (account.getStatus() != Status.APPLIED && account.getStatus() != Status.OPENED) {
      throw new InvalidAccountRequestException("신청 또는 개설 상태의 계좌만 한도를 변경할 수 있습니다.");
    }
    if (account.getLimitAmount().compareTo(newLimit) == 0){
      throw new InvalidAccountRequestException("기존 한도와 다른 금액을 입력해야 합니다.");
    }
    if (expectedCurrentLimit == null || account.getLimitAmount().compareTo(expectedCurrentLimit) != 0) {
      throw new InvalidAccountRequestException("계좌 한도가 변경되었습니다. 다시 조회 후 시도해주세요.");
    }
    BigDecimal ownUsedAndReservedAmount = accountMapper.selectOwnUsedAndReservedAmount(account.getAccountId());
    if (newLimit.compareTo(ownUsedAndReservedAmount) < 0) {
      throw new InvalidAccountRequestException("이미 사용한 매도한도보다 낮게 설정할 수 없습니다.");
    }
    if (accountMapper.updateLimit(account.getAccountId(), account.getStatus(), expectedCurrentLimit, newLimit) != 1){
      throw new InvalidAccountRequestException("계좌 한도 변경 중 상태 또는 한도가 변경되었습니다.");
    }
    AccountDTO updatedAccount = find(account.getAccountId());
    String logMessage = accountLogService.createLimitChangeReason(account.getLimitAmount(), updatedAccount.getLimitAmount());
    accountLogService.recordStatusChange(updatedAccount, account.getStatus(), changedAt, logMessage);
    return updatedAccount;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AccountDTO apply(AccountDTO account, LocalDateTime appliedAt, boolean autoApprove) {
    if (accountMapper.existsByCustomerId(account.getCustomerId())){
      throw new DuplicateAccountException("사용자의 기존 계좌 정보가 있습니다.");
    }
    account.setCreatedAt(appliedAt);
    try {
      if (accountMapper.insertApplication(account) != 1){
        throw new AccountException("계좌 신청 등록 실패");
      }
    } catch (DuplicateKeyException e) {
      throw new DuplicateAccountException("사용자의 기존 계좌 정보가 있습니다.");
    }
    AccountDTO appliedAccount = findByCustomer(account.getCustomerId());
    accountLogService.recordStatusChange(appliedAccount, null, appliedAt, "최초 개설 신청");
    if (!autoApprove) {
      return appliedAccount;
    }
    open(appliedAccount, appliedAt);
    AccountDTO opened = findByCustomer(account.getCustomerId());
    assertStatus(opened, Status.OPENED);
    accountLogService.recordStatusChange(opened, Status.APPLIED, appliedAt, "자동 판정 승인");
    return opened;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AccountDTO approve(Long accountId, BigDecimal expectedLimit, LocalDateTime openedAt) {
    AccountDTO account = find(accountId);
    account.setLimitAmount(expectedLimit);
    open(account, openedAt, "심사 도중 계좌 한도가 변경되었습니다. 다시 심사하세요.");
    AccountDTO openedAccount = find(accountId);
    assertStatus(openedAccount, Status.OPENED);
    accountLogService.recordStatusChange(openedAccount, account.getStatus(), openedAt, "사용자 계좌 개설");
    return openedAccount;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AccountDTO reject(Long accountId, String reason, LocalDateTime changedAt) {
    AccountDTO account = find(accountId);
    if (accountMapper.reject(account) != 1){
      throw new InvalidAccountRequestException("사용자 계좌 신청 반려 실패");
    }
    AccountDTO rejectedAccount = find(accountId);
    assertStatus(rejectedAccount, Status.REJECTED);
    accountLogService.recordStatusChange(rejectedAccount, account.getStatus(), changedAt, reason);
    return rejectedAccount;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AccountDTO reapply(Long accountId, AccountReapplyRequestDTO request, LocalDateTime appliedAt) {
    AccountDTO account = find(accountId);
    AccountDTO newAccount = request.toAccountDTO();
    newAccount.setAccountId(accountId);
    if (newAccount.getLimitAmount() == null){
      newAccount.setLimitAmount(account.getLimitAmount());
    }
    if (accountMapper.reapply(newAccount) != 1){
      throw new InvalidAccountRequestException("사용자 계좌 재신청 실패");
    }
    AccountDTO appliedAccount = find(accountId);
    assertStatus(appliedAccount, Status.APPLIED);
    accountLogService.recordStatusChange(appliedAccount, account.getStatus(), appliedAt, "사용자 계좌 개설 재신청");
    return appliedAccount;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AccountDTO override(Long accountId, String reason, LocalDateTime openedAt) {
    AccountDTO account = find(accountId);
    if (account.getStatus() != Status.REJECTED){
      throw new InvalidAccountRequestException("반려 상태의 계좌만 오버라이드할 수 있습니다.");
    }
    open(account, openedAt);
    AccountDTO openedAccount = find(accountId);
    assertStatus(openedAccount, Status.OPENED);
    accountLogService.recordStatusChange(openedAccount, Status.REJECTED, openedAt, reason);
    return openedAccount;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public AccountDTO updateAmount(AccountDTO newAmountAccount){
    AccountDTO foundAccount = find(newAmountAccount.getAccountId());
    if(accountMapper.updateProvisionalAmount(newAmountAccount)!=1){
      throw new AccountException("계좌 잔액 수정 실패");
    }
    foundAccount.setAmount(foundAccount.getAmount().add(newAmountAccount.getAmount()));
    return foundAccount;
  }

  private void open(AccountDTO account, LocalDateTime openedAt) {
    open(account, openedAt, "사용자 계좌 신청 승인 실패");
  }

  private void open(AccountDTO account, LocalDateTime openedAt, String approvalFailureMessage) {
    boolean override = account.getStatus() == Status.REJECTED;
    account.setOpenedAt(openedAt);
    for (int i = 0; i < ACCOUNT_NO_RETRY_LIMIT; i++) {
      account.setAccountNo(
          Long.toString(ThreadLocalRandom.current().nextLong(ACCOUNT_NO_MIN, ACCOUNT_NO_MAX_EXCLUSIVE))
      );
      try {
        if ((override ? accountMapper.overrideToOpened(account) : accountMapper.approve(account)) == 1){
          return;
        }
        throw new InvalidAccountRequestException(override ? "계좌 오버라이드 실패" : approvalFailureMessage);
      } catch (DuplicateKeyException e) {
        if (i == ACCOUNT_NO_RETRY_LIMIT - 1){
          throw new AccountException("고유한 계좌번호 생성에 실패했습니다.");
        }
      }
    }
  }

  private AccountDTO find(Long id) {
    return accountMapper.selectByAccountId(id).orElseThrow(() -> new AccountNotFoundException("계좌 조회 실패"));
  }
  private AccountDTO findByCustomer(Long id) {
    return accountMapper.selectByCustomerId(id).orElseThrow(() -> new AccountNotFoundException("계좌 조회 실패"));
  }
  private void assertStatus(AccountDTO account, Status status) {
    if (account.getStatus() != status) throw new AccountException("상태 변경 실패");
  }
}
