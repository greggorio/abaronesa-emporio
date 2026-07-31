package com.baronesa.emporio.service.list;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.dynamicform.config.DynamicFormRegistry;
import com.baronesa.emporio.entity.Subcategoria;
import com.baronesa.emporio.repository.SubcategoriaRepository;
import com.baronesa.emporio.service.base.BaseListService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubcategoriaListService extends BaseListService<Subcategoria> {
    private final SubcategoriaRepository subcategoriaRepository;
    private final DynamicFormRegistry formConfigRegistry;

    /*–-–-–-–-– Base hooks –-–-–-–-–*/
    @Override
    protected JpaSpecificationExecutor<Subcategoria> getRepository() {
        return subcategoriaRepository;
    }

    @Override
    protected Class<Subcategoria> getEntityClass() {
        return Subcategoria.class;
    }

    @Override
    protected BaseFormConfig getFormConfig(MessageResolver resolver) {
        return formConfigRegistry.getConfig("subcategorias");
    }

    @Override
    protected Map<String, Object> entityToRow(Subcategoria s) {
        return Map.of(
                "id",        s.getId(),
                "nome",      Objects.toString(s.getNome(), ""),
                "cover",     Objects.toString(s.getCover(), ""),
                "categoria", s.getCategoria() != null ? Objects.toString(s.getCategoria().getNome(), "") : ""
        );
    }

    /**
     * Retorna dados formatados para a tabela do formulário
     */
    public List<Map<String, Object>> listarParaTabela() {
        return subcategoriaRepository.findAll().stream()
                .map(this::entityToMap)
                .collect(Collectors.toList());
    }

    private Map<String, Object> entityToMap(Subcategoria s) {
        return Map.of(
                "id",        s.getId(),
                "nome",      s.getNome(),
                "cover",     s.getCover() != null ? s.getCover() : "",
                "categoria", s.getCategoria() != null && s.getCategoria().getNome() != null ? s.getCategoria().getNome() : ""
        );
    }
}
