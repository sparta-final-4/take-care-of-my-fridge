package org.example.takecareofmyfridgebe.domain.fridge.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemCreateRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemLogResponse;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemLogSearchRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemTakeOutRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemTakeOutResponse;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemUpdateRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeRemoveReason;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeSortType;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemAction;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemStatus;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeSource;
import org.example.takecareofmyfridgebe.domain.fridge.facade.FridgeFacade;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.IngredientType;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FridgeController의 웹 계층(MockMvc + SecurityConfig) 슬라이스 테스트.
 * 인증 여부, 쿼리 파라미터 바인딩 및 기본값 적용, 재료 다건 등록·단건 수정·삭제 요청 검증을 확인한다.
 */
@WebMvcTest(FridgeController.class)
@Import({SecurityConfig.class, FridgeControllerTest.TestConfig.class})
class FridgeControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FridgeFacade fridgeFacade;

    @Autowired
    private FridgeService fridgeService;

    @BeforeEach
    void resetMock() {
        reset(fridgeFacade, fridgeService);
    }

    /**
     * 비로그인(미인증) 상태로 냉장고 재료 목록을 요청하면
     * 서비스를 호출하지 않고 401 Unauthorized(A003) 에러를 반환하는지 검증한다.
     */
    @Test
    void unauthenticatedRequestReturnsUnauthorized() throws Exception {
        // When & Then: 인증 정보 없이 GET 요청 시 401 응답 및 서비스 미호출 확인
        mvc.perform(get("/api/fridge/items"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(fridgeService);
    }

    /**
     * 쿼리 파라미터(storage, sort, imminentOnly)를 빈 문자열로 보내고 임의의 memberId 파라미터를 함께 보냈을 때:
     * 1) 회원 ID는 요청 파라미터(999)가 아닌 인증 토큰(7L)에서만 꺼내는지
     * 2) 빈 파라미터들이 기본값(storage=null, sort=EXPIRATION, imminentOnly=false)으로 보정되는지
     * 3) 응답 JSON 필드가 규격대로 직렬화되는지 검증한다.
     */
    @Test
    void getItemsWithEmptyQueryParamsUsesDefaultsAndTokenMemberId() throws Exception {
        // Given: 7번 회원의 기본 조건 조회 시 반환할 응답 DTO 준비
        FridgeItemResponse item = new FridgeItemResponse(
                1L,
                10L,
                "두부",
                "모",
                "국산 두부",
                new BigDecimal("1.00"),
                StorageType.REFRIGERATED,
                LocalDate.of(2026, 10, 20),
                FridgeSource.MANUAL,
                null,
                true,
                false,
                2L,
                FridgeItemStatus.IMMINENT,
                IngredientType.NORMAL,
                LocalDateTime.of(2026, 10, 18, 12, 0),
                LocalDateTime.of(2026, 10, 18, 12, 0)
        );
        when(fridgeService.getItems(7L, null, FridgeSortType.EXPIRATION, false))
                .thenReturn(List.of(item));

        // When & Then: 빈 쿼리 파라미터 및 위조된 memberId(999)를 담아 7번 회원으로 인증 요청
        mvc.perform(get("/api/fridge/items")
                        .param("storage", "")
                        .param("sort", "")
                        .param("imminentOnly", "")
                        .param("memberId", "999")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].ingredientId").value(10))
                .andExpect(jsonPath("$.data[0].ingredientName").value("두부"))
                .andExpect(jsonPath("$.data[0].baseUnit").value("모"))
                .andExpect(jsonPath("$.data[0].rawName").value("국산 두부"))
                .andExpect(jsonPath("$.data[0].quantity").value(1.00))
                .andExpect(jsonPath("$.data[0].storage").value("REFRIGERATED"))
                .andExpect(jsonPath("$.data[0].expirationDate").value("2026-10-20"))
                .andExpect(jsonPath("$.data[0].source").value("MANUAL"))
                .andExpect(jsonPath("$.data[0].imminent").value(true))
                .andExpect(jsonPath("$.data[0].dDay").value(2))
                .andExpect(jsonPath("$.data[0].status").value("IMMINENT"))
                .andExpect(jsonPath("$.data[0].expired").value(false));

        // 토큰에서 꺼낸 7L과 기본값들이 서비스에 전달되었는지 검증
        verify(fridgeService).getItems(7L, null, FridgeSortType.EXPIRATION, false);
    }

    /**
     * 보관방식(FROZEN), 정렬(NAME), 임박 필터(true) 조건을 모두 지정해 요청했을 때
     * 해당 조건들이 그대로 FridgeService에 전달되는지 검증한다.
     */
    @Test
    void getItemsWithFiltersPassesParametersToService() throws Exception {
        // Given
        when(fridgeService.getItems(7L, StorageType.FROZEN, FridgeSortType.NAME, true))
                .thenReturn(List.of());

        // When & Then
        mvc.perform(get("/api/fridge/items")
                        .param("storage", "FROZEN")
                        .param("sort", "NAME")
                        .param("imminentOnly", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(fridgeService).getItems(7L, StorageType.FROZEN, FridgeSortType.NAME, true);
    }

    /**
     * 정의되지 않은 보관방식(storage) 값을 전달하면
     * 바인딩 단계에서 거절되어 400 Bad Request(C001)를 반환하는지 검증한다.
     */
    @Test
    void getItemsWithInvalidStorageReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/fridge/items")
                        .param("storage", "INVALID_STORAGE")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

        verifyNoInteractions(fridgeService);
    }

    /**
     * 정의되지 않은 정렬 기준(sort) 값을 전달하면
     * 바인딩 단계에서 거절되어 400 Bad Request(C001)를 반환하는지 검증한다.
     */
    @Test
    void getItemsWithInvalidSortReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/fridge/items")
                        .param("sort", "INVALID_SORT")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

        verifyNoInteractions(fridgeService);
    }

    /**
     * 유효한 냉장고 재료 다건 등록 요청(POST /api/fridge/items) 시
     * 토큰의 회원 ID(7L)와 요청 본문이 FridgeFacade.createItems로 전달되고
     * 등록된 재료 목록이 반환되는지 검증한다.
     */
    @Test
    void createItemsUsesTokenMemberIdAndReturnsSavedItems() throws Exception {
        // Given
        FridgeItemResponse saved = new FridgeItemResponse(
                10L,
                1L,
                "두부",
                "모",
                "국산 두부 300g",
                new BigDecimal("1"),
                StorageType.REFRIGERATED,
                LocalDate.of(2026, 10, 24),
                FridgeSource.MANUAL,
                null,
                false,
                false,
                5L,
                FridgeItemStatus.NORMAL,
                IngredientType.NORMAL,
                LocalDateTime.of(2026, 10, 19, 11, 0),
                LocalDateTime.of(2026, 10, 19, 11, 0)
        );
        when(fridgeFacade.createItems(eq(7L), any(FridgeItemCreateRequest.class)))
                .thenReturn(List.of(saved));

        // When & Then
        mvc.perform(post("/api/fridge/items")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "rawName": "국산 두부 300g",
                                      "quantity": 1,
                                      "storage": null,
                                      "expirationDate": null
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].rawName").value("국산 두부 300g"))
                .andExpect(jsonPath("$.data[0].storage").value("REFRIGERATED"));

        verify(fridgeFacade).createItems(eq(7L), any(FridgeItemCreateRequest.class));
    }

    /**
     * 재료 등록 요청 검증 실패 케이스:
     * 1) items가 빈 배열(0개)일 때
     * 2) items가 21개(최대 20개 초과)일 때
     * 3) rawName이 빈 문자열 또는 공백일 때
     * 4) quantity가 0 또는 음수일 때
     * 모두 FridgeFacade를 호출하지 않고 400 Bad Request(C001)를 반환하는지 검증한다.
     */
    @Test
    void createItemsRejectsInvalidRequestsWithBadRequest() throws Exception {
        // 21개 항목 JSON 생성
        String twentyOneItems = IntStream.range(0, 21)
                .mapToObj(i -> "{\"rawName\":\"두부\",\"quantity\":1}")
                .collect(Collectors.joining(",", "{\"items\":[", "]}"));

        List<String> invalidBodies = List.of(
                // items 0개
                "{\"items\":[]}",
                // items 21개
                twentyOneItems,
                // rawName 빈 값
                "{\"items\":[{\"rawName\":\"   \",\"quantity\":1}]}",
                // quantity 0
                "{\"items\":[{\"rawName\":\"두부\",\"quantity\":0}]}",
                // quantity 음수
                "{\"items\":[{\"rawName\":\"두부\",\"quantity\":-1}]}"
        );

        for (String body : invalidBodies) {
            mvc.perform(post("/api/fridge/items")
                            .with(csrf())
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
        }

        verifyNoInteractions(fridgeFacade);
    }

    /**
     * 유효한 냉장고 재료 단건 수정 요청(PATCH /api/fridge/items/{fridgeId}) 시
     * 토큰의 회원 ID(7L), 경로 변수(100L), 요청 본문이 FridgeFacade.updateItem으로 전달되고
     * 수정된 재료 응답이 반환되는지 검증한다.
     */
    @Test
    void updateItemUsesTokenMemberIdAndReturnsUpdatedItem() throws Exception {
        // Given
        FridgeItemResponse updated = new FridgeItemResponse(
                100L,
                1L,
                "두부",
                "모",
                "두부",
                new BigDecimal("1"),
                StorageType.REFRIGERATED,
                LocalDate.of(2026, 10, 20),
                FridgeSource.MANUAL,
                null,
                true,
                false,
                1L,
                FridgeItemStatus.IMMINENT,
                IngredientType.NORMAL,
                LocalDateTime.of(2026, 10, 18, 10, 0),
                LocalDateTime.of(2026, 10, 19, 14, 0)
        );
        when(fridgeFacade.updateItem(eq(7L), eq(100L), any(FridgeItemUpdateRequest.class)))
                .thenReturn(updated);

        // When & Then
        mvc.perform(patch("/api/fridge/items/100")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rawName": "두부",
                                  "quantity": 1,
                                  "storage": "REFRIGERATED",
                                  "expirationDate": "2026-10-20"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.rawName").value("두부"))
                .andExpect(jsonPath("$.data.quantity").value(1))
                .andExpect(jsonPath("$.data.storage").value("REFRIGERATED"))
                .andExpect(jsonPath("$.data.expirationDate").value("2026-10-20"));

        verify(fridgeFacade).updateItem(eq(7L), eq(100L), any(FridgeItemUpdateRequest.class));
    }

    /**
     * 다른 회원의 재료(또는 존재하지 않는 fridgeId)를 수정하려고 할 때
     * BusinessException(ErrorCode.FRIDGE_ITEM_NOT_FOUND)이 발생하여
     * 404 Not Found(FRIDGE_001) 응답을 반환하는지 검증한다.
     */
    @Test
    void updateItemReturnsNotFoundWhenTargetBelongsToAnotherMember() throws Exception {
        // Given
        when(fridgeFacade.updateItem(eq(7L), eq(999L), any(FridgeItemUpdateRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.FRIDGE_ITEM_NOT_FOUND));

        // When & Then
        mvc.perform(patch("/api/fridge/items/999")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rawName": "두부",
                                  "quantity": 1,
                                  "storage": "REFRIGERATED",
                                  "expirationDate": "2026-10-20"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.FRIDGE_ITEM_NOT_FOUND.getCode()));
    }

    /**
     * 재료 수정 요청 검증 실패 케이스 (부분 수정이라 null인 칸은 허용):
     * 1) quantity를 보냈는데 0 또는 음수일 때
     * 2) rawName을 보냈는데 빈 문자열이거나 공백만일 때
     * 모두 FridgeFacade를 호출하지 않고 400 Bad Request(C001)를 반환하는지 검증한다.
     */
    @Test
    void updateItemRejectsInvalidRequestsWithBadRequest() throws Exception {
        List<String> invalidBodies = List.of(
                // quantity 0
                "{\"quantity\":0}",
                // quantity 음수
                "{\"rawName\":\"두부\",\"quantity\":-1}",
                // rawName 빈 문자열
                "{\"rawName\":\"\"}",
                // rawName 공백만
                "{\"rawName\":\"   \",\"expirationDate\":\"2026-10-20\"}"
        );

        for (String body : invalidBodies) {
            mvc.perform(patch("/api/fridge/items/100")
                            .with(csrf())
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
        }

        verifyNoInteractions(fridgeFacade);
    }

    /**
     * 냉장고 재료 빼기 요청(DELETE /api/fridge/items/{fridgeId}?reason=...) 시:
     * 1) reason=CONSUMED(다 먹었어요) 전달 시 FridgeService.removeItem 호출
     * 2) reason=DISCARDED(버렸어요) 전달 시 FridgeService.removeItem 호출
     * 3) reason=WRONG_ENTRY(잘못 등록해서 삭제) 전달 시 FridgeService.removeItem 호출
     * 모두 토큰의 회원 ID(7L)를 사용해 200 OK를 반환하는지 검증한다.
     */
    @Test
    void removeItemPassesTokenMemberIdAndReasonToService() throws Exception {
        for (FridgeRemoveReason reason : FridgeRemoveReason.values()) {
            mvc.perform(delete("/api/fridge/items/100")
                            .param("reason", reason.name())
                            .with(csrf())
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isEmpty());

            verify(fridgeService).removeItem(7L, 100L, reason);
        }
    }

    /**
     * 재료 빼기 시 reason 파라미터가 없거나, 빈 문자열이거나, 정의되지 않은 값(?reason=INVALID)이면
     * 서비스를 호출하지 않고 400 Bad Request(C001)를 반환하는지 검증한다.
     */
    @Test
    void removeItemWithMissingOrInvalidReasonReturnsBadRequest() throws Exception {
        // 1) reason 파라미터 아예 누락
        mvc.perform(delete("/api/fridge/items/100")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

        // 2) reason 파라미터가 빈 문자열
        mvc.perform(delete("/api/fridge/items/100")
                        .param("reason", "")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

        // 3) 정의되지 않은 reason 값
        mvc.perform(delete("/api/fridge/items/100")
                        .param("reason", "INVALID")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

        verifyNoInteractions(fridgeService);
    }

    /**
     * 다른 회원의 재료(또는 존재하지 않는 fridgeId)를 빼려고 할 때
     * FRIDGE_ITEM_NOT_FOUND(404) 응답이 반환되는지 검증한다.
     */
    @Test
    void removeItemReturnsNotFoundWhenTargetBelongsToAnotherMember() throws Exception {
        // Given
        doThrow(new BusinessException(ErrorCode.FRIDGE_ITEM_NOT_FOUND))
                .when(fridgeService).removeItem(7L, 999L, FridgeRemoveReason.CONSUMED);

        // When & Then
        mvc.perform(delete("/api/fridge/items/999")
                        .param("reason", "CONSUMED")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.FRIDGE_ITEM_NOT_FOUND.getCode()));
    }

    /**
     * 재료 먹음/버림 처리 요청 시 토큰의 회원 ID(7L)와 요청 본문(action, quantity)을 서비스에 넘기고,
     * 응답의 fridgeId·removed·remainingQuantity가 규격대로 직렬화되는지 검증한다.
     */
    @Test
    void takeOutItemPassesTokenMemberIdAndRequestToService() throws Exception {
        // Given
        when(fridgeService.takeOutItem(eq(7L), eq(100L), any(FridgeItemTakeOutRequest.class)))
                .thenReturn(new FridgeItemTakeOutResponse(100L, false, new BigDecimal("2.50")));

        // When & Then
        mvc.perform(post("/api/fridge/items/100/take-out")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CONSUMED\",\"quantity\":0.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fridgeId").value(100))
                .andExpect(jsonPath("$.data.removed").value(false))
                .andExpect(jsonPath("$.data.remainingQuantity").value(2.5));

        verify(fridgeService).takeOutItem(
                7L, 100L, new FridgeItemTakeOutRequest(FridgeItemAction.CONSUMED, new BigDecimal("0.5")));
    }

    /**
     * 재료 먹음/버림 요청 검증 실패 케이스:
     * action이 없거나 정의되지 않은 값이거나, quantity가 0 또는 음수이면
     * 서비스를 호출하지 않고 400 Bad Request(C001)를 반환하는지 검증한다.
     */
    @Test
    void takeOutItemRejectsInvalidRequestsWithBadRequest() throws Exception {
        List<String> invalidBodies = List.of(
                // action 누락
                "{\"quantity\":1}",
                // 정의되지 않은 action
                "{\"action\":\"WRONG_ENTRY\",\"quantity\":1}",
                // quantity 0
                "{\"action\":\"CONSUMED\",\"quantity\":0}",
                // quantity 음수
                "{\"action\":\"CONSUMED\",\"quantity\":-1}"
        );

        for (String body : invalidBodies) {
            mvc.perform(post("/api/fridge/items/100/take-out")
                            .with(csrf())
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
        }

        verifyNoInteractions(fridgeService);
    }

    /**
     * 밀키트를 소수 수량으로 빼려고 해 서비스가 FRIDGE_MEALKIT_QUANTITY_NOT_INTEGER를 던지면
     * 400 Bad Request(FRIDGE_005) 응답을 반환하는지 검증한다.
     */
    @Test
    void takeOutItemReturnsBadRequestWhenMealkitQuantityIsNotInteger() throws Exception {
        // Given
        when(fridgeService.takeOutItem(eq(7L), eq(100L), any(FridgeItemTakeOutRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.FRIDGE_MEALKIT_QUANTITY_NOT_INTEGER));

        // When & Then
        mvc.perform(post("/api/fridge/items/100/take-out")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CONSUMED\",\"quantity\":0.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value(ErrorCode.FRIDGE_MEALKIT_QUANTITY_NOT_INTEGER.getCode()));
    }

    /**
     * 쿼리 파라미터 없이 기록을 조회하면:
     * 1) 토큰의 회원 ID(7L)와 기본값(action·from·to = null, page = 1, size = 20)으로 서비스를 호출하는지
     * 2) PageResponse와 FridgeItemLogResponse 필드가 규격대로 직렬화되는지 검증한다.
     */
    @Test
    void getLogsWithoutParamsUsesDefaultsAndTokenMemberId() throws Exception {
        // Given
        FridgeItemLogResponse logResponse = new FridgeItemLogResponse(
                1L, 10L, "두부", "모", "국산 두부", new BigDecimal("0.50"),
                FridgeItemAction.DISCARDED, false, LocalDateTime.of(2026, 10, 18, 21, 30));
        when(fridgeService.getLogs(eq(7L), any(FridgeItemLogSearchRequest.class)))
                .thenReturn(new PageResponse<>(List.of(logResponse), 1, 20, 1, 1, true));

        // When & Then
        mvc.perform(get("/api/fridge/logs")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].ingredientId").value(10))
                .andExpect(jsonPath("$.data.content[0].ingredientName").value("두부"))
                .andExpect(jsonPath("$.data.content[0].baseUnit").value("모"))
                .andExpect(jsonPath("$.data.content[0].rawName").value("국산 두부"))
                .andExpect(jsonPath("$.data.content[0].quantity").value(0.5))
                .andExpect(jsonPath("$.data.content[0].action").value("DISCARDED"))
                .andExpect(jsonPath("$.data.content[0].cookedByRecipe").value(false))
                .andExpect(jsonPath("$.data.content[0].createdAt").value("2026-10-18T21:30:00"))
                .andExpect(jsonPath("$.data.pageNumber").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(fridgeService).getLogs(7L, new FridgeItemLogSearchRequest(null, null, null, 1, 20));
    }

    /**
     * action·from·to·page·size 파라미터가 그대로 바인딩되어 서비스에 전달되는지 검증한다.
     */
    @Test
    void getLogsBindsQueryParams() throws Exception {
        when(fridgeService.getLogs(eq(7L), any(FridgeItemLogSearchRequest.class)))
                .thenReturn(new PageResponse<>(List.of(), 2, 10, 0, 0, true));

        mvc.perform(get("/api/fridge/logs")
                        .param("action", "CONSUMED")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-10")
                        .param("page", "2")
                        .param("size", "10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isOk());

        verify(fridgeService).getLogs(7L, new FridgeItemLogSearchRequest(
                FridgeItemAction.CONSUMED, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10), 2, 10));
    }

    /**
     * 기록 조회 파라미터가 잘못되면(정의되지 않은 action, 날짜 형식 오류, page 0, size 0·101)
     * 서비스를 호출하지 않고 400 Bad Request(C001)를 반환하는지 검증한다.
     */
    @Test
    void getLogsWithInvalidParamsReturnsBadRequest() throws Exception {
        List<String[]> invalidParams = List.of(
                new String[]{"action", "WRONG_ENTRY"},
                new String[]{"from", "2026/10/01"},
                new String[]{"page", "0"},
                new String[]{"size", "0"},
                new String[]{"size", "101"}
        );

        for (String[] param : invalidParams) {
            mvc.perform(get("/api/fridge/logs")
                            .param(param[0], param[1])
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
        }

        verifyNoInteractions(fridgeService);
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        FridgeFacade fridgeFacade() {
            return mock(FridgeFacade.class);
        }

        @Bean
        FridgeService fridgeService() {
            return mock(FridgeService.class);
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
