package org.example.takecareofmyfridgebe.domain.sale.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventsCreateResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleCandidateProductResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleCandidateProductsRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventListRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventListResponse;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventDetailResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventUpdateRequest;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventDetailQueryService;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventListQueryService;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventTerminationService;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventUpdateService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.mockito.ArgumentCaptor;
import org.example.takecareofmyfridgebe.domain.sale.dto.SpecialSaleCreateRequest;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventCreateService;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleCandidateProductQueryService;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AdminSaleEventController.class)
@Import({SecurityConfig.class, AdminSaleEventControllerTest.TestConfig.class})
class AdminSaleEventControllerTest {

    private static final String SPECIAL_BODY = """
            {"startsAt": "2099-10-20T00:00:00", "endsAt": "2099-10-26T23:59:59",
             "items": [{"productId": 102, "discountRate": 20}, {"productId": 104, "discountRate": 18}]}
            """;
    private static final String FLASH_DROP_BODY = """
            {"startsAt": "2099-10-13T18:00:00", "endsAt": "2099-10-13T20:00:00",
             "items": [{"productId": 103, "discountRate": 50, "limitedQuantity": 10},
                       {"productId": 104, "discountRate": 40, "limitedQuantity": 5}]}
            """;

    private final MockMvc mvc;
    private final SaleEventCreateService saleEventCreateService;
    private final SaleCandidateProductQueryService saleCandidateProductQueryService;
    private final SaleEventListQueryService saleEventListQueryService;
    private final SaleEventDetailQueryService saleEventDetailQueryService;
    private final SaleEventUpdateService saleEventUpdateService;
    private final SaleEventTerminationService saleEventTerminationService;

    @Autowired
    AdminSaleEventControllerTest(MockMvc mvc, SaleEventCreateService saleEventCreateService,
                                 SaleCandidateProductQueryService saleCandidateProductQueryService,
                                 SaleEventListQueryService saleEventListQueryService,
                                 SaleEventDetailQueryService saleEventDetailQueryService,
                                 SaleEventUpdateService saleEventUpdateService,
                                 SaleEventTerminationService saleEventTerminationService) {
        this.mvc = mvc;
        this.saleEventCreateService = saleEventCreateService;
        this.saleCandidateProductQueryService = saleCandidateProductQueryService;
        this.saleEventListQueryService = saleEventListQueryService;
        this.saleEventDetailQueryService = saleEventDetailQueryService;
        this.saleEventUpdateService = saleEventUpdateService;
        this.saleEventTerminationService = saleEventTerminationService;
    }

    @BeforeEach
    void resetMock() {
        reset(saleEventCreateService, saleCandidateProductQueryService, saleEventListQueryService,
                saleEventDetailQueryService, saleEventUpdateService, saleEventTerminationService);
    }

