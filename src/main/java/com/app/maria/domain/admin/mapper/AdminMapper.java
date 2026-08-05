package com.app.maria.domain.admin.mapper;

import com.app.maria.domain.admin.dto.AdminUserDTO;
import com.app.maria.domain.admin.type.AdminRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface AdminMapper {

    public Optional<AdminUserDTO> selectAdminByLoginId(@Param("loginId") String loginId);
    public Optional<AdminUserDTO> selectAdminByAdminId(@Param("adminId") Long adminId);
    public void updateRole(@Param("adminId") Long adminId, @Param("role") AdminRole role);

}
