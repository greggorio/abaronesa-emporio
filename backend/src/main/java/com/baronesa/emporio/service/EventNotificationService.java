package com.baronesa.emporio.service;

import com.baronesa.emporio.entity.EventNotificationLog;
import com.baronesa.emporio.repository.EventNotificationLogRepository;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventNotificationService {

    private final EventNotificationLogRepository eventNotificationLogRepository;
    private final EventoEspressoService eventoEspressoService;
    private final ConfigManager configManager;
    private final EspressoNotificationClient espressoNotificationClient;

    /**
     * @return número de notificações efetivamente enviadas
     */
    @Transactional
    public int sendEventNotifications(String tipo, Integer diasAntes) {
        log.info("Iniciando envio de notificações de evento - Tipo: {}, Dias antes: {}", tipo, diasAntes);

        EventNotificationLog.NotificationType notificationType = validateNotificationType(tipo);
        if (notificationType == null) {
            log.error("Tipo de notificação inválido: {}", tipo);
            return 0;
        }

        boolean ativo = configManager.getBooleanConfig("site_evento_ativo", true);
        if (!ativo) {
            log.info("Notificações de eventos desativadas nas configurações");
            return 0;
        }

        int configDiasAntes = configManager.getIntConfig("site_evento_dias_antes", 7);
        String msgPre = configManager.getConfig("site_evento_msg_pre",
                "Vem aí: {evento}. Esperamos você para esse momento especial!");
        String msgDay = configManager.getConfig("site_evento_msg_day",
                "Hoje tem {evento}! Será um prazer receber você.");
        String deeplink = configManager.getConfig("site_evento_deeplink", "/areacliente/eventos");

        if (notificationType == EventNotificationLog.NotificationType.PRE && diasAntes == null) {
            diasAntes = configDiasAntes;
        }

        List<Map<String, Object>> eventos = eventoEspressoService.listarEventosProximos();
        if (eventos.isEmpty()) {
            log.info("Nenhum evento encontrado para notificação");
            return 0;
        }

        List<EventoResumo> elegiveis = filtrarEventos(eventos, notificationType, diasAntes);
        log.info("Encontrados {} eventos elegíveis para notificação tipo {}", elegiveis.size(), tipo);

        int enviados = 0;
        for (EventoResumo evento : elegiveis) {
            int ano = evento.data().getYear();
            if (eventNotificationLogRepository.existsByEventoIdAndAnoAndTipo(
                    evento.id(), ano, notificationType)) {
                log.debug("Notificação já enviada para evento {} no ano {} tipo {}", evento.id(), ano, tipo);
                continue;
            }

            String message = buildMessage(notificationType, msgPre, msgDay, evento);
            String payloadJson = String.format("{\"tipo\":\"%s\",\"eventoId\":%d}", notificationType.name(), evento.id());

            boolean sent = espressoNotificationClient.sendNotificationToBroadcast(
                    "Evento: " + evento.titulo(),
                    message,
                    null,
                    deeplink,
                    "EVENT",
                    payloadJson
            );

            if (!sent) {
                log.warn("Falha ao enviar notificação do evento {}", evento.id());
                continue;
            }

            logNotification(evento.id(), ano, notificationType);
            enviados++;
        }

        return enviados;
    }

    private EventNotificationLog.NotificationType validateNotificationType(String tipo) {
        if (tipo == null) return null;
        try {
            return EventNotificationLog.NotificationType.valueOf(tipo.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private List<EventoResumo> filtrarEventos(List<Map<String, Object>> eventos,
                                              EventNotificationLog.NotificationType tipo,
                                              Integer diasAntes) {
        LocalDate today = LocalDate.now();
        List<EventoResumo> elegiveis = new ArrayList<>();

        for (Map<String, Object> eventoMap : eventos) {
            EventoResumo evento = toEventoResumo(eventoMap);
            if (evento == null) continue;

            LocalDate dataEvento = evento.data().toLocalDate();
            if (tipo == EventNotificationLog.NotificationType.DAY) {
                if (dataEvento.equals(today)) {
                    elegiveis.add(evento);
                }
                continue;
            }

            if (diasAntes == null || diasAntes <= 0) {
                continue;
            }

            LocalDate start = today.plusDays(1);
            LocalDate end = today.plusDays(diasAntes);
            if (!dataEvento.isBefore(start) && !dataEvento.isAfter(end)) {
                elegiveis.add(evento);
            }
        }

        return elegiveis;
    }

    private EventoResumo toEventoResumo(Map<String, Object> eventoMap) {
        try {
            Object idObj = eventoMap.get("id");
            Object tituloObj = eventoMap.get("titulo");
            Object dataObj = eventoMap.get("dataEvento");
            if (idObj == null || tituloObj == null || dataObj == null) return null;

            Long id = idObj instanceof Number ? ((Number) idObj).longValue() : Long.valueOf(idObj.toString());
            String titulo = tituloObj.toString();
            LocalDateTime data = parseDateTime(dataObj.toString());

            return new EventoResumo(id, titulo, data);
        } catch (Exception e) {
            log.warn("Evento inválido para notificação: {}", eventoMap);
            return null;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return LocalDate.parse(value.substring(0, 10)).atStartOfDay();
        }
    }

    private String buildMessage(EventNotificationLog.NotificationType tipo,
                                String msgPre,
                                String msgDay,
                                EventoResumo evento) {
        String template = tipo == EventNotificationLog.NotificationType.PRE ? msgPre : msgDay;
        String dataFormatada = evento.data().toLocalDate().toString();
        return template
                .replace("{evento}", evento.titulo())
                .replace("{data}", dataFormatada);
    }

    private void logNotification(Long eventoId, Integer ano, EventNotificationLog.NotificationType tipo) {
        EventNotificationLog logEntry = EventNotificationLog.builder()
                .eventoId(eventoId)
                .ano(ano)
                .tipo(tipo)
                .sentAt(LocalDateTime.now())
                .build();
        eventNotificationLogRepository.save(logEntry);
    }

    private record EventoResumo(Long id, String titulo, LocalDateTime data) {}
}
