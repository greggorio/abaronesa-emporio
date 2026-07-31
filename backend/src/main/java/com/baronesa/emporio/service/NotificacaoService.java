package com.baronesa.emporio.service;

import com.baronesa.emporio.entity.Notificacao;
import com.baronesa.emporio.entity.SessaoConvidado;
import com.baronesa.emporio.entity.SessaoMesa;
import com.baronesa.emporio.repository.NotificacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificacaoService {

    private final NotificacaoRepository notificacaoRepository;

    @Transactional
    public Notificacao criarNotificacao(SessaoMesa sessaoMesa, SessaoConvidado destinatario,
                                        String tipo, String titulo, String mensagem, String payloadJson) {
        Notificacao notificacao = Notificacao.builder()
                .sessaoMesa(sessaoMesa)
                .sessaoConvidado(destinatario)
                .tipo(tipo)
                .titulo(titulo)
                .mensagem(mensagem)
                .payloadJson(payloadJson)
                .lida(false)
                .build();

        return notificacaoRepository.save(notificacao);
    }

    public List<Notificacao> buscarNaoLidas(SessaoConvidado sessaoConvidado) {
        return notificacaoRepository.findBySessaoConvidadoAndLidaOrderByCriadoEmDesc(sessaoConvidado, false);
    }

    public long contarNaoLidas(SessaoConvidado sessaoConvidado) {
        return notificacaoRepository.countBySessaoConvidadoAndLida(sessaoConvidado, false);
    }

    @Transactional
    public void marcarComoLida(Long notificacaoId) {
        notificacaoRepository.findById(notificacaoId).ifPresent(notif -> {
            notif.setLida(true);
            notif.setLidaEm(LocalDateTime.now());
            notificacaoRepository.save(notif);
        });
    }

    @Transactional
    public void marcarTodasComoLidas(SessaoConvidado sessaoConvidado) {
        List<Notificacao> naoLidas = notificacaoRepository.findBySessaoConvidadoAndLidaOrderByCriadoEmDesc(sessaoConvidado, false);
        LocalDateTime agora = LocalDateTime.now();
        for (Notificacao notif : naoLidas) {
            notif.setLida(true);
            notif.setLidaEm(agora);
        }
        notificacaoRepository.saveAll(naoLidas);
    }
}
