package com.app.maria.domain.account.service;

import com.app.maria.domain.account.dto.AccountDTO;
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
    String result = accountService.applyAccount(newAccountDTO).toString();
    log.info(result);
  }

  @Test
  void openAccount(){
    AccountDTO foundAccountDTO = accountMapper.selectByCustomerId(1L).get();
    log.info(accountService.openAccount(foundAccountDTO).toString());
  }

  @Test
  void rejectAccount(){
    AccountDTO foundAccountDTO = accountMapper.selectByCustomerId(1L).get();
    log.info(accountService.rejectAccount(foundAccountDTO).toString());
  }

  @Test
  void reapplyAccount(){
    AccountDTO foundAccountDTO = accountMapper.selectByCustomerId(1L).get();
    log.info(accountService.reapplyAccount(foundAccountDTO).toString());
  }

}