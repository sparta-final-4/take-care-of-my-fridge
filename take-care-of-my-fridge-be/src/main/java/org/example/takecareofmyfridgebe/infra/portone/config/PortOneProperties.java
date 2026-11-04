package org.example.takecareofmyfridgebe.infra.portone.config;

import org.example.takecareofmyfridgebe.domain.payment.entity.PgProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "portone")
public record PortOneProperties(
        @DefaultValue("https://api.portone.io")
        String baseUrl,
        String apiSecret,
        String storeId,
        Map<PgProvider, String> channels, // 결제대행사별 PortOne 채널 키
        String billingChannelKey, // 카드 등록(빌링키 발급)과 등록 카드 결제에 쓰는 정기결제 채널 키
        String webhookSecret
) {
    // 고를 수 있는 결제대행사의 채널 키가 하나라도 없으면 결제창을 열 수 없으므로 기동을 멈춘다
    public PortOneProperties {
        Map<PgProvider, String> configured = channels == null ? Map.of() : channels;
        List<PgProvider> missing = Arrays.stream(PgProvider.values())
                .filter(pgProvider -> configured.get(pgProvider) == null || configured.get(pgProvider).isBlank())
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("PortOne 채널 키 누락: " + missing);
        }
        channels = Map.copyOf(configured);
        if (billingChannelKey == null || billingChannelKey.isBlank()) {
            throw new IllegalStateException("PortOne 빌링 채널 키 누락");
        }
    }

    public String channelKeyOf(PgProvider pgProvider) {
        return channels.get(pgProvider);
    }
}
