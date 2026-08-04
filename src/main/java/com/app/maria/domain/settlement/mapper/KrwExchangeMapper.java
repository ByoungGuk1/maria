package com.app.maria.domain.settlement.mapper;

import com.app.maria.domain.settlement.dto.KrwExchangeDTO;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.util.Optional;

@Mapper
public interface KrwExchangeMapper {

  Optional<KrwExchangeDTO> selectExchangeById(Long exchangeId);

  Optional<KrwExchangeDTO> selectExchangeByIdForUpdate(Long exchangeId);

  Optional<BigDecimal> selectAccountAmountForUpdate(Long accountId);

  int finalizeExchange(KrwExchangeDTO krwExchangeDTO);

  int increaseAccountAmount(KrwExchangeDTO krwExchangeDTO);

  int insertLeftAmount(KrwExchangeDTO krwExchangeDTO);
}
