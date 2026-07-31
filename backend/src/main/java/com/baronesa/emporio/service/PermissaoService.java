package com.baronesa.emporio.service;

import com.baronesa.emporio.cache.PermissaoCache;
import com.baronesa.emporio.repository.PermissoesGruposRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PermissaoService {
    private static final Logger logger = LoggerFactory.getLogger(PermissaoService.class);

    private final PermissoesGruposRepositorio repo;
    private final PermissaoCache cache;

    @Autowired
    public PermissaoService(PermissoesGruposRepositorio repo,
                            PermissaoCache cache) {
        this.repo = repo;
        this.cache = cache;
    }

    /**
     * Checa se o grupo (idGrupo) possui a permissão informada, usando cache.
     *
     * @param idGrupo    identificador do grupo de usuários
     * @param permissao  string da permissão, ex: "vendas"
     * @return true se existir permissão, false caso contrário
     */
    public boolean hasPermission(Long idGrupo, String permissao) {
        String key = idGrupo + ":" + permissao;
        boolean result = cache.get(key, () -> {
            boolean exists = repo.existsByIdGrupoAndPermissao(idGrupo, permissao);
            logger.warn("PERM DB CHECK: Grupo={}, Permissão={}, Existe={}", idGrupo, permissao, exists);
            return exists;
        });
        logger.warn("PERM CACHE RESULT: Grupo={}, Permissão={}, Cache={}", idGrupo, permissao, result);
        return result;
    }

    /**
     * Invalida todo o cache de permissões.
     * Deve ser chamado quando permissões são modificadas (add/remove).
     */
    public void invalidateCache() {
        cache.invalidateAll();
        logger.info("Cache de permissões invalidado");
    }

    /**
     * Invalida cache específico de um grupo.
     */
    public void invalidateCacheForGroup(Long idGrupo) {
        // Não temos como saber todas as permissões possíveis, então invalidamos tudo
        cache.invalidateAll();
        logger.info("Cache de permissões invalidado para o grupo {}", idGrupo);
    }
}
