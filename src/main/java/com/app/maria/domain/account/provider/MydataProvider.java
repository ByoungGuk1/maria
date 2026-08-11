package com.app.maria.domain.account.provider;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.dto.response.MydataRiaAccountsResponseDTO;
import java.math.BigDecimal;
import org.springframework.http.HttpStatusCode;

public interface MydataProvider {
    BigDecimal getExternalConfiguredLimit(String ciHash);

    MydataRiaAccountsResponseDTO getRiaAccounts(String ciHash);

    boolean hasOwnRiaAccount(String ciHash);

    HttpStatusCode createRiaAccount(String ciHash, AccountDTO account);

    HttpStatusCode updateRiaLimit(String ciHash, AccountDTO account);
}
