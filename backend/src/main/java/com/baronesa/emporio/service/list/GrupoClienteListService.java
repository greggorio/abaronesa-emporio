package com.baronesa.emporio.service.list;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.dynamicform.config.DynamicFormRegistry;
import com.baronesa.emporio.entity.GrupoCliente;
import com.baronesa.emporio.repository.GrupoClienteRepository;
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
public class GrupoClienteListService extends BaseListService<GrupoCliente> {
    private final GrupoClienteRepository repo;
    private final DynamicFormRegistry formConfigRegistry;

    @Override
    protected JpaSpecificationExecutor<GrupoCliente> getRepository() {
        return repo;
    }

    @Override
    protected Class<GrupoCliente> getEntityClass() {
        return GrupoCliente.class;
    }

    @Override
    protected BaseFormConfig getFormConfig(com.baronesa.emporio.config.i18n.MessageResolver r) {
        return formConfigRegistry.getConfig("grupos-clientes");
    }

    @Override
    protected Map<String, Object> entityToRow(GrupoCliente g) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", g.getId() != null ? g.getId() : 0L);
        row.put("descricao", g.getDescricao() != null ? g.getDescricao() : "");
        return row;
    }

}