    @Test
    @DisplayName("관리자가 특가를 등록하면 201과 상품마다 만든 행사 번호를 돌려준다")
    void createSpecialSale_returns201() throws Exception {
        when(saleEventCreateService.createSpecialSale(any(SpecialSaleCreateRequest.class)))
                .thenReturn(new SaleEventsCreateResponse(List.of(7L, 8L)));

        mvc.perform(post("/api/admin/sale-events/special")
                        .with(csrf()).with(loginAs(Member.Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(SPECIAL_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.saleEventIds[0]").value(7))
                .andExpect(jsonPath("$.data.saleEventIds[1]").value(8));
    }

    @Test
    @DisplayName("관리자가 번개 드롭을 등록하면 201과 상품마다 만든 행사 번호를 돌려준다")
    void createFlashDrop_returns201() throws Exception {
        when(saleEventCreateService.createFlashDrop(any(FlashDropCreateRequest.class)))
                .thenReturn(new SaleEventsCreateResponse(List.of(8L, 9L)));

        mvc.perform(post("/api/admin/sale-events/flash-drop")
                        .with(csrf()).with(loginAs(Member.Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(FLASH_DROP_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.saleEventIds[0]").value(8))
                .andExpect(jsonPath("$.data.saleEventIds[1]").value(9));
    }

    @Test
    @DisplayName("이미 행사가 있는 상품이면 409와 SALE_001을 돌려준다")
    void createFlashDrop_duplicated_returns409() throws Exception {
        when(saleEventCreateService.createFlashDrop(any(FlashDropCreateRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.SALE_EVENT_PRODUCT_DUPLICATED, "이미 행사가 있는 상품 · 두부"));

        mvc.perform(post("/api/admin/sale-events/flash-drop")
                        .with(csrf()).with(loginAs(Member.Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(FLASH_DROP_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SALE_001"))
                .andExpect(jsonPath("$.error.message").value("이미 행사가 있는 상품 · 두부"));
    }

    @Test
    @DisplayName("특가 입력값이 잘못되면 400이고 서비스를 부르지 않는다")
    void createSpecialSale_invalidInput_returns400() throws Exception {
        List<String> bodies = List.of(
                // 종료가 시작보다 빠름
                """
                {"startsAt": "2099-10-26T00:00:00", "endsAt": "2099-10-20T00:00:00",
                 "items": [{"productId": 102, "discountRate": 20}]}
                """,
                // 할인율 100%
                """
                {"startsAt": "2099-10-20T00:00:00", "endsAt": "2099-10-26T00:00:00",
                 "items": [{"productId": 102, "discountRate": 100}]}
                """,
                // 같은 상품 두 번
                """
                {"startsAt": "2099-10-20T00:00:00", "endsAt": "2099-10-26T00:00:00",
                 "items": [{"productId": 102, "discountRate": 20}, {"productId": 102, "discountRate": 30}]}
                """,
                // 상품 없음
                """
                {"startsAt": "2099-10-20T00:00:00", "endsAt": "2099-10-26T00:00:00", "items": []}
                """);

        for (String body : bodies) {
            mvc.perform(post("/api/admin/sale-events/special")
                            .with(csrf()).with(loginAs(Member.Role.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("C001"));
        }
        verifyNoInteractions(saleEventCreateService);
    }

    @Test
    @DisplayName("번개 드롭 입력값이 잘못되면 400이고 서비스를 부르지 않는다")
    void createFlashDrop_invalidInput_returns400() throws Exception {
        List<String> bodies = List.of(
                // 한정 수량 없음
                """
                {"startsAt": "2099-10-13T18:00:00", "endsAt": "2099-10-13T20:00:00",
                 "items": [{"productId": 103, "discountRate": 50}]}
                """,
                // 한정 수량 0
                """
                {"startsAt": "2099-10-13T18:00:00", "endsAt": "2099-10-13T20:00:00",
                 "items": [{"productId": 103, "discountRate": 50, "limitedQuantity": 0}]}
                """,
                // 종료가 시작보다 빠름
                """
                {"startsAt": "2099-10-13T20:00:00", "endsAt": "2099-10-13T18:00:00",
                 "items": [{"productId": 103, "discountRate": 50, "limitedQuantity": 5}]}
                """,
                // 같은 상품 두 번
                """
                {"startsAt": "2099-10-13T18:00:00", "endsAt": "2099-10-13T20:00:00",
                 "items": [{"productId": 103, "discountRate": 50, "limitedQuantity": 5},
                           {"productId": 103, "discountRate": 40, "limitedQuantity": 5}]}
                """,
                // 상품 없음
                """
                {"startsAt": "2099-10-13T18:00:00", "endsAt": "2099-10-13T20:00:00", "items": []}
                """);

        for (String body : bodies) {
            mvc.perform(post("/api/admin/sale-events/flash-drop")
                            .with(csrf()).with(loginAs(Member.Role.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(saleEventCreateService);
    }

    @Test
    @DisplayName("일반 회원은 403, 로그인하지 않으면 401이고 서비스를 부르지 않는다")
    void create_nonAdmin_rejects() throws Exception {
        mvc.perform(post("/api/admin/sale-events/special")
                        .with(csrf()).with(loginAs(Member.Role.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(SPECIAL_BODY))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/sale-events/flash-drop")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(FLASH_DROP_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(saleEventCreateService);
    }

    @Test
    @DisplayName("관리자가 등록할 상품 목록을 조회하면 상품마다 재고와 함께 페이지로 돌려준다")
    void getCandidateProducts_returns200() throws Exception {
        when(saleCandidateProductQueryService.getCandidates(any(SaleCandidateProductsRequest.class)))
                .thenReturn(new PageResponse<>(List.of(
                        new SaleCandidateProductResponse(1L, ProductType.INGREDIENT, "대파", null, 3000, 7)),
                        1, 10, 1, 1, true));

        mvc.perform(get("/api/admin/sale-events/products").param("keyword", "대파").param("type", "INGREDIENT")
                        .with(loginAs(Member.Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].productId").value(1))
                .andExpect(jsonPath("$.data.content[0].price").value(3000))
                .andExpect(jsonPath("$.data.content[0].stock").value(7))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("검색어·페이지 값이 잘못되면 400, 일반 회원은 403이며 서비스를 부르지 않는다")
    void getCandidateProducts_invalidOrNonAdmin_rejects() throws Exception {
        mvc.perform(get("/api/admin/sale-events/products").param("page", "0").with(loginAs(Member.Role.ADMIN)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/sale-events/products").param("size", "101").with(loginAs(Member.Role.ADMIN)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/sale-events/products").with(loginAs(Member.Role.USER)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(saleCandidateProductQueryService);
    }

    @Test
    @DisplayName("관리자가 행사 목록을 조회하면 조건을 서비스에 넘기고, 날짜 하나만 주면 그 하루로 본다")
    void getSaleEvents_returns200() throws Exception {
        when(saleEventListQueryService.getSaleEvents(any(SaleEventListRequest.class)))
                .thenReturn(new PageResponse<>(List.of(
                        new SaleEventListResponse(1L, SaleEventType.FLASH_DROP, SaleEventStatus.ACTIVE,
                                LocalDateTime.of(2026, 10, 1, 18, 0), LocalDateTime.of(2026, 10, 1, 20, 0),
                                3L, "대파", 3000, 1500, 50, 10, 3, 7L)),
                        1, 10, 1, 1, true));

        mvc.perform(get("/api/admin/sale-events").param("status", "ACTIVE").param("type", "FLASH_DROP")
                        .param("startFrom", "2026-10-01").with(loginAs(Member.Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].saleEventId").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("FLASH_DROP"))
                .andExpect(jsonPath("$.data.content[0].productName").value("대파"))
                .andExpect(jsonPath("$.data.content[0].remainingQuantity").value(3))
                .andExpect(jsonPath("$.data.content[0].soldQuantity").value(7));

        ArgumentCaptor<SaleEventListRequest> captor = ArgumentCaptor.forClass(SaleEventListRequest.class);
        verify(saleEventListQueryService).getSaleEvents(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(SaleEventStatus.ACTIVE);
        assertThat(captor.getValue().startFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(captor.getValue().startTo()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    @DisplayName("기간 순서·페이지 값이 잘못되면 400, 일반 회원은 403이며 서비스를 부르지 않는다")
    void getSaleEvents_invalidOrNonAdmin_rejects() throws Exception {
        mvc.perform(get("/api/admin/sale-events").param("startFrom", "2026-10-20").param("startTo", "2026-10-01")
                        .with(loginAs(Member.Role.ADMIN)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/sale-events").param("endFrom", "2026-10-20").param("endTo", "2026-10-01")
                        .with(loginAs(Member.Role.ADMIN)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/sale-events").param("page", "0").with(loginAs(Member.Role.ADMIN)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/sale-events").param("sort", "NOPE").with(loginAs(Member.Role.ADMIN)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/sale-events").param("direction", "UP").with(loginAs(Member.Role.ADMIN)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/sale-events").param("startFrom", "not-a-date").with(loginAs(Member.Role.ADMIN)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/sale-events").with(loginAs(Member.Role.USER)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(saleEventListQueryService);
    }

    private static final String UPDATE_BODY = """
            {"startsAt": "2099-10-20T00:00:00", "endsAt": "2099-10-26T23:59:59",
             "items": [{"productId": 102, "discountRate": 30}, {"productId": 104, "discountRate": 18}]}
            """;

    @Test
    @DisplayName("관리자가 행사를 고치면 200이고 요청 값을 서비스에 넘긴다")
    void updateSaleEvent_returns200() throws Exception {
        mvc.perform(patch("/api/admin/sale-events/5").with(csrf()).with(loginAs(Member.Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<SaleEventUpdateRequest> captor = ArgumentCaptor.forClass(SaleEventUpdateRequest.class);
        verify(saleEventUpdateService).update(eq(5L), captor.capture());
        assertThat(captor.getValue().startsAt()).isEqualTo(LocalDateTime.of(2099, 10, 20, 0, 0));
        assertThat(captor.getValue().items()).extracting("productId", "discountRate")
                .containsExactly(tuple(102L, 30),
                        tuple(104L, 18));
    }

    @Test
    @DisplayName("시각·상품·할인율이 잘못되면 400, 일반 회원은 403이며 서비스를 부르지 않는다")
    void updateSaleEvent_invalidOrNonAdmin_rejects() throws Exception {
        for (String body : List.of(
                "{}",
                "{\"startsAt\": \"2099-10-20T00:00:00\", \"endsAt\": \"2099-10-26T00:00:00\", \"items\": []}",
                "{\"startsAt\": \"2099-10-26T00:00:00\", \"endsAt\": \"2099-10-20T00:00:00\","
                        + " \"items\": [{\"productId\": 1, \"discountRate\": 10}]}",
                "{\"startsAt\": \"2099-10-20T00:00:00\", \"endsAt\": \"2099-10-26T00:00:00\","
                        + " \"items\": [{\"productId\": 1, \"discountRate\": 0}]}",
                "{\"startsAt\": \"2099-10-20T00:00:00\", \"endsAt\": \"2099-10-26T00:00:00\","
                        + " \"items\": [{\"productId\": 1, \"discountRate\": 10}, {\"productId\": 1, \"discountRate\": 10}]}",
                "{\"startsAt\": \"2099-10-20T00:00:00\", \"endsAt\": \"2099-10-26T00:00:00\","
                        + " \"items\": [{\"productId\": 1, \"discountRate\": 10, \"limitedQuantity\": 0}]}")) {
            mvc.perform(patch("/api/admin/sale-events/5").with(csrf()).with(loginAs(Member.Role.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/admin/sale-events/5").with(csrf()).with(loginAs(Member.Role.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(saleEventUpdateService);
    }

    @Test
    @DisplayName("시작한 행사나 없는 행사는 서비스의 오류 코드대로 409와 404를 돌려준다")
    void updateSaleEvent_serviceErrors_mapToStatus() throws Exception {
        doThrow(new BusinessException(ErrorCode.SALE_EVENT_NOT_EDITABLE))
                .when(saleEventUpdateService).update(eq(5L), any());
        doThrow(new BusinessException(ErrorCode.SALE_EVENT_NOT_FOUND))
                .when(saleEventUpdateService).update(eq(6L), any());

        mvc.perform(patch("/api/admin/sale-events/5").with(csrf()).with(loginAs(Member.Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SALE_003"));
        mvc.perform(patch("/api/admin/sale-events/6").with(csrf()).with(loginAs(Member.Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SALE_002"));
    }

    @Test
    @DisplayName("관리자가 행사 하나를 조회하면 시각과 행사 항목을 돌려주고, 없는 행사는 404, 일반 회원은 403이다")
    void getSaleEvent_returns200() throws Exception {
        when(saleEventDetailQueryService.getSaleEvent(5L)).thenReturn(new SaleEventDetailResponse(
                5L, SaleEventType.FLASH_DROP, SaleEventStatus.SCHEDULED,
                LocalDateTime.of(2099, 10, 1, 18, 0), LocalDateTime.of(2099, 10, 1, 20, 0),
                List.of(new SaleEventDetailResponse.SaleEventDetailItem(3L, "대파", 3000, 1500, 50, 10, 10))));
        when(saleEventDetailQueryService.getSaleEvent(6L))
                .thenThrow(new BusinessException(ErrorCode.SALE_EVENT_NOT_FOUND));

        mvc.perform(get("/api/admin/sale-events/5").with(loginAs(Member.Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saleEventId").value(5))
                .andExpect(jsonPath("$.data.items[0].productName").value("대파"))
                .andExpect(jsonPath("$.data.items[0].limitedQuantity").value(10));
        mvc.perform(get("/api/admin/sale-events/6").with(loginAs(Member.Role.ADMIN)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/sale-events/5").with(loginAs(Member.Role.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자가 할인 행사를 종료·삭제하면 200이고 서비스를 부르며, 일반 회원은 403이다")
    void endAndDeleteSaleEvent_returns200() throws Exception {
        mvc.perform(patch("/api/admin/sale-events/5/end").with(csrf()).with(loginAs(Member.Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mvc.perform(delete("/api/admin/sale-events/5").with(csrf()).with(loginAs(Member.Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(saleEventTerminationService).end(5L);
        verify(saleEventTerminationService).delete(5L);

        mvc.perform(patch("/api/admin/sale-events/5/end").with(csrf()).with(loginAs(Member.Role.USER)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/sale-events/5").with(csrf()).with(loginAs(Member.Role.USER)))
                .andExpect(status().isForbidden());
        verifyNoMoreInteractions(saleEventTerminationService);
    }

    @Test
    @DisplayName("종료·삭제할 수 없는 할인 행사는 409와 오류 코드, 없는 할인 행사는 404를 돌려준다")
    void endAndDeleteSaleEvent_serviceErrors_mapToStatus() throws Exception {
        doThrow(new BusinessException(ErrorCode.SALE_EVENT_NOT_ENDABLE)).when(saleEventTerminationService).end(5L);
        doThrow(new BusinessException(ErrorCode.SALE_EVENT_NOT_DELETABLE)).when(saleEventTerminationService).delete(5L);
        doThrow(new BusinessException(ErrorCode.SALE_EVENT_NOT_FOUND)).when(saleEventTerminationService).end(7L);

        mvc.perform(patch("/api/admin/sale-events/5/end").with(csrf()).with(loginAs(Member.Role.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SALE_004"));
        mvc.perform(delete("/api/admin/sale-events/5").with(csrf()).with(loginAs(Member.Role.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SALE_005"));
        mvc.perform(patch("/api/admin/sale-events/7/end").with(csrf()).with(loginAs(Member.Role.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SALE_002"));
    }

    private RequestPostProcessor loginAs(Member.Role role) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthSessionService.AuthPrincipal(7L, 8L, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        SaleEventCreateService saleEventCreateService() {
            return mock(SaleEventCreateService.class);
        }

        @Bean
        SaleCandidateProductQueryService saleCandidateProductQueryService() {
            return mock(SaleCandidateProductQueryService.class);
        }

        @Bean
        SaleEventListQueryService saleEventListQueryService() {
            return mock(SaleEventListQueryService.class);
        }

        @Bean
        SaleEventDetailQueryService saleEventDetailQueryService() {
            return mock(SaleEventDetailQueryService.class);
        }

        @Bean
        SaleEventUpdateService saleEventUpdateService() {
            return mock(SaleEventUpdateService.class);
        }

        @Bean
        SaleEventTerminationService saleEventTerminationService() {
            return mock(SaleEventTerminationService.class);
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
