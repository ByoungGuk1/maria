package com.app.maria.domain.tax.batch;

import com.app.maria.domain.tax.dto.TaxSnapshotDTO;
import com.app.maria.domain.tax.mapper.TaxSnapshotMapper;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaxSnapshotWriter implements ItemWriter<TaxSnapshotDTO> {
    private final TaxSnapshotMapper taxSnapshotMapper;

    @Override
    public void write(Chunk<? extends TaxSnapshotDTO> chunk) {
        if (chunk.isEmpty()) {
            return;
        }
        taxSnapshotMapper.upsertSnapshots(new ArrayList<>(chunk.getItems()));
    }
}
