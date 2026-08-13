package com.app.maria.domain.customer.service;

import com.app.maria.domain.customer.dto.request.CustomerNameRequestDTO;
import com.app.maria.domain.customer.dto.response.CustomerResponseDTO;
import com.app.maria.domain.customer.mapper.CustomerMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {
    private final CustomerMapper customerMapper;

    @Override
    public List<CustomerResponseDTO> findAll() {
        return customerMapper.selectAll().stream().map(CustomerResponseDTO::of).toList();
    }

    @Override
    public List<CustomerResponseDTO> findByName(CustomerNameRequestDTO customerNameRequestDTO) {
        String name = customerNameRequestDTO.getName();
        return customerMapper.selectByName(name).stream().map(CustomerResponseDTO::of).toList();
    }
}
