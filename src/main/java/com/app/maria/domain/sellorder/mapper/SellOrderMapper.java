package com.app.maria.domain.sellorder.mapper;

import com.app.maria.domain.sellorder.dto.SellOrderDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface SellOrderMapper {

    public int insertSellOrder(SellOrderDTO dto);
    public Optional<SellOrderDTO> selectSellOrderById(@Param("orderId") Long orderId);

}
