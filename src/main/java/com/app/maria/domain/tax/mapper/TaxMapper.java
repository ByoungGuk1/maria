package com.app.maria.domain.tax.mapper;

import com.app.maria.domain.tax.dto.SellLotDTO;
import com.app.maria.domain.tax.dto.TaxRuleDTO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaxMapper {
    List<SellLotDTO> findFinalizedLotsByAccountAndYear(@Param("accountId") Long accountId, @Param("year") int year,@Param("calcBaseDateTime")  LocalDateTime calcBaseDateTime);
    List<TaxRuleDTO> findTaxRules();
}
