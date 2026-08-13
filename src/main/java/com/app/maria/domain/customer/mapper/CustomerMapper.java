package com.app.maria.domain.customer.mapper;

import com.app.maria.domain.customer.dto.CustomerCiHashDTO;
import com.app.maria.domain.customer.dto.CustomerDTO;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CustomerMapper {

    List<CustomerCiHashDTO> selectActiveRiaCustomers();

    List<CustomerDTO> selectAll();

    List<CustomerDTO> selectByName(@Param("name") String name);

    boolean existsByCustomerId(@Param("customerId") Long customerId);

    Optional<String> selectCiHashByCustomerId(@Param("customerId") Long customerId);
}
