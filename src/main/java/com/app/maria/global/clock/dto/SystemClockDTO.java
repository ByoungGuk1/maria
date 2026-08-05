package com.app.maria.global.clock.dto;

import lombok.*;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class SystemClockDTO {
    private Long clockId;
    private LocalDateTime currentDatetime;
    private LocalDateTime referenceRealDatetime;
}

