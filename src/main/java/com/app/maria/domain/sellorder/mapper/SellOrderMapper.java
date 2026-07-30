package com.app.maria.domain.sellorder.mapper;

import com.app.maria.domain.sellorder.dto.response.SellOrderResponseDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface SellOrderMapper {

    public int insertSellOrder(SellOrderResponseDTO dto);
    public Optional<SellOrderResponseDTO> selectSellOrderById(@Param("orderId") Long orderId);

}
