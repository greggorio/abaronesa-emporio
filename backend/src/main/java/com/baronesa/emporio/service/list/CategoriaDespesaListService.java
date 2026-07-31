package com.baronesa.emporio.service.list;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.dynamicform.config.DynamicFormRegistry;
import com.baronesa.emporio.entity.CategoriaDespesa;
import com.baronesa.emporio.repository.CategoriaDespesaRepository;
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
public class CategoriaDespesaListService extends BaseListService<CategoriaDespesa> {
    private final CategoriaDespesaRepository repo;
    private final CategoriaDespesaRepository categoriaDespesaRepository;
    private final DynamicFormRegistry formConfigRegistry;

    @Override
    protected JpaSpecificationExecutor<CategoriaDespesa> getRepository() {
        return repo;
    }

    @Override
    protected Class<CategoriaDespesa> getEntityClass() {
        return CategoriaDespesa.class;
    }

    @Override
    protected BaseFormConfig getFormConfig(com.baronesa.emporio.config.i18n.MessageResolver r) {
        return formConfigRegistry.getConfig("categorias-despesa");
    }

    @Override
    protected Map<String, Object> entityToRow(CategoriaDespesa cd) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", cd.getId());
        row.put("nome", cd.getNome());
        return row;
    }

}
