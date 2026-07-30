package com.app.maria.domain.account.service;

import com.app.maria.domain.account.dto.AccountDTO;

import java.util.List;

public interface AccountService {
  List<AccountDTO> findAll();
  AccountDTO applyAccount(AccountDTO accountDTO);
  AccountDTO openAccount(AccountDTO accountDTO);
  AccountDTO rejectAccount(AccountDTO accountDTO);
  AccountDTO reapplyAccount(AccountDTO accountDTO);
}
