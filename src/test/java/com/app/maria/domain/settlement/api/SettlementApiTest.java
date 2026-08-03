package com.app.maria.domain.settlement.api;

import com.app.maria.domain.settlement.dto.KrwExchangeDTO;
import com.app.maria.domain.settlement.dto.SettlementBatchDTO;
import com.app.maria.domain.settlement.dto.SettlementItemDTO;
import com.app.maria.domain.settlement.dto.SettlementJoinDTO;
import com.app.maria.domain.settlement.service.SettlementService;
import com.app.maria.domain.settlement.type.BatchStatus;
import com.app.maria.domain.settlement.type.SettlementStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettlementApiTest {

  private SettlementService settlementService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    settlementService = mock(SettlementService.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(new SettlementApi(settlementService))
        .build();
  }

  @Test
  void executeSettlementBatchReturnsAcceptedBatch() throws Exception {
    SettlementBatchDTO batch = batch();
    when(settlementService.executeSettlementBatch()).thenReturn(batch);

    mockMvc.perform(post("/api/settlement/jobs"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.message").value("확정산 배치 실행 요청 완료"))
        .andExpect(jsonPath("$.data.batchId").value(1L))
        .andExpect(jsonPath("$.data.status").value("RUNNING"));

    verify(settlementService).executeSettlementBatch();
  }

  @Test
  void getSettlementBatchEndpointsReturnBatch() throws Exception {
    SettlementBatchDTO batch = batch();
    when(settlementService.getSettlementBatches()).thenReturn(List.of(batch));
    when(settlementService.getSettlementBatch(1L)).thenReturn(batch);
    when(settlementService.getSettlementBatchByRunId("run-1")).thenReturn(batch);

    mockMvc.perform(get("/api/settlement/batches"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].batchId").value(1L));
    mockMvc.perform(get("/api/settlement/batches/{batchId}", 1L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.runId").value("run-1"));
    mockMvc.perform(get("/api/settlement/batches/run/{runId}", "run-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.batchId").value(1L));
  }

  @Test
  void getSettlementItemEndpointsUseBatchAndCursor() throws Exception {
    SettlementItemDTO item = SettlementItemDTO.builder()
        .itemId(10L)
        .batchId(1L)
        .exchangeId(100L)
        .build();
    SettlementJoinDTO detail = SettlementJoinDTO.builder()
        .itemId(10L)
        .batchId(1L)
        .exchangeId(100L)
        .build();
    when(settlementService.getPendingSettlementItems(1L, 0L)).thenReturn(List.of(item));
    when(settlementService.getSettlementItem(1L, 10L)).thenReturn(detail);

    mockMvc.perform(get("/api/settlement/batches/{batchId}/items/pending", 1L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].itemId").value(10L));
    mockMvc.perform(get("/api/settlement/batches/{batchId}/items/{itemId}", 1L, 10L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.exchangeId").value(100L));

    verify(settlementService).getPendingSettlementItems(1L, 0L);
    verify(settlementService).getSettlementItem(1L, 10L);
  }

  @Test
  void getKrwExchangeReturnsExchange() throws Exception {
    KrwExchangeDTO exchange = KrwExchangeDTO.builder()
        .exchangeId(100L)
        .accountId(1L)
        .settlementStatus(SettlementStatus.PROVISIONAL)
        .build();
    when(settlementService.getKrwExchange(100L)).thenReturn(exchange);

    mockMvc.perform(get("/api/settlement/exchanges/{exchangeId}", 100L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.exchangeId").value(100L))
        .andExpect(jsonPath("$.data.settlementStatus").value("PROVISIONAL"));

    verify(settlementService).getKrwExchange(100L);
  }

  private SettlementBatchDTO batch() {
    return SettlementBatchDTO.builder()
        .batchId(1L)
        .status(BatchStatus.RUNNING)
        .runId("run-1")
        .build();
  }
}
