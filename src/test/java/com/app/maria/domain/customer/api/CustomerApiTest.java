package com.app.maria.domain.customer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.maria.domain.customer.dto.request.CustomerNameRequestDTO;
import com.app.maria.domain.customer.dto.response.CustomerResponseDTO;
import com.app.maria.domain.customer.service.CustomerService;
import com.app.maria.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CustomerApiTest {

    private CustomerService customerService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        customerService = mock(CustomerService.class);
        mockMvc =
                MockMvcBuilders.standaloneSetup(new CustomerApi(customerService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void customerApiUsesReadRolePolicy() {
        assertThat(CustomerApi.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAnyRole('ADMIN', 'SETTLEMENT', 'REVIEWER', 'VIEWER')");
    }

    @Test
    void getAllCustomersReturnsServiceResult() throws Exception {
        when(customerService.findAll())
                .thenReturn(
                        List.of(CustomerResponseDTO.builder().customerId(1L).name("홍길동").build()));

        mockMvc.perform(get("/api/customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("사용자 정보 전체 조회"))
                .andExpect(jsonPath("$.data[0].name").value("홍길동"));

        verify(customerService).findAll();
    }

    @Test
    void searchCustomersByNamePassesJsonBodyToService() throws Exception {
        when(customerService.findByName(any(CustomerNameRequestDTO.class))).thenReturn(List.of());

        mockMvc.perform(
                        post("/api/customer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"홍길동\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("사용자 정보 전체 조회"));

        verify(customerService).findByName(any(CustomerNameRequestDTO.class));
    }

    @Test
    void searchCustomersByNameRejectsBlankName() throws Exception {
        mockMvc.perform(
                        post("/api/customer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest());

        verify(customerService, never()).findByName(any());
    }
}
