package com.baronesa.emporio.service.list;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.dynamicform.config.DynamicFormRegistry;
import com.baronesa.emporio.entity.ContaReceber;
import com.baronesa.emporio.entity.ContaReceberParcela;
import com.baronesa.emporio.repository.ContaReceberRepository;
import com.baronesa.emporio.service.base.BaseListService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContaReceberListService extends BaseListService<ContaReceber> {
    private final ContaReceberRepository contaReceberRepository;
    private final DynamicFormRegistry formConfigRegistry;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /*–-–-–-–-– Base hooks –-–-–-–-–*/
    @Override
    protected JpaSpecificationExecutor<ContaReceber> getRepository() {
        return contaReceberRepository;
    }

    @Override
    protected Class<ContaReceber> getEntityClass() {
        return ContaReceber.class;
    }

    @Override
    protected BaseFormConfig getFormConfig(MessageResolver resolver) {
        return formConfigRegistry.getConfig("contas-receber");
    }

    @Override
    protected Map<String, Object> entityToRow(ContaReceber conta) {
        Map<String, Object> row = new HashMap<>();

        // Campos básicos
        row.put("id", conta.getId());
        row.put("numeroDocumento", Objects.toString(conta.getNumeroDocumento(), ""));
        row.put("descricao", Objects.toString(conta.getDescricao(), ""));

        // Cliente
        row.put("cliente", conta.getCliente() != null ? conta.getCliente().getNome() : "");
        row.put("telefone", conta.getCliente() != null ? conta.getCliente().getTelefone() : "");

        // Tipo de receita
        row.put("tipoReceita", conta.getTipoReceita() != null ? conta.getTipoReceita().getNome() : "");
        row.put("tipoReceitaId", conta.getTipoReceita() != null ? conta.getTipoReceita().getId() : null);

        // Valores
        row.put("valorTotal", conta.getValorTotal());
        row.put("numeroParcelas", conta.getNumeroParcelas());

        // Status
        String status;
        String statusColor;
        String statusIcon;

        if (conta.isQuitada()) {
            status = "Quitada";
            statusColor = "green";
            statusIcon = "check_circle";
        } else if (conta.getParcelas().stream().anyMatch(p -> p.isVencida())) {
            status = "Vencida";
            statusColor = "red";
            statusIcon = "error";
        } else {
            status = "Em aberto";
            statusColor = "orange";
            statusIcon = "schedule";
        }
        row.put("status", status);
        row.put("_statusColor", statusColor);
        row.put("_statusIcon", statusIcon);

        // Cálculos sobre parcelas
        long parcelasPagas = conta.getParcelas().stream()
                .filter(ContaReceberParcela::isRecebida)
                .count();
        row.put("parcelasPagas", (int) parcelasPagas);

        BigDecimal valorTotalPago = conta.getParcelas().stream()
                .filter(ContaReceberParcela::isRecebida)
                .map(ContaReceberParcela::getValorRecebido)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        row.put("valorTotalPago", valorTotalPago);

        BigDecimal valorTotalPendente = conta.getParcelas().stream()
                .filter(p -> !p.isRecebida())
                .map(ContaReceberParcela::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        row.put("valorTotalPendente", valorTotalPendente);

        // Parcela pendente (próxima ou vencida)
        Optional<ContaReceberParcela> parcelaPendente = conta.getParcelas().stream()
                .filter(p -> !p.isRecebida())
                .min(Comparator.comparing(ContaReceberParcela::getDataVencimento));

        if (parcelaPendente.isPresent()) {
            row.put("dataVencimentoParcela", parcelaPendente.get().getDataVencimento().format(DATE_FORMATTER));
            row.put("valorParcela", parcelaPendente.get().getValor());
            row.put("numeroParcela", parcelaPendente.get().getNumeroParcela());
        } else {
            row.put("dataVencimentoParcela", "-");
            row.put("valorParcela", BigDecimal.ZERO);
            row.put("numeroParcela", 0);
        }

        return row;
    }
}