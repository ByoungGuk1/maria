package com.app.maria.domain.targetproduct.service;

import com.app.maria.domain.externaltradesync.dto.response.MydataTradeResponseDTO;
import com.app.maria.domain.targetproduct.dto.response.MydataFundResponseDTO;
import com.app.maria.domain.targetproduct.dto.TargetProductJudgementDTO;
import com.app.maria.domain.targetproduct.mapper.TargetProductMapper;
import com.app.maria.domain.targetproduct.type.StockType;
import com.app.maria.domain.targetproduct.type.TradeType;
import com.app.maria.global.client.mydatafund.MydataFundClient;
import com.app.maria.global.clock.service.BusinessClockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TargetProductServiceImplTest {

    @Mock
    private TargetProductMapper targetProductMapper;

    @Mock
    private MydataFundClient mydataFundClient;

    @Mock
    private BusinessClockService businessClockService;

    @InjectMocks
    private TargetProductServiceImpl targetProductService;

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 7, 0, 0);

    @BeforeEach
    void setUp() {
        when(businessClockService.now()).thenReturn(FIXED_NOW);
    }

    private static MydataTradeResponseDTO trade(Long tradeId, String tradeType, String stockType, String fundCode,
                                                  BigDecimal amount, LocalDate tradeDate) {
        return MydataTradeResponseDTO.builder()
                .tradeId(tradeId)
                .ciHash("ci-1")
                .brokerName("증권사A")
                .tradeType(tradeType)
                .stockType(stockType)
                .qty(BigDecimal.TEN)
                .tradeDate(tradeDate)
                .amount(amount)
                .fundCode(fundCode)
                .ticker("AAPL")
                .build();
    }

    @Test
    @DisplayName("FOREIGN_STOCK은 비중요건 없이 대상상품으로 판정하고 mydata를 조회하지 않는다")
    void judgeMarksForeignStockAsTargetWithoutFundLookup() {
        MydataTradeResponseDTO t = trade(1L, "BUY", "FOREIGN_STOCK", null,
                BigDecimal.valueOf(1_000_000), LocalDate.of(2026, 3, 5));

        TargetProductJudgementDTO result = targetProductService.judge(t);

        assertThat(result.getIsTarget()).isTrue();
        assertThat(result.getFundCode()).isNull();
        verifyNoInteractions(mydataFundClient);
        verify(targetProductMapper).insertJudgement(result);
    }

    @Test
    @DisplayName("ETF는 비중요건 없이 대상상품으로 판정한다")
    void judgeMarksEtfAsTarget() {
        MydataTradeResponseDTO t = trade(2L, "BUY", "ETF", null,
                BigDecimal.valueOf(500_000), LocalDate.of(2026, 3, 6));

        TargetProductJudgementDTO result = targetProductService.judge(t);

        assertThat(result.getIsTarget()).isTrue();
        verifyNoInteractions(mydataFundClient);
    }

    @Test
    @DisplayName("FUND는 해외비중 60% 이상 + 설정 1개월 경과를 모두 만족해야 대상상품으로 판정한다")
    void judgeMarksFundAsTargetWhenBothConditionsMet() {
        MydataFundResponseDTO fund = MydataFundResponseDTO.builder()
                .fundCode("448630")
                .fundName("TIGER 미국배당다우존스")
                .foreignStockRatio(BigDecimal.valueOf(72.50))
                .inceptionDate(FIXED_NOW.toLocalDate().minusMonths(2))
                .build();
        when(mydataFundClient.getFund("448630")).thenReturn(fund);
        MydataTradeResponseDTO t = trade(3L, "BUY", "FUND", "448630",
                BigDecimal.valueOf(1_000_000), LocalDate.of(2026, 3, 10));

        TargetProductJudgementDTO result = targetProductService.judge(t);

        assertThat(result.getIsTarget()).isTrue();
        assertThat(result.getForeignStockRatio()).isEqualByComparingTo("72.50");
        assertThat(result.getInceptionDate()).isEqualTo(fund.getInceptionDate());
        assertThat(result.getFundName()).isEqualTo(fund.getFundName());
    }

    @Test
    @DisplayName("FUND의 해외비중이 60% 미만이면 대상상품이 아니다")
    void judgeMarksFundAsNonTargetWhenRatioBelowThreshold() {
        MydataFundResponseDTO fund = MydataFundResponseDTO.builder()
                .fundCode("069500")
                .foreignStockRatio(BigDecimal.valueOf(59.99))
                .inceptionDate(FIXED_NOW.toLocalDate().minusMonths(2))
                .build();
        when(mydataFundClient.getFund("069500")).thenReturn(fund);
        MydataTradeResponseDTO t = trade(4L, "BUY", "FUND", "069500",
                BigDecimal.valueOf(1_000_000), LocalDate.of(2026, 3, 10));

        TargetProductJudgementDTO result = targetProductService.judge(t);

        assertThat(result.getIsTarget()).isFalse();
    }

    @Test
    @DisplayName("FUND의 설정일이 1개월 미경과이면 비중이 충분해도 대상상품이 아니다")
    void judgeMarksFundAsNonTargetWhenInceptionPeriodNotMet() {
        MydataFundResponseDTO fund = MydataFundResponseDTO.builder()
                .fundCode("381170")
                .foreignStockRatio(BigDecimal.valueOf(88.00))
                .inceptionDate(FIXED_NOW.toLocalDate().minusDays(10))
                .build();
        when(mydataFundClient.getFund("381170")).thenReturn(fund);
        MydataTradeResponseDTO t = trade(5L, "BUY", "FUND", "381170",
                BigDecimal.valueOf(1_000_000), LocalDate.of(2026, 3, 10));

        TargetProductJudgementDTO result = targetProductService.judge(t);

        assertThat(result.getIsTarget()).isFalse();
    }

    @Test
    @DisplayName("설정일이 정확히 1개월 경과한 경계값은 요건을 충족한다")
    void judgeTreatsExactlyOneMonthAsMet() {
        MydataFundResponseDTO fund = MydataFundResponseDTO.builder()
                .fundCode("448630")
                .foreignStockRatio(BigDecimal.valueOf(60.00))
                .inceptionDate(FIXED_NOW.toLocalDate().minusMonths(1))
                .build();
        when(mydataFundClient.getFund("448630")).thenReturn(fund);
        MydataTradeResponseDTO t = trade(6L, "BUY", "FUND", "448630",
                BigDecimal.valueOf(1_000_000), LocalDate.of(2026, 3, 10));

        TargetProductJudgementDTO result = targetProductService.judge(t);

        assertThat(result.getIsTarget()).isTrue();
    }

    @Test
    @DisplayName("판정 결과를 mapper에 저장하고, 거래 원본 정보(트레이드타입/금액/거래일)도 함께 저장한다")
    void judgePassesResultToMapper() {
        ArgumentCaptor<TargetProductJudgementDTO> captor = ArgumentCaptor.forClass(TargetProductJudgementDTO.class);
        MydataTradeResponseDTO t = trade(7L, "BUY", "ETN", null,
                BigDecimal.valueOf(200_000), LocalDate.of(2026, 3, 15));

        targetProductService.judge(t);

        verify(targetProductMapper).insertJudgement(captor.capture());
        assertThat(captor.getValue().getMydataTradeId()).isEqualTo(7L);
        assertThat(captor.getValue().getCiHash()).isEqualTo("ci-1");
        assertThat(captor.getValue().getStockType()).isEqualTo(StockType.ETN);
        assertThat(captor.getValue().getTicker()).isEqualTo("AAPL");
        assertThat(captor.getValue().getJudgedAt()).isEqualTo(FIXED_NOW);
        assertThat(captor.getValue().getTradeType()).isEqualTo(TradeType.BUY);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("200000");
        assertThat(captor.getValue().getTradeDate()).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test
    @DisplayName("BUY 거래는 금액 그대로 양수의 net_buy_amount로 저장한다")
    void judgeSetsPositiveNetBuyAmountForBuyTrade() {
        MydataTradeResponseDTO t = trade(8L, "BUY", "FOREIGN_STOCK", null,
                BigDecimal.valueOf(1_000_000), LocalDate.of(2026, 3, 5));

        TargetProductJudgementDTO result = targetProductService.judge(t);

        assertThat(result.getNetBuyAmount()).isEqualByComparingTo("1000000");
    }

    @Test
    @DisplayName("SELL 거래는 금액이 음수로 부호전환된 net_buy_amount로 저장한다")
    void judgeSetsNegativeNetBuyAmountForSellTrade() {
        MydataTradeResponseDTO t = trade(9L, "SELL", "FOREIGN_STOCK", null,
                BigDecimal.valueOf(1_000_000), LocalDate.of(2026, 3, 5));

        TargetProductJudgementDTO result = targetProductService.judge(t);

        assertThat(result.getNetBuyAmount()).isEqualByComparingTo("-1000000");
    }

    @Test
    @DisplayName("INHERITANCE(상속) 거래는 BUY와 동일하게 양수의 net_buy_amount로 저장한다")
    void judgeSetsPositiveNetBuyAmountForInheritanceTrade() {
        MydataTradeResponseDTO t = trade(10L, "INHERITANCE", "FOREIGN_STOCK", null,
                BigDecimal.valueOf(1_000_000), LocalDate.of(2026, 3, 5));

        TargetProductJudgementDTO result = targetProductService.judge(t);

        assertThat(result.getNetBuyAmount()).isEqualByComparingTo("1000000");
    }

    @Test
    @DisplayName("GIFT(증여) 거래는 BUY와 동일하게 양수의 net_buy_amount로 저장한다")
    void judgeSetsPositiveNetBuyAmountForGiftTrade() {
        MydataTradeResponseDTO t = trade(11L, "GIFT", "FOREIGN_STOCK", null,
                BigDecimal.valueOf(1_000_000), LocalDate.of(2026, 3, 5));

        TargetProductJudgementDTO result = targetProductService.judge(t);

        assertThat(result.getNetBuyAmount()).isEqualByComparingTo("1000000");
    }
}