package com.app.maria.domain.admin.service;

import com.app.maria.domain.admin.dto.request.AdminLoginRequestDTO;
import com.app.maria.domain.admin.dto.response.AdminLoginResponseDTO;

public interface AdminService {

    AdminLoginResponseDTO login(AdminLoginRequestDTO request);

}
