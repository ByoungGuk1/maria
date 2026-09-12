package com.app.maria.domain.settlement.batch;

import com.app.maria.domain.settlement.component.SettlementBatchStatusUpdater;
import com.app.maria.domain.settlement.component.SettlementFailureRecorder;
import com.app.maria.domain.settlement.component.SettlementTransactionExecutor;
import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import com.app.maria.domain.settlement.dto.SettlementItemDTO;
import com.app.maria.domain.settlement.dto.SettlementJoinDTO;
import com.app.maria.domain.settlement.exception.ExchangeRateExternalApiException;
import com.app.maria.domain.settlement.exception.SettlementBatchNotFoundException;
import com.app.maria.domain.settlement.exception.SettlementStateConflictException;
import com.app.maria.domain.settlement.mapper.SettlementBatchMapper;
import com.app.maria.domain.settlement.mapper.SettlementItemMapper;
import com.app.maria.domain.settlement.mapper.SettlementJoinMapper;
import com.app.maria.domain.settlement.provider.ExchangeRateProvider;
import com.app.maria.domain.settlement.type.BatchStatus;
import com.app.maria.global.exception.ExchangeRateNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SettlementBatchTasklet implements Tasklet {

    private static final String LAST_ITEM_ID = "settlement.lastItemId";
    private static final String RATE_CACHE_PREFIX = "settlement.rate.";
    private static final long INITIAL_ITEM_ID = 0L;

    private final SettlementBatchMapper settlementBatchMapper;
    private final SettlementItemMapper settlementItemMapper;
    private final SettlementJoinMapper settlementJoinMapper;
    private final ExchangeRateProvider exchangeRateProvider;
    private final SettlementTransactionExecutor settlementTransactionExecutor;
    private final SettlementFailureRecorder settlementFailureRecorder;
    private final SettlementBatchStatusUpdater settlementBatchStatusUpdater;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Object batchParameter = chunkContext.getStepContext().getJobParameters().get("batchId");
        Long batchId =
                batchParameter instanceof Number ? ((Number) batchParameter).longValue() : null;
        Object runParameter = chunkContext.getStepContext().getJobParameters().get("runId");
        String runId = runParameter instanceof String ? (String) runParameter : null;
        if (batchId == null || runId == null || runId.isBlank()) {
            throw new SettlementStateConflictException("확정산 Job Parameter가 올바르지 않습니다.");
        }

        SettlementBatchDTO batch =
                settlementBatchMapper
                        .selectBatchById(batchId)
                        .orElseThrow(
                                () ->
                                        new SettlementBatchNotFoundException(
                                                "Batch를 찾을 수 없습니다. batchId=" + batchId));
        if (batch.getStatus() != BatchStatus.RUNNING || !runId.equals(batch.getRunId())) {
            throw new SettlementStateConflictException(
                    "확정산 Batch 상태가 실행 가능하지 않습니다. batchId=" + batchId);
        }

        var executionContext =
                chunkContext.getStepContext().getStepExecution().getExecutionContext();
        long lastItemId = executionContext.getLong(LAST_ITEM_ID, INITIAL_ITEM_ID);
        SettlementItemDTO cursor =
                SettlementItemDTO.builder().batchId(batchId).itemId(lastItemId).build();
        List<SettlementItemDTO> items = settlementItemMapper.selectPendingItems(cursor);

        if (items.isEmpty()) {
            finalizeBatch(batchId);
            return RepeatStatus.FINISHED;
        }

        for (SettlementItemDTO item : items) {
            processItem(batchId, item, executionContext);
            executionContext.putLong(LAST_ITEM_ID, item.getItemId());
        }

        return RepeatStatus.CONTINUABLE;
    }

    private void processItem(
            Long batchId,
            SettlementItemDTO item,
            ExecutionContext executionContext) {
        try {
            SettlementJoinDTO query =
                    SettlementJoinDTO.builder().batchId(batchId).itemId(item.getItemId()).build();
            Optional<SettlementJoinDTO> target = settlementJoinMapper.selectTargetByItemId(query);
            if (target.isEmpty()) {
                throw new SettlementStateConflictException(
                        "확정산 대상을 찾을 수 없습니다. itemId=" + item.getItemId());
            }

            SettlementJoinDTO value = target.get();
            LocalDate rateDate = requireFinalDate(value);
            BigDecimal finalRate =
                    resolveFinalRate(value.getPurchaseCurrency(), rateDate, executionContext);
            settlementTransactionExecutor.execute(value, finalRate);
        } catch (Exception e) {
            settlementFailureRecorder.markFailed(item.getItemId(), e);
        }
    }

    private LocalDate requireFinalDate(SettlementJoinDTO target) {
        if (target.getFinalAt() == null) {
            throw new SettlementStateConflictException(
                    "확정산 기준일이 없습니다. exchangeId=" + target.getExchangeId());
        }
        return target.getFinalAt().toLocalDate();
    }

    private BigDecimal resolveFinalRate(
            String currency, LocalDate rateDate, ExecutionContext executionContext) {
        String cacheKey = RATE_CACHE_PREFIX + currency + ":" + rateDate;
        String valueKey = cacheKey + ".value";
        String failureTypeKey = cacheKey + ".failureType";
        String failureMessageKey = cacheKey + ".failureMessage";

        if (executionContext.containsKey(valueKey)) {
            return new BigDecimal(executionContext.getString(valueKey));
        }
        if (executionContext.containsKey(failureTypeKey)) {
            throw cachedFailure(
                    executionContext.getString(failureTypeKey),
                    executionContext.getString(failureMessageKey));
        }

        try {
            BigDecimal rate = exchangeRateProvider.getFinalRate(currency, rateDate);
            executionContext.putString(valueKey, rate.toPlainString());
            return rate;
        } catch (ExchangeRateNotFoundException e) {
            cacheFailure(executionContext, failureTypeKey, failureMessageKey, "NOT_FOUND", e);
            throw e;
        } catch (ExchangeRateExternalApiException e) {
            cacheFailure(executionContext, failureTypeKey, failureMessageKey, "EXTERNAL_API", e);
            throw e;
        }
    }

    private void cacheFailure(
            ExecutionContext executionContext,
            String failureTypeKey,
            String failureMessageKey,
            String failureType,
            Exception exception) {
        executionContext.putString(failureTypeKey, failureType);
        executionContext.putString(
                failureMessageKey,
                exception.getMessage() == null ? "환율 조회 실패" : exception.getMessage());
    }

    private RuntimeException cachedFailure(String failureType, String failureMessage) {
        if ("NOT_FOUND".equals(failureType)) {
            return new ExchangeRateNotFoundException(failureMessage);
        }
        return new ExchangeRateExternalApiException(failureMessage, null);
    }

    private void finalizeBatch(Long batchId) {
        int pendingCount = settlementItemMapper.countPendingItems(batchId);
        if (pendingCount > 0) {
            throw new SettlementStateConflictException(
                    "미처리 Item이 남아 있어 Batch를 종료할 수 없습니다. batchId="
                            + batchId
                            + ", pending="
                            + pendingCount);
        }

        settlementBatchStatusUpdater.completeFromLatestItems(batchId);
    }
}
