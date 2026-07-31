package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.ClienteRefSyncRequest;
import com.baronesa.emporio.entity.PerfilCliente;
import com.baronesa.emporio.entity.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClienteRefSyncService {

    @Value("${website.sync.base-url:http://localhost:8085}")
    private String baseUrl;

    @Value("${website.sync.api-key:default-key-for-dev}")
    private String apiKey;

    private final RestClient restClient = RestClient.create();

    /**
     * Sincroniza os dados do cliente com o sistema website_back
     * @param usuario Usuário a ser sincronizado
     */
    public void sync(Usuario usuario) {
        try {
            // Verificar se o usuário é cliente antes de sincronizar
            if (!usuario.isCliente()) {
                log.debug("Usuário {} não é cliente, não sincronizado", usuario.getId());
                return;
            }

            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                log.error("Base URL do website não configurada. Verifique a configuração 'website.sync.base-url'");
                return;
            }

            if (apiKey == null || apiKey.trim().isEmpty()) {
                log.error("API Key do website não configurada. Verifique a configuração 'website.sync.api-key'");
                return;
            }

            // Preparar payload
            ClienteRefSyncRequest payload = buildPayload(usuario);

            // Enviar requisição para o website_back
            restClient.post()
                    .uri(baseUrl + "/api/clientes-ref/sync")
                    .header("X-ERP-KEY", apiKey)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            log.info("SYNC_CLIENTE_REF_OK userId={}", usuario.getId());

        } catch (RestClientException e) {
            log.error("SYNC_CLIENTE_REF_FAIL userId={} error={}", usuario.getId(), e.getMessage());
        } catch (Exception e) {
            log.error("SYNC_CLIENTE_REF_FAIL userId={} error={}", usuario.getId(), e.getMessage());
        }
    }

    /**
     * Executa a sincronização de forma assíncrona
     * @param usuario Usuário a ser sincronizado
     */
    public CompletableFuture<Void> syncAsync(Usuario usuario) {
        return CompletableFuture.runAsync(() -> sync(usuario));
    }

    /**
     * Constroi o payload para envio ao website_back
     * @param usuario Usuário a ser sincronizado
     * @return Payload formatado para sincronização
     */
    private ClienteRefSyncRequest buildPayload(Usuario usuario) {
        // Obter dados do perfil do cliente (se existir)
        PerfilCliente perfilCliente = usuario.getPerfilCliente();

        return new ClienteRefSyncRequest(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTelefone(),
                perfilCliente != null ? perfilCliente.getCpf() : null,
                perfilCliente != null ? perfilCliente.getDataNascimento() : null,
                usuario.getAtivo(),
                usuario.getAtualizadoEm() != null ? usuario.getAtualizadoEm() : LocalDateTime.now()
        );
    }
}