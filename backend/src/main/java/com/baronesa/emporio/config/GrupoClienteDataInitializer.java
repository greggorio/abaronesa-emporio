package com.baronesa.emporio.config;

import com.baronesa.emporio.entity.GrupoCliente;
import com.baronesa.emporio.repository.GrupoClienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicializador de dados para grupos de cliente
 * Cria grupos padrão se não existirem
 */
@Slf4j
@Component
@Order(1) // Executa primeiro
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.seeders.enabled", havingValue = "true", matchIfMissing = true)
public class GrupoClienteDataInitializer implements CommandLineRunner {

    private final GrupoClienteRepository grupoClienteRepository;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("=== Verificando grupos de cliente padrão ===");

        createGrupoIfNotExists("E-commerce",
                "Clientes cadastrados através do site");

        createGrupoIfNotExists("Varejo",
                "Clientes cadastrados na loja física");

        log.info("=== Grupos de cliente verificados com sucesso ===");
    }

    private void createGrupoIfNotExists(String descricao, String observacao) {
        if (!grupoClienteRepository.existsByDescricaoIgnoreCase(descricao)) {
            GrupoCliente grupo = GrupoCliente.builder()
                    .descricao(descricao)
                    .build();

            grupoClienteRepository.save(grupo);
            log.info("✅ Grupo '{}' criado: {}", descricao, observacao);
        } else {
            log.debug("Grupo '{}' já existe", descricao);
        }
    }
}