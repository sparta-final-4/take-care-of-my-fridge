package org.example.takecareofmyfridgebe.domain.search.dto;

import java.util.List;

public record ProductSearchResult(
        List<Long> productIds,
        long total
) {
}
