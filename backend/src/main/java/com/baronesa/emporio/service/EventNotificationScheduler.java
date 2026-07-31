package com.baronesa.emporio.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventNotificationScheduler {

    private final EventNotificationService eventNotificationService;
    private static final AtomicBoolean STARTUP_ALREADY_RAN = new AtomicBoolean(false);

    /**
     * Executa o envio de notificações de eventos ao iniciar o sistema.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void sendEventNotificationsOnStartup() {
        if (!STARTUP_ALREADY_RAN.compareAndSet(false, true)) {
            log.info("Startup já executado para eventos, ignorando novo disparo");
            return;
        }
        log.info("Iniciando envio de notificações de eventos no startup...");

        try {
            int enviados = eventNotificationService.sendEventNotifications("PRE", null);
            log.info("Notificações de pré-evento enviadas no startup - total={}", enviados);
        } catch (Exception e) {
            log.error("Erro ao enviar notificações de pré-evento no startup", e);
        }

        try {
            int enviados = eventNotificationService.sendEventNotifications("DAY", null);
            log.info("Notificações de evento do dia enviadas no startup - total={}", enviados);
        } catch (Exception e) {
            log.error("Erro ao enviar notificações de evento do dia no startup", e);
        }

        log.info("Finalizado envio de notificações de eventos no startup");
    }
}
