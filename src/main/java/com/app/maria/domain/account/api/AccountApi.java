package com.app.maria.domain.account.api;

import com.app.maria.domain.account.dto.response.AccountResponseDTO;
import com.app.maria.domain.account.service.AccountService;
import com.app.maria.global.response.ApiResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/account")
public class AccountApi {

    private final AccountService accountService;

    @GetMapping("/list")
    public ResponseEntity<?> getAccountList(){
        return ResponseEntity.ok(ApiResponseDTO.of("계좌 정보 전체 조회", accountService.findAll()));
    }


    @PostMapping("/{accountId}/approve")
    public ResponseEntity<ApiResponseDTO<AccountResponseDTO>> approve(@PathVariable Long accountId){
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponseDTO.of("계좌 승인", accountService.openAccount(accountId)));
    }

    @PostMapping("/{accountId}/reject")
    public ResponseEntity<ApiResponseDTO<AccountResponseDTO>> reject(@PathVariable Long accountId) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponseDTO.of("계좌 거절", accountService.rejectAccount(accountId)));
    }
}
