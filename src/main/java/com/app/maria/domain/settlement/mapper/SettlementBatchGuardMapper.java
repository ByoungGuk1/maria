package com.app.maria.domain.settlement.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.Optional;

@Mapper
public interface SettlementBatchGuardMapper {

  int ensureGuard(LocalDate businessDate);

  Optional<LocalDate> selectGuardForUpdate(LocalDate businessDate);
}
