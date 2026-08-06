package com.app.maria.domain.account.api;

import com.app.maria.domain.account.dto.request.AccountRequestDTO;
import com.app.maria.domain.account.dto.request.AccountReapplyRequestDTO;
import com.app.maria.domain.account.dto.request.ReasonRequestDTO;
import com.app.maria.domain.account.dto.response.AccountResponseDTO;
import com.app.maria.domain.account.service.AccountService;
import com.app.maria.global.response.ApiResponseDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/account")
public class AccountApi {

    private final AccountService accountService;

    @GetMapping("/list")
    public ResponseEntity<?> getAccountList(){
        return ResponseEntity.ok(ApiResponseDTO.of("계좌 정보 전체 조회", accountService.findAll()));
    }

    @GetMapping("/available-limit")
    public ResponseEntity<ApiResponseDTO<BigDecimal>> getAvailableLimit(@RequestParam @Positive(message = "사용자 ID는 0보다 커야 합니다.") Long customerId) {
        return ResponseEntity.ok(ApiResponseDTO.of("RIA 설정 가능 최대 한도 조회", accountService.getAvailableLimit(customerId)));
    }

    @PostMapping("/applications")
    public ResponseEntity<ApiResponseDTO<AccountResponseDTO>> apply(@Valid @RequestBody AccountRequestDTO requestDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDTO.of("계좌 개설 신청 처리 완료", accountService.applyAccount(requestDTO)));
    }

    @PostMapping("/{accountId}/approve")
    public ResponseEntity<ApiResponseDTO<AccountResponseDTO>> approve(@PathVariable @Positive(message = "계좌 ID는 0보다 커야 합니다.") Long accountId){
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponseDTO.of("계좌 승인", accountService.approveAccount(accountId)));
    }

    @PostMapping("/{accountId}/reject")
    public ResponseEntity<ApiResponseDTO<AccountResponseDTO>> reject(@PathVariable @Positive(message = "계좌 ID는 0보다 커야 합니다.") Long accountId, @Valid @RequestBody ReasonRequestDTO reasonRequestDTO
    ) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponseDTO.of("계좌 반려", accountService.rejectAccount(accountId, reasonRequestDTO.getReason())));
    }

    @PostMapping("/{accountId}/reapply")
    public ResponseEntity<ApiResponseDTO<AccountResponseDTO>> reapply(@PathVariable @Positive(message = "계좌 ID는 0보다 커야 합니다.") Long accountId, @Valid @RequestBody AccountReapplyRequestDTO accountRequestDTO) {
        return ResponseEntity.ok(ApiResponseDTO.of("계좌 재신청", accountService.reapplyAccountByAccountId(accountId, accountRequestDTO)));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<ApiResponseDTO<AccountResponseDTO>> getAccount(@PathVariable @Positive(message = "계좌 ID는 0보다 커야 합니다.") Long accountId) {
        return ResponseEntity.ok(ApiResponseDTO.of("계좌 조회", accountService.getAccountByAccountId(accountId)));
    }

    @GetMapping("/{accountId}/status-logs")
    public ResponseEntity<?> getStatusLogs(@PathVariable @Positive(message = "계좌 ID는 0보다 커야 합니다.") Long accountId) {
        return ResponseEntity.ok(ApiResponseDTO.of("계좌 상태 이력 조회", accountService.getStatusLogsByAccountId(accountId)));
    }

    @PostMapping("/{accountId}/override")
    public ResponseEntity<ApiResponseDTO<AccountResponseDTO>> override(@PathVariable @Positive(message = "계좌 ID는 0보다 커야 합니다.") Long accountId, @Valid @RequestBody ReasonRequestDTO reasonRequestDTO) {
        return ResponseEntity.ok(ApiResponseDTO.of("계좌 상태 오버라이드", accountService.overrideAccount(accountId, reasonRequestDTO.getReason())));
    }
}
