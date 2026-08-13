package com.app.maria.domain.customer.dto;

import com.app.maria.domain.customer.type.InvestorType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "customerId")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDTO {
    private Long customerId;
    private String name;
    private LocalDate birthDate;
    private String phone;
    private InvestorType investorType;
    private String ciHash;
    private LocalDateTime createdAt;
}
