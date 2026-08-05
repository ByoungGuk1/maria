package com.app.maria.global.clock.service;

import com.app.maria.global.audit.dto.AuditLogDTO;
import com.app.maria.global.audit.exception.AuditLogInsertException;
import com.app.maria.global.audit.mapper.AuditLogMapper;
import com.app.maria.global.clock.dto.SystemClockDTO;
import com.app.maria.global.clock.exception.SystemClockNotInitializedException;
import com.app.maria.global.clock.exception.SystemClockUpdateException;
import com.app.maria.global.clock.mapper.SystemClockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SystemClockManagementServiceImpl implements SystemClockManagementService {

    private final SystemClockMapper systemClockMapper;
    private final AuditLogMapper auditLogMapper;



    @Transactional(rollbackFor = Exception.class)
    @Override
    public void changeSystemTime(Long adminId,LocalDateTime newDatetime, String reasonCode){

        if(newDatetime ==null){
            throw new SystemClockUpdateException(
                    "변경할 업무시각은 필수입니다."
            );
        }

        SystemClockDTO currentClock = systemClockMapper.selectSystemClock()
                .orElseThrow(() -> new SystemClockNotInitializedException("SYSTEM_CLOCK 데이터가 존재하지 않습니다."));
        if (currentClock.getCurrentDatetime().equals(newDatetime)){
            return;
        }

        int updatedRows = systemClockMapper.updateSystemClock(newDatetime);

        if(updatedRows !=1){
            throw new SystemClockUpdateException(
                    "SYSTEM_CLOCK 변경에 실패했습니다."
            );
        }

        AuditLogDTO auditLog = AuditLogDTO.builder()
                .adminId(adminId)
                .targetTable("SYSTEM_CLOCK")
                .targetPk("1")
                .beforeValue(currentClock.getCurrentDatetime().toString())
                .afterValue(newDatetime.toString())
                .reasonCode(reasonCode)
                .processedAt(currentClock.getCurrentDatetime())
                .build();
        int insertedRows = auditLogMapper.insertLog(auditLog);

        if(insertedRows != 1){
            throw new AuditLogInsertException(
                    "AUDIT_LOG 저장에 실패했습니다."
            );
        }



    }
}
