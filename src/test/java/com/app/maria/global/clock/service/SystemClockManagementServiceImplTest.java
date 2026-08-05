package com.app.maria.global.clock.service;

import com.app.maria.global.audit.dto.AuditLogDTO;
import com.app.maria.global.audit.exception.AuditLogInsertException;
import com.app.maria.global.audit.mapper.AuditLogMapper;
import com.app.maria.global.clock.dto.SystemClockDTO;
import com.app.maria.global.clock.exception.SystemClockNotInitializedException;
import com.app.maria.global.clock.exception.SystemClockUpdateException;
import com.app.maria.global.clock.mapper.SystemClockMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemClockManagementServiceImplTest {

    @Mock
    private SystemClockMapper systemClockMapper;

    @Mock
    private AuditLogMapper auditLogMapper;

    @InjectMocks
    private SystemClockManagementServiceImpl systemClockManagementService;

    @Test
    void 변경할_시간이_null이면_예외가_발생한다() {
        assertThatThrownBy(() ->
                systemClockManagementService.changeSystemTime(
                        1L,
                        null,
                        "DEMO_TIME_CHANGE"
                )
        )
                .isInstanceOf(SystemClockUpdateException.class)
                .hasMessage("변경할 업무시각은 필수입니다.");

        verifyNoInteractions(systemClockMapper, auditLogMapper);
    }

    @Test
    void 시스템_시계가_초기화되지_않으면_예외가_발생한다() {
        when(systemClockMapper.selectSystemClock())
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                systemClockManagementService.changeSystemTime(
                        1L,
                        LocalDateTime.of(2027, 8, 5, 9, 0),
                        "DEMO_TIME_CHANGE"
                )
        )
                .isInstanceOf(SystemClockNotInitializedException.class)
                .hasMessage("SYSTEM_CLOCK 데이터가 존재하지 않습니다.");

        verify(systemClockMapper).selectSystemClock();
        verify(systemClockMapper, never())
                .updateSystemClock(any(LocalDateTime.class));
        verifyNoInteractions(auditLogMapper);
    }

    @Test
    void 기존_시간과_같으면_변경과_감사로그_저장을_하지_않는다() {
        LocalDateTime currentDatetime =
                LocalDateTime.of(2026, 8, 5, 10, 0);

        SystemClockDTO currentClock =
                new SystemClockDTO(1L, currentDatetime, currentDatetime);

        when(systemClockMapper.selectSystemClock())
                .thenReturn(Optional.of(currentClock));

        systemClockManagementService.changeSystemTime(
                1L,
                currentDatetime,
                "DEMO_TIME_CHANGE"
        );

        verify(systemClockMapper).selectSystemClock();
        verify(systemClockMapper, never())
                .updateSystemClock(any(LocalDateTime.class));
        verifyNoInteractions(auditLogMapper);
    }

    @Test
    void 업무시각을_변경하고_감사로그를_저장한다() {
        Long adminId = 10L;
        LocalDateTime currentDatetime =
                LocalDateTime.of(2026, 8, 5, 10, 0);
        LocalDateTime newDatetime =
                LocalDateTime.of(2027, 8, 5, 9, 0);
        String reasonCode = "DEMO_TIME_CHANGE";

        SystemClockDTO currentClock =
                new SystemClockDTO(1L, currentDatetime, currentDatetime);

        when(systemClockMapper.selectSystemClock())
                .thenReturn(Optional.of(currentClock));
        when(systemClockMapper.updateSystemClock(newDatetime))
                .thenReturn(1);
        when(auditLogMapper.insertLog(any(AuditLogDTO.class)))
                .thenReturn(1);

        systemClockManagementService.changeSystemTime(
                adminId,
                newDatetime,
                reasonCode
        );

        ArgumentCaptor<AuditLogDTO> auditLogCaptor =
                ArgumentCaptor.forClass(AuditLogDTO.class);

        verify(auditLogMapper).insertLog(auditLogCaptor.capture());

        AuditLogDTO savedLog = auditLogCaptor.getValue();

        assertThat(savedLog.getAdminId()).isEqualTo(adminId);
        assertThat(savedLog.getTargetTable()).isEqualTo("SYSTEM_CLOCK");
        assertThat(savedLog.getTargetPk()).isEqualTo("1");
        assertThat(savedLog.getBeforeValue())
                .isEqualTo(currentDatetime.toString());
        assertThat(savedLog.getAfterValue())
                .isEqualTo(newDatetime.toString());
        assertThat(savedLog.getReasonCode()).isEqualTo(reasonCode);
        assertThat(savedLog.getProcessedAt())
                .isEqualTo(currentDatetime);

        InOrder callOrder =
                inOrder(systemClockMapper, auditLogMapper);

        callOrder.verify(systemClockMapper).selectSystemClock();
        callOrder.verify(systemClockMapper)
                .updateSystemClock(newDatetime);
        callOrder.verify(auditLogMapper)
                .insertLog(any(AuditLogDTO.class));
    }

    @Test
    void 시스템_시계_UPDATE가_실패하면_감사로그를_저장하지_않는다() {
        LocalDateTime currentDatetime =
                LocalDateTime.of(2026, 8, 5, 10, 0);
        LocalDateTime newDatetime =
                LocalDateTime.of(2027, 8, 5, 9, 0);

        when(systemClockMapper.selectSystemClock())
                .thenReturn(Optional.of(
                        new SystemClockDTO(1L, currentDatetime, currentDatetime)
                ));
        when(systemClockMapper.updateSystemClock(newDatetime))
                .thenReturn(0);

        assertThatThrownBy(() ->
                systemClockManagementService.changeSystemTime(
                        1L,
                        newDatetime,
                        "DEMO_TIME_CHANGE"
                )
        )
                .isInstanceOf(SystemClockUpdateException.class)
                .hasMessage("SYSTEM_CLOCK 변경에 실패했습니다.");

        verify(systemClockMapper)
                .updateSystemClock(newDatetime);
        verifyNoInteractions(auditLogMapper);
    }

    @Test
    void 감사로그_INSERT가_실패하면_예외가_발생한다() {
        LocalDateTime currentDatetime =
                LocalDateTime.of(2026, 8, 5, 10, 0);
        LocalDateTime newDatetime =
                LocalDateTime.of(2027, 8, 5, 9, 0);

        when(systemClockMapper.selectSystemClock())
                .thenReturn(Optional.of(
                        new SystemClockDTO(1L, currentDatetime, currentDatetime)
                ));
        when(systemClockMapper.updateSystemClock(newDatetime))
                .thenReturn(1);
        when(auditLogMapper.insertLog(any(AuditLogDTO.class)))
                .thenReturn(0);

        assertThatThrownBy(() ->
                systemClockManagementService.changeSystemTime(
                        1L,
                        newDatetime,
                        "DEMO_TIME_CHANGE"
                )
        )
                .isInstanceOf(AuditLogInsertException.class)
                .hasMessage("AUDIT_LOG 저장에 실패했습니다.");

        verify(systemClockMapper)
                .updateSystemClock(newDatetime);
        verify(auditLogMapper)
                .insertLog(any(AuditLogDTO.class));
    }
}
