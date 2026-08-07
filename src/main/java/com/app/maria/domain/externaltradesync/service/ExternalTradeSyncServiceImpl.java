package com.app.maria.domain.externaltradesync.service;

import com.app.maria.domain.customer.dto.CustomerCiHashDTO;
import com.app.maria.domain.customer.mapper.CustomerMapper;
import com.app.maria.domain.externaltradesync.dto.ExternalTradeSyncCursorDTO;
import com.app.maria.domain.externaltradesync.mapper.ExternalTradeSyncCursorMapper;
import com.app.maria.domain.mydatatrade.dto.MydataTradeResponseDTO;
import com.app.maria.domain.mydatatrade.dto.request.MydataTradeRequestDTO;
import com.app.maria.domain.targetproduct.mapper.TargetProductMapper;
import com.app.maria.domain.targetproduct.service.TargetProductService;
import com.app.maria.global.client.mydatatrade.MydataTradeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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

    @Override
    public void syncAll() {
        List<CustomerCiHashDTO> customers = customerMapper.selectActiveRiaCustomers();
        for (CustomerCiHashDTO customer : customers) {
            syncCustomer(customer);
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

        LocalDate latestTradeDate = fromDate;
        for (MydataTradeResponseDTO trade : trades) {
            latestTradeDate = judgeAndAdvance(trade, latestTradeDate);
        }

        if (latestTradeDate != null) {
            cursorMapper.upsertCursor(ExternalTradeSyncCursorDTO.builder()
                    .customerId(customer.getCustomerId())
                    .lastSyncedTradeDate(latestTradeDate)
                    .build());
        }
    }

    private LocalDate judgeAndAdvance(MydataTradeResponseDTO trade, LocalDate latestTradeDate) {
        try {
            if (!targetProductMapper.existsByMydataTradeId(trade.getTradeId())) {
                targetProductService.judge(trade.getTradeId(), trade.getStockType(), trade.getFundCode());
            }
        } catch (Exception e) {
            log.warn("거래 판정 실패, 스킵합니다. tradeId={}", trade.getTradeId(), e);
        }

        if (latestTradeDate == null || trade.getTradeDate().isAfter(latestTradeDate)) {
            return trade.getTradeDate();
        }
        return latestTradeDate;
    }
}
