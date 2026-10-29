package org.example.takecareofmyfridgebe.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealProductInput;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;

class DealProductInputServiceTest {

    private final ProductRepository repository = mock(ProductRepository.class);
    private final DealProductInputService service = new DealProductInputService(repository);

    @Test
    void readsPriceStockAndStatusWithoutFilteringProducts() {
        Product soldOut = mock(Product.class);
        when(soldOut.getId()).thenReturn(1L);
        when(soldOut.getName()).thenReturn("상품 A");
        when(soldOut.getPrice()).thenReturn(12000);
        when(soldOut.getStatus()).thenReturn(ProductStatus.SOLD_OUT);
        when(soldOut.getStock()).thenReturn(0);
        Product stopped = mock(Product.class);
        when(stopped.getId()).thenReturn(2L);
        when(stopped.getName()).thenReturn("상품 B");
        when(stopped.getPrice()).thenReturn(8000);
        when(stopped.getStatus()).thenReturn(ProductStatus.STOPPED);
        when(stopped.getStock()).thenReturn(5);
        when(repository.findAll()).thenReturn(List.of(soldOut, stopped));

        assertThat(service.getProducts()).containsExactly(
                new DealProductInput(1L, "상품 A", 12000, ProductStatus.SOLD_OUT, 0),
                new DealProductInput(2L, "상품 B", 8000, ProductStatus.STOPPED, 5));
    }
}
