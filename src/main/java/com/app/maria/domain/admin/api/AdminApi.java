package com.app.maria.domain.admin.api;

import com.app.maria.domain.admin.dto.request.AdminLoginRequestDTO;
import com.app.maria.domain.admin.dto.request.AdminRoleUpdateRequestDTO;
import com.app.maria.domain.admin.dto.response.AdminLoginResponseDTO;
import com.app.maria.domain.admin.service.AdminService;
import com.app.maria.global.response.ApiResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/admin")
public class AdminApi {

    private final AdminService adminService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponseDTO<AdminLoginResponseDTO>> login(@Valid @RequestBody AdminLoginRequestDTO request) {
        AdminLoginResponseDTO response = adminService.login(request);
        return ResponseEntity.ok(ApiResponseDTO.of("로그인 성공", response));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{adminId}/role")
    public ResponseEntity<ApiResponseDTO<Void>> updateRole(@PathVariable Long adminId, @Valid @RequestBody AdminRoleUpdateRequestDTO request) {
        adminService.updateRole(adminId, request.getRole());
        return ResponseEntity.ok(ApiResponseDTO.of("역할이 변경되었습니다."));
    }


}
