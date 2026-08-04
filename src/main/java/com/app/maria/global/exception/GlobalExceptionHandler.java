package com.app.maria.global.exception;

import com.app.maria.domain.inbound.exception.InboundException;
import com.app.maria.domain.inbound.exception.InboundNotFoundException;
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
  public ResponseEntity<ApiResponseDTO<String>> handleException(MemberException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponseDTO.of(e.getMessage()));
  }
  @ExceptionHandler(MemberNotFoundException.class)
  public ResponseEntity<ApiResponseDTO<Void>>handleMemberNotFound(MemberNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponseDTO.of(e.getMessage()));
  }

  @ExceptionHandler(InboundException.class)
  public ResponseEntity<ApiResponseDTO<Void>> handleInboundException(InboundException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponseDTO.of(e.getMessage()));
  }
  @ExceptionHandler(InboundNotFoundException.class)
  public ResponseEntity<ApiResponseDTO<Void>> handleInboundNotFoundException(InboundNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponseDTO.of(e.getMessage()));
  }
}
