package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.chat")
public record ChatProperties(
        @DefaultValue("30")
        int dailyLimit,
        @DefaultValue("30m")
        Duration sessionTimeout,
        // 한 상담의 답 받기 잠금 유지 시간. 요청 타임아웃보다 길게 두어, 해제를 못 한 경우의 안전망으로만 쓴다
        @DefaultValue("150s")
        Duration replyLockTtl
) {
}
