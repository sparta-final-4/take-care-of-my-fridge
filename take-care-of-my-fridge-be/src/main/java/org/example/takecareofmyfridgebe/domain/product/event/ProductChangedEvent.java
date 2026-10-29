package org.example.takecareofmyfridgebe.domain.product.event;

public record ProductChangedEvent(
        Long productId
) {

    public static ProductChangedEvent from(Long productId) {
        return new ProductChangedEvent(productId);
    }

}
