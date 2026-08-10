package com.app.maria.domain.externaltradesync.dto.request;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class MydataTradeRequestDTO {

    private String ciHash;
    private LocalDate fromDate;
}
