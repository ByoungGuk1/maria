package com.app.maria.domain.externaltradesync.service;

import com.app.maria.domain.customer.dto.CustomerCiHashDTO;
import com.app.maria.domain.customer.mapper.CustomerMapper;
import com.app.maria.domain.externaltradesync.dto.ExternalTradeSyncCursorDTO;
import com.app.maria.domain.externaltradesync.mapper.ExternalTradeSyncCursorMapper;
import com.app.maria.domain.externaltradesync.dto.response.MydataTradeResponseDTO;
import com.app.maria.domain.externaltradesync.dto.request.MydataTradeRequestDTO;
import com.app.maria.domain.targetproduct.mapper.TargetProductMapper;
import com.app.maria.domain.targetproduct.service.TargetProductService;
import com.app.maria.global.client.mydatatrade.MydataTradeClient;
import com.app.maria.global.clock.service.BusinessClockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

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

    @Override
    public void syncAll() {
        List<CustomerCiHashDTO> customers = customerMapper.selectActiveRiaCustomers();
        for (CustomerCiHashDTO customer : customers) {
            try {
                syncCustomer(customer);
            } catch (Exception e) {
                log.warn("고객 동기화 실패, 다음 고객으로 진행합니다. customerId={}", customer.getCustomerId(), e);
            }
        }
    }

    private void syncCustomer(CustomerCiHashDTO customer) {
        LocalDate fromDate = cursorMapper.selectByCustomerId(customer.getCustomerId())
                .map(ExternalTradeSyncCursorDTO::getLastSyncedTradeDate)
                .orElse(null);

        MydataTradeRequestDTO request = MydataTradeRequestDTO.builder()
                .ciHash(customer.getCiHash())
                .fromDate(fromDate)
                .build();

        List<MydataTradeResponseDTO> trades = mydataTradeClient.getTrades(request);
        LocalDate today = businessClockService.now().toLocalDate();
        trades = trades.stream()
                .filter(trade -> !trade.getTradeDate().isAfter(today))
                .map(trade -> trade.toBuilder().ciHash(customer.getCiHash()).build())
                .sorted(Comparator.comparing(MydataTradeResponseDTO::getTradeDate))
                .toList();

        LocalDate cursor = fromDate;
        boolean allSucceededSoFar = true;
        for (MydataTradeResponseDTO trade : trades) {
            boolean succeeded = judgeTrade(trade);
            allSucceededSoFar = allSucceededSoFar && succeeded;
            if (allSucceededSoFar) {
                cursor = trade.getTradeDate();
            }
        }

        if (cursor != null) {
            cursorMapper.upsertCursor(ExternalTradeSyncCursorDTO.builder()
                    .customerId(customer.getCustomerId())
                    .lastSyncedTradeDate(cursor)
                    .build());
        }
    }

    private boolean judgeTrade(MydataTradeResponseDTO trade) {
        try {
            if (!targetProductMapper.existsByMydataTradeId(trade.getTradeId())) {
                targetProductService.judge(trade);
            }
            return true;
        } catch (Exception e) {
            log.warn("거래 판정 실패, 스킵합니다. tradeId={}", trade.getTradeId(), e);
            return false;
        }
    }
}
