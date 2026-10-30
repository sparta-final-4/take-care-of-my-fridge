package org.example.takecareofmyfridgebe.domain.deal.service;

import java.util.List;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;

public final class DealDiscountModel {

    public static final String VERSION = "historical-sale-linear-v1";
    private final double elasticity;

    private DealDiscountModel(double elasticity) {
        this.elasticity = elasticity;
    }

    public static DealDiscountModel train(List<Observation> observations) {
        if (observations == null || observations.size() < 3
                || observations.stream().map(Observation::discountRate).distinct().count() < 2) {
            throw new BusinessException(ErrorCode.DEAL_TRAINING_DATA_INSUFFICIENT);
        }
        double numerator = 0;
        double denominator = 0;
        for (Observation observation : observations) {
            if (observation.discountRate() < 1 || observation.discountRate() > 99
                    || observation.expectedRegularQuantity() <= 0 || observation.actualDiscountedQuantity() < 0) {
                throw new BusinessException(ErrorCode.DEAL_MODEL_INPUT_INVALID);
            }
            double exposure = observation.expectedRegularQuantity() * observation.discountRate() / 100.0;
            numerator += exposure * (observation.actualDiscountedQuantity()
                    - observation.expectedRegularQuantity());
            denominator += exposure * exposure;
        }
        double learnedElasticity = numerator / denominator;
        if (!Double.isFinite(learnedElasticity)) {
            throw new BusinessException(ErrorCode.DEAL_TRAINING_DATA_INSUFFICIENT);
        }
        return new DealDiscountModel(learnedElasticity);
    }

    public Estimate estimate(int regularQuantity, int discountRate, int currentStock) {
        if (regularQuantity < 0 || discountRate < 1 || discountRate > 99 || currentStock < 0) {
            throw new BusinessException(ErrorCode.DEAL_MODEL_INPUT_INVALID);
        }
        int expectedRegular = Math.min(regularQuantity, currentStock);
        double predicted = regularQuantity * (1 + elasticity * discountRate / 100.0);
        int expectedDiscount = (int) Math.round(Math.min(currentStock, Math.max(0, predicted)));
        return new Estimate(expectedRegular, expectedDiscount, expectedDiscount - expectedRegular);
    }

    public record Observation(int discountRate, double expectedRegularQuantity,
                              double actualDiscountedQuantity) {
    }

    public record Estimate(int expectedRegular, int expectedDiscount, int expectedIncrease) {
    }
}
