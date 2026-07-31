package com.baronesa.website.service;

import com.baronesa.website.dto.dashboard.ClientesDashboardResponses.AtivosResponse;
import com.baronesa.website.dto.dashboard.ClientesDashboardResponses.ClienteAppAtivoResponse;
import com.baronesa.website.dto.dashboard.ClientesDashboardResponses.EnviarBrindeRequest;
import com.baronesa.website.dto.dashboard.ClientesDashboardResponses.EnviarBrindeResponse;
import com.baronesa.website.dto.dashboard.ClientesDashboardResponses.OportunidadesResponse;
import com.baronesa.website.dto.dashboard.ClientesDashboardResponses.TokenOrfaoResponse;
import com.baronesa.website.entity.ClienteRef;
import com.baronesa.website.entity.NotificationSubscription;
import com.baronesa.website.repository.ClienteRefRepository;
import com.baronesa.website.repository.NotificationSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClientesDashboardService {

    private final ClienteRefRepository clienteRefRepository;
    private final NotificationSubscriptionRepository subscriptionRepository;
    private final RewardService rewardService;

    @Value("${erp.api.url}")
    private String erpApiUrl;

    @Value("${website.sync.api-key:default-key-for-dev}")
    private String websiteSyncApiKey;

    private final RestClient restClient = RestClient.create();

    public AtivosResponse getAtivos(int periodoDias) {
        LocalDate now = LocalDate.now();
        LocalDateTime nowDateTime = LocalDateTime.now();

        long totalClientes = 0;
        long novosPeriodo = 0;
        try {
            ClientesResumoDto resumo = buscarResumoClientesNoErp(periodoDias);
            if (resumo != null) {
                totalClientes = resumo.totalClientes();
                novosPeriodo = resumo.novosPeriodo();
            }
        } catch (Exception e) {
            log.warn("Falha ao buscar resumo de clientes no ERP, usando contagem local. erro={}", e.getMessage());
            totalClientes = clienteRefRepository.count();
            novosPeriodo = clienteRefRepository.countByCreatedAtAfter(now.minusDays(periodoDias).atStartOfDay());
        }

        List<NotificationSubscription> ativos = subscriptionRepository.findByActiveTrue();
        int appAtivos = (int) ativos.stream()
                .filter(ns -> ns.getUserId() != null)
                .map(NotificationSubscription::getUserId)
                .distinct()
                .count();

        int ativos7d = (int) ativos.stream()
                .filter(ns -> ns.getUserId() != null)
                .filter(ns -> ns.getLastSeenAt() != null && !ns.getLastSeenAt().isBefore(nowDateTime.minusDays(7)))
                .map(NotificationSubscription::getUserId)
                .distinct()
                .count();

        int tokensOrfaos = (int) ativos.stream()
                .filter(ns -> ns.getUserId() == null)
                .count();

        int adocao = totalClientes == 0 ? 0 : (int) Math.round((appAtivos * 100.0) / (double) totalClientes);

        return new AtivosResponse(
            (int) totalClientes,
            (int) novosPeriodo,
            appAtivos,
            ativos7d,
            tokensOrfaos,
            adocao,
            periodoDias
        );
    }

    public OportunidadesResponse getOportunidades(int periodoDias) {
        LocalDate now = LocalDate.now();
        LocalDateTime nowDateTime = LocalDateTime.now();

        List<ClienteAppAtivoResponse> appAtivos = getAppAtivos();

        List<ClienteAppAtivoResponse> novosComApp = appAtivos.stream()
                .filter(c -> c.createdAt() != null && !c.createdAt().isBefore(now.minusDays(periodoDias)))
                .toList();

        List<ClienteAppAtivoResponse> inativosApp = appAtivos.stream()
                .filter(c -> c.lastSeenAt() == null || c.lastSeenAt().isBefore(nowDateTime.minusDays(30).toLocalDate()))
                .toList();

        List<TokenOrfaoResponse> tokensOrfaos = subscriptionRepository.findByActiveTrue().stream()
                .filter(ns -> ns.getUserId() == null)
                .map(ns -> new TokenOrfaoResponse(
                        maskToken(ns.getToken()),
                        Optional.ofNullable(ns.getDeviceInfo()).orElse(""),
                        ns.getCreatedAt() != null ? ns.getCreatedAt().toLocalDate() : null
                ))
                .toList();

        return new OportunidadesResponse(novosComApp, inativosApp, tokensOrfaos);
    }

    public List<ClienteAppAtivoResponse> getAppAtivos() {
        List<NotificationSubscription> ativos = subscriptionRepository.findByActiveTrue();
        Map<Long, List<NotificationSubscription>> porUser = ativos.stream()
                .filter(ns -> ns.getUserId() != null)
                .collect(Collectors.groupingBy(NotificationSubscription::getUserId));

        List<Long> ids = porUser.keySet().stream().toList();
        Map<Long, Integer> saldos = buscarSaldosNoErp(ids);

        return ids.stream()
                .map(id -> {
                    Optional<ClienteRef> c = clienteRefRepository.findById(id);
                    List<NotificationSubscription> subs = porUser.getOrDefault(id, List.of());
                    LocalDateTime lastSeen = subs.stream()
                            .map(NotificationSubscription::getLastSeenAt)
                            .filter(ls -> ls != null)
                            .max(LocalDateTime::compareTo)
                            .orElse(null);
                    int tokensAtivos = subs.size();
                    return new ClienteAppAtivoResponse(
                            id,
                            c.map(ClienteRef::getNome).orElse("Cliente " + id),
                            c.map(ClienteRef::getEmail).orElse(""),
                            c.map(ClienteRef::getTelefone).orElse(null),
                            c.map(cr -> cr.getCreatedAt() != null ? cr.getCreatedAt().toLocalDate() : null).orElse(null),
                            lastSeen != null ? lastSeen.toLocalDate() : null,
                            tokensAtivos,
                            saldos.getOrDefault(id, 0)
                    );
                })
                .toList();
    }

    public EnviarBrindeResponse enviarBrinde(EnviarBrindeRequest request) {
        try {
            rewardService.createAndNotifyReward(
                    request.userId(),
                    "Brinde/Voucher",
                    Optional.ofNullable(request.mensagem()).orElse("Você recebeu um brinde!"),
                    null,
                    LocalDateTime.now().plusDays(7)
            );
            return new EnviarBrindeResponse(
                "OK",
                "Brinde/voucher enviado para o usuário " + request.userId()
            );
        } catch (Exception e) {
            log.error("Erro ao enviar brinde para userId={} - {}", request.userId(), e.getMessage(), e);
            return new EnviarBrindeResponse(
                "ERRO",
                "Não foi possível enviar o brinde: " + e.getMessage()
            );
        }
    }

    private Map<Long, Integer> buscarSaldosNoErp(List<Long> clienteIds) {
        if (clienteIds == null || clienteIds.isEmpty()) return new HashMap<>();
        try {
            String idsParam = clienteIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            String url = erpApiUrl + "/api/admin/gamificacao/saldos?ids=" + idsParam;
            Map<String, Integer> response = restClient.get()
                    .uri(url)
                    .header("X-ERP-KEY", websiteSyncApiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            // body Map may come com keys como String/Integer — normalizar para Long
            Map<Long, Integer> result = new HashMap<>();
            if (response != null) {
                response.forEach((k, v) -> {
                    try {
                        Long key = Long.parseLong(String.valueOf(k));
                        result.put(key, v != null ? v : 0);
                    } catch (NumberFormatException ignore) {
                        log.warn("Chave de saldo não numérica retornada pelo ERP: {}", k);
                    }
                });
            }
            return result;
        } catch (Exception e) {
            log.warn("Falha ao buscar saldos no ERP, usando pontos mock. erro={}", e.getMessage());
            return new HashMap<>();
        }
    }

    private ClientesResumoDto buscarResumoClientesNoErp(int rangeDias) {
        String url = erpApiUrl + "/api/analytics/clientes/resumo?range=" + rangeDias;
        return restClient.get()
                .uri(url)
                .header("X-ERP-KEY", websiteSyncApiKey)
                .retrieve()
                .body(ClientesResumoDto.class);
    }

    private record ClientesResumoDto(long totalClientes, long novosPeriodo, int periodoDias) {}

    private String maskToken(String token) {
        if (token == null) return "";
        if (token.length() <= 10) return token;
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
