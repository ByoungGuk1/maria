package com.app.maria.domain.admin.service;

import com.app.maria.domain.admin.dto.AdminUserDTO;
import com.app.maria.domain.admin.dto.request.AdminLoginRequestDTO;
import com.app.maria.domain.admin.dto.response.AdminLoginResponseDTO;
import com.app.maria.domain.admin.exception.AdminException;
import com.app.maria.domain.admin.mapper.AdminMapper;
import com.app.maria.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class AdminServiceImpl implements AdminService {

    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional(readOnly = true)
    public AdminLoginResponseDTO login(AdminLoginRequestDTO request) {
        AdminUserDTO admin = adminMapper.selectAdminByLoginId(request.getLoginId())
                .orElseThrow(() -> new AdminException("아이디 또는 비밀번호가 일치하지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            throw new AdminException("아이디 또는 비밀번호가 일치하지 않습니다.");
        }

        String accessToken = jwtTokenProvider.createAccessToken(admin.getAdminId(), admin.getLoginId(), admin.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(admin.getAdminId());

        return AdminLoginResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

}
