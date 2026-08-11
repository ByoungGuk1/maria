package com.app.maria.global.clock.service;

import com.app.maria.global.clock.dto.SystemClockDTO;
import com.app.maria.global.clock.exception.SystemClockNotInitializedException;
import com.app.maria.global.clock.mapper.SystemClockMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BusinessClockServiceImpl implements BusinessClockService {

    private final SystemClockMapper systemClockMapper;

    @Override
    public LocalDateTime now() {
        SystemClockDTO systemClock =
                systemClockMapper
                        .selectSystemClock()
                        .orElseThrow(
                                () ->
                                        new SystemClockNotInitializedException(
                                                "SYSTEM_CLOCK 데이터가 존재하지 않습니다."));
        return systemClock.getCurrentDatetime();
    }
}
