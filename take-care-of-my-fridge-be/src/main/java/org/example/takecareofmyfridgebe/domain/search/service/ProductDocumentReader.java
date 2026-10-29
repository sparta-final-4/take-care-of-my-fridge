package org.example.takecareofmyfridgebe.domain.search.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductIndexSource;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.infra.elasticsearch.dto.EsDocument;
import org.example.takecareofmyfridgebe.infra.elasticsearch.index.EsDocumentReader;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProductDocumentReader implements EsDocumentReader {

    private final ProductService productService;

    @Override
    public List<EsDocument> readAll() {
        return productService.findIndexSources().stream()
                .map(this::toDocument)
                .toList();
    }

    @Override
    // 단건 색인용: 상품 하나. 상품은 행을 지우지 않으므로 보통 값이 있다
    public Optional<EsDocument> readOne(long productId) {
        return productService.findIndexSource(productId)
                .map(this::toDocument);
    }

    // 매핑에 필드를 추가하면 여기도 추가한다
    private EsDocument toDocument(ProductIndexSource product) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", product.id());
        doc.put("product_type", product.productType().name());
        doc.put("name", product.name());
        doc.put("description", product.description());
        doc.put("price", product.price());
        doc.put("status", product.status().name());

        return new EsDocument(product.id(), doc);
    }

}
