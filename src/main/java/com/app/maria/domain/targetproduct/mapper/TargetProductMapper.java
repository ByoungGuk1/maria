package com.app.maria.domain.targetproduct.mapper;

import com.app.maria.domain.targetproduct.dto.TargetProductJudgementDTO;
import com.app.maria.domain.targetproduct.dto.TargetProductJudgementListDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TargetProductMapper {

    void insertJudgement(TargetProductJudgementDTO judgementDTO);

    boolean existsByMydataTradeId(Long mydataTradeId);

    List<TargetProductJudgementListDTO> selectRecentJudgements();
}
