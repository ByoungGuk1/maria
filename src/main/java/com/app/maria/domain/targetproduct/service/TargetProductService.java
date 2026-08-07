package com.app.maria.domain.targetproduct.service;

import com.app.maria.domain.targetproduct.dto.TargetProductJudgementDTO;

public interface TargetProductService {

    public TargetProductJudgementDTO judge(Long mydataTradeId, String stockType, String fundCode);
}
