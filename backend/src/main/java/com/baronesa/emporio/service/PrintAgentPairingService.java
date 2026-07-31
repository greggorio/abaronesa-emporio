package com.baronesa.emporio.service;

import com.baronesa.emporio.entity.PrintAgentPairing;
import com.baronesa.emporio.entity.PrintAgentStatus;
import com.baronesa.emporio.repository.PrintAgentPairingRepository;
import com.baronesa.emporio.repository.PrintAgentStatusRepository;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PrintAgentPairingService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_CLAIMED = "CLAIMED";

    private final PrintAgentPairingRepository pairingRepository;
    private final PrintAgentStatusRepository statusRepository;
    private final ConfigManager configManager;

    @Transactional
    public Map<String, Object> registerPending(String pairingCode, String agentToken, String storeName) {
        String normalizedCode = pairingCode != null ? pairingCode.trim().toUpperCase() : "";
        if (!StringUtils.hasText(normalizedCode)) {
            throw new IllegalArgumentException("Código de pareamento inválido");
        }

        PrintAgentPairing pairing = pairingRepository.findByCode(normalizedCode)
                .orElseGet(() -> PrintAgentPairing.builder().code(normalizedCode).build());

        pairing.setAgentToken(agentToken);
        pairing.setStoreName(StringUtils.hasText(storeName) ? storeName : "ERP");
        pairing.setStatus(STATUS_PENDING);

        pairingRepository.save(pairing);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Código registrado. O agente concluirá o pareamento automaticamente.");
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> claim(String pairingCode) {
        String normalizedCode = pairingCode != null ? pairingCode.trim().toUpperCase() : "";

        PrintAgentPairing pairing = pairingRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new IllegalArgumentException("Código de pareamento não encontrado"));

        if (!STATUS_PENDING.equalsIgnoreCase(pairing.getStatus())) {
            throw new IllegalStateException("Código já utilizado");
        }

        pairing.setStatus(STATUS_CLAIMED);
        pairingRepository.save(pairing);

        // Save the pairing details to config for later retrieval
        configManager.setConfig("print_agent_agent_id", configManager.getConfig("print_agent_agent_id", "agent-local-1"));
        configManager.setConfig("print_agent_erp_url", configManager.getConfig("print_agent_erp_url", "ws://localhost:8080/ws/print"));
        configManager.setConfig("print_agent_store_name", pairing.getStoreName());

        String agentId = configManager.getConfig("print_agent_agent_id", "agent-local-1");
        String erpWsUrl = configManager.getConfig("print_agent_erp_url", "ws://localhost:8080/ws/print");

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("agent_id", agentId);
        response.put("agent_token", pairing.getAgentToken());
        response.put("store_name", pairing.getStoreName());
        response.put("erp_url", erpWsUrl);
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStatus() {
        // Check if there's an active pairing by looking for recent pairings
        // For now, we'll return basic status information
        Map<String, Object> status = new HashMap<>();

        // Get the print agent URL from config
        String printAgentUrl = configManager.getConfig("print_agent_url", "http://localhost:8765");

        // Check if we have pairing information stored
        String agentId = configManager.getConfig("print_agent_agent_id", "");
        String erpWsUrl = configManager.getConfig("print_agent_erp_url", "");

        boolean isPaired = agentId != null && !agentId.isEmpty() && erpWsUrl != null && !erpWsUrl.isEmpty();

        status.put("paired", isPaired);
        status.put("print_agent_url", printAgentUrl);
        status.put("agent_id", agentId);

        // Check actual connection status based on heartbeat
        boolean isConnected = false;
        if (isPaired) {
            isConnected = isAgentConnected(agentId);
        }
        status.put("connected", isConnected);

        // If paired, we could potentially fetch more detailed status from the print agent
        if (isPaired) {
            status.put("store_name", configManager.getConfig("print_agent_store_name", "ERP"));
        } else {
            status.put("store_name", null);
        }

        return status;
    }

    /**
     * Check if the agent is connected based on the last heartbeat received
     * Consider connected if heartbeat was received within the last 60 seconds
     */
    private boolean isAgentConnected(String agentId) {
        Optional<PrintAgentStatus> statusOpt = statusRepository.findByAgentId(agentId);

        if (statusOpt.isPresent()) {
            PrintAgentStatus agentStatus = statusOpt.get();
            LocalDateTime lastHeartbeat = agentStatus.getLastHeartbeatAt();

            if (lastHeartbeat != null) {
                // Consider connected if heartbeat was received within last 60 seconds
                long minutesSinceHeartbeat = ChronoUnit.MINUTES.between(lastHeartbeat, LocalDateTime.now());
                return minutesSinceHeartbeat < 1; // Less than 1 minute
            }
        }

        return false;
    }

    @Transactional
    public void resetPairing() {
        // Clear the pairing-related configurations
        configManager.setConfig("print_agent_agent_id", "");
        configManager.setConfig("print_agent_erp_url", "");
        configManager.setConfig("print_agent_store_name", "");
    }

    /**
     * Update the heartbeat timestamp for a print agent
     */
    @Transactional
    public void updateHeartbeat(String agentId) {
        if (agentId == null || agentId.trim().isEmpty()) {
            return;
        }

        PrintAgentStatus status = statusRepository.findByAgentId(agentId)
            .orElse(PrintAgentStatus.builder()
                .agentId(agentId)
                .status("CONNECTED")
                .build());

        status.setLastHeartbeatAt(LocalDateTime.now());
        status.setStatus("CONNECTED");

        statusRepository.save(status);
    }

    /**
     * Mark an agent as disconnected
     */
    @Transactional
    public void markAgentDisconnected(String agentId) {
        if (agentId == null || agentId.trim().isEmpty()) {
            return;
        }

        statusRepository.findByAgentId(agentId).ifPresent(status -> {
            status.setStatus("DISCONNECTED");
            statusRepository.save(status);
        });
    }

}
