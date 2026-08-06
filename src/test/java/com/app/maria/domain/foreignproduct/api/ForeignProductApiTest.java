package com.app.maria.domain.foreignproduct.api;

import com.app.maria.domain.foreignproduct.dto.response.ForeignProductResponseDTO;
import com.app.maria.domain.foreignproduct.exception.ForeignProductNotFoundException;
import com.app.maria.domain.foreignproduct.service.ForeignProductService;
import com.app.maria.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ForeignProductApiTest {

    private ForeignProductService foreignProductService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        foreignProductService = mock(ForeignProductService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ForeignProductApi(foreignProductService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllForeignProductsReturnsProductList() throws Exception {
        ForeignProductResponseDTO response = ForeignProductResponseDTO.builder()
                .foreignProductId(1L)
                .ticker("AAPL")
                .name("애플")
                .market("NASDAQ")
                .currency("USD")
                .type("FOREIGN_STOCK")
                .build();
        when(foreignProductService.getAllForeignProducts()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/foreign-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("종목 전체 조회 성공"))
                .andExpect(jsonPath("$.data[0].ticker").value("AAPL"));
    }

    @Test
    void getAllForeignProductsReturnsNotFoundWhenNoProductsExist() throws Exception {
        when(foreignProductService.getAllForeignProducts())
                .thenThrow(new ForeignProductNotFoundException("등록된 종목이 없습니다."));

        mockMvc.perform(get("/api/foreign-products"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("등록된 종목이 없습니다."));
    }
}