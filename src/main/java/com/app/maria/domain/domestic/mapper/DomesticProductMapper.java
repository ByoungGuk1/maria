package com.app.maria.domain.domestic.mapper;

import com.app.maria.domain.domestic.dto.DomesticProductDTO;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DomesticProductMapper {
    Optional<DomesticProductDTO> selectById(@Param("domesticProductId") Long domesticProductId);
}
