package org.example.takecareofmyfridgebe.domain.sale.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.sale.dto.PopularSaleResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleResponse;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.domain.sale.facade.PopularSaleFacade;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.example.takecareofmyfridgebe.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SaleController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SaleControllerTest.TestConfig.class})
class SaleControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PopularSaleFacade popularSaleFacade;

    @BeforeEach
    void resetMock() {
        reset(popularSaleFacade);
    }

    @Test
    @DisplayName("비로그인 상태에서 인기 특가를 조회하면 200 OK와 특가 목록을 반환한다")
    void getPopularSales_unauthenticated_ok() throws Exception {
        // given
        SaleResponse sale = new SaleResponse(SaleEventType.SPECIAL, 1600, 20,
                OffsetDateTime.of(2026, 11, 1, 18, 0, 0, 0, ZoneOffset.ofHours(9)), null, null);
        when(popularSaleFacade.getPopularSales())
                .thenReturn(List.of(new PopularSaleResponse(1L, "두부", "https://img.test/tofu.png", 2000, sale)));

        // when & then
        mvc.perform(get("/api/sales/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].productId").value(1L))
                .andExpect(jsonPath("$.data[0].name").value("두부"))
                .andExpect(jsonPath("$.data[0].price").value(2000))
                .andExpect(jsonPath("$.data[0].sale.salePrice").value(1600))
                .andExpect(jsonPath("$.data[0].sale.discountRate").value(20));
    }

    @Test
    @DisplayName("공개한 것은 인기 특가 경로 하나뿐이라, 다른 할인 경로는 비로그인이면 401이다")
    void otherSalePath_unauthenticated_unauthorized() throws Exception {
        mvc.perform(get("/api/sales/other"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        PopularSaleFacade popularSaleFacade() {
            return mock(PopularSaleFacade.class);
        }

        @Bean
        JwtTokenService jwtTokenService() {
            return mock(JwtTokenService.class);
        }

        @Bean
        AuthSessionService authSessionService() {
            return mock(AuthSessionService.class);
        }
    }
}
