package com.app.maria.domain.inbound.mapper;

import com.app.maria.domain.inbound.dto.InboundDTO;
import com.app.maria.domain.inbound.dto.InboundDetailDTO;
import com.app.maria.domain.inbound.dto.InboundMinDTO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InboundMapper {
    void insertInbound(InboundDTO inboundDTO);
    void insertInboundDetail(InboundDetailDTO inboundDetailDTO);
    void insertInboundMin(InboundMinDTO inboundMinDTO);
}
