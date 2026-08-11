package com.app.maria.domain.sellorder.mapper;

import java.math.BigDecimal;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SellLimitMapper {

    Optional<BigDecimal> selectAccountLimitForUpdate(@Param("accountId") Long accountId);

    BigDecimal sumFinalizedExchangeAmount(@Param("accountId") Long accountId);

    Optional<String> selectCiHashByAccountId(@Param("accountId") Long accountId);

    BigDecimal sumPendingSellOrderAmount(@Param("accountId") Long accountId);
}
