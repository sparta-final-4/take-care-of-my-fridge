package org.example.takecareofmyfridgebe.domain.deal.service;

import java.util.ArrayList;
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
        int sampleCount = observations == null ? 0 : observations.size();
        long rateCount = observations == null ? 0
                : observations.stream().map(Observation::discountRate).distinct().count();
        if (sampleCount < 3 || rateCount < 2) {
            List<String> missing = new ArrayList<>();
            if (sampleCount < 3) missing.add("종료 일반 특가 " + sampleCount + "/3건");
            if (rateCount < 2) missing.add("할인율 종류 " + rateCount + "/2개");
            throw new BusinessException(ErrorCode.DEAL_TRAINING_DATA_INSUFFICIENT,
                    "할인 효과 학습 자료 부족: " + String.join(", ", missing));
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
