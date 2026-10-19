package org.example.takecareofmyfridgebe.infra.portone.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "portone")
public record PortOneProperties(
        @DefaultValue("https://api.portone.io")
        String baseUrl,
        String apiSecret,
        String storeId,
        String channelKey,
        String webhookSecret
) {
}
