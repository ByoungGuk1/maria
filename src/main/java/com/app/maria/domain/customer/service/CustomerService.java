package com.app.maria.domain.customer.service;

import com.app.maria.domain.customer.dto.request.CustomerNameRequestDTO;
import com.app.maria.domain.customer.dto.response.CustomerResponseDTO;
import java.util.List;

public interface CustomerService {
    List<CustomerResponseDTO> findAll();

    List<CustomerResponseDTO> findByName(CustomerNameRequestDTO customerNameRequestDTO);
}
