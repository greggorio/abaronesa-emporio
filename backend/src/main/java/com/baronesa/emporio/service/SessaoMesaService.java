package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.CriarConvidadoRequest;
import com.baronesa.emporio.dto.CriarConvidadoResponse;
import com.baronesa.emporio.entity.Mesa;
import com.baronesa.emporio.entity.SessaoConvidado;
import com.baronesa.emporio.entity.SessaoMesa;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.enums.StatusSessao;
import com.baronesa.emporio.exception.NotFoundException;
import com.baronesa.emporio.repository.ClienteRepository;
import com.baronesa.emporio.repository.MesaRepository;
import com.baronesa.emporio.repository.SessaoConvidadoRepository;
import com.baronesa.emporio.repository.SessaoMesaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SessaoMesaService {

    private final MesaRepository mesaRepository;
    private final SessaoMesaRepository sessaoMesaRepository;
    private final SessaoConvidadoRepository sessaoConvidadoRepository;
    private final ClienteRepository clienteRepository;

    @Transactional
    public CriarConvidadoResponse criarConvidado(String mesaSlug, CriarConvidadoRequest req) {
        Mesa mesa = mesaRepository.findBySlug(mesaSlug)
                .orElseThrow(() -> new NotFoundException("Mesa não encontrada: " + mesaSlug));

        SessaoMesa sessaoMesa = obterOuAbrirSessaoMesa(mesa);

        String nome = (req != null && req.nomeExibicao() != null && !req.nomeExibicao().isBlank())
                ? req.nomeExibicao().trim()
                : "Convidado";

        String guestToken = gerarGuestTokenUnico();

        long guestsCount = sessaoConvidadoRepository.countBySessaoMesa_Id(sessaoMesa.getId());
        boolean isFirst = guestsCount == 0;

        Usuario usuario = null;
        if (req != null && req.userToken() != null && !req.userToken().isBlank()) {
            usuario = clienteRepository.findByIdWithPerfilCliente(Long.valueOf(req.userToken()), Usuario.Role.CLIENTE)
                    .orElse(null);
        }

        SessaoConvidado convidado = SessaoConvidado.builder()
                .sessaoMesa(sessaoMesa)
                .nomeExibicao(nome)
                .guestToken(guestToken)
                .usuario(usuario)
                .host(isFirst)
                .build();

        convidado = sessaoConvidadoRepository.save(convidado);

        return new CriarConvidadoResponse(convidado.getId(), sessaoMesa.getId(), convidado.getGuestToken(), convidado.getHost());
    }

    private SessaoMesa obterOuAbrirSessaoMesa(Mesa mesa) {
        Optional<SessaoMesa> aberta = sessaoMesaRepository.findFirstByMesaAndStatusOrderByAbertaEmDesc(mesa, StatusSessao.OPEN);
        if (aberta.isPresent()) {
            return aberta.get();
        }
        SessaoMesa nova = SessaoMesa.builder()
                .mesa(mesa)
                .status(StatusSessao.OPEN)
                .observacoes(null)
                .build();
        return sessaoMesaRepository.save(nova);
    }

    private String gerarGuestTokenUnico() {
        // 32 bytes secure random → base64url (sem padding)
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        String token;
        do {
            random.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (sessaoConvidadoRepository.findByGuestToken(token).isPresent());
        return token;
    }

    @Transactional
    public void fecharSessao(Long sessaoMesaId) {
        SessaoMesa sessao = sessaoMesaRepository.findById(sessaoMesaId)
                .orElseThrow(() -> new NotFoundException("Sessão de mesa não encontrada"));
        sessao.setStatus(StatusSessao.CLOSED);
        sessao.setFechadaEm(java.time.LocalDateTime.now());
        sessaoMesaRepository.save(sessao);
    }

    public java.util.Optional<SessaoConvidado> getSessaoConvidadoByToken(String token) {
        return sessaoConvidadoRepository.findByGuestToken(token);
    }

    public SessaoMesa getSessaoAtivaPorMesaSlug(String mesaSlug) {
        Mesa mesa = mesaRepository.findBySlug(mesaSlug).orElse(null);
        if (mesa == null) {
            return null;
        }
        return sessaoMesaRepository.findFirstByMesaAndStatusOrderByAbertaEmDesc(mesa, StatusSessao.OPEN)
                .orElse(null);
    }

    @Transactional
    public SessaoConvidado obterOuCriarSessaoEBalcaoGuest(String mesaSlug, String nomeGuest, Usuario usuario) {
        Mesa mesa = mesaRepository.findBySlug(mesaSlug)
                .orElseThrow(() -> new NotFoundException("Mesa não encontrada: " + mesaSlug));

        SessaoMesa sessao = sessaoMesaRepository.findFirstByMesaAndStatusOrderByAbertaEmDesc(mesa, StatusSessao.OPEN)
                .orElse(null);
        if (sessao == null) {
            sessao = SessaoMesa.builder()
                    .mesa(mesa)
                    .status(StatusSessao.OPEN)
                    .observacoes("sessao_autocriada_balcao")
                    .build();
            sessao = sessaoMesaRepository.save(sessao);
        }

        // Buscar host existente
        Optional<SessaoConvidado> host = sessaoConvidadoRepository.findBySessaoMesa_Id(sessao.getId()).stream()
                .filter(c -> Boolean.TRUE.equals(c.getHost()))
                .findFirst();
        if (host.isPresent()) {
            SessaoConvidado existente = host.get();
            if (usuario != null) {
                existente.setUsuario(usuario);
                existente.setNomeExibicao(nomeGuest != null ? nomeGuest : usuario.getNome());
                existente = sessaoConvidadoRepository.save(existente);
            }
            return existente;
        }

        // Criar convidado host
        SessaoConvidado convidado = SessaoConvidado.builder()
                .sessaoMesa(sessao)
                .usuario(usuario)
                .nomeExibicao(nomeGuest != null ? nomeGuest : (usuario != null ? usuario.getNome() : "Balcão"))
                .guestToken(gerarGuestTokenUnico())
                .host(true)
                .build();
        return sessaoConvidadoRepository.save(convidado);
    }

    @Transactional
    public SessaoConvidado iniciarSessaoAssistida(String mesaSlug, Long clienteId, String nomeInformado) {
        Mesa mesa = mesaRepository.findBySlug(mesaSlug)
                .orElseThrow(() -> new NotFoundException("Mesa não encontrada: " + mesaSlug));

        SessaoMesa sessao = sessaoMesaRepository.findFirstByMesaAndStatusOrderByAbertaEmDesc(mesa, StatusSessao.OPEN)
                .orElse(null);
        if (sessao == null) {
            sessao = SessaoMesa.builder()
                    .mesa(mesa)
                    .status(StatusSessao.OPEN)
                    .observacoes("sessao_assistida")
                    .build();
            sessao = sessaoMesaRepository.save(sessao);
        } else if (sessao.getObservacoes() == null || !sessao.getObservacoes().toLowerCase(java.util.Locale.ROOT).contains("assistida")) {
            sessao.setObservacoes("sessao_assistida");
            sessao = sessaoMesaRepository.save(sessao);
        }

        SessaoConvidado host = sessaoConvidadoRepository.findBySessaoMesa_Id(sessao.getId()).stream()
                .filter(c -> Boolean.TRUE.equals(c.getHost()))
                .findFirst()
                .orElse(null);

        Usuario cliente = null;
        if (clienteId != null) {
            cliente = clienteRepository.findByIdWithPerfilCliente(clienteId, Usuario.Role.CLIENTE)
                    .orElseThrow(() -> new NotFoundException("Cliente não encontrado para sessão assistida"));
        }

        String nomeExibicao = (nomeInformado != null && !nomeInformado.isBlank())
                ? nomeInformado.trim()
                : (cliente != null ? cliente.getNome() : "Convidado");

        if (host == null) {
            host = SessaoConvidado.builder()
                    .sessaoMesa(sessao)
                    .usuario(cliente)
                    .nomeExibicao(nomeExibicao)
                    .guestToken(gerarGuestTokenUnico())
                    .host(true)
                    .build();
        } else {
            host.setUsuario(cliente);
            host.setNomeExibicao(nomeExibicao);
        }

        return sessaoConvidadoRepository.save(host);
    }

    @Transactional
    public SessaoConvidado adicionarConvidadoExtra(Long sessaoMesaId, String nomeExibicao) {
        SessaoMesa sessaoMesa = sessaoMesaRepository.findById(sessaoMesaId)
                .orElseThrow(() -> new NotFoundException("Sessão de mesa não encontrada"));

        String nome = (nomeExibicao != null && !nomeExibicao.isBlank())
                ? nomeExibicao.trim()
                : "Convidado " + (System.currentTimeMillis() % 10000);

        SessaoConvidado convidado = SessaoConvidado.builder()
                .sessaoMesa(sessaoMesa)
                .nomeExibicao(nome)
                .guestToken(gerarGuestTokenUnico()) // Garantindo que o guestToken é gerado
                .host(false) // Sempre false para convidados extras
                .build();

        return sessaoConvidadoRepository.save(convidado);
    }

    @Transactional
    public SessaoMesa moverSessaoMesa(Long sessaoMesaId, Mesa mesaDestino) {
        if (mesaDestino == null) {
            throw new NotFoundException("Mesa de destino não encontrada");
        }
        if (Boolean.FALSE.equals(mesaDestino.getAtivo())) {
            throw new IllegalStateException("Mesa de destino inativa");
        }

        SessaoMesa sessao = sessaoMesaRepository.findById(sessaoMesaId)
                .orElseThrow(() -> new NotFoundException("Sessão de mesa não encontrada"));

        if (sessao.getStatus() != StatusSessao.OPEN) {
            throw new IllegalStateException("Apenas sessões abertas podem ser movidas");
        }

        // Evitar corrida: garantir que não há sessão aberta na mesa destino (diferente da atual)
        sessaoMesaRepository.findFirstByMesaAndStatusOrderByAbertaEmDesc(mesaDestino, StatusSessao.OPEN)
                .filter(s -> !s.getId().equals(sessaoMesaId))
                .ifPresent(s -> {
                    throw new IllegalStateException("Mesa de destino já está ocupada por outra sessão");
                });

        // Se já está na mesa destino, apenas retorna
        if (sessao.getMesa() != null && mesaDestino.getId().equals(sessao.getMesa().getId())) {
            return sessao;
        }

        sessao.setMesa(mesaDestino);

        // Adicionar observação de auditoria leve (sem criar novo campo)
        String obs = sessao.getObservacoes();
        String marker = "migrada_para_" + mesaDestino.getSlug();
        if (obs == null || !obs.contains(marker)) {
            String novaObs = (obs != null ? obs + " | " : "") + marker;
            sessao.setObservacoes(novaObs);
        }

        return sessaoMesaRepository.save(sessao);
    }
}
