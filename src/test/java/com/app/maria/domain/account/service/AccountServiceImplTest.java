package com.app.maria.domain.account.service;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.dto.request.AccountRequestDTO;
import com.app.maria.domain.account.mapper.AccountMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@SpringBootTest
@Slf4j
@Commit
class AccountServiceImplTest {
  @Autowired
  AccountMapper accountMapper;
  @Autowired
  private AccountService accountService;

  private final LocalDateTime NOW = LocalDateTime.now();

  private final AccountDTO newAccountDTO = AccountDTO.builder().customerId(1L).createdAt(NOW).limitAmount(BigDecimal.valueOf(50_000_000)).build();

  @Test
  void findAll() {
    log.info(accountService.findAll().toString());
  }

  @Test
  void applyAccount(){
    //계좌 계설
    AccountDTO foundAccountDTO = newAccountDTO;
    AccountRequestDTO reqDTO = AccountRequestDTO.builder()
        .accountId(foundAccountDTO.getAccountId()).customerId(foundAccountDTO.getCustomerId()).status(foundAccountDTO.getStatus()).accountNo(foundAccountDTO.getAccountNo()).limitAmount(foundAccountDTO.getLimitAmount()).amount(foundAccountDTO.getLimitAmount()).build();
    String result = accountService.applyAccount(reqDTO).toString();
    log.info(result);
  }

  @Test
  void openAccount(){
    AccountDTO foundAccountDTO = accountMapper.selectByCustomerId(1L).get();
    log.info(accountService.openAccount(foundAccountDTO.getAccountId()).toString());
  }

  @Test
  void rejectAccount(){
    AccountDTO foundAccountDTO = accountMapper.selectByCustomerId(1L).get();
    log.info(accountService.rejectAccount(foundAccountDTO.getAccountId()).toString());
  }

  @Test
  void reapplyAccount(){
    AccountDTO foundAccountDTO = accountMapper.selectByCustomerId(1L).get();
    AccountRequestDTO reqDTO = AccountRequestDTO.builder()
        .accountId(foundAccountDTO.getAccountId()).customerId(foundAccountDTO.getCustomerId()).status(foundAccountDTO.getStatus()).accountNo(foundAccountDTO.getAccountNo()).limitAmount(foundAccountDTO.getLimitAmount()).amount(foundAccountDTO.getLimitAmount()).build();
    log.info(accountService.reapplyAccount(foundAccountDTO.getAccountId(), reqDTO).toString());
  }

  @Test
  void getAccount(){
    AccountDTO foundAccountDTO = accountMapper.selectByCustomerId(1L).get();
    log.info(accountService.getAccount(foundAccountDTO.getAccountId()).toString());
  }

  @Test
  void getAccountStatus(){
    AccountDTO foundAccountDTO = accountMapper.selectByCustomerId(1L).get();
    log.info(accountService.getStatusLogList(foundAccountDTO.getAccountId()).toString());
  }
}