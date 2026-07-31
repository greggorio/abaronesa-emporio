package com.baronesa.emporio.service.list;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.dynamicform.config.DynamicFormRegistry;
import com.baronesa.emporio.entity.MovimentoCaixa;
import com.baronesa.emporio.repository.MovimentoCaixaRepository;
import com.baronesa.emporio.service.base.BaseListService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovimentoCaixaListService extends BaseListService<MovimentoCaixa> {

    private final MovimentoCaixaRepository repository;
    private final DynamicFormRegistry formConfigRegistry;

    @Override
    protected JpaSpecificationExecutor<MovimentoCaixa> getRepository() {
        return repository;
    }

    @Override
    protected Class<MovimentoCaixa> getEntityClass() {
        return MovimentoCaixa.class;
    }

    @Override
    protected BaseFormConfig getFormConfig(MessageResolver resolver) {
        return formConfigRegistry.getConfig("movimento-caixa");
    }

    @Override
    protected Map<String, Object> entityToRow(MovimentoCaixa m) {
        Map<String, Object> row = new HashMap<>();

        row.put("id", m.getId());
        row.put("dataHora", m.getDataHora());
        row.put("tipo", m.getTipo() != null ? m.getTipo().name() : null);
        row.put("valor", m.getValor());
        row.put("meioPagamento", m.getMeioPagamento() != null ? m.getMeioPagamento().name() : null);
        row.put("afetaCaixa", m.isAfetaCaixa());
        row.put("operacao", m.getOperacao() != null ? m.getOperacao().name() : null);
        row.put("observacao", Objects.requireNonNullElse(m.getObservacao(), ""));
        row.put("referenciaId", Objects.requireNonNullElse(m.getReferenciaId(), ""));
        row.put("referenciaTipo", Objects.requireNonNullElse(m.getReferenciaTipo(), ""));
        row.put("responsavel", m.getResponsavel() != null ? m.getResponsavel().getNome() : null);

        return row;
    }


    /**
     * Retorna dados formatados para a tabela do formulário
     */
    public List<Map<String, Object>> listarParaTabela() {
        return repository.findAll().stream()
                .map(this::entityToRow)
                .collect(Collectors.toList());
    }
}