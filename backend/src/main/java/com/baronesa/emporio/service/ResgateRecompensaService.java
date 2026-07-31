package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.ResgateRecompensaRequest;
import com.baronesa.emporio.dto.ResgateRecompensaResponse;
import com.baronesa.emporio.entity.MovimentoPontos;
import com.baronesa.emporio.entity.Recompensa;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.enums.OrigemMovimentoPontos;
import com.baronesa.emporio.enums.TipoMovimentoPontos;
import com.baronesa.emporio.exception.BusinessException;
import com.baronesa.emporio.repository.MovimentoPontosRepository;
import com.baronesa.emporio.repository.RecompensaRepository;
import com.baronesa.emporio.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ResgateRecompensaService {

    private final RecompensaRepository recompensaRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimentoPontosRepository movimentoPontosRepository;
    private final GamificacaoService gamificacaoService;

    @Transactional
    public ResgateRecompensaResponse resgatarRecompensa(ResgateRecompensaRequest request) {
        // Validar recompensa
        Recompensa recompensa = recompensaRepository.findById(request.getRecompensaId())
            .orElseThrow(() -> new BusinessException("Recompensa não encontrada"));
        
        validarRecompensa(recompensa);
        
        // Validar cliente
        Usuario cliente = usuarioRepository.findById(request.getClienteId())
            .orElseThrow(() -> new BusinessException("Cliente não encontrado"));
        
        // Verificar saldo do cliente
        Integer saldoCliente = gamificacaoService.getSaldoCliente(cliente.getId());
        if (saldoCliente < recompensa.getPontosNecessarios()) {
            throw new BusinessException("Saldo insuficiente");
        }
        
        // Obter saldo anterior
        Integer saldoAnterior = saldoCliente;
        
        // Calcular saldo após o resgate
        Integer saldoApos = saldoAnterior - recompensa.getPontosNecessarios();
        
        // Criar movimento de resgate
        MovimentoPontos movimento = new MovimentoPontos();
        movimento.setCliente(cliente);
        movimento.setTipo(TipoMovimentoPontos.RESGATE);
        movimento.setOrigem(OrigemMovimentoPontos.RECOMPENSA);
        movimento.setReferenciaTipo("RECOMPENSA"); // ou pode ser o tipo do enum
        movimento.setReferenciaId(recompensa.getId());
        movimento.setPontos(-recompensa.getPontosNecessarios()); // Negativo para débito
        movimento.setSaldoApos(saldoApos);
        movimento.setDataHora(LocalDateTime.now());
        movimento.setObservacao(request.getObservacao() != null ? 
            "Resgate da recompensa: " + recompensa.getNome() + " - " + request.getObservacao() : 
            "Resgate da recompensa: " + recompensa.getNome());
        
        MovimentoPontos movimentoSalvo = movimentoPontosRepository.save(movimento);
        
        // Atualizar estoque da recompensa se aplicável
        if (recompensa.getEstoque() != null) {
            recompensa.setEstoque(recompensa.getEstoque() - 1);
            recompensaRepository.save(recompensa);
        }
        
        return new ResgateRecompensaResponse(
            movimentoSalvo.getId(),
            cliente.getId(),
            recompensa.getId(),
            recompensa.getPontosNecessarios(),
            saldoAnterior,
            saldoApos,
            movimentoSalvo.getDataHora()
        );
    }

    private void validarRecompensa(Recompensa recompensa) {
        // Verificar se está ativa
        if (!recompensa.getAtivo()) {
            throw new BusinessException("Recompensa inativa");
        }

        // Verificar validade
        LocalDateTime agora = LocalDateTime.now();
        if (recompensa.getValidadeInicio() != null && 
            agora.isBefore(recompensa.getValidadeInicio().atStartOfDay())) {
            throw new BusinessException("Recompensa fora da validade");
        }
        if (recompensa.getValidadeFim() != null && 
            agora.isAfter(recompensa.getValidadeFim().atTime(23, 59, 59))) {
            throw new BusinessException("Recompensa fora da validade");
        }

        // Verificar estoque
        if (recompensa.getEstoque() != null && recompensa.getEstoque() <= 0) {
            throw new BusinessException("Recompensa esgotada");
        }
    }
}