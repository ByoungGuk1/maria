package com.app.maria.domain.admin.service;

import com.app.maria.domain.admin.dto.request.AdminLoginRequestDTO;
import com.app.maria.domain.admin.dto.response.AdminLoginResponseDTO;
import com.app.maria.domain.admin.type.AdminRole;

public interface AdminService {

    AdminLoginResponseDTO login(AdminLoginRequestDTO request);
    void updateRole(Long adminId, AdminRole newRole);
    AdminLoginResponseDTO refresh(String refreshToken);

}
