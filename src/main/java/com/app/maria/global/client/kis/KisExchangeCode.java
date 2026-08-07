package com.app.maria.global.client.kis;

import com.app.maria.global.exception.UnsupportedExchangeException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KisExchangeCode {

    NASDAQ("NAS"),
    NYSE("NYS"),
    HKEX("HKS"),
    TSE("TSE");

    private final String kisCode;

    public static String fromMarket(String market) {
        if (market == null) {
            throw new UnsupportedExchangeException("거래소를 입력하세요.");
        }

        for (KisExchangeCode code : values()) {
            if (code.name().equalsIgnoreCase(market.trim())) {
                return code.getKisCode();
            }
        }

        throw new UnsupportedExchangeException("지원하지 않는 거래소입니다.");
    }

}
