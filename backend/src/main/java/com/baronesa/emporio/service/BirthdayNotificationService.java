package com.baronesa.emporio.service;

import com.baronesa.emporio.entity.BirthdayNotificationLog;
import com.baronesa.emporio.entity.PerfilCliente;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.BirthdayNotificationLogRepository;
import com.baronesa.emporio.repository.UsuarioRepository;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BirthdayNotificationService {

    private final BirthdayNotificationLogRepository birthdayNotificationLogRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfigManager configManager;
    private final EspressoNotificationClient espressoNotificationClient;

    /**
     * Envia notificações de aniversário com base no tipo (PRE ou DAY)
     * @param tipo Tipo da notificação (PRE ou DAY)
     * @param diasAntes Dias antes do aniversário (usado apenas para PRE)
     */
    @Transactional
    /**
     * @return número de notificações efetivamente enviadas
     */
    public int sendBirthdayNotifications(String tipo, Integer diasAntes) {
        log.info("Iniciando envio de notificações de aniversário - Tipo: {}, Dias antes: {}", tipo, diasAntes);

        // Validar tipo
        BirthdayNotificationLog.NotificationType notificationType = validateNotificationType(tipo);
        if (notificationType == null) {
            log.error("Tipo de notificação inválido: {}", tipo);
            return 0;
        }

        // Obter configurações
        Integer configDiasAntes = configManager.getIntConfig("site_aniversario_dias_antes", 7);
        Boolean aniversarioAtivo = configManager.getBooleanConfig("site_aniversario_ativo", true);
        String mensagemPre = configManager.getConfig("site_aniversario_msg_pre",
            "Seu aniversário está chegando! Será uma honra receber você para comemorar.");
        String mensagemDia = configManager.getConfig("site_aniversario_msg_dia",
            "Feliz aniversário! Desejamos um dia incrível para você.");
        Double voucherValor = configManager.getDoubleConfig("site_aniversario_voucher_valor", 0.0);
        String voucherMensagem = configManager.getConfig("site_aniversario_voucher_msg",
            "Você terá um voucher de R$ {valor} disponível para celebrar.");

        // Verificar se aniversário está ativo
        if (!aniversarioAtivo) {
            log.info("Notificações de aniversário desativadas nas configurações");
            return 0;
        }

        // Determinar dias antes para tipo PRE
        if (notificationType == BirthdayNotificationLog.NotificationType.PRE) {
            if (diasAntes == null) {
                diasAntes = configDiasAntes;
            }
        } else if (diasAntes != null) {
            log.warn("Parâmetro diasAntes ignorado para tipo DAY, deve ser enviado apenas no dia do aniversário");
            diasAntes = 0;
        }

        // Obter clientes elegíveis
        List<Usuario> clientes = findEligibleClientes(notificationType, diasAntes);

        log.info("Encontrados {} clientes elegíveis para notificação tipo {}", clientes.size(), tipo);

        int enviados = 0;
        for (Usuario cliente : clientes) {
            try {
                // Verificar dedupe - se já foi enviado para este cliente no ano/tipo
                if (birthdayNotificationLogRepository.existsByClienteIdAndAnoAndTipo(
                        cliente.getId(), LocalDate.now().getYear(), notificationType)) {
                    log.debug("Notificação já enviada para cliente {} no ano {} tipo {}",
                        cliente.getId(), LocalDate.now().getYear(), tipo);
                    continue;
                }

                // Preparar mensagem
                String mensagem = prepareMessage(notificationType, mensagemPre, mensagemDia, voucherValor, voucherMensagem);

                // Enviar notificação via espresso_back
                boolean sent = sendNotificationToCliente(cliente, mensagem, notificationType, voucherValor);
                if (!sent) {
                    log.warn("Notificação de aniversário não enviada para cliente {}: {}",
                        cliente.getId(), cliente.getNome());
                    continue;
                }

                // Registrar no log de dedupe apenas se o envio foi bem-sucedido
                logNotification(cliente.getId(), notificationType);

                log.info("Notificação de aniversário enviada com sucesso para cliente {}: {}",
                    cliente.getId(), cliente.getNome());
                enviados++;

            } catch (Exception e) {
                log.error("Erro ao enviar notificação para cliente {}: {}", cliente.getId(), e.getMessage(), e);
            }
        }

        return enviados;
    }

    private BirthdayNotificationLog.NotificationType validateNotificationType(String tipo) {
        if (tipo == null) {
            return null;
        }
        try {
            return BirthdayNotificationLog.NotificationType.valueOf(tipo.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private List<Usuario> findEligibleClientes(BirthdayNotificationLog.NotificationType tipo, Integer diasAntes) {
        if (tipo == BirthdayNotificationLog.NotificationType.PRE) {
            if (diasAntes == null || diasAntes <= 0) {
                return List.of();
            }

            // Enviar para aniversários dentro da janela [1..diasAntes] dias
            Set<Long> seen = new HashSet<>();
            List<Usuario> result = new ArrayList<>();
            for (int offset = 1; offset <= diasAntes; offset++) {
                LocalDate targetDate = LocalDate.now().plusDays(offset);
                List<Usuario> dayMatches = usuarioRepository.findClientesByBirthdayMonthDay(
                    targetDate.getMonthValue(), targetDate.getDayOfMonth()
                );
                for (Usuario usuario : dayMatches) {
                    if (seen.add(usuario.getId())) {
                        result.add(usuario);
                    }
                }
            }
            return result;
        }

        // DAY: apenas aniversariantes de hoje
        LocalDate today = LocalDate.now();
        return usuarioRepository.findClientesByBirthdayMonthDay(today.getMonthValue(), today.getDayOfMonth());
    }

    private String prepareMessage(BirthdayNotificationLog.NotificationType tipo, String mensagemPre,
                                  String mensagemDia, Double voucherValor, String voucherMensagem) {
        String mensagem = tipo == BirthdayNotificationLog.NotificationType.PRE ? mensagemPre : mensagemDia;

        // Adicionar mensagem de voucher se aplicável
        if (voucherValor != null && voucherValor > 0) {
            String voucherMsg = voucherMensagem.replace("{valor}", "R$ " + String.format("%.2f", voucherValor));
            mensagem += " " + voucherMsg;
        }

        return mensagem;
    }

    private boolean sendNotificationToCliente(Usuario cliente, String mensagem,
                                              BirthdayNotificationLog.NotificationType tipo, Double voucherValor) {
        String titulo = tipo == BirthdayNotificationLog.NotificationType.PRE ?
            "Pré-aniversário" : "Feliz Aniversário!";

        String deeplink = voucherValor > 0 ? "/areacliente/recompensas" : "/areacliente/notificacoes";

        String payloadJson = String.format("{\"tipo\":\"%s\"}", tipo.name());

        // Enviar notificação via espresso_back
        return espressoNotificationClient.sendNotificationToUser(
            cliente.getId(),
            titulo,
            mensagem,
            null,  // imageUrl
            deeplink,
            "BIRTHDAY",
            payloadJson
        );
    }

    private void logNotification(Long clienteId, BirthdayNotificationLog.NotificationType tipo) {
        BirthdayNotificationLog logEntry = BirthdayNotificationLog.builder()
            .clienteId(clienteId)
            .ano(LocalDate.now().getYear())
            .tipo(tipo)
            .sentAt(LocalDateTime.now())
            .build();

        birthdayNotificationLogRepository.save(logEntry);
    }
}
