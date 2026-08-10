package com.app.maria.domain.externaltradesync.mapper;

import com.app.maria.domain.externaltradesync.dto.ExternalTradeSyncCursorDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface ExternalTradeSyncCursorMapper {

    Optional<ExternalTradeSyncCursorDTO> selectByCustomerId(Long customerId);
    void upsertCursor(ExternalTradeSyncCursorDTO cursor);
}
