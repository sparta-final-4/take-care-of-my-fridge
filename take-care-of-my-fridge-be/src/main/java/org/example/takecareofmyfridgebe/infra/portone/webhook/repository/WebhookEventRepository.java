package org.example.takecareofmyfridgebe.infra.portone.webhook.repository;

import org.example.takecareofmyfridgebe.infra.portone.webhook.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    // PortOne이 같은 웹훅을 재전송하면 이전 기록의 처리 상태로 다시 처리할지 정한다
    Optional<WebhookEvent> findByWebhookId(String webhookId);

    boolean existsByWebhookId(String webhookId);
}
