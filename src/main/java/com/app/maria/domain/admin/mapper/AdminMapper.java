package com.app.maria.domain.admin.mapper;

import com.app.maria.domain.admin.dto.AdminUserDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface AdminMapper {

    public Optional<AdminUserDTO> selectAdminByLoginId(@Param("loginId") String loginId);

}
