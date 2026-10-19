package org.example.takecareofmyfridgebe.domain.cart.dto;

// 새 줄이 만들어졌는지(201)와 기존 줄에 수량만 더했는지(200)를 컨트롤러가 가르도록 함께 돌려준다
public record AddCartItemResult(AddCartItemResponse response, boolean created) {
}
