package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.ClienteOptionDTO;
import com.baronesa.emporio.dto.DashboardGamificacaoResponse;
import com.baronesa.emporio.dto.RecompensaClienteDTO;
import com.baronesa.emporio.entity.Recompensa;
import com.baronesa.emporio.entity.TipoRecompensa;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.enums.TipoMovimentoPontos;
import com.baronesa.emporio.repository.MovimentoPontosRepository;
import com.baronesa.emporio.repository.RecompensaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardGamificacaoService {

    private final MovimentoPontosRepository movimentoPontosRepository;
    private final RecompensaRepository recompensaRepository;
    private final GamificacaoService gamificacaoService;

    public DashboardGamificacaoResponse getDashboardAdmin() {
        // Calcular KPIs
        int recompensasAtivas = recompensaRepository.countAtivas();
        int participantesComPontos = movimentoPontosRepository.countParticipantesComPontos(TipoMovimentoPontos.GANHO);

        LocalDateTime trintaDiasAtras = LocalDateTime.now().minusDays(30);
        Integer pontosEmitidosUltimos30Dias = movimentoPontosRepository.sumPontosEmitidosDesde(TipoMovimentoPontos.GANHO, trintaDiasAtras);
        if (pontosEmitidosUltimos30Dias == null) {
            pontosEmitidosUltimos30Dias = 0;
        }
        
        int adesoesUltimos30Dias = movimentoPontosRepository.countAdesoesDesde(trintaDiasAtras);

        int saldoTotalAtivo = calcularSaldoTotalAtivo();

        Integer pontosResgatadosUltimos30Dias = movimentoPontosRepository.sumPontosResgatadosDesde(trintaDiasAtras);
        if (pontosResgatadosUltimos30Dias == null) {
            pontosResgatadosUltimos30Dias = 0;
        }

        // Calcular taxa de resgate
        double taxaResgate = 0.0;
        if (pontosEmitidosUltimos30Dias > 0) {
            taxaResgate = (double) pontosResgatadosUltimos30Dias / pontosEmitidosUltimos30Dias;
        }

        // Criar KPIs
        DashboardGamificacaoResponse.KPIs kpis = new DashboardGamificacaoResponse.KPIs(
            recompensasAtivas,
            participantesComPontos,
            adesoesUltimos30Dias,
            pontosEmitidosUltimos30Dias,
            saldoTotalAtivo,
            pontosResgatadosUltimos30Dias,
            taxaResgate
        );

        // Obter rankings
        List<DashboardGamificacaoResponse.ClientePontos> topPontuadores = 
            movimentoPontosRepository.findTopPontuadoresDesdeRaw(trintaDiasAtras).stream()
            .map(row -> new DashboardGamificacaoResponse.ClientePontos(
                ((Number) row[0]).longValue(), // clienteId
                (String) row[1], // nome
                ((Number) row[2]).intValue() // pontos
            ))
            .collect(Collectors.toList());

        List<DashboardGamificacaoResponse.ClienteSaldo> topSaldos = 
            movimentoPontosRepository.findTopSaldosRaw().stream()
            .map(row -> new DashboardGamificacaoResponse.ClienteSaldo(
                ((Number) row[0]).longValue(), // clienteId
                (String) row[1], // nome
                ((Number) row[2]).intValue() // saldo
            ))
            .collect(Collectors.toList());

        // Obter últimos resgates
        List<DashboardGamificacaoResponse.UltimoResgate> ultimosResgates = 
            movimentoPontosRepository.findUltimosResgatesRaw().stream()
            .map(row -> new DashboardGamificacaoResponse.UltimoResgate(
                ((Number) row[0]).longValue(), // clienteId
                (String) row[1], // clienteNome
                ((Number) row[2]).longValue(), // recompensaId
                (String) row[3], // recompensaNome
                ((Number) row[4]).intValue(), // pontos
                row[5] != null ? ((java.sql.Timestamp) row[5]).toLocalDateTime() : null // dataHora
            ))
            .collect(Collectors.toList());

        // Obter top recompensas resgatadas nos últimos 30 dias
        List<DashboardGamificacaoResponse.TopRecompensaResgatada> topRecompensasResgatadas = 
            movimentoPontosRepository.findTopRecompensasResgatadasUltimos30Dias(trintaDiasAtras).stream()
            .map(row -> new DashboardGamificacaoResponse.TopRecompensaResgatada(
                ((Number) row[0]).longValue(), // recompensaId
                (String) row[1], // nome
                ((Number) row[2]).longValue(), // totalResgates
                ((Number) row[3]).intValue() // pontosTotalResgatado
            ))
            .collect(Collectors.toList());

        DashboardGamificacaoResponse.Rankings rankings = new DashboardGamificacaoResponse.Rankings(
            topPontuadores,
            topSaldos,
            ultimosResgates,
            topRecompensasResgatadas
        );

        return new DashboardGamificacaoResponse(kpis, rankings);
    }

    public List<RecompensaClienteDTO> getRecompensasDisponiveisParaCliente(Long clienteId) {
        // Obter todas as recompensas disponíveis (ativas, válidas e com estoque)
        List<Recompensa> recompensasDisponiveis = recompensaRepository.findDisponiveis();

        // Obter saldo do cliente
        Integer saldo = gamificacaoService.getSaldoCliente(clienteId);

        // Mapear para DTO com informações de elegibilidade baseadas no cliente especificado
        // e filtrar apenas as recompensas que o cliente pode resgatar
        return recompensasDisponiveis.stream()
                .map(recompensa -> mapToClienteDTO(recompensa, saldo))
                .filter(dto -> Boolean.TRUE.equals(dto.podeResgatar())) // Filtrar apenas as elegíveis
                .collect(Collectors.toList());
    }

    private RecompensaClienteDTO mapToClienteDTO(Recompensa recompensa, Integer saldo) {
        // Determinar se a recompensa está disponível (verificação interna do sistema)
        boolean disponivel = isRecompensaDisponivel(recompensa);
        
        // Determinar se o cliente pode resgatar
        boolean podeResgatar = disponivel && saldo >= recompensa.getPontosNecessarios();
        
        // Calcular pontos faltantes
        int faltamPontos = Math.max(0, recompensa.getPontosNecessarios() - (saldo != null ? saldo : 0));

        // Campos específicos por tipo
        Long produtoId = null;
        BigDecimal descontoPercentual = null;
        BigDecimal descontoValor = null;
        BigDecimal descontoValorMaximo = null;

        if (recompensa.getTipo() == TipoRecompensa.PRODUTO) {
            produtoId = recompensa.getProdutoId();
        } else if (recompensa.getTipo() == TipoRecompensa.DESCONTO_PERCENTUAL) {
            descontoPercentual = recompensa.getDescontoPercentual();
            descontoValorMaximo = recompensa.getDescontoValorMaximo();
        } else if (recompensa.getTipo() == TipoRecompensa.DESCONTO_VALOR) {
            descontoValor = recompensa.getDescontoValor();
        }

        return new RecompensaClienteDTO(
                recompensa.getId(),
                recompensa.getNome(),
                recompensa.getDescricao(),
                recompensa.getTipo(),
                recompensa.getPontosNecessarios(),
                recompensa.getImageUrl(),
                produtoId,
                descontoPercentual,
                descontoValor,
                descontoValorMaximo,
                recompensa.getValidadeInicio(),
                recompensa.getValidadeFim(),
                disponivel,
                podeResgatar,
                faltamPontos
        );
    }

    private boolean isRecompensaDisponivel(Recompensa recompensa) {
        // Verificar se está ativa
        if (!recompensa.getAtivo()) {
            return false;
        }

        // Verificar validade
        LocalDate hoje = LocalDate.now();
        if (recompensa.getValidadeInicio() != null && hoje.isBefore(recompensa.getValidadeInicio())) {
            return false;
        }
        if (recompensa.getValidadeFim() != null && hoje.isAfter(recompensa.getValidadeFim())) {
            return false;
        }

        // Verificar estoque
        if (recompensa.getEstoque() != null && recompensa.getEstoque() <= 0) {
            return false;
        }

        return true;
    }

    private int calcularSaldoTotalAtivo() {
        // Obter o saldo mais recente para cada cliente e somar
        // Usando a mesma lógica da query findTopSaldosRaw para obter saldos atuais
        List<Object[]> resultados = movimentoPontosRepository.findTopSaldosRaw();
        return resultados.stream()
            .mapToInt(row -> ((Number) row[2]).intValue()) // saldo
            .sum();
    }

    public List<ClienteOptionDTO> getClientesComPontos() {
        // Buscar clientes com role 'CLIENTE' que têm movimentos de pontos
        List<Object[]> resultados = movimentoPontosRepository.findClientesComPontosRaw();
        return resultados.stream()
            .map(row -> new ClienteOptionDTO(
                ((Number) row[0]).longValue(), // id
                (String) row[1], // nome
                (String) row[2], // telefone
                (String) row[3]  // email
            ))
            .collect(Collectors.toList());
    }


}