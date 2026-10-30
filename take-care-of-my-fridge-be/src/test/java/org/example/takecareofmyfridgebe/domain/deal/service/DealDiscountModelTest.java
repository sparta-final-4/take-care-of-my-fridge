package org.example.takecareofmyfridgebe.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.example.takecareofmyfridgebe.domain.deal.service.DealDiscountModel.Estimate;
import org.example.takecareofmyfridgebe.domain.deal.service.DealDiscountModel.Observation;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

class DealDiscountModelTest {

    @Test
    void learnsDiscountEffectFromActualEventSalesAndCapsPredictionAtStock() {
        DealDiscountModel model = DealDiscountModel.train(List.of(
                new Observation(10, 100, 120),
                new Observation(20, 100, 140),
                new Observation(30, 100, 160)));

        assertThat(model.estimate(10, 20, 100)).isEqualTo(new Estimate(10, 14, 4));
        assertThat(model.estimate(10, 30, 12)).isEqualTo(new Estimate(10, 12, 2));
    }

    @Test
    void rejectsInsufficientActualEventHistory() {
        assertThatThrownBy(() -> DealDiscountModel.train(List.of(
                new Observation(10, 100, 120), new Observation(10, 100, 110))))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.DEAL_TRAINING_DATA_INSUFFICIENT));
    }

    @Test
    void rejectsInvalidTrainingDataAsServerError() {
        assertThatThrownBy(() -> DealDiscountModel.train(List.of(
                new Observation(10, 100, 120), new Observation(20, 0, 140), new Observation(30, 100, 160))))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.DEAL_MODEL_INPUT_INVALID));
    }
}
