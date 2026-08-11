package com.app.maria.domain.customer.mapper;

import com.app.maria.domain.customer.dto.CustomerCiHashDTO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerMapper {

    List<CustomerCiHashDTO> selectActiveRiaCustomers();
}
