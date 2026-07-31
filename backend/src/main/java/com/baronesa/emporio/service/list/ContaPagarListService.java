package com.baronesa.emporio.service.list;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.dynamicform.config.DynamicFormRegistry;
import com.baronesa.emporio.entity.ContaPagar;
import com.baronesa.emporio.repository.ContaPagarRepository;
import com.baronesa.emporio.service.base.BaseListService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ContaPagarListService extends BaseListService<ContaPagar> {
    private final ContaPagarRepository repo;
    private final DynamicFormRegistry formConfigRegistry;

    @Override
    protected JpaSpecificationExecutor<ContaPagar> getRepository() {
        return repo;
    }

    @Override
    protected Class<ContaPagar> getEntityClass() {
        return ContaPagar.class;
    }

    @Override
    protected BaseFormConfig getFormConfig(com.baronesa.emporio.config.i18n.MessageResolver r) {
        return formConfigRegistry.getConfig("contas-pagar");
    }

    @Override
    protected Map<String, Object> entityToRow(ContaPagar conta) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", conta.getId() != null ? conta.getId() : 0L);
        row.put("fornecedor", (conta.getFornecedor() != null && conta.getFornecedor().getRazaoSocial() != null) ? conta.getFornecedor().getRazaoSocial() : "");
        row.put("categoria", (conta.getCategoriaDespesa() != null && conta.getCategoriaDespesa().getNome() != null) ? conta.getCategoriaDespesa().getNome() : "");
        row.put("descricao", conta.getDescricao() != null ? conta.getDescricao() : "");
        row.put("valorTotal", conta.getValorTotal() != null ? conta.getValorTotal() : null);
        row.put("parcelas", conta.getNumeroParcelas() != null ? (conta.getNumeroParcelas() + "x") : "");

        // Status
        String status;
        if (conta.isQuitada()) {
            status = "Quitada";
        } else if (conta.getParcelas() != null && conta.getParcelas().stream().anyMatch(p -> p.isVencida())) {
            status = "Vencida";
        } else {
            status = "Em aberto";
        }
        row.put("status", status);
        row.put("_statusColor", getStatusColor(status));
        row.put("_statusIcon", getStatusIcon(status));

        return row;
    }

    /**
     * Retorna a cor do badge baseado no status da conta
     */
    private String getStatusColor(String status) {
        return switch (status) {
            case "Quitada" -> "green";
            case "Vencida" -> "red";
            case "Em aberto" -> "orange";
            default -> "grey";
        };
    }

    /**
     * Retorna o ícone do badge baseado no status da conta
     */
    private String getStatusIcon(String status) {
        return switch (status) {
            case "Quitada" -> "check_circle";
            case "Vencida" -> "error";
            case "Em aberto" -> "schedule";
            default -> "o_hourglass_empty";
        };
    }
}
