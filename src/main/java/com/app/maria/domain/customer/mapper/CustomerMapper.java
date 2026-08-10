package com.app.maria.domain.customer.mapper;

import com.app.maria.domain.customer.dto.CustomerCiHashDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CustomerMapper {

    List<CustomerCiHashDTO> selectActiveRiaCustomers();
}
