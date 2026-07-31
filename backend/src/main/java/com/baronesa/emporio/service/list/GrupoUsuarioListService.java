package com.baronesa.emporio.service.list;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.dynamicform.config.DynamicFormRegistry;
import com.baronesa.emporio.entity.GrupoUsuario;
import com.baronesa.emporio.repository.GrupoUsuarioRepository;
import com.baronesa.emporio.service.base.BaseListService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GrupoUsuarioListService extends BaseListService<GrupoUsuario> {
    private final GrupoUsuarioRepository repo;
    private final GrupoUsuarioRepository grupoUsuarioRepository;
    private final DynamicFormRegistry formConfigRegistry;

    @Override
    protected JpaSpecificationExecutor<GrupoUsuario> getRepository() {
        return repo;
    }

    @Override
    protected Class<GrupoUsuario> getEntityClass() {
        return GrupoUsuario.class;
    }

    @Override
    protected BaseFormConfig getFormConfig(com.baronesa.emporio.config.i18n.MessageResolver r) {
        return formConfigRegistry.getConfig("grupos-usuario");
    }

    @Override
    protected Map<String, Object> entityToRow(GrupoUsuario g) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", g.getId() != null ? g.getId() : 0L);
        row.put("descricao", g.getDescricao() != null ? g.getDescricao() : "");
        row.put("ativo", g.getAtivo() != null ? g.getAtivo() : true);
        return row;
    }

    public List<Map<String, Object>> listarParaTabela() {
        return grupoUsuarioRepository.findAll().stream()
                .map(this::entityToMapSafe)
                .collect(Collectors.toList());
    }

    // Método auxiliar para tratar nulos
    private Map<String, Object> entityToMapSafe(GrupoUsuario grupoUsuario) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", grupoUsuario.getId() != null ? grupoUsuario.getId() : 0L);
        map.put("descricao", grupoUsuario.getDescricao() != null ? grupoUsuario.getDescricao() : "");
        map.put("ativo", grupoUsuario.getAtivo() != null ? grupoUsuario.getAtivo() : true);
        return map;
    }
}