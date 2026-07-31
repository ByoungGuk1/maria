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
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
  private final AccountMapper accountMapper;
  private final AccountStatusLogMapper accountStatusLogMapper;

  //TIME_TABLE_NOW 추후 수정
  private final BigDecimal MAX_LIMIT_AMOUNT = BigDecimal.valueOf(50_000_000L);

  @Override
  public List<AccountResponseDTO> findAll(){
    return accountMapper.selectAllAccount().stream().map(AccountResponseDTO::new).toList();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AccountResponseDTO applyAccount(Long customerId, AccountRequestDTO accountRequestDTO){
    AccountDTO accountDTO = new AccountDTO(accountRequestDTO);
    accountDTO.setCustomerId(customerId);
    BigDecimal limitAmount = accountDTO.getLimitAmount();

    if(customerId == null || customerId <= 0L){
      throw new InvalidAccountRequestException("개설할 계좌의 사용자 정보가 없습니다.");
    }

    if(!accountMapper.existsCustomerById(customerId)){
      throw new AccountNotFoundException("개설할 계좌의 사용자를 찾을 수 없습니다."); // 추후 CustomerNotFoundException 으로 변경 예정
    }

    if(accountMapper.existsByCustomerId(customerId)){
      throw new DuplicateAccountException("사용자의 기존 계좌 정보가 있습니다.");
    }

    if(limitAmount==null){
      throw new InvalidAccountRequestException("계좌 한도 입력이 필요합니다.");
    }

    if(limitAmount.compareTo(MAX_LIMIT_AMOUNT) > 0 || limitAmount.compareTo(BigDecimal.valueOf(0)) < 0){
      throw new InvalidAccountRequestException("계좌의 한도는 0부터 "+MAX_LIMIT_AMOUNT+"이하 입니다.");
    }
    LocalDateTime TIME_TABLE_NOW =  LocalDateTime.now();
    accountDTO.setCreatedAt(TIME_TABLE_NOW);
    try {
      if (accountMapper.insertApplication(accountDTO) < 1) {
        throw new AccountException("계좌 신청 등록 실패");
      }
    } catch (DuplicateKeyException e) {
      throw new DuplicateAccountException("사용자의 기존 계좌 정보가 있습니다.");
    }
    AccountDTO result = accountMapper.selectByCustomerId(customerId).orElseThrow(()->new AccountException("계좌 생성 후 재조회 실패"));
    AccountStatusLogDTO accountStatusLogDTO = AccountStatusLogDTO.builder()
        .accountId(result.getAccountId()).prevStatus(null).newStatus(result.getStatus()).changedAt(TIME_TABLE_NOW).reason("최초 개설 신청").build();
    if(accountStatusLogMapper.insertLog(accountStatusLogDTO) < 1){
      throw new AccountException("로그 등록 실패");
    }
    return new AccountResponseDTO(result);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AccountResponseDTO approveAccount(Long accountId) {
    AccountDTO foundAccount = accountMapper.selectByAccountId(accountId).orElseThrow(() -> new AccountNotFoundException("계좌 조회 실패"));
    LocalDateTime TIME_TABLE_NOW =  LocalDateTime.now();
    LocalDateTime openedAt = LocalDateTime.parse(TIME_TABLE_NOW.toString());
    BigDecimal accountNo = BigDecimal.valueOf(1000000000L + (long)(Math.random() * 9000000000L));
    while(accountMapper.existsByAccountNo(accountNo)){
      accountNo = BigDecimal.valueOf(1000000000L + (long)(Math.random() * 9000000000L));
    }
    foundAccount.setAccountNo(accountNo);

    AccountStatusLogDTO accountStatusLogDTO = AccountStatusLogDTO.builder()
        .accountId(foundAccount.getAccountId())
        .prevStatus(foundAccount.getStatus())
        .changedAt(TIME_TABLE_NOW)
        .reason("사용자 계좌 개설")
        .build();

    foundAccount.setOpenedAt(openedAt);
    if(accountMapper.approve(foundAccount) < 1){
      throw new InvalidAccountRequestException("사용자 계좌 신청 승인 실패");
    }

    AccountDTO result = accountMapper.selectByAccountId(foundAccount.getAccountId()).orElseThrow(()->new AccountException("계좌 개설 후 재조회 실패"));
    if(result.getStatus() != Status.OPENED){
      throw new AccountException("상태 변경 실패");
    }

    matchLog(result, accountStatusLogDTO);

    return new AccountResponseDTO(result);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AccountResponseDTO rejectAccount(Long accountId, String reason){
    if (reason == null || reason.isBlank()) {
      throw new InvalidAccountRequestException("계좌 반려 사유를 입력해야 합니다.");
    }
    String normalizedReason = reason.trim();
    if (normalizedReason.length() > 200) {
      throw new InvalidAccountRequestException("계좌 반려 사유는 200자 이하로 입력해야 합니다.");
    }
    AccountDTO foundAccount = accountMapper.selectByAccountId(accountId).orElseThrow(() -> new AccountNotFoundException("계좌 조회 실패"));
    LocalDateTime TIME_TABLE_NOW =  LocalDateTime.now();
    AccountStatusLogDTO accountStatusLogDTO = AccountStatusLogDTO.builder().accountId(foundAccount.getAccountId()).prevStatus(foundAccount.getStatus()).changedAt(TIME_TABLE_NOW).reason(normalizedReason).build();

    if(accountMapper.reject(foundAccount) < 1){
      throw new InvalidAccountRequestException("사용자 계좌 신청 반려 실패");
    }

    AccountDTO result = accountMapper.selectByAccountId(accountId).orElseThrow(() -> new AccountException("계좌 상태 변경 후 재조회 실패"));
    if(result.getStatus() != Status.REJECTED){
      throw new AccountException("상태 변경 실패");
    }

    matchLog(result, accountStatusLogDTO);

    return new AccountResponseDTO(result);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AccountResponseDTO reapplyAccountByAccountId(Long accountId, AccountRequestDTO accountRequestDTO) {
    AccountDTO accountDTO = new AccountDTO(accountRequestDTO);
    accountDTO.setAccountId(accountId);
    AccountDTO foundAccount = accountMapper.selectByAccountId(accountId).orElseThrow(() -> new AccountNotFoundException("계좌 조회 실패"));
    LocalDateTime TIME_TABLE_NOW =  LocalDateTime.now();
    AccountStatusLogDTO accountStatusLogDTO = AccountStatusLogDTO.builder().accountId(foundAccount.getAccountId()).prevStatus(foundAccount.getStatus()).changedAt(TIME_TABLE_NOW).reason("사용자 계좌 개설 재신청").build();

    if(accountDTO.getLimitAmount()==null){
      accountDTO.setLimitAmount(foundAccount.getLimitAmount());
    }

    if(accountDTO.getLimitAmount().compareTo(MAX_LIMIT_AMOUNT) > 0 || accountDTO.getLimitAmount().compareTo(BigDecimal.valueOf(0)) < 0){
      throw new InvalidAccountRequestException("계좌의 한도는 0부터 "+MAX_LIMIT_AMOUNT+"까지 입니다.");
    }

    if(accountMapper.reapply(accountDTO) < 1){
      throw new InvalidAccountRequestException("사용자 계좌 재신청 실패");
    }
    AccountDTO result = accountMapper.selectByAccountId(accountDTO.getAccountId()).orElseThrow(() -> new AccountException("계좌 상태 변경 후 재조회 실패"));
    if(result.getStatus() != Status.APPLIED){
      throw new AccountException("상태 변경 실패");
    }

    matchLog(result, accountStatusLogDTO);

    return new AccountResponseDTO(result);
  }

  @Override
  public AccountResponseDTO getAccountByAccountId(Long accountId) {
    AccountDTO accountDTO = accountMapper.selectByAccountId(accountId).orElseThrow(()->new AccountNotFoundException("계좌 조회 실패"));
    return new AccountResponseDTO(accountDTO);
  }

  @Override
  public List<AccountLogResponseDTO> getStatusLogsByAccountId(Long accountId) {
    accountMapper.selectByAccountId(accountId)
        .orElseThrow(() -> new AccountNotFoundException("계좌 조회 실패"));
    return accountStatusLogMapper.selectByAccountId(accountId).stream().map(AccountLogResponseDTO::new).toList();
  }

  private void matchLog(AccountDTO accountDTO, AccountStatusLogDTO logDTO){
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
  }
}
