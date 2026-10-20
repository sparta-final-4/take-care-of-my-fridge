package org.example.takecareofmyfridgebe.domain.cart.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemRequest;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemResponse;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemResult;
import org.example.takecareofmyfridgebe.domain.cart.dto.CartItemsResponse;
import org.example.takecareofmyfridgebe.domain.cart.dto.ChangeCartItemQuantityRequest;
import org.example.takecareofmyfridgebe.domain.cart.dto.ChangeCartItemQuantityResponse;
import org.example.takecareofmyfridgebe.domain.cart.dto.RemoveCartItemsRequest;
import org.example.takecareofmyfridgebe.domain.cart.service.CartService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart-items")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping
    public ResponseEntity<ApiResponse<AddCartItemResponse>> addItem(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal,
            @Valid @RequestBody AddCartItemRequest request) {
        AddCartItemResult result = cartService.addItem(principal.memberId(), request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(result.response()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CartItemsResponse>> getItems(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(cartService.getItems(principal.memberId())));
    }

    @PatchMapping("/{cartItemId}")
    public ResponseEntity<ApiResponse<ChangeCartItemQuantityResponse>> changeQuantity(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal,
            @PathVariable Long cartItemId,
            @Valid @RequestBody ChangeCartItemQuantityRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(cartService.changeQuantity(principal.memberId(), cartItemId, request)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> removeItems(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal,
            @Valid @ModelAttribute RemoveCartItemsRequest request) {
        cartService.removeItems(principal.memberId(), request);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
