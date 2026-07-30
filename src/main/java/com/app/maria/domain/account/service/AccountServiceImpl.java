package com.app.maria.domain.account.service;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.dto.AccountStatusLogDTO;
import com.app.maria.domain.account.exception.AccountException;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.account.mapper.AccountStatusLogMapper;
import com.app.maria.domain.account.type.Status;
import lombok.RequiredArgsConstructor;
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
  private final LocalDateTime TIME_TABLE_NOW =  LocalDateTime.now();

  @Override
  public List<AccountDTO> findAll(){
    return accountMapper.selectAllAccount();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AccountDTO applyAccount(AccountDTO accountDTO){
    Long customerId = accountDTO.getCustomerId();
    BigDecimal limitAmount = accountDTO.getLimitAmount();
    if(customerId == null || customerId == 0L){
      throw new AccountException("개설할 계좌의 사용자를 찾을 수 없습니다.");
    }
    if(accountMapper.existsByCustomerId(customerId)){
      throw new AccountException("사용자의 기존 계좌 정보가 있습니다.");
    }

    if(limitAmount==null|| limitAmount.equals(BigDecimal.ZERO)){
      throw new AccountException("계좌 한도 입력이 필요합니다.");
    }

    accountMapper.insertApplication(accountDTO);
    return accountMapper.selectByCustomerId(customerId).orElseThrow(()->new AccountException("계좌 생성 후 재조회 실패"));
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AccountDTO openAccount(AccountDTO accountDTO) {
    AccountDTO foundAccount = accountMapper.selectByAccountId(accountDTO.getAccountId()).orElseThrow(() -> new AccountException("계좌 조회 실패"));

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
        .reason("사용자 계좌 계설")
        .build();

    foundAccount.setOpenedAt(openedAt);
    accountMapper.approve(foundAccount);

    AccountDTO result = accountMapper.selectByAccountId(foundAccount.getAccountId()).orElseThrow(()->new AccountException("계좌 개설 후 재조회 실패"));
    if(result.getStatus() != Status.OPENED){
      throw new AccountException("상태 변경 실패");
    }

    matchLog(result, accountStatusLogDTO);

    return result;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AccountDTO rejectAccount(AccountDTO accountDTO){
    AccountDTO foundAccount = accountMapper.selectByAccountId(accountDTO.getAccountId()).orElseThrow(() -> new AccountException("계좌 조회 실패"));
    AccountStatusLogDTO accountStatusLogDTO = AccountStatusLogDTO.builder().accountId(foundAccount.getAccountId()).prevStatus(foundAccount.getStatus()).changedAt(TIME_TABLE_NOW).reason("사용자 계좌 개설 거부").build();

    accountMapper.reject(foundAccount);
    AccountDTO result = accountMapper.selectByAccountId(accountDTO.getAccountId()).orElseThrow(() -> new AccountException("계좌 상태 변경 후 재조회 실패"));
    if(result.getStatus() != Status.REJECTED){
      throw new AccountException("상태 변경 실패");
    }

    matchLog(result, accountStatusLogDTO);

    return result;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AccountDTO reapplyAccount(AccountDTO accountDTO) {
    AccountDTO foundAccount = accountMapper.selectByAccountId(accountDTO.getAccountId()).orElseThrow(() -> new AccountException("계좌 조회 실패"));
    AccountStatusLogDTO accountStatusLogDTO = AccountStatusLogDTO.builder().accountId(foundAccount.getAccountId()).prevStatus(foundAccount.getStatus()).changedAt(TIME_TABLE_NOW).reason("사용자 계좌 개설 재신청").build();

    if(accountDTO.getLimitAmount()==null|| accountDTO.getLimitAmount().equals(BigDecimal.ZERO)){
      accountDTO.setLimitAmount(foundAccount.getLimitAmount());
    }
    accountMapper.reapply(foundAccount);
    AccountDTO result = accountMapper.selectByAccountId(accountDTO.getAccountId()).orElseThrow(() -> new AccountException("계좌 상태 변경 후 재조회 실패"));
    if(result.getStatus() != Status.APPLIED){
      throw new AccountException("상태 변경 실패");
    }

    matchLog(result, accountStatusLogDTO);

    return result;
  }

  private void matchLog(AccountDTO accountDTO, AccountStatusLogDTO logDTO){
    logDTO.setNewStatus(accountDTO.getStatus());
    accountStatusLogMapper.insertLog(logDTO);
    accountStatusLogMapper.selectLatestByAccountId(accountDTO.getAccountId()).ifPresentOrElse((lDTO)->{
      if(!logDTO.getNewStatus().equals(accountDTO.getStatus())){
        throw new AccountException("상태 변경 로그 불일치");
      }
    },()->{
      throw new AccountException("상태 변경 후 로그 재조회 실패");
    });
  }
}
