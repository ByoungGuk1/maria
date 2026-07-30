package com.app.maria.domain.account.mapper;

import com.app.maria.domain.account.dto.AccountDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@SpringBootTest
@Slf4j
class AccountMapperTest {

  @Autowired
  private AccountMapper accountMapper;

  private final Long customerId = 1L;
  private Long accountId;
  private BigDecimal accountNo;
  private final AccountDTO applyAccountDTO = AccountDTO.builder()
      .customerId(customerId)
      .createdAt(LocalDateTime.parse("2007-12-03T10:15:30"))
      .limitAmount(BigDecimal.valueOf(30_000_000))
    .build();

  //고객 존재 여부 확인
  @Test
  void existsCustomer(){
    boolean result = accountMapper.existsCustomerById(customerId);
    log.info("existsCustomerById(customerId) 결과 : {}", result);
  }

  //account_id 기준 단건 조회
  @Test
  void selectAccount(){
    accountMapper.selectByAccountId(accountId).ifPresentOrElse((account)->{
      log.info("selectByAccountId(accountId) 성공, 결과: {}", account);
    },()->{
      log.error("selectByAccountId(accountId) 실패");
    });
  }

  //customer_id 기준 단건 조회
  @Test
  void selectAccountByCustomer(){
    accountMapper.selectByCustomerId(customerId).ifPresentOrElse((account)->{
      log.info("selectByCustomerId(customerId) 성공, 결과: {}", account);
    },()->{
      log.error("selectByCustomerId(customerId) 실패");
    });
  }

  //customer_id 기준 계좌 존재 여부
  @Test
  void existsByCustomerId(){
    boolean result = accountMapper.existsByCustomerId(customerId);
    log.info("existsByCustomerId(customerId) 결과 : {}", result);
  }

  //계좌번호 중복 사전 확인
  @Test
  void existsByAccountNo(){
    boolean result = accountMapper.existsByAccountNo(accountNo);
    log.info("existsByAccountNo(accountNo) 결과 : {}",result);
  }

  //최초 계좌 개설 신청
  @Test
  void insertApplication(){
    int result = accountMapper.insertApplication(applyAccountDTO);
    log.info("insertApplication(applyAccountDTO) 결과 : {}", result);
  }

  //반려 후 재신청 : 수정 건수가 0이면 현재 상태가 반려가 아님
  @Test
  void reapply(){
    accountId = 2L;
    AccountDTO reapplyAccountDTO = AccountDTO.builder()
        //accountId 필요
        .accountId(accountId)
        .limitAmount(BigDecimal.valueOf(50_000_000))
        .build();
    int result = accountMapper.reapply(reapplyAccountDTO);
    log.info("reapply(limitAmount) 결과 : {}", result);
  }

  //신청 승인
  // - 계좌번호는 승인 시점에 최초 1회 발급
  // - 수정 건수가 0이면 이미 처리됐거나 신청 상태가 아님
  @Test
  void approve(){
    accountId = 1L;
    AccountDTO approveAccountDTO = AccountDTO.builder()
        //accountId 필요
        .accountId(accountId)
        .accountNo(BigDecimal.valueOf(1000000000L + (long)(Math.random() * 9000000000L)))
        .openedAt(LocalDateTime.parse(LocalDateTime.now().toString()))
        .build();
    int result = accountMapper.approve(approveAccountDTO);
    log.info("approve(accountNo,openedAt) 결과 : {}", result);
  }

  //신청 반려
  // - 반려 사유는 account_status_log에 별도로 저장
  // - 수정 건수가 0이면 이미 처리됐거나 신청 상태가 아님
  @Test
  void reject(){
    accountId = 2L;
    AccountDTO rejectAccountDTO = AccountDTO.builder()
        //accountId 필요
        .accountId(accountId)
        .build();
    int result = accountMapper.reject(rejectAccountDTO);
    log.info("reject() 결과 : {}", result);
  }

  //상태별 계좌 목록
  @Test
  void selectAllAccount(){
    List<AccountDTO> accountDTOList = accountMapper.selectAllAccount();
    log.info("계좌 전체 조회 결과 : {}", accountDTOList);
  }
}