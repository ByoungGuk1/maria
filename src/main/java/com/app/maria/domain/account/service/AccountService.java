package com.app.maria.domain.account.service;

import com.app.maria.domain.account.dto.request.AccountRequestDTO;
import com.app.maria.domain.account.dto.response.AccountLogResponseDTO;
import com.app.maria.domain.account.dto.response.AccountResponseDTO;

import java.util.List;

public interface AccountService {
  List<AccountResponseDTO> findAll();
  AccountResponseDTO applyAccount(AccountRequestDTO accountRequestDTO);
  AccountResponseDTO openAccount(Long accountId);
  AccountResponseDTO rejectAccount(Long accountId);
  AccountResponseDTO reapplyAccount(Long accountId, AccountRequestDTO accountRequestDTO);
  AccountResponseDTO getAccount(Long accountId);
  List<AccountLogResponseDTO> getStatusLogList(Long accountId);
}
