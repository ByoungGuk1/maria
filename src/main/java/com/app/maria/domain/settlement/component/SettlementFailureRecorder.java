package com.app.maria.domain.settlement.component;

import com.app.maria.domain.settlement.dto.SettlementItemDTO;
import com.app.maria.domain.settlement.exception.SettlementStateConflictException;
import com.app.maria.domain.settlement.mapper.SettlementItemMapper;
import com.app.maria.domain.settlement.type.SettlementItemResult;
import com.app.maria.global.clock.service.BusinessClockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SettlementFailureRecorder {

  private final SettlementItemMapper settlementItemMapper;
  private final BusinessClockService systemClock;

  @Transactional(transactionManager = "transactionManager", propagation = Propagation.REQUIRES_NEW)
  public void markFailed(Long itemId) {
    if (itemId == null) {
      throw new SettlementStateConflictException("기록 대상 Item 미확인");
    }

    SettlementItemDTO item = SettlementItemDTO.builder()
        .itemId(itemId)
        .result(SettlementItemResult.FAILED)
        .processedAt(systemClock.now())
        .build();

    if (settlementItemMapper.updateItemResult(item) != 1) {
      throw new SettlementStateConflictException("정산 Item 실패 기록 실패");
    }
  }
}
