package com.baronesa.emporio.service.list;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.dynamicform.config.DynamicFormRegistry;
import com.baronesa.emporio.entity.Pagamento;
import com.baronesa.emporio.enums.StatusPagamento;
import com.baronesa.emporio.repository.PagamentoRepository;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.util.FilterSpecificationBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PagamentoListService extends BaseListService<Pagamento> {

    private final PagamentoRepository pagamentoRepository;
    private final DynamicFormRegistry formConfigRegistry;

    @Override
    protected JpaSpecificationExecutor<Pagamento> getRepository() {
        return pagamentoRepository;
    }

    @Override
    protected Class<Pagamento> getEntityClass() {
        return Pagamento.class;
    }

    @Override
    protected BaseFormConfig getFormConfig(MessageResolver resolver) {
        // Pode não existir definição dinâmica; BaseListService trata null com fallback simples
        try {
            return formConfigRegistry.getConfig("vendas");
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected Map<String, String> getFieldMappings() {
        // Mapear campos de ordenação do frontend para propriedades JPA
        return Map.of(
                "mesaRotulo", "sessaoMesa.mesa.rotulo",
                "mesaSlug", "sessaoMesa.mesa.slug",
                "beneficiario", "sessaoConvidado.nomeExibicao",
                "pagante", "pagante.nomeExibicao",
                "metodo", "metodo",
                "valor", "valor",
                "status", "status",
                "pagoEm", "pagoEm",
                "criadoEm", "criadoEm",
                "providerRef", "providerRef"
        );
    }

    @Override
    public Map<String, Object> list(int pagina, int tamanho, String ordenacao, String direcao, String filtroJson, MessageResolver resolver) {
        // Envolver a listagem padrão adicionando restrição status = PAID
        String sortField = ordenacao;
        if (ordenacao != null && !ordenacao.isBlank()) {
            sortField = getFieldMappings().getOrDefault(ordenacao, ordenacao);
        }

        Sort sort = (sortField != null && !sortField.isBlank())
                ? Sort.by(Sort.Direction.fromString(direcao), sortField)
                : Sort.unsorted();

        Pageable pageable = PageRequest.of(pagina, tamanho, sort);

        Specification<Pagamento> specBase = (root, query, cb) -> cb.equal(root.get("status"), StatusPagamento.PAID);
        Specification<Pagamento> specFilter = new FilterSpecificationBuilder<Pagamento>(getEntityClass()).build(filtroJson);
        Specification<Pagamento> spec = (specFilter != null) ? specBase.and(specFilter) : specBase;

        Page<Pagamento> page = pagamentoRepository.findAll(spec, pageable);

        List<Map<String, Object>> tableData = page.getContent().stream()
                .map(this::entityToRow)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        BaseFormConfig formConfig = getFormConfig(resolver);
        if (formConfig != null) {
            response = formConfig.createResponse(tableData);
        } else {
            response.put("table_data", tableData);
        }

        response.put("totalElementos", page.getTotalElements());
        response.put("totalPaginas", page.getTotalPages());
        response.put("paginaAtual", page.getNumber());
        response.put("tamanhoPagina", page.getSize());

        // Não adicionar table_order aqui - é responsabilidade do FormConfigService
        // que já obtém isso via config.createResponse()

        return response;
    }

    // Suportar FormConfigService.processFormConfig (reflexão) quando houver config dinâmica
    public Map<String, Object> listarPaginado(int pagina, int tamanho, String ordenacao, String direcao, String filtroJson) {
        return list(pagina, tamanho, ordenacao, direcao, filtroJson, null);
    }

    @Override
    protected Map<String, Object> entityToRow(Pagamento p) {
        Map<String, Object> row = new LinkedHashMap<>();

        row.put("id", p.getId());
        // Mesa
        String mesaSlug = null;
        String mesaRotulo = null;
        if (p.getSessaoMesa() != null && p.getSessaoMesa().getMesa() != null) {
            mesaSlug = p.getSessaoMesa().getMesa().getSlug();
            mesaRotulo = p.getSessaoMesa().getMesa().getRotulo();
        }
        row.put("mesaSlug", mesaSlug);
        row.put("mesaRotulo", mesaRotulo);

        // Beneficiário (quando pagamento de convidado)
        String beneficiario = p.getSessaoConvidado() != null ? p.getSessaoConvidado().getNomeExibicao() : null;
        row.put("beneficiario", beneficiario != null ? beneficiario : "Mesa toda");
        row.put("beneficiarioId", p.getSessaoConvidado() != null ? p.getSessaoConvidado().getId() : null);

        // Pagante (se informado)
        String pagante = p.getPagante() != null ? p.getPagante().getNomeExibicao() : null;
        row.put("pagante", pagante);
        row.put("paganteId", p.getPagante() != null ? p.getPagante().getId() : null);

        // Valores e método
        row.put("valor", p.getValor());
        row.put("valorBase", p.getValorBase() != null ? p.getValorBase() : p.getValor());
        row.put("valorTaxaServico", p.getValorTaxaServico());
        row.put("percentualTaxaServico", p.getPercentualTaxaServico());
        row.put("incluiTaxaServico", p.getIncluiTaxaServico());
        row.put("metodo", p.getMetodo());
        row.put("status", p.getStatus() != null ? p.getStatus().name() : null);

        // Datas e referência
        row.put("criadoEm", p.getCriadoEm());
        row.put("pagoEm", p.getPagoEm());
        row.put("providerRef", p.getProviderRef());

        return row;
    }
}
