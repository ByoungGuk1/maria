package com.app.maria.domain.member.service;

import com.app.maria.domain.member.dto.response.MemberResponseDTO;
import com.app.maria.domain.member.exception.MemberNotFoundException;
import com.app.maria.domain.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class MemberServiceImpl implements MemberService {

    private final MemberMapper memberMapper;

    @Override
    public MemberResponseDTO getMemberById(String id) {
        return memberMapper
                .selectById(id)
                .map(MemberResponseDTO::new)
                .orElseThrow(() -> new MemberNotFoundException("회원 조회 실패"));
    }
}
