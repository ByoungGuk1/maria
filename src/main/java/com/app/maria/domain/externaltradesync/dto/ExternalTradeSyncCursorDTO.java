package com.app.maria.domain.externaltradesync.dto;

import lombok.*;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter @ToString @Builder
public class ExternalTradeSyncCursorDTO {

    private Long cursorId;
    private Long customerId;
    private LocalDate lastSyncedTradeDate;
}
