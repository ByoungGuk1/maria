package com.app.maria.domain.customer.api;

import com.app.maria.domain.customer.dto.request.CustomerNameRequestDTO;
import com.app.maria.domain.customer.dto.response.CustomerResponseDTO;
import com.app.maria.domain.customer.service.CustomerService;
import com.app.maria.global.response.ApiResponseDTO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customer")
@PreAuthorize("hasAnyRole('ADMIN', 'SETTLEMENT', 'REVIEWER', 'VIEWER')")
public class CustomerApi {
    private final CustomerService customerService;

    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<CustomerResponseDTO>>> getAllCustomers() {
        return ResponseEntity.ok(ApiResponseDTO.of("사용자 정보 전체 조회", customerService.findAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDTO<List<CustomerResponseDTO>>> getCustomersByName(
            @Valid @RequestBody CustomerNameRequestDTO customerNameRequestDTO) {
        return ResponseEntity.ok(
                ApiResponseDTO.of(
                        "사용자 정보 전체 조회", customerService.findByName(customerNameRequestDTO)));
    }
}
