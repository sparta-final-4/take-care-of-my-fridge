package org.example.takecareofmyfridgebe.infra.portone.dto;

// PortOne V2 빌링키 결제 요청(POST /payments/{paymentId}/billing-key)
public record PortOneBillingKeyPaymentRequest(
        String storeId,
        String billingKey,
        String orderName,
        Customer customer,
        Amount amount,
        String currency
) {
    private static final String CURRENCY_KRW = "KRW";

    public static PortOneBillingKeyPaymentRequest of(String storeId, String billingKey, String orderName, long amount,
                                                     String customerId) {
        return new PortOneBillingKeyPaymentRequest(storeId, billingKey, orderName, new Customer(customerId),
                new Amount(amount), CURRENCY_KRW);
    }

    public record Customer(String id) {
    }

    public record Amount(long total) {
    }
}
