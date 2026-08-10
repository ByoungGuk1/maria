package com.app.maria.domain.targetproduct.mapper;

import com.app.maria.domain.targetproduct.dto.TargetProductJudgementDTO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TargetProductMapper {

    void insertJudgement(TargetProductJudgementDTO judgementDTO);
    boolean existsByMydataTradeId(Long mydataTradeId);
}
