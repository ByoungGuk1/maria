package com.app.maria.domain.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.maria.domain.customer.dto.CustomerDTO;
import com.app.maria.domain.customer.dto.request.CustomerNameRequestDTO;
import com.app.maria.domain.customer.dto.response.CustomerResponseDTO;
import com.app.maria.domain.customer.mapper.CustomerMapper;
import com.app.maria.domain.customer.type.InvestorType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock private CustomerMapper customerMapper;

    private CustomerServiceImpl customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerServiceImpl(customerMapper);
    }

    @Test
    void findAllMapsCustomerDtosToResponseDtos() {
        when(customerMapper.selectAll()).thenReturn(List.of(customer(1L, "홍길동")));

        List<CustomerResponseDTO> result = customerService.findAll();

        assertThat(result)
                .singleElement()
                .satisfies(
                        customer -> {
                            assertThat(customer.getCustomerId()).isEqualTo(1L);
                            assertThat(customer.getName()).isEqualTo("홍길동");
                            assertThat(customer.getInvestorType()).isEqualTo(InvestorType.NEUTRAL);
                        });
        verify(customerMapper).selectAll();
    }

    @Test
    void findByNamePassesNameToMapperAndMapsResponse() {
        CustomerNameRequestDTO request = new CustomerNameRequestDTO();
        request.setName("길동");
        when(customerMapper.selectByName("길동")).thenReturn(List.of(customer(1L, "홍길동")));

        List<CustomerResponseDTO> result = customerService.findByName(request);

        assertThat(result).extracting(CustomerResponseDTO::getName).containsExactly("홍길동");
        verify(customerMapper).selectByName("길동");
    }

    private CustomerDTO customer(Long customerId, String name) {
        return CustomerDTO.builder()
                .customerId(customerId)
                .name(name)
                .birthDate(LocalDate.of(1990, 1, 1))
                .phone("010-1234-5678")
                .investorType(InvestorType.NEUTRAL)
                .ciHash("ci-hash")
                .createdAt(LocalDateTime.of(2026, 8, 13, 10, 0))
                .build();
    }
}
