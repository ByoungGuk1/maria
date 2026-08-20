package com.app.maria.domain.externaltradesync.service;

import com.app.maria.domain.customer.dto.CustomerCiHashDTO;
import com.app.maria.domain.customer.mapper.CustomerMapper;
import com.app.maria.domain.externaltradesync.dto.ExternalTradeSyncCursorDTO;
import com.app.maria.domain.externaltradesync.dto.request.MydataTradeRequestDTO;
import com.app.maria.domain.externaltradesync.dto.response.ExternalTradeSyncResultDTO;
import com.app.maria.domain.externaltradesync.dto.response.MydataTradeResponseDTO;
import com.app.maria.domain.externaltradesync.mapper.ExternalTradeSyncCursorMapper;
import com.app.maria.domain.targetproduct.dto.TargetProductJudgementDTO;
import com.app.maria.domain.targetproduct.mapper.TargetProductMapper;
import com.app.maria.domain.targetproduct.service.TargetProductService;
import com.app.maria.global.client.mydatatrade.MydataTradeClient;
import com.app.maria.global.clock.service.BusinessClockService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalTradeSyncServiceImpl implements ExternalTradeSyncService {

    private final CustomerMapper customerMapper;
    private final ExternalTradeSyncCursorMapper cursorMapper;
    private final MydataTradeClient mydataTradeClient;
    private final TargetProductMapper targetProductMapper;
    private final TargetProductService targetProductService;
    private final BusinessClockService businessClockService;

    private enum JudgeOutcome {
        NEW,
        SKIPPED,
        FAILED
    }

    private record JudgeResult(JudgeOutcome outcome, Long judgementId) {}

    private record CustomerSyncResult(List<Long> newJudgementIds, int skippedCount) {}

    @Override
    public ExternalTradeSyncResultDTO syncAll() {
        List<CustomerCiHashDTO> customers = customerMapper.selectActiveRiaCustomers();
        int failedCustomerCount = 0;
        int skippedJudgementCount = 0;
        List<Long> newJudgementIds = new ArrayList<>();
        for (CustomerCiHashDTO customer : customers) {
            try {
                CustomerSyncResult result = syncCustomer(customer);
                newJudgementIds.addAll(result.newJudgementIds());
                skippedJudgementCount += result.skippedCount();
            } catch (Exception e) {
                failedCustomerCount++;
                log.warn("고객 동기화 실패, 다음 고객으로 진행합니다. customerId={}", customer.getCustomerId(), e);
            }
        }
        return ExternalTradeSyncResultDTO.builder()
                .customerCount(customers.size())
                .failedCustomerCount(failedCustomerCount)
                .newJudgementCount(newJudgementIds.size())
                .skippedJudgementCount(skippedJudgementCount)
                .newJudgementIds(newJudgementIds)
                .build();
    }

    private CustomerSyncResult syncCustomer(CustomerCiHashDTO customer) {
        LocalDate fromDate =
                cursorMapper
                        .selectByCustomerId(customer.getCustomerId())
                        .map(ExternalTradeSyncCursorDTO::getLastSyncedTradeDate)
                        .orElse(null);

        MydataTradeRequestDTO request =
                MydataTradeRequestDTO.builder()
                        .ciHash(customer.getCiHash())
                        .fromDate(fromDate)
                        .build();

        List<MydataTradeResponseDTO> trades = mydataTradeClient.getTrades(request);
        LocalDate today = businessClockService.now().toLocalDate();
        trades =
                trades.stream()
                        .filter(trade -> !trade.getTradeDate().isAfter(today))
                        .filter(
                                trade -> {
                                    boolean match = customer.getCiHash().equals(trade.getCiHash());
                                    if (!match) {
                                        log.warn(
                                                "요청과 다른 ci_hash 응답, 스킵합니다. customerId={}, tradeId={}",
                                                customer.getCustomerId(),
                                                trade.getTradeId());
                                    }
                                    return match;
                                })
                        .sorted(Comparator.comparing(MydataTradeResponseDTO::getTradeDate))
                        .toList();

        LocalDate cursor = fromDate;
        boolean allSucceededSoFar = true;
        List<Long> newJudgementIds = new ArrayList<>();
        int skippedCount = 0;
        for (MydataTradeResponseDTO trade : trades) {
            JudgeResult result = judgeTrade(trade);
            if (result.outcome() == JudgeOutcome.NEW) {
                newJudgementIds.add(result.judgementId());
            } else if (result.outcome() == JudgeOutcome.SKIPPED) {
                skippedCount++;
            }
            boolean succeeded = result.outcome() != JudgeOutcome.FAILED;
            allSucceededSoFar = allSucceededSoFar && succeeded;
            if (allSucceededSoFar) {
                cursor = trade.getTradeDate();
            }
        }

        if (cursor != null) {
            cursorMapper.upsertCursor(
                    ExternalTradeSyncCursorDTO.builder()
                            .customerId(customer.getCustomerId())
                            .lastSyncedTradeDate(cursor)
                            .build());
        }
        return new CustomerSyncResult(newJudgementIds, skippedCount);
    }

    private JudgeResult judgeTrade(MydataTradeResponseDTO trade) {
        try {
            if (targetProductMapper.existsByMydataTradeId(trade.getTradeId())) {
                return new JudgeResult(JudgeOutcome.SKIPPED, null);
            }
            TargetProductJudgementDTO judged = targetProductService.judge(trade);
            return new JudgeResult(JudgeOutcome.NEW, judged.getJudgementId());
        } catch (Exception e) {
            log.warn("거래 판정 실패, 스킵합니다. tradeId={}", trade.getTradeId(), e);
            return new JudgeResult(JudgeOutcome.FAILED, null);
        }
    }
}
