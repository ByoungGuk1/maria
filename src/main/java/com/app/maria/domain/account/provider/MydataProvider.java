package com.app.maria.domain.account.provider;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.dto.response.MydataRiaAccountsResponseDTO;
import org.springframework.http.HttpStatusCode;

public interface MydataProvider {
  MydataRiaAccountsResponseDTO getRiaAccounts(String ciHash);
  HttpStatusCode saveRiaAccounts(String ciHash, AccountDTO account);
}
