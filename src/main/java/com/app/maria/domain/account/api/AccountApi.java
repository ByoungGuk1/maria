package com.app.maria.domain.account.api;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.dto.response.AccountResponseDTO;
import com.app.maria.domain.account.service.AccountService;
import com.app.maria.global.response.ApiResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/account")
public class AccountApi {

    private final AccountService accountService;
}
