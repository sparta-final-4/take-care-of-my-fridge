package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.chat")
public record ChatProperties(
        @DefaultValue("30")
        int dailyLimit,
        @DefaultValue("30m")
        Duration sessionTimeout
) {
}
