package com.baronesa.emporio.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SseEventsService {

    private final Map<Long, Set<SseEmitter>> emittersBySessaoMesa = new ConcurrentHashMap<>();
    private final Set<SseEmitter> kdsEmitters = ConcurrentHashMap.newKeySet();
    private final Set<SseEmitter> waiterEmitters = ConcurrentHashMap.newKeySet();

    public SseEmitter subscribe(Long sessaoMesaId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        emittersBySessaoMesa.computeIfAbsent(sessaoMesaId, k -> ConcurrentHashMap.newKeySet()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(sessaoMesaId, emitter));
        emitter.onTimeout(() -> removeEmitter(sessaoMesaId, emitter));
        emitter.onError(e -> removeEmitter(sessaoMesaId, emitter));

        // Send an initial ping
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .name("connected")
                    .data(Map.of("ts", Instant.now().toString()))
                    .id(String.valueOf(System.currentTimeMillis()))
                    .reconnectTime(3000);
            emitter.send(event);
        } catch (IOException e) {
            removeEmitter(sessaoMesaId, emitter);
        }

        return emitter;
    }

    public void publish(Long sessaoMesaId, String type, Object payload) {
        Set<SseEmitter> emitters = emittersBySessaoMesa.get(sessaoMesaId);
        if (emitters == null || emitters.isEmpty()) {
            log.debug("No emitters for sessaoMesaId={}, event type={}", sessaoMesaId, type);
            return;
        }
        log.debug("Publishing event type={} to {} emitters for sessaoMesaId={}", type, emitters.size(), sessaoMesaId);
        for (SseEmitter emitter : emitters) {
            try {
                SseEmitter.SseEventBuilder event = SseEmitter.event()
                        .name(type)
                        .data(payload, MediaType.APPLICATION_JSON);
                emitter.send(event);
            } catch (IOException e) {
                log.warn("Failed to send event type={} to emitter: {}", type, e.getMessage());
                safeComplete(sessaoMesaId, emitter, e);
            } catch (Exception e) {
                log.error("Unexpected error publishing event type={}: {}", type, e.getMessage(), e);
                safeComplete(sessaoMesaId, emitter, e);
            }
        }
    }

    private void safeComplete(Long sessaoMesaId, SseEmitter emitter, Exception e) {
        removeEmitter(sessaoMesaId, emitter);
        try {
            emitter.completeWithError(e);
        } catch (Exception ignored) {
            // Evitar erro de resposta já encerrada
        }
    }

    private void removeEmitter(Long sessaoMesaId, SseEmitter emitter) {
        Set<SseEmitter> set = emittersBySessaoMesa.get(sessaoMesaId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) emittersBySessaoMesa.remove(sessaoMesaId);
        }
    }

    public SseEmitter subscribeKds() {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        kdsEmitters.add(emitter);

        emitter.onCompletion(() -> kdsEmitters.remove(emitter));
        emitter.onTimeout(() -> kdsEmitters.remove(emitter));
        emitter.onError(e -> kdsEmitters.remove(emitter));

        // Send an initial ping
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .name("connected")
                    .data(Map.of("ts", Instant.now().toString()))
                    .id(String.valueOf(System.currentTimeMillis()))
                    .reconnectTime(3000);
            emitter.send(event);
        } catch (IOException e) {
            kdsEmitters.remove(emitter);
        }

        return emitter;
    }

    public SseEmitter subscribeWaiter() {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        waiterEmitters.add(emitter);

        emitter.onCompletion(() -> waiterEmitters.remove(emitter));
        emitter.onTimeout(() -> waiterEmitters.remove(emitter));
        emitter.onError(e -> waiterEmitters.remove(emitter));

        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .name("connected")
                    .data(Map.of("ts", Instant.now().toString()))
                    .id(String.valueOf(System.currentTimeMillis()))
                    .reconnectTime(3000);
            emitter.send(event);
        } catch (IOException e) {
            waiterEmitters.remove(emitter);
        }

        return emitter;
    }

    public void publishWaiter(String type, Object payload) {
        if (waiterEmitters.isEmpty()) {
            log.debug("No waiter emitters, event type={}", type);
            return;
        }
        log.debug("Publishing waiter event type={} to {} emitters", type, waiterEmitters.size());
        for (SseEmitter emitter : waiterEmitters) {
            try {
                SseEmitter.SseEventBuilder event = SseEmitter.event()
                        .name(type)
                        .data(payload, MediaType.APPLICATION_JSON);
                emitter.send(event);
            } catch (IOException e) {
                log.warn("Failed to send waiter event type={} to emitter: {}", type, e.getMessage());
                waiterEmitters.remove(emitter);
                safeCompleteKds(emitter, e);
            } catch (Exception e) {
                log.error("Unexpected error publishing waiter event type={}: {}", type, e.getMessage(), e);
                waiterEmitters.remove(emitter);
                safeCompleteKds(emitter, e);
            }
        }
    }

    public void publishKds(String type, Object payload) {
        if (type != null && type.startsWith("kds.delivery_")) {
            log.info("KDS SSE emit type={} emitters={} payload={}", type, kdsEmitters.size(), payload);
        }
        if (kdsEmitters.isEmpty()) {
            log.debug("No KDS emitters, event type={}", type);
            return;
        }
        log.debug("Publishing KDS event type={} to {} emitters", type, kdsEmitters.size());
        for (SseEmitter emitter : kdsEmitters) {
            try {
                SseEmitter.SseEventBuilder event = SseEmitter.event()
                        .name(type)
                        .data(payload, MediaType.APPLICATION_JSON);
                emitter.send(event);
            } catch (IOException e) {
                log.warn("Failed to send KDS event type={} to emitter: {}", type, e.getMessage());
                kdsEmitters.remove(emitter);
                safeCompleteKds(emitter, e);
            } catch (Exception e) {
                log.error("Unexpected error publishing KDS event type={}: {}", type, e.getMessage(), e);
                kdsEmitters.remove(emitter);
                safeCompleteKds(emitter, e);
            }
        }
    }

    private void safeCompleteKds(SseEmitter emitter, Exception e) {
        try {
            emitter.completeWithError(e);
        } catch (Exception ignored) {
            // Evitar erro de resposta já encerrada
        }
    }

    /**
     * Send periodic heartbeat to keep SSE connections alive
     * Runs every 15 seconds
     */
    @Scheduled(fixedRate = 15000)
    public void sendHeartbeat() {
        // Heartbeat for mesa emitters
        for (Map.Entry<Long, Set<SseEmitter>> entry : emittersBySessaoMesa.entrySet()) {
            Long sessaoMesaId = entry.getKey();
            Set<SseEmitter> emitters = entry.getValue();
            Set<SseEmitter> toRemove = ConcurrentHashMap.newKeySet();

            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("ping")
                            .data(Map.of("ts", Instant.now().toString())));
                } catch (Exception e) {
                    log.debug("Heartbeat failed for sessaoMesaId={}, marking for removal", sessaoMesaId);
                    toRemove.add(emitter);
                }
            }

            // Remove failed emitters outside the iteration
            toRemove.forEach(emitters::remove);
        }

        // Heartbeat for KDS emitters
        Set<SseEmitter> toRemove = ConcurrentHashMap.newKeySet();
        for (SseEmitter emitter : kdsEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("ping")
                        .data(Map.of("ts", Instant.now().toString())));
            } catch (Exception e) {
                log.debug("Heartbeat failed for KDS emitter, marking for removal");
                toRemove.add(emitter);
            }
        }

        // Remove failed emitters
        toRemove.forEach(kdsEmitters::remove);

        // Heartbeat for waiter emitters
        Set<SseEmitter> toRemoveWaiter = ConcurrentHashMap.newKeySet();
        for (SseEmitter emitter : waiterEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("ping")
                        .data(Map.of("ts", Instant.now().toString())));
            } catch (Exception e) {
                log.debug("Heartbeat failed for waiter emitter, marking for removal");
                toRemoveWaiter.add(emitter);
            }
        }
        toRemoveWaiter.forEach(waiterEmitters::remove);

        log.debug("Heartbeat sent. Mesa emitters: {}, KDS emitters: {}",
                emittersBySessaoMesa.values().stream().mapToInt(Set::size).sum(),
                kdsEmitters.size());
    }
}
