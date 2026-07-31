package com.baronesa.emporio.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class BirthdayNotificationScheduler {

    private final BirthdayNotificationService birthdayNotificationService;
    private final TaskScheduler taskScheduler;
    private static final AtomicBoolean STARTUP_ALREADY_RAN = new AtomicBoolean(false);

    /**
     * Executa o envio de notificações de aniversário ao iniciar o sistema
     */
    @EventListener(ApplicationReadyEvent.class)
    public void scheduleBirthdayNotificationsOnStartup() {
        if (!STARTUP_ALREADY_RAN.compareAndSet(false, true)) {
            log.info("Startup já executado, ignorando novo disparo");
            return;
        }
        log.info("Agendando envio de notificações de aniversário pós startup...");
        taskScheduler.schedule(this::executeStartupNotifications, Instant.now().plusSeconds(5));
    }

    private void executeStartupNotifications() {
        log.info("Iniciando envio de notificações de aniversário no startup...");
        try {
            int enviados = birthdayNotificationService.sendBirthdayNotifications("PRE", null);
            log.info("Notificações de pré-aniversário enviadas no startup - total={}", enviados);
        } catch (Exception e) {
            log.error("Erro ao enviar notificações de pré-aniversário no startup", e);
        }

        try {
            int enviados = birthdayNotificationService.sendBirthdayNotifications("DAY", null);
            log.info("Notificações de aniversário do dia enviadas no startup - total={}", enviados);
        } catch (Exception e) {
            log.error("Erro ao enviar notificações de aniversário do dia no startup", e);
        }

        log.info("Finalizado envio de notificações de aniversário no startup");
    }

    /**
     * Executa o envio de notificações de aniversário diariamente às 09:00
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendDailyBirthdayNotifications() {
        log.info("Iniciando envio diário de notificações de aniversário...");
        
        try {
            // Enviar notificações de pré-aniversário
            birthdayNotificationService.sendBirthdayNotifications("PRE", null);
            log.info("Notificações de pré-aniversário enviadas com sucesso");
        } catch (Exception e) {
            log.error("Erro ao enviar notificações de pré-aniversário", e);
        }

        try {
            // Enviar notificações de aniversário do dia
            birthdayNotificationService.sendBirthdayNotifications("DAY", null);
            log.info("Notificações de aniversário do dia enviadas com sucesso");
        } catch (Exception e) {
            log.error("Erro ao enviar notificações de aniversário do dia", e);
        }
        
        log.info("Finalizado envio diário de notificações de aniversário");
    }
}
