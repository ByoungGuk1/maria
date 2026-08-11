package com.app.maria.domain.externaltradesync.dto;

import java.time.LocalDate;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class ExternalTradeSyncCursorDTO {

    private Long cursorId;
    private Long customerId;
    private LocalDate lastSyncedTradeDate;
}
