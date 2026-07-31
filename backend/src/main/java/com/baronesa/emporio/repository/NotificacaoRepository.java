package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Notificacao;
import com.baronesa.emporio.entity.SessaoConvidado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    List<Notificacao> findBySessaoConvidadoAndLidaOrderByCriadoEmDesc(SessaoConvidado sessaoConvidado, Boolean lida);

    long countBySessaoConvidadoAndLida(SessaoConvidado sessaoConvidado, Boolean lida);
}
