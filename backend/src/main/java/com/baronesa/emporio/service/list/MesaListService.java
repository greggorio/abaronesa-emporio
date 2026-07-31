package com.baronesa.emporio.service.list;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.dynamicform.config.DynamicFormRegistry;
import com.baronesa.emporio.entity.Mesa;
import com.baronesa.emporio.repository.MesaRepository;
import com.baronesa.emporio.service.base.BaseListService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MesaListService extends BaseListService<Mesa> {
    private final MesaRepository repo;
    private final MesaRepository mesaRepository;
    private final DynamicFormRegistry formConfigRegistry;

    @Override
    protected JpaSpecificationExecutor<Mesa> getRepository() {
        return repo;
    }

    @Override
    protected Class<Mesa> getEntityClass() {
        return Mesa.class;
    }

    @Override
    protected BaseFormConfig getFormConfig(com.baronesa.emporio.config.i18n.MessageResolver r) {
        return formConfigRegistry.getConfig("mesas");
    }

    @Override
    protected Map<String, Object> entityToRow(Mesa mesa) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", mesa.getId());
        row.put("slug", mesa.getSlug());
        row.put("rotulo", mesa.getRotulo());
        row.put("ativo", mesa.getAtivo());
        row.put("criadoEm", mesa.getCriadoEm());
        row.put("atualizadoEm", mesa.getAtualizadoEm());
        return row;
    }
}
