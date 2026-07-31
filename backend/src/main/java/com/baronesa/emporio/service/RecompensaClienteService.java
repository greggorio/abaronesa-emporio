package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.RecompensaClienteDTO;
import com.baronesa.emporio.entity.Recompensa;
import com.baronesa.emporio.entity.TipoRecompensa;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.exception.NotFoundException;
import com.baronesa.emporio.repository.RecompensaRepository;
import com.baronesa.emporio.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecompensaClienteService {

    private final RecompensaRepository recompensaRepository;
    private final UsuarioRepository usuarioRepository;
    private final GamificacaoService gamificacaoService;

    public List<RecompensaClienteDTO> getRecompensasDisponiveis(Long clienteId) {
        Usuario cliente = usuarioRepository.findById(clienteId)
                .orElseThrow(() -> new NotFoundException("Cliente não encontrado: " + clienteId));

        // Obter saldo do cliente
        Integer saldo = gamificacaoService.getSaldoCliente(clienteId);

        // Obter recompensas disponíveis
        List<Recompensa> recompensasDisponiveis = recompensaRepository.findDisponiveis();

        return recompensasDisponiveis.stream()
                .map(recompensa -> mapToClienteDTO(recompensa, saldo))
                .collect(Collectors.toList());
    }

    private RecompensaClienteDTO mapToClienteDTO(Recompensa recompensa, Integer saldo) {
        // Determinar se a recompensa está disponível
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
}