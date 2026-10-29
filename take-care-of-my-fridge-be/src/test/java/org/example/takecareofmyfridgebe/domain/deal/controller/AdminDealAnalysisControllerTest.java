package org.example.takecareofmyfridgebe.domain.deal.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealAnalysisResponse;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealReviewRequest;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealEventRequest;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealEventResponse;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealReviewResponse;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate;
import org.example.takecareofmyfridgebe.domain.deal.facade.DealReviewFacade;
import org.example.takecareofmyfridgebe.domain.deal.facade.DealAnalysisExecutionFacade;
import org.example.takecareofmyfridgebe.domain.deal.facade.DealEventFacade;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.domain.deal.service.DealAnalysisReadService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminDealAnalysisController.class)
@Import({SecurityConfig.class, AdminDealAnalysisControllerTest.TestConfig.class})
class AdminDealAnalysisControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DealAnalysisReadService service;
    @Autowired
    private DealReviewFacade reviewFacade;
    @Autowired
    private DealAnalysisExecutionFacade executionFacade;
    @Autowired
    private DealEventFacade eventFacade;

    @BeforeEach
    void resetMock() {
        reset(service, reviewFacade, eventFacade, executionFacade);
    }

    @Test
    void adminRunsAnalysis() throws Exception {
        when(executionFacade.run(7L, Member.Role.ADMIN, LocalDate.of(2030, 1, 7), DealAnalysis.Day.WED, 20))
                .thenReturn(new DealAnalysisResponse(13L, LocalDate.of(2030, 1, 7),
                        DealAnalysis.Day.WED, 20, 50, "model-v1", null, null, null, false,
                        new DealAnalysisResponse.ReviewCounts(0, 0, 0), List.of(), List.of()));
        mvc.perform(post("/api/admin/deal-analyses")
                        .with(csrf()).with(authenticationFor(Member.Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetWeekStart\":\"2030-01-07\",\"dayOfWeek\":\"WED\",\"discountRate\":20}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.analysisId").value(13));
    }

    @Test
    void userRunIsDeniedByFacade() throws Exception {
        when(executionFacade.run(7L, Member.Role.USER, LocalDate.of(2030, 1, 7), DealAnalysis.Day.WED, 20))
                .thenThrow(new BusinessException(ErrorCode.ADMIN_ACCESS_DENIED));
        mvc.perform(post("/api/admin/deal-analyses")
                        .with(csrf()).with(authenticationFor(Member.Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetWeekStart\":\"2030-01-07\",\"dayOfWeek\":\"WED\",\"discountRate\":20}"))
                .andExpect(status().isForbidden());
        verify(executionFacade).run(7L, Member.Role.USER, LocalDate.of(2030, 1, 7), DealAnalysis.Day.WED, 20);
    }

    @Test
    void rejectsInvalidRunRequest() throws Exception {
        mvc.perform(post("/api/admin/deal-analyses")
                        .with(csrf()).with(authenticationFor(Member.Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetWeekStart\":\"2030-01-07\",\"dayOfWeek\":\"WED\",\"discountRate\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(executionFacade);
    }
    @Test
    void adminReadsAnalysis() throws Exception {
        when(service.getById(8L)).thenReturn(new DealAnalysisResponse(
                8L, LocalDate.of(2026, 10, 26), DealAnalysis.Day.WED, 30, 50,
                "model-v1", null, null, null, false,
                new DealAnalysisResponse.ReviewCounts(0, 0, 0), List.of(), List.of()));

        mvc.perform(get("/api/admin/deal-analyses/8").with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisId").value(8));
    }

    @Test
    void missingAnalysisReturnsD002() throws Exception {
        when(service.getById(9L)).thenThrow(new BusinessException(ErrorCode.DEAL_ANALYSIS_NOT_FOUND));

        mvc.perform(get("/api/admin/deal-analyses/9").with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("D002"));
    }

    @Test
    void userAndAnonymousCannotRead() throws Exception {
        mvc.perform(get("/api/admin/deal-analyses/8").with(authenticationFor(Member.Role.USER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/deal-analyses/8"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void adminReadsOwnLatestAnalysis() throws Exception {
        when(service.getLatestForMember(7L)).thenReturn(new DealAnalysisResponse(
                12L, LocalDate.of(2026, 10, 26), DealAnalysis.Day.WED, 30, 50,
                "model-v1", null, null, null, false,
                new DealAnalysisResponse.ReviewCounts(0, 0, 0), List.of(), List.of()));

        mvc.perform(get("/api/admin/deal-analyses/latest")
                        .with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisId").value(12));
    }

    @Test
    void userCannotReadLatestAnalysis() throws Exception {
        mvc.perform(get("/api/admin/deal-analyses/latest")
                        .with(authenticationFor(Member.Role.USER)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void adminSavesSelectedCandidate() throws Exception {
        var reviews = List.of(new DealReviewRequest.Review(11L, DealCandidate.ReviewStatus.SELECTED));
        when(reviewFacade.save(7L, Member.Role.ADMIN, 3L, reviews))
                .thenReturn(new DealReviewResponse(null,
                        new DealAnalysisResponse.ReviewCounts(1, 0, 0)));

        mvc.perform(put("/api/admin/deal-analyses/3/reviews")
                        .with(csrf()).with(authenticationFor(Member.Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviews\":[{\"candidateId\":11,\"reviewStatus\":\"SELECTED\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewCounts.selected").value(1));
    }

    @Test
    void rejectsEmptyReviewList() throws Exception {
        mvc.perform(put("/api/admin/deal-analyses/3/reviews")
                        .with(csrf()).with(authenticationFor(Member.Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reviews\":[]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reviewFacade);
    }

    @Test
    void userCannotSaveReviews() throws Exception {
        var reviews = List.of(new DealReviewRequest.Review(11L, DealCandidate.ReviewStatus.SELECTED));
        when(reviewFacade.save(7L, Member.Role.USER, 3L, reviews))
                .thenThrow(new BusinessException(ErrorCode.ADMIN_ACCESS_DENIED));
        mvc.perform(put("/api/admin/deal-analyses/3/reviews")
                        .with(csrf()).with(authenticationFor(Member.Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviews\":[{\"candidateId\":11,\"reviewStatus\":\"SELECTED\"}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("A004"));
        verify(reviewFacade).save(7L, Member.Role.USER, 3L, reviews);
    }

    @Test
    void adminRegistersSelectedCandidate() throws Exception {
        var request = new DealEventRequest(11L, SaleEventType.SPECIAL,
                LocalDateTime.of(2026, 11, 1, 10, 0),
                LocalDateTime.of(2026, 11, 1, 14, 0), null);
        when(eventFacade.register(7L, Member.Role.ADMIN, 3L, request))
                .thenReturn(new DealEventResponse(11L, 55L, DealCandidate.EventRequestStatus.APPROVED));

        mvc.perform(post("/api/admin/deal-analyses/3/event-requests")
                        .with(csrf()).with(authenticationFor(Member.Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\":11,\"type\":\"SPECIAL\",\"startsAt\":\"2026-11-01T10:00:00\",\"endsAt\":\"2026-11-01T14:00:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.saleEventId").value(55));
    }

    @Test
    void flashDropRequiresLimitedQuantity() throws Exception {
        mvc.perform(post("/api/admin/deal-analyses/3/event-requests")
                        .with(csrf()).with(authenticationFor(Member.Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\":11,\"type\":\"FLASH_DROP\",\"startsAt\":\"2026-11-01T10:00:00\",\"endsAt\":\"2026-11-01T14:00:00\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(eventFacade);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor authenticationFor(Member.Role role) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthSessionService.AuthPrincipal(7L, 8L, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        DealAnalysisReadService dealAnalysisReadService() {
            return mock(DealAnalysisReadService.class);
        }

        @Bean
        DealReviewFacade dealReviewFacade() {
            return mock(DealReviewFacade.class);
        }

        @Bean
        DealAnalysisExecutionFacade dealAnalysisExecutionFacade() {
            return mock(DealAnalysisExecutionFacade.class);
        }
        @Bean
        DealEventFacade dealEventFacade() {
            return mock(DealEventFacade.class);
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
