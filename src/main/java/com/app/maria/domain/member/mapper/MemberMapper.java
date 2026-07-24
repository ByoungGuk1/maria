package com.app.maria.domain.member.mapper;

import com.app.maria.domain.member.dto.MemberDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface MemberMapper {
  public Optional<MemberDTO> selectById(@Param("id") String mid);
}
