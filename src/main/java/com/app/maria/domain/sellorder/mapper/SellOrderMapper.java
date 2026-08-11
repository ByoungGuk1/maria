package com.app.maria.domain.sellorder.mapper;

import com.app.maria.domain.sellorder.dto.SellOrderDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface SellOrderMapper {

    int insertSellOrder(SellOrderDTO dto);

    Optional<SellOrderDTO> selectSellOrderById(@Param("orderId") Long orderId);

    List<SellOrderDTO> selectSellOrdersByAccountId(@Param("accountId") Long accountId);

    BigDecimal sumSellAmountBetween(LocalDateTime start, LocalDateTime end);

}
