package com.app.maria.domain.domestic.type;

public enum Status {
    HOLDING,         // 보유중
    PARTIALLY_SOLD,  // 일부매도
    FULLY_SOLD,      // 전량매도
    RESTRICTED,      // 거래제한
    SUSPENDED,       // 거래정지
    CLOSED           // 보유종료
}
