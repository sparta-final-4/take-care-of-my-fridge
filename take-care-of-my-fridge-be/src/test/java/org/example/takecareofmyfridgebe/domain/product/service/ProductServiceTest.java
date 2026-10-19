package org.example.takecareofmyfridgebe.domain.product.service;

import org.example.takecareofmyfridgebe.domain.product.dto.ProductListItemResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductSort;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.dummy.ProductFixture;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @InjectMocks
    private ProductService service;
    @Captor
    private ArgumentCaptor<Collection<ProductStatus>> statusCaptor;

    @Test
    @DisplayName("판매 중지(STOPPED) 상품은 조회 대상에서 제외한다")
    void getProducts_excludesStopped() {
        // given
        when(productRepository.findByStatusIn(anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // when
        service.getProducts(ProductSort.LATEST, 1, 20);

        // then
        verify(productRepository).findByStatusIn(statusCaptor.capture(), any(Pageable.class));
        assertThat(statusCaptor.getValue())
                .containsExactlyInAnyOrder(ProductStatus.ON_SALE, ProductStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("1부터 시작하는 요청 페이지를 0부터 시작하는 PageRequest로 바꾸고 정렬을 적용한다")
    void getProducts_convertsPageAndSort() {
        // given
        when(productRepository.findByStatusIn(anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // when
        service.getProducts(ProductSort.PRICE_ASC, 2, 20);

        // then
        PageRequest expected = PageRequest.of(1, 20, ProductSort.PRICE_ASC.toSort());
        verify(productRepository).findByStatusIn(anyCollection(), eq(expected));
    }

    @Test
    @DisplayName("조회한 상품을 목록 응답 DTO와 1부터 시작하는 페이지 정보로 변환한다")
    void getProducts_mapsToResponse() {
        // given
        Product onion = ProductFixture.createProduct(5L, "양파 1kg", 3900);
        when(productRepository.findByStatusIn(anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(onion), PageRequest.of(1, 20), 21));

        // when
        PageResponse<ProductListItemResponse> result = service.getProducts(ProductSort.LATEST, 2, 20);

        // then
        assertThat(result.pageNumber()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(21);
        assertThat(result.content()).containsExactly(new ProductListItemResponse(
                5L, ProductType.INGREDIENT, "양파 1kg", null, 3900, ProductStatus.ON_SALE));
    }

}
