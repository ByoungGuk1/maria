package com.app.maria.domain.account.api;

import com.app.maria.domain.account.dto.request.AccountRequestDTO;
import com.app.maria.domain.account.dto.response.AccountLogResponseDTO;
import com.app.maria.domain.account.dto.response.AccountResponseDTO;
import com.app.maria.domain.account.service.AccountService;
import com.app.maria.global.response.ApiResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customer/account")
public class AccountForCustomerAPI {
  private final AccountService accountService;

  @PostMapping("/applications")
  public ResponseEntity<?> apply(@RequestBody AccountRequestDTO requestDTO) {
    AccountResponseDTO account = accountService.applyAccount(requestDTO);
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(
            ApiResponseDTO.of(
                "계좌 개설 신청이 완료되었습니다.",
                account
            )
        );
  }

  @PostMapping("/{accountId}/reapply")
  public ResponseEntity<ApiResponseDTO<AccountResponseDTO>> reapply(@PathVariable Long accountId, @RequestBody AccountRequestDTO accountRequestDTO) {
    AccountResponseDTO account = accountService.reapplyAccount(accountId, accountRequestDTO);

    return ResponseEntity.ok(
        ApiResponseDTO.of(
            "계좌 재신청이 완료되었습니다.",
            account
        )
    );
  }

  @GetMapping("/{accountId}")
  public ResponseEntity<ApiResponseDTO<AccountResponseDTO>> getAccount(@PathVariable Long accountId) {
    AccountResponseDTO account = accountService.getAccount(accountId);

    return ResponseEntity.ok(
        ApiResponseDTO.of(
            "계좌 조회에 성공했습니다.",
            account
        )
    );
  }

  @GetMapping("/{accountId}/status-logs")
  public ResponseEntity<?> getStatusLogs(@PathVariable Long accountId) {
    List<AccountLogResponseDTO> statusLogs = accountService.getStatusLogList(accountId);
    return ResponseEntity.ok(
        ApiResponseDTO.of(
            "계좌 상태 이력 조회에 성공했습니다.",
            statusLogs
        )
    );
  }
}
