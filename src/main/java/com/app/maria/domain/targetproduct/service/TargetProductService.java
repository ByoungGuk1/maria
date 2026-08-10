package com.app.maria.domain.targetproduct.service;

import com.app.maria.domain.externaltradesync.dto.response.MydataTradeResponseDTO;
import com.app.maria.domain.targetproduct.dto.TargetProductJudgementDTO;

public interface TargetProductService {

    public TargetProductJudgementDTO judge(MydataTradeResponseDTO trade);
}
