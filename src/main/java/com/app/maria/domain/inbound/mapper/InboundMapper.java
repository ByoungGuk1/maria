package com.app.maria.domain.inbound.mapper;

import com.app.maria.domain.inbound.dto.InboundDTO;
import com.app.maria.domain.inbound.dto.InboundDetailDTO;
import com.app.maria.domain.inbound.dto.InboundHoldingDTO;
import com.app.maria.domain.inbound.dto.InboundListDTO;
import com.app.maria.domain.inbound.dto.InboundMinDTO;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InboundMapper {
    void insertInbound(InboundDTO inboundDTO);

    void insertInboundDetail(InboundDetailDTO inboundDetailDTO);

    void insertInboundMin(InboundMinDTO inboundMinDTO);

    BigDecimal sumApprovedQtyByAccountAndProduct(
            @Param("accountId") Long accountId, @Param("foreignProductId") Long foreignProductId);

    Optional<InboundDetailDTO> selectInboundDetailById(Long inboundDetailId);

    int decreaseCurrentQty(
            @Param("inboundDetailId") Long inboundDetailId, @Param("qty") BigDecimal qty);

    List<InboundDetailDTO> selectFifoLots(
            @Param("accountId") Long accountId, @Param("foreignProductId") Long foreignProductId);

    List<InboundHoldingDTO> selectHoldingsByAccount(@Param("accountId") Long accountId);

    List<InboundListDTO> selectInbounds(@Param("offset") int offset, @Param("size") int size);

    int countInbounds();
}
