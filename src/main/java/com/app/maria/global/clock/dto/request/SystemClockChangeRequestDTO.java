package com.app.maria.global.clock.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class SystemClockChangeRequestDTO {
    @NotNull
    private LocalDateTime newDatetime;
    @NotBlank
    private String reasonCode;
}
