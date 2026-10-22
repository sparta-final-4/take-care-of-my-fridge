package org.example.takecareofmyfridgebe.domain.member.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SignUpRequestTest {

    @Test
    void acceptsConfirmedPasswordRuleAndEmptyTastes() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            SignUpRequest request = new SignUpRequest("cook@example.com", "Abcdefg1!", "  요리왕  ",
                    "010-1234-5678", List.of());

            assertThat(request.nickname()).isEqualTo("요리왕");
            assertThat(request.normalizedPhone()).isEqualTo("01012345678");
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }

    @Test
    void rejectsPasswordsOutsideConfirmedRule() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            for (String password : List.of("A1!", "12345678!", "abcdefgh!", "abcdefgh1",
                    "Abcdefg1! ", "가나다라Abc1!", "Abcdefg1!" + "x".repeat(22))) {
                SignUpRequest request = new SignUpRequest("cook@example.com", password, "요리왕",
                        "01012345678", List.of());

                assertThat(validator.validate(request)).anySatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("password"));
            }
        }
    }

    @Test
    void rejectsMissingAndMalformedPhone() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            for (String phone : new String[]{null, "", "01112345678", "010-123-45678",
                    "010-1234-567", "0101234567a"}) {
                SignUpRequest request = new SignUpRequest("cook@example.com", "Abcdefg1!", "요리왕",
                        phone, List.of());

                assertThat(validator.validate(request)).anySatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("phone"));
            }
        }
    }
}
