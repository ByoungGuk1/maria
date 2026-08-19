package com.app.maria.domain.domestic.mapper;

import com.app.maria.domain.domestic.dto.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DomesticStockBalanceMapper {
    List<DomesticInvestmentListDTO> selectAccountSummaries(DomesticInvestmentSearchDTO condition);

    int countAccountSummaries(DomesticInvestmentSearchDTO condition);

    Optional<DomesticInvestmentListDTO> selectAccountSummaryById(
            @Param("accountId") Long accountId);

    List<DomesticHoldingDTO> selectHoldingsByAccountId(@Param("accountId") Long accountId);

    DomesticInvestmentSummaryDTO selectSummaryStats(@Param("sinceDate") LocalDate sinceDate);

    List<DomesticFundHoldingDetailDTO> selectActiveFundHoldings();

    List<DomesticRestrictedHoldingDTO> selectRestrictedHoldings();

    List<DomesticCashHeavyAccountDTO> selectCashHeavyAccounts(
            @Param("offset") int offset, @Param("size") int size);

    int countCashHeavyAccounts();

    List<DomesticAccountLiteDTO> selectAccountsByRecentBuyStatus(
            @Param("sinceDate") LocalDate sinceDate, @Param("hasRecentBuy") boolean hasRecentBuy);
}
