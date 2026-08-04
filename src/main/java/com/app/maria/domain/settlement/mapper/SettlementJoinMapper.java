package com.app.maria.domain.settlement.mapper;

import com.app.maria.domain.settlement.dto.SettlementJoinDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface SettlementJoinMapper {

  Optional<SettlementJoinDTO> selectTargetByItemId(SettlementJoinDTO settlementJoinDTO);

  Optional<SettlementJoinDTO> selectItemDetail(SettlementJoinDTO settlementJoinDTO);
}
