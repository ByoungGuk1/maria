package com.app.maria.domain.account.service;

import com.app.maria.domain.account.mapper.AccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
  private final AccountMapper accountMapper;
}
