package com.app.maria.domain.foreignproduct.service;

import com.app.maria.domain.foreignproduct.dto.ForeignProductDTO;
import com.app.maria.domain.foreignproduct.dto.response.ForeignProductResponseDTO;
import com.app.maria.domain.foreignproduct.exception.ForeignProductNotFoundException;
import com.app.maria.domain.foreignproduct.mapper.ForeignProductMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForeignProductServiceImplTest {

    @Mock
    private ForeignProductMapper foreignProductMapper;

    @InjectMocks
    private ForeignProductServiceImpl foreignProductService;

    @Test
    void getAllForeignProductsReturnsAllProductsWhenProductsExist() {
        ForeignProductDTO dto1 = ForeignProductDTO.builder()
                .foreignProductId(1L)
                .ticker("AAPL")
                .name("애플")
                .market("NASDAQ")
                .currency("USD")
                .type("FOREIGN_STOCK")
                .build();
        ForeignProductDTO dto2 = ForeignProductDTO.builder()
                .foreignProductId(2L)
                .ticker("TSLA")
                .name("테슬라")
                .market("NASDAQ")
                .currency("USD")
                .type("FOREIGN_STOCK")
                .build();
        when(foreignProductMapper.selectAll()).thenReturn(List.of(dto1, dto2));

        List<ForeignProductResponseDTO> result = foreignProductService.getAllForeignProducts();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ForeignProductResponseDTO::getTicker)
                .containsExactly("AAPL", "TSLA");
    }

    @Test
    void getAllForeignProductsThrowsNotFoundExceptionWhenNoProductsExist() {
        when(foreignProductMapper.selectAll()).thenReturn(List.of());

        assertThatThrownBy(() -> foreignProductService.getAllForeignProducts())
                .isInstanceOf(ForeignProductNotFoundException.class)
                .hasMessage("등록된 종목이 없습니다.");
    }
}