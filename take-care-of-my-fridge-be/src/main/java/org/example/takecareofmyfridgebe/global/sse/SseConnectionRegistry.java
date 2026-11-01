package org.example.takecareofmyfridgebe.global.sse;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.global.config.SseProperties;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// 이 서버에 연결된 회원의 SSE 연결을 들고 있다. 한 회원이 탭을 여러 개 열 수 있어 회원마다 연결 목록을 둔다.
// 연결이 끊기거나 보내기에 실패하면 목록에서 뺀다. 서버 간 전달은 이 클래스가 하지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class SseConnectionRegistry {

    private final SseProperties sseProperties;
    private final TaskScheduler taskScheduler;
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long memberId) {
        SseEmitter emitter = new SseEmitter(sseProperties.timeout().toMillis());
        emitter.onCompletion(() -> remove(memberId, emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> remove(memberId, emitter));

        // 한도를 넘으면 가장 오래된 연결부터 목록에서 먼저 빼고, 닫는 일은 목록 밖에서 한다
        List<SseEmitter> evicted = new ArrayList<>();
        emitters.compute(memberId, (id, current) -> {
            List<SseEmitter> targets = current == null ? new CopyOnWriteArrayList<>() : current;
            while (!targets.isEmpty() && targets.size() >= sseProperties.maxConnectionsPerMember()) {
                evicted.add(targets.remove(0));
            }
            targets.add(emitter);
            return targets;
        });
        evicted.forEach(SseEmitter::complete);

        // 연결 직후 한 번 보내 두어야 브라우저가 연결 성공으로 본다
        send(memberId, emitter, SseEmitter.event().comment("connected"));
        return emitter;
    }

    public void send(Long memberId, String eventName, Object data) {
        List<SseEmitter> targets = emitters.get(memberId);
        if (targets == null) {
            return;
        }
        targets.forEach(emitter -> send(memberId, emitter, SseEmitter.event().name(eventName).data(data)));
    }

    // 프록시나 브라우저가 조용한 연결을 끊지 않도록 주기적으로 주석을 보낸다
    @PostConstruct
    void scheduleHeartbeat() {
        taskScheduler.scheduleWithFixedDelay(this::heartbeat, sseProperties.heartbeatInterval());
    }

    void heartbeat() {
        emitters.forEach((memberId, targets) -> targets.forEach(emitter ->
                send(memberId, emitter, SseEmitter.event().comment("heartbeat"))));
    }

    int connectionCount(Long memberId) {
        List<SseEmitter> targets = emitters.get(memberId);
        return targets == null ? 0 : targets.size();
    }

    private void send(Long memberId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            remove(memberId, emitter);
            log.debug("SSE 연결 정리: memberId={}", memberId);
        }
    }

    private void remove(Long memberId, SseEmitter emitter) {
        emitters.computeIfPresent(memberId, (id, targets) -> {
            targets.remove(emitter);
            return targets.isEmpty() ? null : targets;
        });
    }
}
