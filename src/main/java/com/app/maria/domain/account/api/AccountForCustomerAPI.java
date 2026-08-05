package com.app.maria.domain.account.api;

import com.app.maria.domain.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customer/account")
public class AccountForCustomerAPI {
  private final AccountService accountService;

}
