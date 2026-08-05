package com.app.maria.global.clock.service;


import java.time.LocalDateTime;

public interface SystemClockManagementService {


    void changeSystemTime(Long adminId, LocalDateTime newDatetime, String reasonCode);
}
