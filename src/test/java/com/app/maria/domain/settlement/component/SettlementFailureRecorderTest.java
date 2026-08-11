package com.app.maria.domain.settlement.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.maria.domain.settlement.dto.SettlementItemDTO;
import com.app.maria.domain.settlement.exception.SettlementStateConflictException;
import com.app.maria.domain.settlement.mapper.SettlementItemMapper;
import com.app.maria.domain.settlement.type.SettlementItemResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementFailureRecorderTest {

    @Mock private SettlementItemMapper settlementItemMapper;

    @Test
    void recordsFailedItemInIndependentComponent() {
        when(settlementItemMapper.updateItemResult(any(SettlementItemDTO.class))).thenReturn(1);
        SettlementFailureRecorder recorder = new SettlementFailureRecorder(settlementItemMapper);

        recorder.markFailed(10L);

        ArgumentCaptor<SettlementItemDTO> captor = ArgumentCaptor.forClass(SettlementItemDTO.class);
        verify(settlementItemMapper).updateItemResult(captor.capture());
        assertThat(captor.getValue().getItemId()).isEqualTo(10L);
        assertThat(captor.getValue().getResult()).isEqualTo(SettlementItemResult.FAILED);
        assertThat(captor.getValue().getProcessedAt()).isNotNull();
    }

    @Test
    void rejectsWhenFailureUpdateDoesNotAffectOneRow() {
        when(settlementItemMapper.updateItemResult(any(SettlementItemDTO.class))).thenReturn(0);
        SettlementFailureRecorder recorder = new SettlementFailureRecorder(settlementItemMapper);

        assertThatThrownBy(() -> recorder.markFailed(10L))
                .isInstanceOf(SettlementStateConflictException.class);
    }

    @Test
    void rejectsNullItemIdWithoutMapperCall() {
        SettlementFailureRecorder recorder = new SettlementFailureRecorder(settlementItemMapper);

        assertThatThrownBy(() -> recorder.markFailed(null))
                .isInstanceOf(SettlementStateConflictException.class);

        verify(settlementItemMapper, never()).updateItemResult(any());
    }
}
