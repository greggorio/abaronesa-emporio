package com.baronesa.emporio.config;

import com.baronesa.emporio.entity.GrupoUsuario;
import com.baronesa.emporio.entity.Permissoes;
import com.baronesa.emporio.entity.PermissoesGrupos;
import com.baronesa.emporio.repository.GrupoUsuarioRepository;
import com.baronesa.emporio.repository.PermissoesGruposRepositorio;
import com.baronesa.emporio.repository.PermissoesRepositorio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Garante a existência do grupo Admin (id=1) e vincula todas as permissões já cadastradas.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.seeders.enabled", havingValue = "true", matchIfMissing = true)
public class GrupoAdminInitializer implements CommandLineRunner {

    private final GrupoUsuarioRepository grupoUsuarioRepository;
    private final PermissoesRepositorio permissoesRepositorio;
    private final PermissoesGruposRepositorio permissoesGruposRepositorio;

    private static final Long ADMIN_GROUP_ID = 1L;
    private static final String ADMIN_DESCRICAO = "Admin";
    private static final List<PermissaoSeed> DEFAULT_PERMISSOES = Arrays.asList(
            new PermissaoSeed("vendas", "Acesso ao módulo de vendas e PDV"),
            new PermissaoSeed("notificacoes", "Visualização de notificações do sistema"),
            new PermissaoSeed("produtos", "Gestão de produtos e catálogo"),
            new PermissaoSeed("movimento_caixa", "Gestão de movimento de caixa"),
            new PermissaoSeed("contas_pagar", "Gestão de contas a pagar"),
            new PermissaoSeed("contas_receber", "Gestão de contas a receber"),
            new PermissaoSeed("categoria_despesa", "Gestão de categorias de despesa"),
            new PermissaoSeed("cartoes", "Configuração de cartões de pagamento"),
            new PermissaoSeed("clientes", "Gestão de clientes"),
            new PermissaoSeed("fornecedores", "Gestão de fornecedores"),
            new PermissaoSeed("usuarios", "Gestão de usuários do sistema"),
            new PermissaoSeed("empresa", "Configuração de dados da empresa"),
            new PermissaoSeed("financeiro", "Gestão financeira (contas, despesas, receitas)"),
            new PermissaoSeed("nfe", "Gestão de Notas Fiscais Eletrônicas"),
            new PermissaoSeed("mesas", "Permissão para gerenciar mesas"),
            new PermissaoSeed("movimentos-caixa", "Permissão para gerenciar movimentos de caixa")
    );

    @Override
    @Transactional
    public void run(String... args) {
        try {
            ensureAdminGroup();
            int novasPermissoes = seedPermissoes();
            int vinculadas = vincularTodasPermissoes();
            log.info("Grupo Admin inicializado. Permissões criadas: {} | Novos vínculos de permissões criados: {}", novasPermissoes, vinculadas);
        } catch (Exception e) {
            log.error("Erro ao inicializar grupo Admin: {}", e.getMessage(), e);
        }
    }

    private void ensureAdminGroup() {
        grupoUsuarioRepository.findById(ADMIN_GROUP_ID)
                .map(existing -> {
                    boolean updated = false;
                    if (!ADMIN_DESCRICAO.equals(existing.getDescricao())) {
                        existing.setDescricao(ADMIN_DESCRICAO);
                        updated = true;
                    }
                    if (existing.getAtivo() == null || !existing.getAtivo()) {
                        existing.setAtivo(true);
                        updated = true;
                    }
                    if (updated) {
                        grupoUsuarioRepository.save(existing);
                        log.info("Grupo Admin (id=1) atualizado.");
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    GrupoUsuario novo = GrupoUsuario.builder()
                            .id(ADMIN_GROUP_ID)
                            .descricao(ADMIN_DESCRICAO)
                            .ativo(true)
                            .build();
                    GrupoUsuario salvo = grupoUsuarioRepository.save(novo);
                    log.info("Grupo Admin (id=1) criado.");
                    return salvo;
                });
    }

    private int seedPermissoes() {
        int criadas = 0;
        for (PermissaoSeed seed : DEFAULT_PERMISSOES) {
            if (seed == null || seed.codigo == null) continue;
            boolean exists = permissoesRepositorio.findByPermissao(seed.codigo).isPresent();
            if (!exists) {
                Permissoes perm = new Permissoes();
                perm.setPermissao(seed.codigo);
                perm.setDescricao(seed.descricao);
                permissoesRepositorio.save(perm);
                criadas++;
            }
        }
        return criadas;
    }

    private int vincularTodasPermissoes() {
        List<Permissoes> permissoes = permissoesRepositorio.findAllDistinct();
        int novosVinculos = 0;

        for (Permissoes permissao : permissoes) {
            if (permissao == null || permissao.getPermissao() == null) continue;
            String codigo = permissao.getPermissao();
            boolean alreadyLinked = permissoesGruposRepositorio.existsByIdGrupoAndPermissao(ADMIN_GROUP_ID, codigo);

            if (!alreadyLinked) {
                PermissoesGrupos pg = new PermissoesGrupos();
                pg.setIdGrupo(ADMIN_GROUP_ID);
                pg.setPermissao(codigo);
                permissoesGruposRepositorio.save(pg);
                novosVinculos++;
            }
        }

        return novosVinculos;
    }

    private record PermissaoSeed(String codigo, String descricao) {}
}
