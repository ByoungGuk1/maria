package com.app.maria.domain.customer.dto.response;

import com.app.maria.domain.customer.dto.CustomerDTO;
import com.app.maria.domain.customer.type.InvestorType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "customerId")
@Builder
public class CustomerResponseDTO {
    private Long customerId;
    private String name;
    private LocalDate birthDate;
    private String phone;
    private InvestorType investorType;
    private String ciHash;
    private LocalDateTime createdAt;

    public static CustomerResponseDTO of(CustomerDTO customerDTO) {
        return CustomerResponseDTO.builder()
                .customerId(customerDTO.getCustomerId())
                .name(customerDTO.getName())
                .birthDate(customerDTO.getBirthDate())
                .phone(customerDTO.getPhone())
                .investorType(customerDTO.getInvestorType())
                .ciHash(customerDTO.getCiHash())
                .createdAt(customerDTO.getCreatedAt())
                .build();
    }
}
