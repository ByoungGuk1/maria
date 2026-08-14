package com.app.maria.domain.tax.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.app.maria.domain.tax.dto.TaxSnapshotDTO;
import com.app.maria.domain.tax.mapper.TaxSnapshotMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

@ExtendWith(MockitoExtension.class)
class TaxSnapshotWriterTest {

    @Mock private TaxSnapshotMapper taxSnapshotMapper;

    private TaxSnapshotWriter writer;

    @BeforeEach
    void setUp() {
        writer = new TaxSnapshotWriter(taxSnapshotMapper);
    }

    @Test
    @DisplayName("chunk에 담긴 스냅샷을 한 번에 upsert한다")
    void write_chunk을_그대로_upsert에_넘긴다() {
        TaxSnapshotDTO a = TaxSnapshotDTO.builder().accountId(1L).build();
        TaxSnapshotDTO b = TaxSnapshotDTO.builder().accountId(2L).build();

        writer.write(new Chunk<>(List.of(a, b)));

        ArgumentCaptor<List<TaxSnapshotDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(taxSnapshotMapper).upsertSnapshots(captor.capture());
        assertThat(captor.getValue()).containsExactly(a, b);
    }

    @Test
    @DisplayName("빈 chunk는 upsert를 호출하지 않는다")
    void write_빈chunk은_호출하지_않는다() {
        writer.write(new Chunk<>());

        verify(taxSnapshotMapper, never()).upsertSnapshots(org.mockito.ArgumentMatchers.any());
    }
}
