package com.app.maria.domain.customer.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerNameRequestDTO {
    @NotBlank private String name;
}
