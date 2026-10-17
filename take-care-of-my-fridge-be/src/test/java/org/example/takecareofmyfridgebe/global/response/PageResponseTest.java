package org.example.takecareofmyfridgebe.global.response;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PageResponseTest {

    @Test
    void convertsZeroBasedSpringPageToOneBasedApiPage() {
        PageResponse<String> response = PageResponse.of(new PageImpl<>(
                List.of("member"), PageRequest.of(1, 20), 45));

        assertEquals(List.of("member"), response.content());
        assertEquals(2, response.pageNumber());
        assertEquals(20, response.pageSize());
        assertEquals(45, response.totalElements());
        assertEquals(3, response.totalPages());
        assertFalse(response.isLast());
    }
}
