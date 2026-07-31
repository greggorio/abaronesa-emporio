package com.baronesa.emporio.service.list;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.dynamicform.config.DynamicFormRegistry;
import com.baronesa.emporio.entity.RecebimentoMercadoria;
import com.baronesa.emporio.repository.RecebimentoMercadoriaRepository;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.config.i18n.MessageResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecebimentoMercadoriaListService extends BaseListService<RecebimentoMercadoria> {
    private final DynamicFormRegistry formConfigRegistry;
    private final RecebimentoMercadoriaRepository repository;
    private final MessageResolver messageResolver;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    protected JpaSpecificationExecutor<RecebimentoMercadoria> getRepository() {
        return repository;
    }

    @Override
    protected Class<RecebimentoMercadoria> getEntityClass() {
        return RecebimentoMercadoria.class;
    }

    @Override
    protected BaseFormConfig getFormConfig(MessageResolver resolver) {
        return formConfigRegistry.getConfig("recebimentos");
    }

    @Override
    protected Map<String, Object> entityToRow(RecebimentoMercadoria recebimento) {
        Map<String, Object> row = new LinkedHashMap<>();

        row.put("id", recebimento.getId() != null ? recebimento.getId() : 0L);
        row.put("numeroNf", recebimento.getNumeroNf() != null ? recebimento.getNumeroNf() : "");
        row.put("fornecedor", recebimento.getFornecedor() != null && recebimento.getFornecedor().getRazaoSocial() != null ?
                recebimento.getFornecedor().getRazaoSocial() : "-");
        row.put("dataRecebimento", recebimento.getDataRecebimento() != null ?
                recebimento.getDataRecebimento().format(DATE_FORMATTER) : "-");
        row.put("dataEmissaoNf", recebimento.getDataEmissaoNf() != null ?
                recebimento.getDataEmissaoNf() : "-");
        row.put("valorTotal", recebimento.getValorTotal() != null ? recebimento.getValorTotal() : null);
        row.put("quantidadeItens", recebimento.getQuantidadeItens() != null ? recebimento.getQuantidadeItens() : 0);
        row.put("status", recebimento.getStatus() != null ? recebimento.getStatus().name() : "");

        // Adicionar metadados para formatação no frontend
        row.put("_statusColor", recebimento.getStatus() != null ? recebimento.getStatus().getColor() : "");
        row.put("_statusLabel", recebimento.getStatus() != null ? recebimento.getStatus().getLabel() : "");
        row.put("_statusIcon", recebimento.getStatus() != null ? recebimento.getStatus().getIcon() : "");

        // Flags para ações
        row.put("_podeEditar", recebimento.podeEditar());
        row.put("_podeFinalizar", recebimento.podeFinalizar());
        row.put("_podeCancelar", recebimento.podeCancelar());
        return row;
    }

    public List<Map<String, Object>> listarParaTabela() {
        return repository.findAll().stream()
                .map(this::entityToRow)
                .collect(Collectors.toList());
    }
}