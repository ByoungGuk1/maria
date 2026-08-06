package com.app.maria.global.audit.mapper;

import com.app.maria.global.audit.dto.AuditLogDTO;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface AuditLogMapper {
    public int insertLog(AuditLogDTO auditLogDTO);
}
