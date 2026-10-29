package org.example.takecareofmyfridgebe.domain.deal.service;

import java.util.List;

public final class DealDiscountModel {

    public static final String VERSION = "historical-sale-linear-v1";
    private final double elasticity;

    private DealDiscountModel(double elasticity) {
        this.elasticity = elasticity;
    }

    public static DealDiscountModel train(List<Observation> observations) {
        if (observations == null || observations.size() < 3
                || observations.stream().map(Observation::discountRate).distinct().count() < 2) {
            throw new IllegalStateException("할인 효과를 학습할 행사 이력이 부족합니다.");
        }
        double numerator = 0;
        double denominator = 0;
        for (Observation observation : observations) {
            if (observation.discountRate() < 1 || observation.discountRate() > 99
                    || observation.expectedRegularQuantity() <= 0 || observation.actualDiscountedQuantity() < 0) {
                throw new IllegalArgumentException("할인 행사 학습 자료가 올바르지 않습니다.");
            }
            double exposure = observation.expectedRegularQuantity() * observation.discountRate() / 100.0;
            numerator += exposure * (observation.actualDiscountedQuantity()
                    - observation.expectedRegularQuantity());
            denominator += exposure * exposure;
        }
        double learnedElasticity = numerator / denominator;
        if (!Double.isFinite(learnedElasticity)) {
            throw new IllegalStateException("할인 효과를 학습할 행사 이력이 부족합니다.");
        }
        return new DealDiscountModel(learnedElasticity);
    }

    public Estimate estimate(int regularQuantity, int discountRate, int currentStock) {
        if (regularQuantity < 0 || discountRate < 1 || discountRate > 99 || currentStock < 0) {
            throw new IllegalArgumentException("할인 예측 입력이 올바르지 않습니다.");
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
