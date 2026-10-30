package org.example.takecareofmyfridgebe.domain.settlement.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementSearchRequestTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("오늘까지 고른 기간은 통과한다")
    void endDateToday_passes() {
        assertThat(messages(TODAY.minusDays(7), TODAY)).isEmpty();
    }

    @Test
    @DisplayName("종료일이 내일 이후면 막는다")
    void endDateAfterToday_fails() {
        assertThat(messages(TODAY, TODAY.plusDays(1))).containsExactly("종료일은 오늘까지 선택할 수 있습니다.");
    }

    @Test
    @DisplayName("시작일부터 3개월 전날까지는 고를 수 있다")
    void withinThreeMonths_passes() {
        LocalDate start = TODAY.minusMonths(4);

        assertThat(messages(start, start.plusMonths(3).minusDays(1))).isEmpty();
    }

    @Test
    @DisplayName("조회 기간이 3개월을 넘으면 막는다")
    void overThreeMonths_fails() {
        LocalDate start = TODAY.minusMonths(4);

        assertThat(messages(start, start.plusMonths(3))).containsExactly("조회 기간은 최대 3개월입니다.");
    }

    @Test
    @DisplayName("종료일이 시작일보다 빠르면 막는다")
    void endBeforeStart_fails() {
        assertThat(messages(TODAY, TODAY.minusDays(1))).containsExactly("종료일은 시작일과 같거나 늦어야 합니다.");
    }

    private List<String> messages(LocalDate start, LocalDate end) {
        return validator.validate(new SettlementSearchRequest(SalesPeriodUnit.DAY, start, end)).stream()
                .map(ConstraintViolation::getMessage)
                .toList();
    }
}
