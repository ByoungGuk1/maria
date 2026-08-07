package com.app.maria.domain.targetproduct.service;

import com.app.maria.domain.mydatafund.dto.MydataFundResponseDTO;
import com.app.maria.domain.targetproduct.dto.TargetProductJudgementDTO;
import com.app.maria.domain.targetproduct.mapper.TargetProductMapper;
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

    @Test
    @DisplayName("FOREIGN_STOCK은 비중요건 없이 대상상품으로 판정하고 mydata를 조회하지 않는다")
    void judgeMarksForeignStockAsTargetWithoutFundLookup() {
        TargetProductJudgementDTO result = targetProductService.judge(1L, "FOREIGN_STOCK", null);

        assertThat(result.getIsTarget()).isTrue();
        assertThat(result.getFundCode()).isNull();
        verifyNoInteractions(mydataFundClient);
        verify(targetProductMapper).insertJudgement(result);
    }

    @Test
    @DisplayName("ETF는 비중요건 없이 대상상품으로 판정한다")
    void judgeMarksEtfAsTarget() {
        TargetProductJudgementDTO result = targetProductService.judge(2L, "ETF", null);

        assertThat(result.getIsTarget()).isTrue();
        verifyNoInteractions(mydataFundClient);
    }

    @Test
    @DisplayName("FUND는 해외비중 60% 이상 + 설정 1개월 경과를 모두 만족해야 대상상품으로 판정한다")
    void judgeMarksFundAsTargetWhenBothConditionsMet() {
        MydataFundResponseDTO fund = MydataFundResponseDTO.builder()
                .fundCode("448630")
                .fundName("TIGER 미국배당다우존스")
                .foreignStockRatio(72.50)
                .inceptionDate(FIXED_NOW.toLocalDate().minusMonths(2))
                .build();
        when(mydataFundClient.getFund("448630")).thenReturn(fund);

        TargetProductJudgementDTO result = targetProductService.judge(3L, "FUND", "448630");

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
                .foreignStockRatio(59.99)
                .inceptionDate(FIXED_NOW.toLocalDate().minusMonths(2))
                .build();
        when(mydataFundClient.getFund("069500")).thenReturn(fund);

        TargetProductJudgementDTO result = targetProductService.judge(4L, "FUND", "069500");

        assertThat(result.getIsTarget()).isFalse();
    }

    @Test
    @DisplayName("FUND의 설정일이 1개월 미경과이면 비중이 충분해도 대상상품이 아니다")
    void judgeMarksFundAsNonTargetWhenInceptionPeriodNotMet() {
        MydataFundResponseDTO fund = MydataFundResponseDTO.builder()
                .fundCode("381170")
                .foreignStockRatio(88.00)
                .inceptionDate(FIXED_NOW.toLocalDate().minusDays(10))
                .build();
        when(mydataFundClient.getFund("381170")).thenReturn(fund);

        TargetProductJudgementDTO result = targetProductService.judge(5L, "FUND", "381170");

        assertThat(result.getIsTarget()).isFalse();
    }

    @Test
    @DisplayName("설정일이 정확히 1개월 경과한 경계값은 요건을 충족한다")
    void judgeTreatsExactlyOneMonthAsMet() {
        MydataFundResponseDTO fund = MydataFundResponseDTO.builder()
                .fundCode("448630")
                .foreignStockRatio(60.00)
                .inceptionDate(FIXED_NOW.toLocalDate().minusMonths(1))
                .build();
        when(mydataFundClient.getFund("448630")).thenReturn(fund);

        TargetProductJudgementDTO result = targetProductService.judge(6L, "FUND", "448630");

        assertThat(result.getIsTarget()).isTrue();
    }

    @Test
    @DisplayName("판정 결과를 mapper에 저장한다")
    void judgePassesResultToMapper() {
        ArgumentCaptor<TargetProductJudgementDTO> captor = ArgumentCaptor.forClass(TargetProductJudgementDTO.class);

        targetProductService.judge(7L, "ETN", null);

        verify(targetProductMapper).insertJudgement(captor.capture());
        assertThat(captor.getValue().getMydataTradeId()).isEqualTo(7L);
        assertThat(captor.getValue().getJudgedAt()).isEqualTo(FIXED_NOW);
    }
}
