package org.example.takecareofmyfridgebe.domain.sale.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.sale.dto.PopularSaleResponse;
import org.example.takecareofmyfridgebe.domain.sale.facade.PopularSaleFacade;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final PopularSaleFacade popularSaleFacade;

    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<List<PopularSaleResponse>>> getPopularSales() {
        List<PopularSaleResponse> sales = popularSaleFacade.getPopularSales();
        return ResponseEntity.ok(ApiResponse.ok(sales));
    }
}
