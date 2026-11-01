package org.example.takecareofmyfridgebe.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.sse")
public record SseProperties(
        @DefaultValue("30m")
        Duration timeout,
        @DefaultValue("30s")
        Duration heartbeatInterval,
        @DefaultValue("5")
        int maxConnectionsPerMember
) {
}
