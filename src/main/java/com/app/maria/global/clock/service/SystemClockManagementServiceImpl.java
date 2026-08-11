package com.app.maria.global.clock.service;

import com.app.maria.global.audit.dto.AuditLogDTO;
import com.app.maria.global.audit.exception.AuditLogInsertException;
import com.app.maria.global.audit.mapper.AuditLogMapper;
import com.app.maria.global.clock.dto.SystemClockDTO;
import com.app.maria.global.clock.dto.request.SystemClockChangeRequestDTO;
import com.app.maria.global.clock.exception.SystemClockNotInitializedException;
import com.app.maria.global.clock.exception.SystemClockUpdateException;
import com.app.maria.global.clock.mapper.SystemClockMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemClockManagementServiceImpl implements SystemClockManagementService {

    private final SystemClockMapper systemClockMapper;
    private final AuditLogMapper auditLogMapper;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public LocalDateTime changeSystemTime(Long adminId, SystemClockChangeRequestDTO requestDTO) {
        LocalDateTime newDatetime = requestDTO.getNewDatetime();
        String reasonCode = requestDTO.getReasonCode();

        SystemClockDTO currentClock =
                systemClockMapper
                        .selectSystemClock()
                        .orElseThrow(
                                () ->
                                        new SystemClockNotInitializedException(
                                                "SYSTEM_CLOCK 데이터가 존재하지 않습니다."));
        if (currentClock.getCurrentDatetime().equals(newDatetime)) {
            return currentClock.getCurrentDatetime();
        }

        int updatedRows =
                systemClockMapper.updateSystemClock(
                        newDatetime, currentClock.getReferenceRealDatetime());

        if (updatedRows != 1) {
            throw new SystemClockUpdateException("다른 관리자가 업무시각을 먼저 변경했습니다. 다시 조회해 주세요.");
        }

        AuditLogDTO auditLog =
                AuditLogDTO.builder()
                        .adminId(adminId)
                        .targetTable("SYSTEM_CLOCK")
                        .targetPk("1")
                        .beforeValue(currentClock.getCurrentDatetime().toString())
                        .afterValue(newDatetime.toString())
                        .reasonCode(reasonCode)
                        .build();
        int insertedRows = auditLogMapper.insertLog(auditLog);

        if (insertedRows != 1) {
            throw new AuditLogInsertException("AUDIT_LOG 저장에 실패했습니다.");
        }

        return newDatetime;
    }
}
