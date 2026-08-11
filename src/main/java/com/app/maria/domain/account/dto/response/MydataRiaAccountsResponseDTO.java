package com.app.maria.domain.account.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@ToString
public class MydataRiaAccountsResponseDTO {
    private String message;
    private List<MyDataAccountResponse> data;

    @Getter
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MyDataAccountResponse {
        private Long mydataAccountId;
        private String ciHash;
        private String brokerName;
        private BigDecimal riaLimit;
        private BigDecimal riaCumulativeSell;
    }
}
