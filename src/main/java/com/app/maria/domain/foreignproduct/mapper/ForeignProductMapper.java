package com.app.maria.domain.foreignproduct.mapper;

import com.app.maria.domain.foreignproduct.dto.ForeignProductDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ForeignProductMapper {
    List<ForeignProductDTO> selectAll();
}