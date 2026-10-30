package org.example.takecareofmyfridgebe.domain.settlement.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SalesAmountResponse;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SalesPeriodUnit;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementPeriodResponse;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementResponse;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementSearchRequest;
import org.example.takecareofmyfridgebe.domain.settlement.dto.TodaySalesResponse;
import org.example.takecareofmyfridgebe.domain.settlement.facade.SettlementQueryFacade;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettlementController.class)
@Import({SecurityConfig.class, SettlementControllerTest.TestConfig.class})
class SettlementControllerTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SettlementQueryFacade settlementQueryFacade;

    @BeforeEach
    void resetMock() {
        reset(settlementQueryFacade);
    }

    @Test
    @DisplayName("관리자는 단위·기간으로 정산을 조회한다")
    void adminGetsSettlement() throws Exception {
        LocalDate start = TODAY.minusDays(1);
        SettlementSearchRequest request = new SettlementSearchRequest(SalesPeriodUnit.DAY, start, TODAY);
        SalesAmountResponse sales = SalesAmountResponse.of(30000, 10000, 2);
        when(settlementQueryFacade.getSettlement(request)).thenReturn(new SettlementResponse(sales,
                List.of(SettlementPeriodResponse.of(start, start, sales))));

        mvc.perform(get("/api/admin/settlements")
                        .param("unit", "DAY")
                        .param("startDate", start.toString())
                        .param("endDate", TODAY.toString())
                        .with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summary.netAmount").value(20000))
                .andExpect(jsonPath("$.data.periods[0].startDate").value(start.toString()))
                .andExpect(jsonPath("$.data.periods[0].paidCount").value(2));

        verify(settlementQueryFacade).getSettlement(request);
    }

    @Test
    @DisplayName("종료일이 내일 이후면 400으로 막는다")
    void endDateAfterToday_returnsBadRequest() throws Exception {
        mvc.perform(get("/api/admin/settlements")
                        .param("unit", "DAY")
                        .param("startDate", TODAY.toString())
                        .param("endDate", TODAY.plusDays(1).toString())
                        .with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()))
                .andExpect(jsonPath("$.error.message").value("종료일은 오늘까지 선택할 수 있습니다."));

        verifyNoInteractions(settlementQueryFacade);
    }

    @Test
    @DisplayName("조회 기간이 3개월을 넘으면 400으로 막는다")
    void overThreeMonths_returnsBadRequest() throws Exception {
        mvc.perform(get("/api/admin/settlements")
                        .param("unit", "MONTH")
                        .param("startDate", TODAY.minusMonths(3).toString())
                        .param("endDate", TODAY.toString())
                        .with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("조회 기간은 최대 3개월입니다."));

        verifyNoInteractions(settlementQueryFacade);
    }

    @Test
    @DisplayName("관리자가 아니면 정산을 조회할 수 없다")
    void userCannotGetSettlement() throws Exception {
        mvc.perform(get("/api/admin/settlements")
                        .param("unit", "DAY")
                        .param("startDate", TODAY.toString())
                        .param("endDate", TODAY.toString())
                        .with(authenticationFor(Member.Role.USER)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(settlementQueryFacade);
    }

    @Test
    @DisplayName("관리자는 오늘 지금까지의 매출을 조회한다")
    void adminGetsTodaySales() throws Exception {
        when(settlementQueryFacade.getTodaySales())
                .thenReturn(new TodaySalesResponse(TODAY, SalesAmountResponse.of(5000, 0, 1)));

        mvc.perform(get("/api/admin/settlements/today").with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.salesDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.sales.paidAmount").value(5000));
    }

    private RequestPostProcessor authenticationFor(Member.Role role) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthSessionService.AuthPrincipal(7L, 8L, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        SettlementQueryFacade settlementQueryFacade() {
            return mock(SettlementQueryFacade.class);
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
