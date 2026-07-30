package com.app.maria.domain.account.mapper;

import com.app.maria.domain.account.dto.AccountDTO;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Mapper
public interface AccountMapper {
  //고객 존재 여부 확인
  boolean existsCustomerById(Long customerId);

  //account_id 기준 단건 조회
  Optional<AccountDTO> selectByAccountId(Long accountId);

  //customer_id 기준 단건 조회
  Optional<AccountDTO> selectByCustomerId(Long customerId);

  //customer_id 기준 계좌 존재 여부
  boolean existsByCustomerId(Long customerId);

  //계좌번호 중복 사전 확인
  boolean existsByAccountNo(BigDecimal accountNo);

  //최초 계좌 개설 신청
  int insertApplication(AccountDTO accountDTO);

  //반려 후 재신청 : 수정 건수가 0이면 현재 상태가 반려가 아님
  int reapply(AccountDTO accountDTO);

  //신청 승인
  // - 계좌번호는 승인 시점에 최초 1회 발급
  // - 수정 건수가 0이면 이미 처리됐거나 신청 상태가 아님
  int approve(AccountDTO accountDTO);

  //신청 반려
  // - 반려 사유는 account_status_log에 별도로 저장
  // - 수정 건수가 0이면 이미 처리됐거나 신청 상태가 아님
  int reject(AccountDTO  accountDTO);

  //상태별 계좌 목록
  List<AccountDTO> selectAllAccount();
}
