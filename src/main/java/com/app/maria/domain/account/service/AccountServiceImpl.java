package com.app.maria.domain.account.service;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.dto.AccountStatusLogDTO;
import com.app.maria.domain.account.dto.request.AccountRequestDTO;
import com.app.maria.domain.account.dto.response.AccountLogResponseDTO;
import com.app.maria.domain.account.dto.response.AccountResponseDTO;
import com.app.maria.domain.account.exception.AccountException;
import com.app.maria.domain.account.exception.AccountNotFoundException;
import com.app.maria.domain.account.exception.DuplicateAccountException;
import com.app.maria.domain.account.exception.InvalidAccountRequestException;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.account.mapper.AccountStatusLogMapper;
import com.app.maria.domain.account.type.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
  private final AccountMapper accountMapper;
  private final AccountStatusLogMapper accountStatusLogMapper;

  private static final BigDecimal MAX_LIMIT_AMOUNT = BigDecimal.valueOf(50_000_000L);
  private static final BigDecimal MIN_LIMIT_AMOUNT = BigDecimal.ONE;
  private static final LocalDate RIA_APPLICATION_START_DATE = LocalDate.of(2026, 3, 23);
  private static final LocalDate RIA_APPLICATION_END_DATE = LocalDate.of(2026, 12, 31);
  private static final int ACCOUNT_NO_RETRY_LIMIT = 5;
  private static final long ACCOUNT_NO_MIN = 1_000_000_000L;
  private static final long ACCOUNT_NO_MAX_EXCLUSIVE = 10_000_000_000L;

  @Override
  public List<AccountResponseDTO> findAll(){
    return accountMapper.selectAllAccount().stream().map(AccountResponseDTO::new).toList();
  }

  @Override
  public BigDecimal getAvailableLimit(Long customerId) {
    getApplicationTime();
    validateCustomer(customerId);
    return calculateAvailableLimit(customerId);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AccountResponseDTO applyAccount(Long customerId, AccountRequestDTO accountRequestDTO){
    AccountDTO accountDTO = new AccountDTO(accountRequestDTO);
    accountDTO.setCustomerId(customerId);

    LocalDateTime applicationTime = getApplicationTime();
    validateCustomer(customerId);

    if(accountMapper.existsByCustomerId(customerId)){
      throw new DuplicateAccountException("사용자의 기존 계좌 정보가 있습니다.");
    }

    validateRequestedLimit(accountDTO.getLimitAmount(), calculateAvailableLimit(customerId));

    accountDTO.setCreatedAt(applicationTime);
    return createAndAutoApproveAccount(accountDTO, applicationTime);
  }

  private AccountResponseDTO createAndAutoApproveAccount(AccountDTO accountDTO, LocalDateTime appliedAt) {
    try {
      if (accountMapper.insertApplication(accountDTO) < 1) {
        throw new AccountException("계좌 신청 등록 실패");
      }
    } catch (DuplicateKeyException e) {
      throw new DuplicateAccountException("사용자의 기존 계좌 정보가 있습니다.");
    }
    AccountDTO result = accountMapper.selectByCustomerId(accountDTO.getCustomerId()).orElseThrow(()->new AccountException("계좌 생성 후 재조회 실패"));
    AccountStatusLogDTO accountStatusLogDTO = AccountStatusLogDTO.builder()
        .accountId(result.getAccountId())
        .prevStatus(null)
        .newStatus(result.getStatus())
        .changedAt(appliedAt)
        .reason("최초 개설 신청")
        .build();
    if(accountStatusLogMapper.insertLog(accountStatusLogDTO) < 1){
      throw new AccountException("로그 등록 실패");
    }
    return autoApproveAccount(result, appliedAt);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AccountResponseDTO approveAccount(Long accountId) {
    AccountDTO foundAccount = accountMapper.selectByAccountId(accountId).orElseThrow(() -> new AccountNotFoundException("계좌 조회 실패"));
    LocalDateTime applicationTime = getApplicationTime();
    validateRequestedLimit(foundAccount.getLimitAmount(), calculateAvailableLimit(foundAccount.getCustomerId()));
    AccountStatusLogDTO accountStatusLogDTO = AccountStatusLogDTO.builder()
        .accountId(foundAccount.getAccountId())
        .prevStatus(foundAccount.getStatus())
        .changedAt(applicationTime)
        .reason("사용자 계좌 개설")
        .build();
    foundAccount.setOpenedAt(applicationTime);
    return approveAccount(foundAccount, accountStatusLogDTO);
  }

  private AccountResponseDTO approveAccount(AccountDTO foundAccount, AccountStatusLogDTO accountStatusLogDTO) {
    approveWithAccountNoRetry(foundAccount);
    AccountDTO result = accountMapper.selectByAccountId(foundAccount.getAccountId()).orElseThrow(()->new AccountException("계좌 개설 후 재조회 실패"));
    if(result.getStatus() != Status.OPENED){
      throw new AccountException("상태 변경 실패");
    }
    return new AccountResponseDTO(matchLog(result, accountStatusLogDTO));
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AccountResponseDTO rejectAccount(Long accountId, String reason){
    AccountDTO foundAccount = accountMapper.selectByAccountId(accountId).orElseThrow(() -> new AccountNotFoundException("계좌 조회 실패"));
    LocalDateTime currentTime = getCurrentDateTime();
    String normalizedReason = checkReason(reason, "계좌 반려 사유를 입력해야 합니다.");
    AccountStatusLogDTO accountStatusLogDTO = AccountStatusLogDTO.builder().accountId(foundAccount.getAccountId()).prevStatus(foundAccount.getStatus()).changedAt(currentTime).reason(normalizedReason).build();
    if(accountMapper.reject(foundAccount) < 1){
      throw new InvalidAccountRequestException("사용자 계좌 신청 반려 실패");
    }
    AccountDTO result = accountMapper.selectByAccountId(accountId).orElseThrow(() -> new AccountException("계좌 상태 변경 후 재조회 실패"));
    if(result.getStatus() != Status.REJECTED){
      throw new AccountException("상태 변경 실패");
    }
    return new AccountResponseDTO(matchLog(result, accountStatusLogDTO));
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AccountResponseDTO reapplyAccountByAccountId(Long accountId, AccountRequestDTO accountRequestDTO) {
    AccountDTO accountDTO = new AccountDTO(accountRequestDTO);
    accountDTO.setAccountId(accountId);
    AccountDTO foundAccount = accountMapper.selectByAccountId(accountId).orElseThrow(() -> new AccountNotFoundException("계좌 조회 실패"));
    LocalDateTime applicationTime = getApplicationTime();
    AccountStatusLogDTO accountStatusLogDTO = AccountStatusLogDTO.builder().accountId(foundAccount.getAccountId()).prevStatus(foundAccount.getStatus()).changedAt(applicationTime).reason("사용자 계좌 개설 재신청").build();
    if(accountDTO.getLimitAmount()==null){
      accountDTO.setLimitAmount(foundAccount.getLimitAmount());
    }
    validateRequestedLimit(accountDTO.getLimitAmount(), calculateAvailableLimit(foundAccount.getCustomerId()));
    return reapplyAccount(accountDTO, accountStatusLogDTO);
  }

  private AccountResponseDTO reapplyAccount(AccountDTO accountDTO, AccountStatusLogDTO accountStatusLogDTO) {
    if(accountMapper.reapply(accountDTO) < 1){
      throw new InvalidAccountRequestException("사용자 계좌 재신청 실패");
    }
    AccountDTO result = accountMapper.selectByAccountId(accountDTO.getAccountId()).orElseThrow(() -> new AccountException("계좌 상태 변경 후 재조회 실패"));
    if(result.getStatus() != Status.APPLIED){
      throw new AccountException("상태 변경 실패");
    }
    return new AccountResponseDTO(matchLog(result, accountStatusLogDTO));
  }

  @Override
  public AccountResponseDTO getAccountByAccountId(Long accountId) {
    AccountDTO accountDTO = accountMapper.selectByAccountId(accountId).orElseThrow(()->new AccountNotFoundException("계좌 조회 실패"));
    return new AccountResponseDTO(accountDTO);
  }

  @Override
  public List<AccountLogResponseDTO> getStatusLogsByAccountId(Long accountId) {
    accountMapper.selectByAccountId(accountId).orElseThrow(() -> new AccountNotFoundException("계좌 조회 실패"));
    return accountStatusLogMapper.selectByAccountId(accountId).stream().map(AccountLogResponseDTO::new).toList();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AccountResponseDTO overrideAccount(Long accountId, String reason){
    String normalizedReason = checkReason(reason, "관리자 오버라이드 사유를 입력해야 합니다.");
    AccountDTO foundAccount = accountMapper.selectByAccountId(accountId).orElseThrow(() -> new AccountNotFoundException("계좌 조회 실패"));
    if (foundAccount.getStatus() != Status.REJECTED) {
      throw new InvalidAccountRequestException("반려 상태의 계좌만 오버라이드할 수 있습니다.");
    }
    LocalDateTime applicationTime = getApplicationTime();
    validateRequestedLimit(foundAccount.getLimitAmount(), calculateAvailableLimit(foundAccount.getCustomerId()));
    foundAccount.setOpenedAt(applicationTime);
    AccountStatusLogDTO logDTO = AccountStatusLogDTO.builder()
        .accountId(foundAccount.getAccountId())
        .prevStatus(foundAccount.getStatus())
        .changedAt(applicationTime)
        .reason(normalizedReason)
        .build();
    return overrideAccount(foundAccount, logDTO);
  }

  private AccountResponseDTO overrideAccount(AccountDTO foundAccount, AccountStatusLogDTO logDTO) {
    overrideWithAccountNoRetry(foundAccount);
    AccountDTO result = accountMapper.selectByAccountId(foundAccount.getAccountId()).orElseThrow(() -> new AccountException("계좌 오버라이드 후 재조회 실패"));
    if (result.getStatus() != Status.OPENED) {
      throw new AccountException("계좌 오버라이드 상태 변경 실패");
    }
    return new AccountResponseDTO(matchLog(result, logDTO));
  }

  private AccountDTO matchLog(AccountDTO accountDTO, AccountStatusLogDTO logDTO){
    logDTO.setNewStatus(accountDTO.getStatus());
    if(accountStatusLogMapper.insertLog(logDTO) < 1){
      throw new AccountException("로그 생성 실패");
    }
    accountStatusLogMapper.selectLatestByAccountId(accountDTO.getAccountId()).ifPresentOrElse((lDTO)->{
      if(!lDTO.getNewStatus().equals(accountDTO.getStatus())){
        throw new AccountException("상태 변경 로그 불일치");
      }
    },()->{
      throw new AccountException("상태 변경 후 로그 재조회 실패");
    });
    return accountDTO;
  }

  private BigDecimal calculateAvailableLimit(Long customerId) {
    // TODO 타 금융회사의 RIA 납입한도 조회 API
    BigDecimal otherFinancialCompanyLimitTotal = BigDecimal.ZERO;
    return MAX_LIMIT_AMOUNT.subtract(otherFinancialCompanyLimitTotal);
  }

  private void validateRequestedLimit(BigDecimal requestedLimit, BigDecimal availableLimit) {
    if (availableLimit.compareTo(MIN_LIMIT_AMOUNT) < 0) {
      throw new InvalidAccountRequestException("설정 가능한 RIA 납입한도가 없어 계좌를 개설할 수 없습니다.");
    }
    if (requestedLimit == null) {
      throw new InvalidAccountRequestException("계좌 한도 입력이 필요합니다.");
    }
    if (requestedLimit.compareTo(MIN_LIMIT_AMOUNT) < 0) {
      throw new InvalidAccountRequestException("계좌의 한도는 " + MIN_LIMIT_AMOUNT + "원 이상이어야 합니다.");
    }
    if (requestedLimit.stripTrailingZeros().scale() > 0) {
      throw new InvalidAccountRequestException("계좌의 한도는 원 단위로 입력해야 합니다.");
    }
    if (requestedLimit.compareTo(availableLimit) > 0) {
      throw new InvalidAccountRequestException("계좌의 한도는 " + MIN_LIMIT_AMOUNT + "부터 " + availableLimit + "이하 입니다.");
    }
  }

  private void validateCustomer(Long customerId) {
    if(customerId == null || customerId <= 0L){
      throw new InvalidAccountRequestException("개설할 계좌의 사용자 정보가 없습니다.");
    }
    if(!accountMapper.existsCustomerById(customerId)){
      throw new AccountNotFoundException("개설할 계좌의 사용자를 찾을 수 없습니다."); // 추후 CustomerNotFoundException 으로 변경 예정
    }
  }

  private LocalDateTime getApplicationTime() {
    LocalDateTime applicationTime = getCurrentDateTime();
    LocalDate today = applicationTime.toLocalDate();
    if (today.isBefore(RIA_APPLICATION_START_DATE) || today.isAfter(RIA_APPLICATION_END_DATE)) {
      throw new InvalidAccountRequestException("RIA 계좌 신청 가능 기간이 아닙니다.");
    }
    return applicationTime;
  }

  private LocalDateTime getCurrentDateTime() {
    // TODO 타임 테이블 조회
    return LocalDateTime.now();
  }

  private AccountResponseDTO autoApproveAccount(AccountDTO appliedAccount, LocalDateTime openedAt) {
    if (appliedAccount.getStatus() != Status.APPLIED) {
      throw new AccountException("자동 판정할 수 없는 계좌 상태입니다.");
    }
    AccountStatusLogDTO logDTO = AccountStatusLogDTO.builder()
        .accountId(appliedAccount.getAccountId())
        .prevStatus(Status.APPLIED)
        .changedAt(openedAt)
        .reason("자동 판정 승인")
        .build();
    appliedAccount.setOpenedAt(openedAt);
    approveWithAccountNoRetry(appliedAccount);
    AccountDTO openedAccount = accountMapper.selectByAccountId(appliedAccount.getAccountId()).orElseThrow(() -> new AccountException("자동 승인 후 계좌 재조회 실패"));
    if (openedAccount.getStatus() != Status.OPENED) {
      throw new AccountException("자동 승인 상태 변경 실패");
    }
    return new AccountResponseDTO(matchLog(openedAccount, logDTO));
  }

  private void approveWithAccountNoRetry(AccountDTO accountDTO) {
    for (int attempt = 1; attempt <= ACCOUNT_NO_RETRY_LIMIT; attempt++) {
      accountDTO.setAccountNo(makeAccountNo());
      try {
        if (accountMapper.approve(accountDTO) < 1) {
          throw new InvalidAccountRequestException("사용자 계좌 신청 승인 실패");
        }
        return;
      } catch (DuplicateKeyException e) {
        if (attempt == ACCOUNT_NO_RETRY_LIMIT) {
          throw new AccountException("고유한 계좌번호 생성에 실패했습니다.");
        }
      }
    }
  }

  private void overrideWithAccountNoRetry(AccountDTO accountDTO) {
    for (int attempt = 1; attempt <= ACCOUNT_NO_RETRY_LIMIT; attempt++) {
      accountDTO.setAccountNo(makeAccountNo());
      try {
        if (accountMapper.overrideToOpened(accountDTO) < 1) {
          throw new InvalidAccountRequestException("계좌 오버라이드 실패");
        }
        return;
      } catch (DuplicateKeyException e) {
        if (attempt == ACCOUNT_NO_RETRY_LIMIT) {
          throw new AccountException("고유한 계좌번호 생성에 실패했습니다.");
        }
      }
    }
  }

  private BigDecimal makeAccountNo(){
    long accountNo = ThreadLocalRandom.current().nextLong(ACCOUNT_NO_MIN, ACCOUNT_NO_MAX_EXCLUSIVE);
    return BigDecimal.valueOf(accountNo);
  }

  private String checkReason(String reason, String InvalidMessage){
    if (reason == null || reason.isBlank()) {
      throw new InvalidAccountRequestException(InvalidMessage);
    }
    String normalizedReason = reason.trim();
    if (normalizedReason.length() > 200) {
      throw new InvalidAccountRequestException("사유는 200자 이하로 입력해야 합니다.");
    }
    return normalizedReason;
  }
}
