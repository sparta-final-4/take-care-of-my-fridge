package org.example.takecareofmyfridgebe.domain.deal.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealProductInput;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealProductInputService {

    private final ProductRepository productRepository;

    public List<DealProductInput> getProducts() {
        return productRepository.findAll().stream()
                .map(product -> new DealProductInput(product.getId(), product.getName(),
                        product.getPrice(), product.getStatus(), product.getStock()))
                .toList();
    }
}
