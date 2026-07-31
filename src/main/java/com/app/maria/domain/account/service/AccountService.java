package com.app.maria.domain.account.service;

import com.app.maria.domain.account.dto.request.AccountRequestDTO;
import com.app.maria.domain.account.dto.response.AccountLogResponseDTO;
import com.app.maria.domain.account.dto.response.AccountResponseDTO;

import java.util.List;

public interface AccountService {
  // 관리자 목록 조회
  List<AccountResponseDTO> findAll();
  // 관리자 대리 신청과 사용자 본인 신청이 공통으로 사용
  AccountResponseDTO applyAccount(Long customerId, AccountRequestDTO requestDTO);
  // 관리자 기능
  AccountResponseDTO approveAccount(Long accountId);
  AccountResponseDTO rejectAccount(Long accountId, String reason);
  AccountResponseDTO reapplyAccountByAccountId(Long accountId, AccountRequestDTO requestDTO);
  AccountResponseDTO getAccountByAccountId(Long accountId);
  List<AccountLogResponseDTO> getStatusLogsByAccountId(Long accountId);
}
