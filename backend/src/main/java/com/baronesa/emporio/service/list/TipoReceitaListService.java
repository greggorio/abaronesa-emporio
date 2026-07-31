package com.baronesa.emporio.service.list;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.dynamicform.config.DynamicFormRegistry;
import com.baronesa.emporio.entity.TipoReceita;
import com.baronesa.emporio.repository.TipoReceitaRepository;
import com.baronesa.emporio.service.base.BaseListService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TipoReceitaListService extends BaseListService<TipoReceita> {
    private final TipoReceitaRepository repo;
    private final TipoReceitaRepository tipoReceitaRepository;
    private final DynamicFormRegistry formConfigRegistry;

    @Override
    protected JpaSpecificationExecutor<TipoReceita> getRepository() {
        return repo;
    }

    @Override
    protected Class<TipoReceita> getEntityClass() {
        return TipoReceita.class;
    }

    @Override
    protected BaseFormConfig getFormConfig(com.baronesa.emporio.config.i18n.MessageResolver r) {
        return formConfigRegistry.getConfig("tipos-receita");
    }

    @Override
    protected Map<String, Object> entityToRow(TipoReceita tr) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", tr.getId());
        row.put("nome", tr.getNome());
        return row;
    }

}
