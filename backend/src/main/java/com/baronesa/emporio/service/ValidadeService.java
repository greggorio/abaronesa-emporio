package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.*;
import com.baronesa.emporio.entity.*;
import com.baronesa.emporio.exception.BusinessException;
import com.baronesa.emporio.exception.NotFoundException;
import com.baronesa.emporio.enums.TarefaValidadeDivergenciaAcao;
import com.baronesa.emporio.enums.TarefaValidadeItemAcao;
import com.baronesa.emporio.enums.TarefaValidadeStatus;
import com.baronesa.emporio.repository.*;
import com.baronesa.emporio.util.ConfigManager;
import com.baronesa.emporio.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValidadeService {

    private final EstoqueLoteRepository estoqueLoteRepository;
    private final TarefaValidadeRepository tarefaValidadeRepository;
    private final TarefaValidadeItemRepository tarefaValidadeItemRepository;
    private final TarefaValidadeDivergenciaRepository tarefaValidadeDivergenciaRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoSKURepository produtoSKURepository;
    private final MovimentoEstoqueService movimentoEstoqueService;
    private final MovimentoEstoqueRepository movimentoEstoqueRepository;
    private final MovimentoEstoqueLoteRepository movimentoEstoqueLoteRepository;
    private final EstoqueRepository estoqueRepository;
    private final SecurityUtils securityUtils;
    private final ConfigManager configManager;

    public List<ValidadeAlertaLoteDTO> getAlertasValidade(Boolean somenteComSaldo, Long skuId, Long produtoId, String status, Integer limit, Integer offset) {
        // Validar status se fornecido
        if (status != null && !status.isEmpty()) {
            validarStatusValidade(status);
        }

        // Obter o percentual de alerta do config manager
        int alertaPercentual = configManager.getValidadeAlertarVencimentoPercentual();

        // Buscar os lotes com base nos filtros
        List<EstoqueLote> lotes = estoqueLoteRepository.findAlertasValidade(somenteComSaldo, skuId, produtoId);

        List<ValidadeAlertaLoteDTO> alertas = new ArrayList<>();

        for (EstoqueLote lote : lotes) {
            ValidadeAlertaLoteDTO dto = criarAlertaDTO(lote, alertaPercentual);

            // Aplicar filtro de status se especificado
            if (status != null && !status.isEmpty() && !dto.getStatus().equals(status.toUpperCase())) {
                continue;
            }

            alertas.add(dto);
        }

        // Aplicar paginação
        int offsetValue = offset != null ? offset : 0;
        int limitValue = limit != null ? limit : 200;

        if (offsetValue < 0) offsetValue = 0;
        if (limitValue <= 0) limitValue = 200;

        int startIndex = offsetValue;
        int endIndex = Math.min(startIndex + limitValue, alertas.size());

        if (startIndex >= alertas.size()) {
            return new ArrayList<>();
        }

        return alertas.subList(startIndex, endIndex);
    }

    private ValidadeAlertaLoteDTO criarAlertaDTO(EstoqueLote lote, int alertaPercentual) {
        LocalDate hoje = LocalDate.now();
        LocalDate dataValidade = lote.getDataValidade();

        // Calcular dias para vencer
        Integer diasParaVencer = null;
        if (dataValidade != null) {
            if (dataValidade.isBefore(hoje)) {
                // Se vencido, diasParaVencer é negativo (dias desde o vencimento)
                diasParaVencer = (int) ChronoUnit.DAYS.between(dataValidade, hoje) * -1;
            } else {
                // Se ainda não venceu, diasParaVencer é positivo
                diasParaVencer = (int) ChronoUnit.DAYS.between(hoje, dataValidade);
            }
        }

        // Obter vida útil do produto
        Integer vidaUtilDias = null;
        if (lote.getProdutoSku() != null && lote.getProdutoSku().getProduto() != null) {
            Integer produtoVidaUtil = lote.getProdutoSku().getProduto().getVidaUtilDias();
            if (produtoVidaUtil != null && produtoVidaUtil > 0) {
                vidaUtilDias = produtoVidaUtil;
            }
        }

        // Calcular percentual de vida restante
        Double percentualVidaRestante = null;
        String status;

        if (diasParaVencer == null) {
            status = "SEM_DATA_VALIDADE";
        } else if (diasParaVencer < 0) {
            // VENCIDO tem precedência sobre SEM_VIDA_UTIL
            status = "VENCIDO";
        } else if (vidaUtilDias == null || vidaUtilDias <= 0) {
            status = "SEM_VIDA_UTIL";
        } else {
            // Calcular percentual de vida restante
            percentualVidaRestante = ((double) diasParaVencer * 100) / vidaUtilDias;

            if (percentualVidaRestante <= alertaPercentual) {
                status = "CRITICO";
            } else if (percentualVidaRestante <= (alertaPercentual * 2)) {
                status = "ATENCAO";
            } else {
                status = "OK";
            }
        }

        return ValidadeAlertaLoteDTO.builder()
                .estoqueLoteId(lote.getId())
                .skuId(lote.getProdutoSku() != null ? lote.getProdutoSku().getId() : null)
                .skuCodigo(lote.getProdutoSku() != null ? lote.getProdutoSku().getSku() : null)
                .skuDescricao(lote.getProdutoSku() != null ?
                    (lote.getProdutoSku().getProduto() != null ?
                        lote.getProdutoSku().getProduto().getNome() +
                        (lote.getProdutoSku().getVariacao() != null ? " - " + lote.getProdutoSku().getVariacao() : "")
                        : null) : null)
                .produtoId(lote.getProdutoSku() != null && lote.getProdutoSku().getProduto() != null ?
                    lote.getProdutoSku().getProduto().getId() : null)
                .produtoNome(lote.getProdutoSku() != null && lote.getProdutoSku().getProduto() != null ?
                    lote.getProdutoSku().getProduto().getNome() : null)
                .lote(lote.getLote())
                .dataValidade(lote.getDataValidade())
                .quantidade(lote.getQuantidade())
                .diasParaVencer(diasParaVencer)
                .vidaUtilDias(vidaUtilDias)
                .alertaPercentual(alertaPercentual)
                .percentualVidaRestante(percentualVidaRestante)
                .status(status)
                .build();
    }

    public ValidadeDashboardDTO getDashboard(Boolean somenteComSaldo, Long skuId, Long produtoId) {
        // Obter o percentual de alerta do config manager
        int alertaPercentual = configManager.getValidadeAlertarVencimentoPercentual();

        // Buscar os lotes com base nos filtros
        List<EstoqueLote> lotes = estoqueLoteRepository.findAlertasValidade(somenteComSaldo, skuId, produtoId);

        // Contadores para cada status
        int vencido = 0;
        int critico = 0;
        int atencao = 0;
        int semVidaUtil = 0;
        int ok = 0;

        // Processar cada lote e contar por status
        for (EstoqueLote lote : lotes) {
            String status = determinarStatus(lote, alertaPercentual);

            switch (status) {
                case "VENCIDO":
                    vencido++;
                    break;
                case "CRITICO":
                    critico++;
                    break;
                case "ATENCAO":
                    atencao++;
                    break;
                case "SEM_VIDA_UTIL":
                    semVidaUtil++;
                    break;
                case "OK":
                    ok++;
                    break;
            }
        }

        int total = vencido + critico + atencao + semVidaUtil + ok;

        return ValidadeDashboardDTO.builder()
                .vencido(vencido)
                .critico(critico)
                .atencao(atencao)
                .semVidaUtil(semVidaUtil)
                .ok(ok)
                .total(total)
                .build();
    }

    public ValidadeProdutoLotesDTO getLotesPorProduto(Long produtoId,
                                                      Long skuId,
                                                      String status,
                                                      Boolean somenteComSaldo,
                                                      Boolean incluirSemLote,
                                                      String buscaLote) {
        if (status != null && !status.isBlank()) {
            validarStatusValidade(status);
        }

        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new NotFoundException("Produto não encontrado"));

        List<ProdutoSKU> skus = produtoSKURepository.findByProdutoIdWithProdutoAndEstoque(produtoId);
        if (skuId != null) {
            skus = skus.stream()
                    .filter(sku -> sku.getId().equals(skuId))
                    .collect(Collectors.toList());
        }

        Map<Long, ProdutoSKU> skuMap = skus.stream()
                .collect(Collectors.toMap(ProdutoSKU::getId, sku -> sku, (a, b) -> a, LinkedHashMap::new));

        List<EstoqueLote> lotesProduto = estoqueLoteRepository.findByProdutoIdOrderBySkuAndDataValidadeAscNullsLast(produtoId);
        List<ValidadeProdutoLoteDTO> lotesFiltradosResumo = new ArrayList<>();

        Map<Long, List<ValidadeProdutoLoteDTO>> lotesPorSku = new LinkedHashMap<>();
        for (ProdutoSKU sku : skus) {
            lotesPorSku.put(sku.getId(), new ArrayList<>());
        }

        int alertaPercentual = configManager.getValidadeAlertarVencimentoPercentual();
        String buscaNormalizada = buscaLote != null ? buscaLote.trim().toLowerCase() : null;
        String statusNormalizado = status != null ? status.trim().toUpperCase() : null;
        boolean somenteSaldo = somenteComSaldo == null || somenteComSaldo;
        boolean incluirSemLoteValue = incluirSemLote == null || incluirSemLote;

        for (EstoqueLote lote : lotesProduto) {
            ProdutoSKU sku = lote.getProdutoSku();
            if (sku == null || !skuMap.containsKey(sku.getId())) {
                continue;
            }

            BigDecimal quantidade = lote.getQuantidade() == null ? BigDecimal.ZERO : lote.getQuantidade();
            if (somenteSaldo && quantidade.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            boolean semLote = isSemLote(lote);
            if (!incluirSemLoteValue && semLote) {
                continue;
            }

            String loteStatus = determinarStatus(lote, alertaPercentual);
            if (statusNormalizado != null && !statusNormalizado.equals(loteStatus)) {
                continue;
            }

            String loteCodigo = lote.getLote() == null ? "" : lote.getLote();
            if (buscaNormalizada != null && !buscaNormalizada.isBlank() &&
                    !loteCodigo.toLowerCase().contains(buscaNormalizada)) {
                continue;
            }

            ValidadeProdutoLoteDTO dto = ValidadeProdutoLoteDTO.builder()
                    .estoqueLoteId(lote.getId())
                    .lote(lote.getLote())
                    .dataValidade(lote.getDataValidade())
                    .status(loteStatus)
                    .quantidade(quantidade)
                    .rastreabilidade(semLote ? "SEM_LOTE" : "LOTE_VALIDO")
                    .build();

            lotesPorSku.computeIfAbsent(sku.getId(), ignored -> new ArrayList<>()).add(dto);
            lotesFiltradosResumo.add(dto);
        }

        List<ValidadeProdutoSkuDTO> skusDTO = new ArrayList<>();
        for (ProdutoSKU sku : skus) {
            BigDecimal estoqueAgregado = BigDecimal.valueOf(
                    sku.getEstoque() != null && sku.getEstoque().getQuantidade() != null
                            ? sku.getEstoque().getQuantidade()
                            : 0
            );

            BigDecimal somaLotes = lotesProduto.stream()
                    .filter(lote -> lote.getProdutoSku() != null && sku.getId().equals(lote.getProdutoSku().getId()))
                    .map(lote -> lote.getQuantidade() == null ? BigDecimal.ZERO : lote.getQuantidade())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String skuDescricao = sku.getProduto().getNome();
            if (sku.getVariacao() != null && !sku.getVariacao().isBlank()) {
                skuDescricao += " - " + sku.getVariacao();
            }

            skusDTO.add(ValidadeProdutoSkuDTO.builder()
                    .skuId(sku.getId())
                    .skuCodigo(sku.getSku())
                    .skuDescricao(skuDescricao)
                    .estoqueAgregado(estoqueAgregado)
                    .somaLotes(somaLotes)
                    .possuiDivergencia(somaLotes.compareTo(estoqueAgregado) != 0)
                    .lotes(lotesPorSku.getOrDefault(sku.getId(), List.of()))
                    .build());
        }

        ValidadeProdutoResumoDTO resumo = ValidadeProdutoResumoDTO.builder()
                .totalSkus(skus.size())
                .totalLotes(lotesFiltradosResumo.size())
                .lotesComSaldo((int) lotesFiltradosResumo.stream()
                        .filter(lote -> lote.getQuantidade() != null && lote.getQuantidade().compareTo(BigDecimal.ZERO) > 0)
                        .count())
                .vencidos(contarStatus(lotesFiltradosResumo, "VENCIDO"))
                .criticos(contarStatus(lotesFiltradosResumo, "CRITICO"))
                .atencao(contarStatus(lotesFiltradosResumo, "ATENCAO"))
                .semLote((int) lotesFiltradosResumo.stream()
                        .filter(lote -> "SEM_LOTE".equals(lote.getRastreabilidade()))
                        .count())
                .build();

        return ValidadeProdutoLotesDTO.builder()
                .produtoId(produto.getId())
                .produtoNome(produto.getNome())
                .controlaValidade(Boolean.TRUE.equals(produto.getControlaValidade()))
                .vidaUtilDias(produto.getVidaUtilDias())
                .resumo(resumo)
                .skus(skusDTO)
                .build();
    }

    @Transactional
    public ValidadeProdutoLoteDTO criarLotePorProduto(Long produtoId, CriarLoteProdutoValidadeRequest request) {
        if (request == null) {
            throw new BusinessException("Dados do lote são obrigatórios");
        }
        if (request.getSkuId() == null) {
            throw new BusinessException("SKU é obrigatório");
        }
        validarQuantidadePositiva(request.getQuantidade(), "Quantidade deve ser maior que zero");

        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new NotFoundException("Produto não encontrado"));
        validarProdutoControlaValidade(produto);

        ProdutoSKU sku = produtoSKURepository.findById(request.getSkuId())
                .orElseThrow(() -> new NotFoundException("SKU não encontrado"));

        if (sku.getProduto() == null || !produtoId.equals(sku.getProduto().getId())) {
            throw new BusinessException("SKU não pertence ao produto informado");
        }

        EstoqueLote estoqueLote = buscarOuCriarLote(
                sku,
                request.getLote(),
                request.getDataValidade()
        );

        BigDecimal quantidadeAtual = valorSeguro(estoqueLote.getQuantidade());
        BigDecimal novaQuantidade = quantidadeAtual.add(request.getQuantidade());

        LocalDateTime agora = LocalDateTime.now();
        if (estoqueLote.getId() == null) {
            estoqueLote.setCreatedAt(agora);
        }
        estoqueLote.setQuantidade(novaQuantidade);
        estoqueLote.setUpdatedAt(agora);
        estoqueLote = estoqueLoteRepository.save(estoqueLote);

        registrarAuditoriaLote(
                sku,
                estoqueLote,
                request.getQuantidade(),
                request.getObservacao(),
                "CRIACAO_LOTE"
        );

        return toProdutoLoteDTO(estoqueLote);
    }

    @Transactional
    public ValidadeProdutoLoteDTO ajustarLote(Long estoqueLoteId, AjusteEstoqueLoteRequest request) {
        if (request == null) {
            throw new BusinessException("Dados do ajuste são obrigatórios");
        }
        if (request.getAcao() == null || request.getAcao().isBlank()) {
            throw new BusinessException("Ação é obrigatória");
        }
        validarQuantidadePositiva(request.getQuantidade(), "Quantidade do ajuste deve ser maior que zero");

        EstoqueLote estoqueLote = estoqueLoteRepository.findById(estoqueLoteId)
                .orElseThrow(() -> new NotFoundException("Lote não encontrado"));

        ProdutoSKU sku = estoqueLote.getProdutoSku();
        if (sku == null || sku.getProduto() == null) {
            throw new BusinessException("Lote sem SKU ou produto vinculado");
        }
        validarProdutoControlaValidade(sku.getProduto());

        String acao = request.getAcao().trim().toUpperCase();
        BigDecimal quantidadeAtual = valorSeguro(estoqueLote.getQuantidade());
        BigDecimal novaQuantidade;

        switch (acao) {
            case "SET":
                novaQuantidade = request.getQuantidade();
                break;
            case "ADD":
                novaQuantidade = quantidadeAtual.add(request.getQuantidade());
                break;
            case "REMOVE":
                novaQuantidade = quantidadeAtual.subtract(request.getQuantidade());
                break;
            default:
                throw new BusinessException("Ação inválida. Use: SET, ADD ou REMOVE");
        }

        if (novaQuantidade.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("O ajuste não pode deixar o lote com quantidade negativa");
        }

        BigDecimal delta = novaQuantidade.subtract(quantidadeAtual);
        estoqueLote.setQuantidade(novaQuantidade);
        estoqueLote.setUpdatedAt(LocalDateTime.now());
        estoqueLote = estoqueLoteRepository.save(estoqueLote);

        if (delta.compareTo(BigDecimal.ZERO) != 0) {
            registrarAuditoriaLote(
                    sku,
                    estoqueLote,
                    delta,
                    request.getObservacao(),
                    "AJUSTE_LOTE_" + acao
            );
        }

        return toProdutoLoteDTO(estoqueLote);
    }

    @Transactional
    public ValidadeProdutoLoteDTO zerarLote(Long estoqueLoteId, ZerarEstoqueLoteRequest request) {
        EstoqueLote estoqueLote = estoqueLoteRepository.findById(estoqueLoteId)
                .orElseThrow(() -> new NotFoundException("Lote não encontrado"));

        ProdutoSKU sku = estoqueLote.getProdutoSku();
        if (sku == null || sku.getProduto() == null) {
            throw new BusinessException("Lote sem SKU ou produto vinculado");
        }
        validarProdutoControlaValidade(sku.getProduto());

        BigDecimal quantidadeAtual = valorSeguro(estoqueLote.getQuantidade());
        if (quantidadeAtual.compareTo(BigDecimal.ZERO) == 0) {
            return toProdutoLoteDTO(estoqueLote);
        }

        estoqueLote.setQuantidade(BigDecimal.ZERO);
        estoqueLote.setUpdatedAt(LocalDateTime.now());
        estoqueLote = estoqueLoteRepository.save(estoqueLote);

        registrarAuditoriaLote(
                sku,
                estoqueLote,
                quantidadeAtual.negate(),
                request != null ? request.getObservacao() : null,
                "ZERAR_LOTE"
        );

        return toProdutoLoteDTO(estoqueLote);
    }

    public List<ValidadeProdutoLoteMovimentoDTO> listarMovimentosLote(Long estoqueLoteId) {
        EstoqueLote estoqueLote = estoqueLoteRepository.findById(estoqueLoteId)
                .orElseThrow(() -> new NotFoundException("Lote não encontrado"));

        return movimentoEstoqueLoteRepository.findByEstoqueLoteIdOrderByMovimentoDesc(estoqueLote.getId()).stream()
                .map(this::toProdutoLoteMovimentoDTO)
                .collect(Collectors.toList());
    }

    /**
     * Método utilitário para determinar o status de um lote com base nas mesmas regras
     * usadas no método criarAlertaDTO
     */
    private String determinarStatus(EstoqueLote lote, int alertaPercentual) {
        LocalDate hoje = LocalDate.now();
        LocalDate dataValidade = lote.getDataValidade();

        // Calcular dias para vencer
        Integer diasParaVencer = null;
        if (dataValidade != null) {
            if (dataValidade.isBefore(hoje)) {
                // Se vencido, diasParaVencer é negativo (dias desde o vencimento)
                diasParaVencer = (int) ChronoUnit.DAYS.between(dataValidade, hoje) * -1;
            } else {
                // Se ainda não venceu, diasParaVencer é positivo
                diasParaVencer = (int) ChronoUnit.DAYS.between(hoje, dataValidade);
            }
        }

        // Obter vida útil do produto
        Integer vidaUtilDias = null;
        if (lote.getProdutoSku() != null && lote.getProdutoSku().getProduto() != null) {
            Integer produtoVidaUtil = lote.getProdutoSku().getProduto().getVidaUtilDias();
            if (produtoVidaUtil != null && produtoVidaUtil > 0) {
                vidaUtilDias = produtoVidaUtil;
            }
        }

        // Determinar status com base nas regras de precedência
        if (diasParaVencer == null) {
            return "SEM_DATA_VALIDADE";
        } else if (diasParaVencer < 0) {
            // VENCIDO tem precedência sobre SEM_VIDA_UTIL
            return "VENCIDO";
        } else if (vidaUtilDias == null || vidaUtilDias <= 0) {
            return "SEM_VIDA_UTIL";
        } else {
            // Calcular percentual de vida restante
            double percentualVidaRestante = ((double) diasParaVencer * 100) / vidaUtilDias;

            if (percentualVidaRestante <= alertaPercentual) {
                return "CRITICO";
            } else if (percentualVidaRestante <= (alertaPercentual * 2)) {
                return "ATENCAO";
            } else {
                return "OK";
            }
        }
    }

    private void validarStatusValidade(String status) {
        String statusUpper = status.toUpperCase();
        if (!statusUpper.equals("VENCIDO") &&
            !statusUpper.equals("CRITICO") &&
            !statusUpper.equals("ATENCAO") &&
            !statusUpper.equals("SEM_VIDA_UTIL") &&
            !statusUpper.equals("SEM_DATA_VALIDADE") &&
            !statusUpper.equals("OK")) {
            throw new BusinessException(
                    "Status inválido. Use: VENCIDO, CRITICO, ATENCAO, SEM_VIDA_UTIL, SEM_DATA_VALIDADE, OK");
        }
    }

    private boolean isSemLote(EstoqueLote lote) {
        if (lote == null) {
            return false;
        }
        String codigo = lote.getLote();
        return codigo == null || codigo.isBlank() || EstoqueLote.DEFAULT_LOTE.equals(codigo);
    }

    private int contarStatus(List<ValidadeProdutoLoteDTO> lotes, String status) {
        return (int) lotes.stream()
                .filter(lote -> status.equals(lote.getStatus()))
                .count();
    }

    private void validarQuantidadePositiva(BigDecimal quantidade, String mensagem) {
        if (quantidade == null || quantidade.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(mensagem);
        }
    }

    private void validarProdutoControlaValidade(Produto produto) {
        if (produto == null) {
            throw new BusinessException("Produto inválido");
        }
        if (!Boolean.TRUE.equals(produto.getControlaValidade())) {
            throw new BusinessException("Produto não está configurado para controle de validade");
        }
    }

    private EstoqueLote buscarOuCriarLote(ProdutoSKU sku, String lote, LocalDate dataValidade) {
        String loteNormalizado = lote == null ? "" : lote;
        LocalDate dataValidadeNormalizada = dataValidade != null
                ? dataValidade
                : EstoqueLote.DEFAULT_DATA_VALIDADE;

        return estoqueLoteRepository.findBySkuLoteValidadeNullable(
                        sku.getId(),
                        loteNormalizado,
                        dataValidadeNormalizada,
                        EstoqueLote.DEFAULT_DATA_VALIDADE
                )
                .orElseGet(() -> EstoqueLote.builder()
                        .produtoSku(sku)
                        .lote(lote)
                        .dataValidade(dataValidade)
                        .quantidade(BigDecimal.ZERO)
                        .build());
    }

    private void registrarAuditoriaLote(ProdutoSKU sku,
                                        EstoqueLote estoqueLote,
                                        BigDecimal deltaLote,
                                        String observacao,
                                        String operacao) {
        Usuario usuario = null;
        try {
            usuario = securityUtils.getUsuarioAtual();
        } catch (Exception e) {
            log.warn("Não foi possível obter usuário logado para auditoria de lote: {}", e.getMessage());
        }

        BigDecimal estoqueAgregado = BigDecimal.valueOf(
                sku.getEstoque() != null && sku.getEstoque().getQuantidade() != null
                        ? sku.getEstoque().getQuantidade()
                        : 0
        );

        String observacaoFinal = "Aba validade produto - " + operacao;
        if (observacao != null && !observacao.isBlank()) {
            observacaoFinal += ": " + observacao.trim();
        }

        MovimentoEstoque movimento = MovimentoEstoque.builder()
                .sku(sku)
                .produto(sku.getProduto())
                .tipoMovimento(com.baronesa.emporio.enums.TipoMovimentoEstoque.AJUSTE.getCodigo())
                .quantidade(BigDecimal.ZERO)
                .estoqueAnterior(estoqueAgregado)
                .estoqueAtual(estoqueAgregado)
                .observacao(observacaoFinal)
                .documentoReferencia("ABA_VALIDADE_PRODUTO")
                .usuario(usuario)
                .build();

        movimento = movimentoEstoqueRepository.save(movimento);

        MovimentoEstoqueLote movimentoLote = MovimentoEstoqueLote.builder()
                .movimentoEstoque(movimento)
                .estoqueLote(estoqueLote)
                .quantidade(deltaLote)
                .createdAt(LocalDateTime.now())
                .build();
        movimentoEstoqueLoteRepository.save(movimentoLote);
    }

    private BigDecimal valorSeguro(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private ValidadeProdutoLoteDTO toProdutoLoteDTO(EstoqueLote lote) {
        int alertaPercentual = configManager.getValidadeAlertarVencimentoPercentual();
        return ValidadeProdutoLoteDTO.builder()
                .estoqueLoteId(lote.getId())
                .lote(lote.getLote())
                .dataValidade(lote.getDataValidade())
                .status(determinarStatus(lote, alertaPercentual))
                .quantidade(valorSeguro(lote.getQuantidade()))
                .rastreabilidade(isSemLote(lote) ? "SEM_LOTE" : "LOTE_VALIDO")
                .build();
    }

    private ValidadeProdutoLoteMovimentoDTO toProdutoLoteMovimentoDTO(MovimentoEstoqueLote movimentoLote) {
        MovimentoEstoque movimento = movimentoLote.getMovimentoEstoque();
        com.baronesa.emporio.enums.TipoMovimentoEstoque tipo =
                com.baronesa.emporio.enums.TipoMovimentoEstoque.fromCodigo(movimento.getTipoMovimento());

        return ValidadeProdutoLoteMovimentoDTO.builder()
                .id(movimentoLote.getId())
                .dataMovimento(movimento.getDataMovimento() != null ? movimento.getDataMovimento() : movimentoLote.getCreatedAt())
                .tipoMovimento(movimento.getTipoMovimento())
                .tipoMovimentoDescricao(tipo.getDescricao())
                .deltaQuantidade(normalizarDeltaHistoricoLote(tipo, movimentoLote.getQuantidade()))
                .observacao(movimento.getObservacao())
                .documentoReferencia(movimento.getDocumentoReferencia())
                .usuarioNome(movimento.getUsuario() != null ? movimento.getUsuario().getNome() : null)
                .build();
    }

    private BigDecimal normalizarDeltaHistoricoLote(com.baronesa.emporio.enums.TipoMovimentoEstoque tipo,
                                                    BigDecimal quantidade) {
        BigDecimal valor = valorSeguro(quantidade);
        switch (tipo) {
            case VENDA:
            case CONSUMO_PRODUCAO:
            case CONSIGNACAO:
            case ESTORNO_ENTRADA:
            case ZERAR_ESTOQUE:
                return valor.abs().negate();
            case ENTRADA:
            case PRODUCAO:
            case ESTORNO_VENDA:
            case ESTORNO_CONSIGNACAO:
            case INICIAR_ESTOQUE:
            case INVENTARIO:
                return valor.abs();
            case AJUSTE:
            default:
                return valor;
        }
    }

    // =========================
    // Tarefa de validade (contagem)
    // =========================

    public TarefaValidadeDTO criarTarefa(TarefaValidadeRequest request) {
        if (request == null) {
            request = new TarefaValidadeRequest();
        }
        Usuario usuario = null;
        try {
            usuario = securityUtils.getUsuarioAtual();
        } catch (Exception e) {
            log.warn("Não foi possível obter usuário logado: {}", e.getMessage());
        }

        LocalDateTime agora = LocalDateTime.now();

        TarefaValidade tarefa = TarefaValidade.builder()
                .status(TarefaValidadeStatus.RASCUNHO)
                .observacao(request.getObservacao())
                .usuario(usuario)
                .criadoEm(agora)
                .updatedAt(agora)
                .build();

        tarefa = tarefaValidadeRepository.save(tarefa);

        if (request.getItens() != null && !request.getItens().isEmpty()) {
            adicionarItens(tarefa.getId(), request.getItens());
        }

        return toDTO(tarefaValidadeRepository.findById(tarefa.getId()).orElseThrow());
    }

    public TarefaValidadeDTO adicionarItens(Long tarefaId, List<TarefaValidadeItemRequest> itens) {
        TarefaValidade tarefa = tarefaValidadeRepository.findById(tarefaId)
                .orElseThrow(() -> new NotFoundException("Tarefa de validade não encontrada"));

        if (tarefa.getStatus() != TarefaValidadeStatus.RASCUNHO) {
            throw new BusinessException("Só é possível adicionar itens em tarefas em rascunho");
        }

        if (itens == null || itens.isEmpty()) {
            return toDTO(tarefa);
        }

        LocalDateTime agora = LocalDateTime.now();
        List<TarefaValidadeItem> novos = new ArrayList<>();
        for (TarefaValidadeItemRequest req : itens) {
            ProdutoSKU sku = produtoSKURepository.findById(req.getSkuId())
                    .orElseThrow(() -> new NotFoundException("SKU não encontrado: " + req.getSkuId()));

            TarefaValidadeItem item = TarefaValidadeItem.builder()
                    .tarefa(tarefa)
                    .sku(sku)
                    .lote(req.getLote())
                    .dataValidade(req.getDataValidade())
                    .quantidade(req.getQuantidade())
                    .acao(req.getAcao() != null ? req.getAcao() : TarefaValidadeItemAcao.SET)
                    .criadoEm(agora)
                    .updatedAt(agora)
                    .build();
            novos.add(item);
        }

        tarefaValidadeItemRepository.saveAll(novos);
        if (tarefa.getItens() == null) {
            tarefa.setItens(new ArrayList<>());
        }
        tarefa.getItens().addAll(novos);
        tarefa.setUpdatedAt(agora);
        tarefaValidadeRepository.save(tarefa);

        return toDTO(tarefa);
    }

    public TarefaValidadeDTO removerItem(Long tarefaId, Long itemId) {
        TarefaValidade tarefa = tarefaValidadeRepository.findById(tarefaId)
                .orElseThrow(() -> new NotFoundException("Tarefa de validade não encontrada"));

        if (tarefa.getStatus() != TarefaValidadeStatus.RASCUNHO) {
            throw new BusinessException("Só é possível remover itens de tarefas em rascunho");
        }

        TarefaValidadeItem item = tarefaValidadeItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item da tarefa não encontrado"));

        if (item.getTarefa() == null || !tarefaId.equals(item.getTarefa().getId())) {
            throw new BusinessException("O item informado não pertence à tarefa");
        }

        tarefaValidadeItemRepository.delete(item);

        if (tarefa.getItens() != null) {
            tarefa.getItens().removeIf(existing -> itemId.equals(existing.getId()));
        }

        tarefa.setUpdatedAt(LocalDateTime.now());
        tarefaValidadeRepository.save(tarefa);

        return toDTO(tarefa);
    }

    public TarefaValidadeDTO finalizarTarefa(Long tarefaId) {
        TarefaValidade tarefa = tarefaValidadeRepository.findById(tarefaId)
                .orElseThrow(() -> new NotFoundException("Tarefa de validade não encontrada"));

        if (tarefa.getStatus() != TarefaValidadeStatus.RASCUNHO) {
            throw new BusinessException("Só é possível finalizar tarefas em rascunho");
        }

        List<TarefaValidadeItem> itens = tarefaValidadeItemRepository.findByTarefa(tarefa);
        if (itens.isEmpty()) {
            throw new BusinessException("Tarefa sem itens para finalizar");
        }

        LocalDateTime agora = LocalDateTime.now();

        // Aplicar contagem no sub-ledger (não mexe no agregado)
        for (TarefaValidadeItem item : itens) {
            String loteNormalizado = item.getLote() == null ? "" : item.getLote();
            LocalDate dataValidadeNormalizada = item.getDataValidade() != null
                    ? item.getDataValidade()
                    : EstoqueLote.DEFAULT_DATA_VALIDADE;

            EstoqueLote estoqueLote = estoqueLoteRepository.findBySkuLoteValidadeNullable(
                            item.getSku().getId(),
                            loteNormalizado,
                            dataValidadeNormalizada,
                            EstoqueLote.DEFAULT_DATA_VALIDADE
                    )
                    .orElseGet(() -> EstoqueLote.builder()
                            .produtoSku(item.getSku())
                            .lote(item.getLote())
                            .dataValidade(item.getDataValidade())
                            .quantidade(BigDecimal.ZERO)
                            .createdAt(agora)
                            .updatedAt(agora)
                            .build());

            BigDecimal quantidadeAtual = estoqueLote.getQuantidade() == null ? BigDecimal.ZERO : estoqueLote.getQuantidade();
            BigDecimal novaQuantidade = switch (item.getAcao()) {
                case SET -> item.getQuantidade();
                case ADD -> quantidadeAtual.add(item.getQuantidade());
                case REMOVE -> quantidadeAtual.subtract(item.getQuantidade());
            };

            estoqueLote.setQuantidade(novaQuantidade);
            estoqueLote.setUpdatedAt(agora);
            estoqueLoteRepository.save(estoqueLote);
        }

        // Registrar divergências por SKU (soma dos lotes vs agregado)
        Map<Long, List<TarefaValidadeItem>> itensPorSku = itens.stream()
                .collect(Collectors.groupingBy(i -> i.getSku().getId()));

        for (Long skuId : itensPorSku.keySet()) {
            BigDecimal somaLotes = estoqueLoteRepository.findByProdutoSkuIdOrderByDataValidadeAscNullsLast(skuId).stream()
                    .map(el -> el.getQuantidade() == null ? BigDecimal.ZERO : el.getQuantidade())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            ProdutoSKU sku = produtoSKURepository.findById(skuId)
                    .orElseThrow(() -> new NotFoundException("SKU não encontrado: " + skuId));

            BigDecimal estoqueAgregado = BigDecimal.valueOf(
                    sku.getEstoque() != null && sku.getEstoque().getQuantidade() != null
                            ? sku.getEstoque().getQuantidade()
                            : 0
            );

            BigDecimal diferenca = somaLotes.subtract(estoqueAgregado);

            if (diferenca.compareTo(BigDecimal.ZERO) != 0) {
                TarefaValidadeDivergencia divergencia = TarefaValidadeDivergencia.builder()
                        .tarefa(tarefa)
                        .sku(sku)
                        .estoqueAgregado(estoqueAgregado)
                        .somaLotes(somaLotes)
                        .diferenca(diferenca)
                        .acaoTomada(TarefaValidadeDivergenciaAcao.PENDENTE)
                        .criadoEm(agora)
                        .updatedAt(agora)
                        .build();
                tarefaValidadeDivergenciaRepository.save(divergencia);
            }
        }

        tarefa.setStatus(TarefaValidadeStatus.FINALIZADA);
        tarefa.setFinalizadoEm(agora);
        tarefa.setUpdatedAt(agora);
        tarefaValidadeRepository.save(tarefa);

        return toDTO(tarefa);
    }

    public List<TarefaValidadeDTO> listarTarefas() {
        return tarefaValidadeRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public TarefaValidadeDTO buscarTarefa(Long id) {
        return tarefaValidadeRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new NotFoundException("Tarefa de validade não encontrada"));
    }

    public TarefaValidadeDTO atualizarObservacao(Long tarefaId, TarefaValidadeRequest request) {
        TarefaValidade tarefa = tarefaValidadeRepository.findById(tarefaId)
                .orElseThrow(() -> new NotFoundException("Tarefa de validade não encontrada"));

        tarefa.setObservacao(request.getObservacao());
        tarefa.setUpdatedAt(LocalDateTime.now());
        tarefaValidadeRepository.save(tarefa);

        return toDTO(tarefa);
    }

    public TarefaValidadeDTO cancelarTarefa(Long tarefaId) {
        TarefaValidade tarefa = tarefaValidadeRepository.findById(tarefaId)
                .orElseThrow(() -> new NotFoundException("Tarefa de validade não encontrada"));

        if (tarefa.getStatus() != TarefaValidadeStatus.RASCUNHO) {
            throw new BusinessException("Só é possível cancelar tarefas em rascunho");
        }

        tarefa.setStatus(TarefaValidadeStatus.CANCELADA);
        tarefa.setUpdatedAt(LocalDateTime.now());
        tarefaValidadeRepository.save(tarefa);

        return toDTO(tarefa);
    }

    public List<TarefaValidadeDivergenciaDTO> listarDivergencias(Long tarefaId) {
        List<TarefaValidadeDivergencia> divergencias = tarefaId != null
                ? tarefaValidadeDivergenciaRepository.findByTarefaId(tarefaId)
                : tarefaValidadeDivergenciaRepository.findAll();

        return divergencias.stream().map(this::toDivergenciaDTO).collect(Collectors.toList());
    }

    public TarefaValidadeDivergenciaDTO tratarDivergencia(Long divergenciaId, TarefaValidadeTratarRequest request) {
        TarefaValidadeDivergencia divergencia = tarefaValidadeDivergenciaRepository.findById(divergenciaId)
                .orElseThrow(() -> new NotFoundException("Divergência não encontrada"));

        if (request.getAcao() == null) {
            throw new BusinessException("Ação é obrigatória (IGNORAR ou CRIAR_AJUSTE)");
        }

        LocalDateTime agora = LocalDateTime.now();

        if (request.getAcao() == TarefaValidadeDivergenciaAcao.IGNORAR) {
            divergencia.setAcaoTomada(TarefaValidadeDivergenciaAcao.IGNORAR);
            divergencia.setUpdatedAt(agora);
            tarefaValidadeDivergenciaRepository.save(divergencia);
            return toDivergenciaDTO(divergencia);
        }

        if (request.getAcao() == TarefaValidadeDivergenciaAcao.CRIAR_AJUSTE) {
            BigDecimal diferenca = divergencia.getDiferenca();

            MovimentoEstoqueRequest movimentoRequest = MovimentoEstoqueRequest.builder()
                    .skuId(divergencia.getSku().getId())
                    .tipoMovimento(com.baronesa.emporio.enums.TipoMovimentoEstoque.AJUSTE.getCodigo())
                    .quantidade(diferenca)
                    .observacao(String.format("Ajuste tarefa validade %d divergência %d", divergencia.getTarefa().getId(), divergencia.getId()))
                    .documentoReferencia("Tarefa validade " + divergencia.getTarefa().getId())
                    .build();

            var movimentoDTO = movimentoEstoqueService.movimentarEstoque(movimentoRequest);

            MovimentoEstoque movimento = movimentoEstoqueRepository.findById(movimentoDTO.getId())
                    .orElseThrow(() -> new NotFoundException("Movimento de ajuste não encontrado"));

            divergencia.setMovimentoEstoque(movimento);
            divergencia.setAcaoTomada(TarefaValidadeDivergenciaAcao.CRIAR_AJUSTE);
            divergencia.setUpdatedAt(agora);
            tarefaValidadeDivergenciaRepository.save(divergencia);

            return toDivergenciaDTO(divergencia);
        }

        throw new BusinessException("Ação inválida");
    }

    private TarefaValidadeDTO toDTO(TarefaValidade tarefa) {
        List<TarefaValidadeItem> itens = tarefaValidadeItemRepository.findByTarefa(tarefa);
        List<TarefaValidadeItemResumoDTO> itensDTO = itens.stream().map(item -> TarefaValidadeItemResumoDTO.builder()
                        .id(item.getId())
                        .skuId(item.getSku() != null ? item.getSku().getId() : null)
                        .produtoNome(item.getSku() != null && item.getSku().getProduto() != null ? item.getSku().getProduto().getNome() : null)
                        .lote(item.getLote())
                        .dataValidade(item.getDataValidade())
                        .quantidade(item.getQuantidade())
                        .acao(item.getAcao())
                        .build())
                .collect(Collectors.toList());

        return TarefaValidadeDTO.builder()
                .id(tarefa.getId())
                .status(tarefa.getStatus())
                .observacao(tarefa.getObservacao())
                .criadoEm(tarefa.getCriadoEm())
                .finalizadoEm(tarefa.getFinalizadoEm())
                .itens(itensDTO)
                .build();
    }

    private TarefaValidadeDivergenciaDTO toDivergenciaDTO(TarefaValidadeDivergencia div) {
        return TarefaValidadeDivergenciaDTO.builder()
                .id(div.getId())
                .tarefaId(div.getTarefa() != null ? div.getTarefa().getId() : null)
                .skuId(div.getSku() != null ? div.getSku().getId() : null)
                .skuCodigo(div.getSku() != null ? div.getSku().getSku() : null)
                .produtoNome(div.getSku() != null && div.getSku().getProduto() != null ? div.getSku().getProduto().getNome() : null)
                .estoqueAgregado(div.getEstoqueAgregado())
                .somaLotes(div.getSomaLotes())
                .diferenca(div.getDiferenca())
                .acaoTomada(div.getAcaoTomada())
                .movimentoEstoqueId(div.getMovimentoEstoque() != null ? div.getMovimentoEstoque().getId() : null)
                .criadoEm(div.getCriadoEm())
                .build();
    }
}
