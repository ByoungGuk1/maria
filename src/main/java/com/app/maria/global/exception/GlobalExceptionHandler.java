package com.app.maria.global.exception;

import com.app.maria.domain.account.exception.AccountException;
import com.app.maria.domain.account.exception.AccountNotFoundException;
import com.app.maria.domain.account.exception.DuplicateAccountException;
import com.app.maria.domain.account.exception.InvalidAccountRequestException;
import com.app.maria.domain.member.exception.MemberException;
import com.app.maria.domain.member.exception.MemberNotFoundException;
import com.app.maria.global.response.ApiResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(MemberException.class)
  public ResponseEntity<ApiResponseDTO<Void>> handleException(MemberException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponseDTO.of(e.getMessage()));
  }
  @ExceptionHandler(MemberNotFoundException.class)
  public ResponseEntity<ApiResponseDTO<Void>>handleMemberNotFound(MemberNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponseDTO.of(e.getMessage()));
  }

  //Account
  @ExceptionHandler(AccountException.class)
  public ResponseEntity<ApiResponseDTO<Void>>handleAccountException(AccountException e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponseDTO.of(e.getMessage()));
  }
  @ExceptionHandler(AccountNotFoundException.class)
  public ResponseEntity<ApiResponseDTO<Void>>handleAccountNotFoundException(AccountException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponseDTO.of(e.getMessage()));
  }
  @ExceptionHandler(DuplicateAccountException.class)
  public ResponseEntity<ApiResponseDTO<Void>>handleDuplicateAccountException(AccountException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponseDTO.of(e.getMessage()));
  }
  @ExceptionHandler(InvalidAccountRequestException.class)
  public ResponseEntity<ApiResponseDTO<Void>>handleInvalidAccountRequestException(AccountException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponseDTO.of(e.getMessage()));
  }
}
