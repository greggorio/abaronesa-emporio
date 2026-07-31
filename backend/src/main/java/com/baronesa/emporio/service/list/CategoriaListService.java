package com.baronesa.emporio.service.list;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.dynamicform.config.DynamicFormRegistry; // ✅ MUDANÇA: Use DynamicFormRegistry
import com.baronesa.emporio.entity.Categoria;
import com.baronesa.emporio.repository.CategoriaRepository;
import com.baronesa.emporio.service.base.BaseListService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CategoriaListService extends BaseListService<Categoria> {
    private final CategoriaRepository categoriaRepository;
    private final DynamicFormRegistry formConfigRegistry; // ✅ MUDANÇA: DynamicFormRegistry ao invés de HybridFormConfigRegistry

    @Override
    protected JpaSpecificationExecutor<Categoria> getRepository() {
        return categoriaRepository;
    }

    @Override
    protected Class<Categoria> getEntityClass() {
        return Categoria.class;
    }

    @Override
    protected BaseFormConfig getFormConfig(MessageResolver resolver) {
        return formConfigRegistry.getConfig("categorias"); // ✅ Agora usa totalmente dinâmico
    }

    @Override
    protected Map<String, Object> entityToRow(Categoria c) {
        return new HashMap<String, Object>() {{
            put("id", c.getId());
            put("cover", Objects.toString(c.getCover(), ""));
            put("nome", Objects.toString(c.getNome(), ""));
            put("icone", Objects.toString(c.getIcone(), ""));
            put("exibirNoCardapio", c.getExibirNoCardapio() != null ? c.getExibirNoCardapio() : false);
            put("ordem", c.getOrdem() != null ? c.getOrdem() : 0);
        }};
    }
}