package com.app.maria.domain.foreignproduct.service;

import com.app.maria.domain.foreignproduct.dto.ForeignProductDTO;
import com.app.maria.domain.foreignproduct.dto.response.ForeignProductResponseDTO;
import com.app.maria.domain.foreignproduct.exception.ForeignProductNotFoundException;
import com.app.maria.domain.foreignproduct.mapper.ForeignProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ForeignProductServiceImpl implements ForeignProductService {

    private final ForeignProductMapper foreignProductMapper;

    @Override
    public List<ForeignProductResponseDTO> getAllForeignProducts() {
        List<ForeignProductDTO> products = foreignProductMapper.selectAll();

        if (products.isEmpty()) {
            throw new ForeignProductNotFoundException("등록된 종목이 없습니다.");
        }

        return products.stream()
                .map(ForeignProductResponseDTO::of)
                .collect(Collectors.toList());
    }
}