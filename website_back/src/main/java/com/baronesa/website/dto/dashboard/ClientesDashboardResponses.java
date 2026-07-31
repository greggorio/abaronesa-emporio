package com.baronesa.website.dto.dashboard;

import java.time.LocalDate;
import java.util.List;

public class ClientesDashboardResponses {

    public record AtivosResponse(
        int totalClientes,
        int novosPeriodo,
        int appAtivos,
        int ativos7d,
        int tokensOrfaos,
        int adocaoPercentual,
        int periodoDias
    ) {}

    public record ClienteAppAtivoResponse(
        Long id,
        String nome,
        String email,
        String telefone,
        LocalDate createdAt,
        LocalDate lastSeenAt,
        int tokensAtivos,
        int pontos
    ) {}

    public record TokenOrfaoResponse(
        String token,
        String deviceInfo,
        LocalDate createdAt
    ) {}

    public record OportunidadesResponse(
        List<ClienteAppAtivoResponse> novosComApp,
        List<ClienteAppAtivoResponse> inativosApp,
        List<TokenOrfaoResponse> tokensOrfaos
    ) {}

    public record EnviarBrindeRequest(
        Long userId,
        String mensagem
    ) {}

    public record EnviarBrindeResponse(
        String status,
        String detalhes
    ) {}
}
