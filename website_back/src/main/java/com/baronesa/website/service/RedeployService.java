package com.baronesa.website.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedeployService {

    @Value("${app.redeploy.enabled:false}")
    private boolean enabled;

    @Value("${app.redeploy.trigger-path:/app/redeploy}")
    private String triggerPath;

    @Value("${app.redeploy.status-file:.redeploy_status}")
    private String statusFileName;

    private final ObjectMapper objectMapper;

    /**
     * Dispara o processo de redeploy criando o arquivo de sinal.
     */
    public boolean triggerRedeploy(String tenantId) {
        if (!enabled) {
            log.info("Redeploy solicitado para tenant {}, mas a feature está desabilitada.", tenantId);
            return false;
        }

        log.info("Iniciando processo de redeploy para tenant {}", tenantId);

        try {
            File dir = new File(triggerPath);
            if (!dir.exists() || !dir.isDirectory()) {
                log.error("Diretório de trigger não existe ou não é diretório: {}", triggerPath);
                return false;
            }

            // 1. Criar arquivo de sinal (.redeploy_signal)
            String signalPath = triggerPath + "/.redeploy_signal";
            try (FileWriter writer = new FileWriter(signalPath)) {
                ObjectNode metadata = objectMapper.createObjectNode();
                metadata.put("timestamp", System.currentTimeMillis());
                metadata.put("tenant", tenantId);
                metadata.put("requestId", UUID.randomUUID().toString());
                metadata.put("source", "theme_update");

                writer.write(objectMapper.writeValueAsString(metadata));
            }

            // 2. Criar arquivo de status inicial (.redeploy_status)
            String statusPath = triggerPath + "/" + statusFileName;
            try (FileWriter writer = new FileWriter(statusPath)) {
                writer.write("PENDING");
            }

            log.info("Sinal de redeploy criado com sucesso em {}", signalPath);
            return true;

        } catch (Exception e) {
            log.error("Erro ao criar sinal de redeploy", e);
            return false;
        }
    }

    /**
     * Verifica o status atual do redeploy.
     */
    public String getRedeployStatus() {
        if (!enabled) {
            return "DISABLED";
        }

        try {
            String statusPath = triggerPath + "/" + statusFileName;
            File statusFile = new File(statusPath);

            if (!statusFile.exists()) {
                // Se não existe arquivo de status, assumimos que não há redeploy em andamento
                // ou o último foi concluído e limpo (embora o script de monitor não limpe o status, ele deixa como COMPLETED/FAILED)
                // Se o script monitor limpar, retornamos IDLE. Se não, retornamos o que tiver lá.
                // Na lógica do script monitor: "echo 'COMPLETED' > status_file". Ele não apaga o arquivo.
                // Mas o script monitor apaga o arquivo de SIGNAL.
                return "IDLE";
            }

            return Files.readString(Path.of(statusPath)).trim();

        } catch (Exception e) {
            log.error("Erro ao ler status do redeploy", e);
            return "ERROR";
        }
    }
}
