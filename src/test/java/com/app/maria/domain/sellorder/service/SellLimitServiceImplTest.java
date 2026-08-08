package com.app.maria.domain.sellorder.service;

import com.app.maria.domain.sellorder.exception.SellOrderException;
import com.app.maria.domain.sellorder.mapper.SellLimitMapper;
import com.app.maria.global.client.mydata.MydataClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellLimitServiceImplTest {

    @Mock
    SellLimitMapper sellLimitMapper;

    @Mock
    MydataClient mydataClient;

    @InjectMocks
    SellLimitServiceImpl sellLimitService;

    private void stubAccount(Long inboundId, Long accountId, String limitAmount, String finalizedSum, String pendingSum, String ciHash) {
        when(sellLimitMapper.selectAccountByInboundId(inboundId)).thenReturn(Optional.of(accountId));
        when(sellLimitMapper.selectAccountLimitForUpdate(accountId)).thenReturn(Optional.of(new BigDecimal(limitAmount)));
        when(sellLimitMapper.sumFinalizedExchangeAmount(accountId)).thenReturn(new BigDecimal(finalizedSum));
        when(sellLimitMapper.sumPendingSellOrderAmount(accountId)).thenReturn(new BigDecimal(pendingSum));
        when(sellLimitMapper.selectCiHashByAccountId(accountId)).thenReturn(Optional.of(ciHash));
    }

    @Test
    @DisplayName("RIA 내 확정산 + myData 외부 순매수 + 이번 주문금액 합이 한도 이내면 true를 반환한다")
    void isWithinSellLimitReturnsTrueWhenTotalIsWithinLimit() {
        stubAccount(1L, 100L, "50000000", "20000000", "0", "ci-hash-1");
        when(mydataClient.getExternalSellTotal("ci-hash-1")).thenReturn(new BigDecimal("10000000"));

        assertThat(sellLimitService.isWithinSellLimit(1L, new BigDecimal("15000000"))).isTrue();
    }

    @Test
    @DisplayName("확정산+외부순매수+이번주문금액 합이 한도를 넘으면 false를 반환한다")
    void isWithinSellLimitReturnsFalseWhenTotalExceedsLimit() {
        stubAccount(1L, 100L, "50000000", "20000000", "0", "ci-hash-1");
        when(mydataClient.getExternalSellTotal("ci-hash-1")).thenReturn(new BigDecimal("10000000"));

        assertThat(sellLimitService.isWithinSellLimit(1L, new BigDecimal("20000001"))).isFalse();
    }

    @Test
    @DisplayName("합계가 한도와 정확히 같으면(경계값) 초과가 아니므로 true를 반환한다")
    void isWithinSellLimitReturnsTrueWhenTotalExactlyEqualsLimit() {
        stubAccount(1L, 100L, "50000000", "20000000", "0", "ci-hash-1");
        when(mydataClient.getExternalSellTotal("ci-hash-1")).thenReturn(new BigDecimal("10000000"));

        assertThat(sellLimitService.isWithinSellLimit(1L, new BigDecimal("20000000"))).isTrue();
    }

    @Test
    @DisplayName("RIA 확정산 + 미확정 매도주문 + myData 외부 순매수를 각각 따로 더한 뒤 이번 주문금액까지 정확히 합산한다")
    void isWithinSellLimitSumsFinalizedAndPendingAndExternalAndOrderAmountCorrectly() {
        stubAccount(1L, 100L, "6000", "2000", "1500", "ci-hash-1");
        when(mydataClient.getExternalSellTotal("ci-hash-1")).thenReturn(new BigDecimal("1500"));

        assertThat(sellLimitService.isWithinSellLimit(1L, new BigDecimal("1000"))).isTrue();
        assertThat(sellLimitService.isWithinSellLimit(1L, new BigDecimal("1001"))).isFalse();
    }

    @Test
    @DisplayName("RIA 확정산이 0이어도 미확정(RECEIVED) 매도주문 합계만으로 한도초과를 잡아낸다")
    void isWithinSellLimitCatchesExcessFromPendingSellOrdersAloneWhenFinalizedSumIsZero() {
        stubAccount(1L, 100L, "50000000", "0", "40000000", "ci-hash-1");
        when(mydataClient.getExternalSellTotal("ci-hash-1")).thenReturn(BigDecimal.ZERO);

        assertThat(sellLimitService.isWithinSellLimit(1L, new BigDecimal("10000000"))).isTrue();
        assertThat(sellLimitService.isWithinSellLimit(1L, new BigDecimal("10000001"))).isFalse();
    }

    @Test
    @DisplayName("inboundId로 계좌를 찾을 수 없으면 예외를 던지고 이후 조회는 하지 않는다")
    void isWithinSellLimitThrowsWhenAccountNotFoundByInboundId() {
        when(sellLimitMapper.selectAccountByInboundId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellLimitService.isWithinSellLimit(1L, new BigDecimal("1000")))
                .isInstanceOf(SellOrderException.class)
                .hasMessage("계좌 정보를 찾을 수 없습니다.");

        verify(sellLimitMapper, never()).selectAccountLimitForUpdate(any());
        verifyNoInteractions(mydataClient);
    }

    @Test
    @DisplayName("계좌 한도 정보를 찾을 수 없으면 예외를 던지고 myData는 조회하지 않는다")
    void isWithinSellLimitThrowsWhenAccountLimitNotFound() {
        when(sellLimitMapper.selectAccountByInboundId(1L)).thenReturn(Optional.of(100L));
        when(sellLimitMapper.selectAccountLimitForUpdate(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellLimitService.isWithinSellLimit(1L, new BigDecimal("1000")))
                .isInstanceOf(SellOrderException.class)
                .hasMessage("계좌 한도 정보를 찾을 수 없습니다.");

        verifyNoInteractions(mydataClient);
    }

    @Test
    @DisplayName("ci_hash를 찾을 수 없으면 예외를 던지고 myData는 조회하지 않는다")
    void isWithinSellLimitThrowsWhenCiHashNotFound() {
        when(sellLimitMapper.selectAccountByInboundId(1L)).thenReturn(Optional.of(100L));
        when(sellLimitMapper.selectAccountLimitForUpdate(100L)).thenReturn(Optional.of(new BigDecimal("50000000")));
        when(sellLimitMapper.sumFinalizedExchangeAmount(100L)).thenReturn(new BigDecimal("0"));
        when(sellLimitMapper.selectCiHashByAccountId(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellLimitService.isWithinSellLimit(1L, new BigDecimal("1000")))
                .isInstanceOf(SellOrderException.class)
                .hasMessage("고객 정보를 확인할 수 없습니다.");

        verifyNoInteractions(mydataClient);
    }
}