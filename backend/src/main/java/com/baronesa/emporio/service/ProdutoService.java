package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.*;
import com.baronesa.emporio.dto.lookup.ProdutoLookupDTO;
import com.baronesa.emporio.entity.*;
import com.baronesa.emporio.enums.ProductSignageStatus;
import com.baronesa.emporio.enums.TipoMidia;
import com.baronesa.emporio.enums.UnidadeBase;
import com.baronesa.emporio.enums.UnidadeMedida;
import com.baronesa.emporio.event.VideoGeneratedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baronesa.emporio.exception.BusinessException;
import com.baronesa.emporio.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;
    private final SubcategoriaRepository subcategoriaRepository;
    private final FornecedorRepository fornecedorRepository;
    private final VideoProcessingService videoProcessingService;
    private final EstoqueProdutoRepository estoqueProdutoRepository;
    private final EstoqueLoteRepository estoqueLoteRepository;
    private final EmbalagemRepository embalagemRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final TranslationService translationService;
    private final ProductSignageRepository productSignageRepository;
    private final SignageRenderService signageRenderService;
    private final SignageVideoStorageService signageVideoStorageService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiImageStorageService aiImageStorageService;
    private final ApplicationEventPublisher eventPublisher;

    private static final Set<String> PRICE_REQUIRED_TEMPLATES = Set.of("clean-elegance");
    private static final Set<String> VALID_METADATA_SOURCES = Set.of("AUTO_AI", "AUTO_VIBRANT", "MANUAL");

    @Value("${store.upload.produto-dir:uploads/produtos}")
    private String uploadProdutoDir;

    public ProdutoDTO criar(ProdutoRequest request) {
        // Validar apenas o nome como obrigatório
        if (request.getNome() == null || request.getNome().trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do produto é obrigatório");
        }
        if (request.getUnidadeMedida() == null) {
            throw new IllegalArgumentException("Unidade de Medida é obrigatória");
        }
        if (request.getUnidadeBase() == null) {
            throw new IllegalArgumentException("Unidade Base é obrigatória");
        }

        validarDadosUnicos(request, null);

        Produto produto = new Produto();
        mapearRequestParaEntidade(request, produto);

        // Salva primeiro para garantir ID para geração de SKU
        produto = produtoRepository.save(produto);

        // Inicializar estoque centralizado do produto (saldo zero)
        if (produto.getEstoqueProduto() == null) {
            EstoqueProduto ep = EstoqueProduto.builder()
                    .produto(produto)
                    .quantidadeBase(0)
                    .reservadoBase(0)
                    .estoqueMinimoBase(0)
                    .build();
            estoqueProdutoRepository.save(ep);
            produto.setEstoqueProduto(ep);
        }

        // Garantir embalagem principal baseada na unidadeBase (fator 1)
        ensurePrincipalEmbalagem(produto);

        // Sincronizar SKUs após possuir ID
        sincronizarSkus(produto, request.getSkus(), produto.getTipoPrecificacao());

        // Se vendável SIMPLES e veio estoqueMinimoSkuUnico, atualizar no SKU único
        if (Boolean.FALSE.equals(produto.getInsumo())
                && produto.getTipoPrecificacao() == com.baronesa.emporio.enums.TipoPrecificacao.SIMPLES
                && request.getEstoqueMinimoSkuUnico() != null
                && produto.getSkus() != null && !produto.getSkus().isEmpty()) {
            ProdutoSKU unico = produto.getSkus().get(0);
            if (unico.getEstoque() == null) {
                unico.setEstoque(Estoque.builder().quantidade(0).estoqueMinimo(0).reservado(0).build());
            }
            unico.getEstoque().setEstoqueMinimo(request.getEstoqueMinimoSkuUnico());
        }

        produto = produtoRepository.save(produto);
        persistirSignage(produto, request);
        marcarTraducoesProduto(produto);
        return converterParaDTO(produto);
    }

    public ProdutoDTO atualizar(Long id, ProdutoRequest request) {
        // Validar apenas o nome como obrigatório
        if (request.getNome() == null || request.getNome().trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do produto é obrigatório");
        }
        if (request.getUnidadeMedida() == null) {
            throw new IllegalArgumentException("Unidade de Medida é obrigatória");
        }
        if (request.getUnidadeBase() == null) {
            throw new IllegalArgumentException("Unidade Base é obrigatória");
        }

        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + id));

        validarDadosUnicos(request, id);
        mapearRequestParaEntidade(request, produto);

        // Garantir que o estoque centralizado exista
        if (produto.getEstoqueProduto() == null) {
            EstoqueProduto ep = EstoqueProduto.builder()
                    .produto(produto)
                    .quantidadeBase(0)
                    .reservadoBase(0)
                    .estoqueMinimoBase(0)
                    .build();
            estoqueProdutoRepository.save(ep);
            produto.setEstoqueProduto(ep);
        }

        // Garantir embalagem principal baseada na unidadeBase (fator 1)
        ensurePrincipalEmbalagem(produto);

        // Após atualizar dados básicos, sincronizar SKUs
        sincronizarSkus(produto, request.getSkus(), produto.getTipoPrecificacao());

        // Se vendável SIMPLES e veio estoqueMinimoSkuUnico, atualizar no SKU único
        if (Boolean.FALSE.equals(produto.getInsumo())
                && produto.getTipoPrecificacao() == com.baronesa.emporio.enums.TipoPrecificacao.SIMPLES
                && request.getEstoqueMinimoSkuUnico() != null
                && produto.getSkus() != null && !produto.getSkus().isEmpty()) {
            ProdutoSKU unico = produto.getSkus().get(0);
            if (unico.getEstoque() == null) {
                unico.setEstoque(Estoque.builder().quantidade(0).estoqueMinimo(0).reservado(0).build());
            }
            unico.getEstoque().setEstoqueMinimo(request.getEstoqueMinimoSkuUnico());
        }

        produto = produtoRepository.save(produto);
        persistirSignage(produto, request);
        dispararSyncSignageSeNecessario(produto);
        marcarTraducoesProduto(produto);
        return converterParaDTO(produto);
    }

    @Transactional(readOnly = true)
    public ProdutoDTO buscarPorId(Long id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + id));
        return converterParaDTO(produto);
    }

    @Transactional(readOnly = true)
    public Page<ProdutoDTO> listar(Specification<Produto> spec, Pageable pageable) {
        return produtoRepository.findAll(spec, pageable)
                .map(this::converterParaDTO);
    }

    @Transactional(readOnly = true)
    public List<ProdutoDTO> listarTodos() {
        return produtoRepository.findAll().stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProdutoDTO> listarAtivos() {
        return produtoRepository.findAllAtivos().stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProdutoOptionDTO> listarOptions() {
        return produtoRepository.findByAtivoTrue().stream()
                .map(p -> ProdutoOptionDTO.builder()
                        .id(p.getId())
                        .label(p.getNome())
                        .build())
                .collect(Collectors.toList());
    }

    public void deletar(Long id) {
        if (!produtoRepository.existsById(id)) {
            throw new EntityNotFoundException("Produto não encontrado: " + id);
        }
        produtoRepository.deleteById(id);
    }

    private void validarDadosUnicos(ProdutoRequest request, Long idExcluir) {
        if (request.getCodigoInterno() != null) {
            produtoRepository.findByCodigoInterno(request.getCodigoInterno())
                    .ifPresent(p -> {
                        if (idExcluir == null || !p.getId().equals(idExcluir)) {
                            throw new IllegalArgumentException("Código interno já cadastrado: " + request.getCodigoInterno());
                        }
                    });
        }

        if (request.getCodigoBarras() != null) {
            produtoRepository.findByCodigoBarras(request.getCodigoBarras())
                    .ifPresent(p -> {
                        if (idExcluir == null || !p.getId().equals(idExcluir)) {
                            throw new IllegalArgumentException("Código de barras já cadastrado: " + request.getCodigoBarras());
                        }
                    });
        }
    }

    private void mapearRequestParaEntidade(ProdutoRequest request, Produto produto) {
        Integer vidaUtilDias = request.getVidaUtilDias();
        if (vidaUtilDias != null && vidaUtilDias <= 0) {
            throw new BusinessException("Vida útil deve ser maior que zero");
        }

        boolean possuiVidaUtil = vidaUtilDias != null;
        boolean controlaValidade = request.getControlaValidade() != null
                ? request.getControlaValidade()
                : true;
        boolean controlaEstoque = request.getControlaEstoque() != null
                ? request.getControlaEstoque()
                : true;

        if (possuiVidaUtil) {
            controlaValidade = true;
        }

        if (controlaValidade) {
            controlaEstoque = true;
        }

        if (produto.getId() != null
                && !controlaValidade
                && estoqueLoteRepository.existsByProdutoSkuProdutoId(produto.getId())) {
            throw new BusinessException("Não é possível desabilitar controle de validade em produto com histórico de lotes");
        }

        produto.setNome(request.getNome());
        produto.setSetor(request.getSetor());
        produto.setDescricao(request.getDescricao());
        produto.setCodigoInterno(request.getCodigoInterno());
        produto.setCodigoBarras(request.getCodigoBarras());
        produto.setTipo(request.getTipo());
        produto.setUnidadeMedida(request.getUnidadeMedida());
        // unidadeBase vem do request; validação acontece antes de salvar
        if (request.getUnidadeBase() != null) {
            produto.setUnidadeBase(request.getUnidadeBase());
        }

        if (request.getCategoriaId() != null) {
            produto.setCategoria(categoriaRepository.findById(request.getCategoriaId())
                    .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada: " + request.getCategoriaId())));
        } else {
            produto.setCategoria(null);
        }

        if (request.getSubcategoriaId() != null) {
            produto.setSubcategoria(subcategoriaRepository.findById(request.getSubcategoriaId())
                    .orElseThrow(() -> new EntityNotFoundException("Subcategoria não encontrada: " + request.getSubcategoriaId())));
        } else {
            produto.setSubcategoria(null);
        }

        if (request.getFornecedorId() != null) {
            produto.setFornecedor(fornecedorRepository.findById(request.getFornecedorId())
                    .orElseThrow(() -> new EntityNotFoundException("Fornecedor não encontrado: " + request.getFornecedorId())));
        } else {
            produto.setFornecedor(null);
        }

        produto.setPrecoCusto(request.getPrecoCusto());
        produto.setPrecoVenda(request.getPrecoVenda());
        produto.setTipoPrecificacao(request.getTipoPrecificacao() != null ? request.getTipoPrecificacao() : produto.getTipoPrecificacao());
        produto.setControlaEstoque(controlaEstoque);
        produto.setControlaValidade(controlaValidade);
        produto.setVidaUtilDias(vidaUtilDias);
        produto.setEstoqueAtual(request.getEstoqueAtual() != null ? request.getEstoqueAtual() : produto.getEstoqueAtual());
        produto.setAtivo(request.getAtivo() != null ? request.getAtivo() : true);
        produto.setVendavel(request.getVendavel() != null ? request.getVendavel() : true);
        // 'insumo' define estoque compartilhado por SKUs
        if (request.getInsumo() != null) {
            produto.setInsumo(request.getInsumo());
        } else if (produto.getInsumo() == null) {
            produto.setInsumo(false);
        }
        produto.setExibirNoCardapio(request.getExibirNoCardapio() != null ? request.getExibirNoCardapio() : false);
        produto.setPromocao(request.getPromocao() != null ? request.getPromocao() : false);
        produto.setDestaque(request.getDestaque() != null ? request.getDestaque() : false);
        produto.setNecessitaPreparacao(request.getNecessitaPreparacao() != null ? request.getNecessitaPreparacao() : true);
        produto.setProducaoPropria(request.getProducaoPropria() != null ? request.getProducaoPropria() : false);
        produto.setLocalPreparacao(request.getLocalPreparacao());
        produto.setOrdem(request.getOrdem() != null ? request.getOrdem() : 0);
        produto.setImagemPrincipal(request.getImagemPrincipal());

        // INSUMO: atualizar estoque_minimo_base se informado
        if (Boolean.TRUE.equals(produto.getInsumo()) && request.getEstoqueMinimoBase() != null) {
            EstoqueProduto ep = produto.getEstoqueProduto();
            if (ep == null) {
                ep = EstoqueProduto.builder()
                        .produto(produto)
                        .quantidadeBase(0)
                        .reservadoBase(0)
                        .estoqueMinimoBase(0)
                        .build();
            }
            ep.setEstoqueMinimoBase(request.getEstoqueMinimoBase());
            produto.setEstoqueProduto(ep);
        }
        
        // Lógica SIMPLES e DIRETA para dados fiscais
        ProdutoFiscal fiscal = produto.getInformacoesFiscais();
        if (fiscal == null) {
            fiscal = new ProdutoFiscal();
            fiscal.setProduto(produto);
            produto.setInformacoesFiscais(fiscal);
        }

        // 1. Primeiro aplica o objeto completo (se houver)
        if (request.getProdutoFiscal() != null) {
            mapearProdutoFiscal(produto, request.getProdutoFiscal());
        }

        // 2. Depois aplica/sobrescreve com campos planos (se houverem)
        if (request.getNcm() != null) fiscal.setNcm(request.getNcm());
        if (request.getCest() != null) fiscal.setCest(request.getCest());
        if (request.getOrigem() != null) fiscal.setOrigem(request.getOrigem());
        if (request.getCsosn() != null) fiscal.setCsosn(request.getCsosn());
        if (request.getCfop() != null) fiscal.setCfop(request.getCfop());
    }

    private void ensurePrincipalEmbalagem(Produto produto) {
        // Se já houver embalagem principal ativa, não criar
        if (produto.getEmbalagens() != null) {
            for (Embalagem e : produto.getEmbalagens()) {
                if (Boolean.TRUE.equals(e.getPrincipal()) && Boolean.TRUE.equals(e.getAtivo())) {
                    return;
                }
            }
        }

        // Criar embalagem base (fator 1) com nome conforme unidadeBase
        String nome;
        if (produto.getUnidadeBase() == UnidadeBase.MILILITRO) nome = "ML";
        else if (produto.getUnidadeBase() == UnidadeBase.GRAMA) nome = "G";
        else nome = "UN";

        Embalagem emb = Embalagem.builder()
                .produto(produto)
                .nome(nome)
                .fatorBase(1)
                .permiteVenda(true)
                .principal(true)
                .ativo(true)
                .build();
        embalagemRepository.save(emb);

        // Anexar à coleção em memória
        if (produto.getEmbalagens() != null) {
            produto.getEmbalagens().add(emb);
        }
    }

    /**
     * Sincroniza a coleção de SKUs do produto com a lista enviada no request.
     * Regras:
     * - Para SIMPLES: garante exatamente 1 SKU (principal) e gera código se necessário.
     * - Para UNIFICADA: múltiplas variações com preços do produto (não individuais).
     * - Para INDIVIDUAL: múltiplas variações com preços individuais por SKU.
     */
    private void sincronizarSkus(Produto produto, java.util.List<ProdutoSKUDTO> skusRequest, com.baronesa.emporio.enums.TipoPrecificacao tipoPrecificacao) {
        if (tipoPrecificacao == null) {
            tipoPrecificacao = com.baronesa.emporio.enums.TipoPrecificacao.SIMPLES;
        }

        if (tipoPrecificacao == com.baronesa.emporio.enums.TipoPrecificacao.SIMPLES) {
            // Garante um único SKU
            if (produto.getSkus() == null || produto.getSkus().isEmpty()) {
                com.baronesa.emporio.entity.ProdutoSKU novo = new com.baronesa.emporio.entity.ProdutoSKU();
                novo.setProduto(produto);
                novo.setVariacao("Único");
                novo.setPrecoCusto(produto.getPrecoCusto());
                novo.setPrecoVenda(produto.getPrecoVenda());
                // Estoque gerenciado pela entidade Estoque separada
                novo.setAtivo(Boolean.TRUE);
                novo.setPrincipal(Boolean.TRUE);
                // Gera SKU após vincular ao produto
                novo.gerarSKU();
                produto.adicionarSKU(novo);
            } else {
                // Mantém apenas o primeiro como principal e remove demais
                com.baronesa.emporio.entity.ProdutoSKU principal = produto.getSkus().get(0);
                principal.setPrincipal(Boolean.TRUE);
                // Atualiza preços com base no produto se não definidos
                if (produto.getPrecoCusto() != null) principal.setPrecoCusto(produto.getPrecoCusto());
                if (produto.getPrecoVenda() != null) principal.setPrecoVenda(produto.getPrecoVenda());
                if (principal.getSku() == null || principal.getSku().isBlank()) principal.gerarSKU();

                if (produto.getSkus().size() > 1) {
                    // Remove excedentes
                    java.util.List<com.baronesa.emporio.entity.ProdutoSKU> toRemove = new java.util.ArrayList<>();
                    for (int i = 1; i < produto.getSkus().size(); i++) {
                        toRemove.add(produto.getSkus().get(i));
                    }
                    toRemove.forEach(this::verificarSkuSemPedidos);
                    toRemove.forEach(produto::removerSKU);
                }
            }
            // Vincular embalagem principal ao SKU se existir; exigir para insumo
            Embalagem embPrincipal = obterEmbalagemPrincipal(produto);
            if (produto.getSkus() != null && !produto.getSkus().isEmpty()) {
                com.baronesa.emporio.entity.ProdutoSKU unico = produto.getSkus().get(0);
                if (Boolean.TRUE.equals(produto.getInsumo())) {
                    if (unico.getEmbalagem() == null) {
                        if (embPrincipal == null) {
                            throw new RuntimeException("Produtos do tipo insumo exigem embalagem definida");
                        }
                        unico.setEmbalagem(embPrincipal);
                    }
                } else if (embPrincipal != null && unico.getEmbalagem() == null) {
                    unico.setEmbalagem(embPrincipal);
                }
            }
            return;
        }

        // Para UNIFICADA/INDIVIDUAL: sincronizar com a lista vinda do request
        java.util.Map<Long, com.baronesa.emporio.entity.ProdutoSKU> existentes = new java.util.HashMap<>();
        if (produto.getSkus() != null) {
            for (com.baronesa.emporio.entity.ProdutoSKU s : produto.getSkus()) {
                if (s.getId() != null) existentes.put(s.getId(), s);
            }
        }

        java.util.Set<Long> idsEnviados = new java.util.HashSet<>();
        if (skusRequest != null) {
            for (ProdutoSKUDTO dto : skusRequest) {
                if (dto.getId() != null) idsEnviados.add(dto.getId());
            }
        }

        // Remover SKUs que existem no produto mas não vieram no request
        if (produto.getSkus() != null) {
            produto.getSkus().removeIf(s -> s.getId() != null && !idsEnviados.contains(s.getId()));
        }

        if (skusRequest == null) return;

        boolean isUnificada = tipoPrecificacao == com.baronesa.emporio.enums.TipoPrecificacao.UNIFICADA;

        for (ProdutoSKUDTO dto : skusRequest) {
            if (dto.getId() == null) {
                // Novo SKU
                com.baronesa.emporio.entity.ProdutoSKU s = new com.baronesa.emporio.entity.ProdutoSKU();
                s.setProduto(produto);
                s.setVariacao(dto.getVariacao());
                s.setSku(dto.getSku());
                s.setCodigoBarras(dto.getCodigoBarras());
                // Para UNIFICADA, usar preços do produto; para INDIVIDUAL, usar preços do SKU
                s.setPrecoCusto(isUnificada ? produto.getPrecoCusto() : dto.getPrecoCusto());
                s.setPrecoVenda(isUnificada ? produto.getPrecoVenda() : dto.getPrecoVenda());
                // Estoque gerenciado pela entidade Estoque separada
                s.setAtivo(dto.getAtivo() != null ? dto.getAtivo() : Boolean.TRUE);
                s.setPrincipal(dto.getPrincipal() != null ? dto.getPrincipal() : Boolean.FALSE);
                if (s.getSku() == null || s.getSku().isBlank()) {
                    s.gerarSKU();
                }
                produto.adicionarSKU(s);

                // Se produto NÃO é insumo, mapear estoque_minimo por SKU
                if (Boolean.FALSE.equals(produto.getInsumo())) {
                    if (s.getEstoque() == null) {
                        s.setEstoque(Estoque.builder().quantidade(0).estoqueMinimo(0).reservado(0).build());
                    }
                    if (dto.getEstoqueMinimo() != null) {
                        s.getEstoque().setEstoqueMinimo(dto.getEstoqueMinimo());
                    }
                }
            } else {
                // Atualizar existente
                com.baronesa.emporio.entity.ProdutoSKU s = existentes.get(dto.getId());
                if (s != null) {
                    s.setVariacao(dto.getVariacao());
                    if (dto.getSku() != null && !dto.getSku().isBlank()) s.setSku(dto.getSku());
                    s.setCodigoBarras(dto.getCodigoBarras());
                    // Para UNIFICADA, usar preços do produto; para INDIVIDUAL, usar preços do SKU
                    s.setPrecoCusto(isUnificada ? produto.getPrecoCusto() : dto.getPrecoCusto());
                    s.setPrecoVenda(isUnificada ? produto.getPrecoVenda() : dto.getPrecoVenda());
                    // REMOVED - Estoque gerenciado pela entidade Estoque separada
                    // s.setEstoqueAtual(dto.getEstoqueAtual());
                    if (dto.getAtivo() != null) s.setAtivo(dto.getAtivo());
                    if (dto.getPrincipal() != null) s.setPrincipal(dto.getPrincipal());
                    if (s.getSku() == null || s.getSku().isBlank()) s.gerarSKU();

                    // Se produto NÃO é insumo, mapear estoque_minimo por SKU
                    if (Boolean.FALSE.equals(produto.getInsumo())) {
                        if (s.getEstoque() == null) {
                            s.setEstoque(Estoque.builder().quantidade(0).estoqueMinimo(0).reservado(0).build());
                        }
                        if (dto.getEstoqueMinimo() != null) {
                            s.getEstoque().setEstoqueMinimo(dto.getEstoqueMinimo());
                        }
                    }
                }
            }
        }

        // Garantir que haja no máximo um principal
        boolean marcouPrincipal = false;
        if (produto.getSkus() != null) {
            for (com.baronesa.emporio.entity.ProdutoSKU s : produto.getSkus()) {
                if (!marcouPrincipal && Boolean.TRUE.equals(s.getPrincipal())) {
                    marcouPrincipal = true;
                } else if (Boolean.TRUE.equals(s.getPrincipal())) {
                    s.setPrincipal(false);
                }
            }
            if (!marcouPrincipal && !produto.getSkus().isEmpty()) {
                produto.getSkus().get(0).setPrincipal(true);
            }
        }

        // Vincular embalagem para SKUs sem embalagem
        Embalagem embPrincipal = obterEmbalagemPrincipal(produto);
        if (produto.getSkus() != null) {
            for (com.baronesa.emporio.entity.ProdutoSKU s : produto.getSkus()) {
                if (s.getEmbalagem() == null) {
                    if (Boolean.TRUE.equals(produto.getInsumo())) {
                        if (embPrincipal == null) {
                            throw new RuntimeException("Produtos do tipo insumo exigem embalagem definida em cada SKU");
                        }
                        s.setEmbalagem(embPrincipal);
                    } else if (embPrincipal != null) {
                        s.setEmbalagem(embPrincipal);
                    }
                }
            }
        }
    }

    private void marcarTraducoesProduto(Produto produto) {
        translationService.markSourceChanged("PRODUCT", produto.getId(), "nome", produto.getNome());
        translationService.markSourceChanged("PRODUCT", produto.getId(), "descricao", produto.getDescricao());
        if (produto.getSkus() != null) {
            for (ProdutoSKU sku : produto.getSkus()) {
                if (sku.getId() != null) {
                    translationService.markSourceChanged("SKU", sku.getId(), "variacao", sku.getVariacao());
                }
            }
        }
    }

    private Embalagem obterEmbalagemPrincipal(Produto produto) {
        if (produto.getEmbalagens() == null) return null;
        for (Embalagem e : produto.getEmbalagens()) {
            if (Boolean.TRUE.equals(e.getPrincipal()) && Boolean.TRUE.equals(e.getAtivo())) {
                return e;
            }
        }
        return null;
    }

    private void verificarSkuSemPedidos(ProdutoSKU sku) {
        if (sku.getId() != null && itemPedidoRepository.existsBySkuId(sku.getId())) {
            throw new BusinessException("Não é possível retornar ao produto único porque existem variações já utilizadas em pedidos.");
        }
    }

    private ProdutoDTO converterParaDTO(Produto produto) {
        // Calcular precoMinimo e precoMaximo baseado nos SKUs
        java.math.BigDecimal precoMinimo = null;
        java.math.BigDecimal precoMaximo = null;

        if (produto.getSkus() != null && !produto.getSkus().isEmpty()) {
            java.util.List<java.math.BigDecimal> precos = produto.getSkus().stream()
                    .filter(sku -> sku.getPrecoVenda() != null)
                    .map(ProdutoSKU::getPrecoVenda)
                    .collect(Collectors.toList());

            if (!precos.isEmpty()) {
                precoMinimo = precos.stream().min(java.math.BigDecimal::compareTo).orElse(null);
                precoMaximo = precos.stream().max(java.math.BigDecimal::compareTo).orElse(null);
            }
        }

        Integer estoqueMinimoSkuUnico = null;
        if (Boolean.FALSE.equals(produto.getInsumo())
                && produto.getTipoPrecificacao() == com.baronesa.emporio.enums.TipoPrecificacao.SIMPLES
                && produto.getSkus() != null && !produto.getSkus().isEmpty()) {
            Estoque e = produto.getSkus().get(0).getEstoque();
            estoqueMinimoSkuUnico = (e != null ? e.getEstoqueMinimo() : null);
        }

        ProdutoSignageDTO signageDTO = carregarSignageDTO(produto.getId());

        return ProdutoDTO.builder()
                .id(produto.getId())
                .nome(produto.getNome())
                .setor(produto.getSetor())
                .descricao(produto.getDescricao())
                .codigoInterno(produto.getCodigoInterno())
                .codigoBarras(produto.getCodigoBarras())
                .tipo(produto.getTipo())
                .unidadeMedida(produto.getUnidadeMedida())
                .unidadeBase(produto.getUnidadeBase())
                .categoriaId(produto.getCategoria() != null ? produto.getCategoria().getId() : null)
                .categoriaNome(produto.getCategoria() != null ? produto.getCategoria().getNome() : null)
                .subcategoriaId(produto.getSubcategoria() != null ? produto.getSubcategoria().getId() : null)
                .subcategoriaNome(produto.getSubcategoria() != null ? produto.getSubcategoria().getNome() : null)
                .fornecedorId(produto.getFornecedor() != null ? produto.getFornecedor().getId() : null)
                .fornecedorNome(produto.getFornecedor() != null ? produto.getFornecedor().getNomeFantasia() : null)
                .precoCusto(produto.getPrecoCusto())
                .precoVenda(produto.getPrecoVenda())
                .precoMinimo(precoMinimo)
                .precoMaximo(precoMaximo)
                .tipoPrecificacao(produto.getTipoPrecificacao())
                .controlaEstoque(produto.getControlaEstoque())
                .controlaValidade(produto.getControlaValidade())
                .vidaUtilDias(produto.getVidaUtilDias())
                .estoqueAtual(produto.getEstoqueAtual())
                .ativo(produto.getAtivo())
                .vendavel(produto.getVendavel())
                .insumo(produto.getInsumo())
                .exibirNoCardapio(produto.getExibirNoCardapio())
                .promocao(produto.getPromocao())
                .destaque(produto.getDestaque())
                .necessitaPreparacao(produto.getNecessitaPreparacao())
                .producaoPropria(produto.getProducaoPropria())
                .localPreparacao(produto.getLocalPreparacao())
                .ordem(produto.getOrdem())
                .imagemPrincipal(produto.getImagemPrincipal())
                .skus(produto.getSkus() != null ? produto.getSkus().stream()
                        .map(this::converterSKUParaDTO)
                        .collect(Collectors.toList()) : null)
                .estoqueMinimoBase(produto.getEstoqueProduto() != null ? produto.getEstoqueProduto().getEstoqueMinimoBase() : null)
                .midias(produto.getMidias() != null ? produto.getMidias().stream()
                        .map(this::converterMidiaParaDTO)
                        .collect(Collectors.toList()) : null)
                .criadoEm(produto.getCriadoEm())
                .atualizadoEm(produto.getAtualizadoEm())
                .estoqueMinimoSkuUnico(estoqueMinimoSkuUnico)
                .produtoFiscal(converterProdutoFiscalParaDTO(produto.getInformacoesFiscais()))
                // Preencher campos planos fiscais
                .ncm(produto.getInformacoesFiscais() != null ? produto.getInformacoesFiscais().getNcm() : null)
                .cest(produto.getInformacoesFiscais() != null ? produto.getInformacoesFiscais().getCest() : null)
                .origem(produto.getInformacoesFiscais() != null ? produto.getInformacoesFiscais().getOrigem() : null)
                .csosn(produto.getInformacoesFiscais() != null ? produto.getInformacoesFiscais().getCsosn() : null)
                .cfop(produto.getInformacoesFiscais() != null ? produto.getInformacoesFiscais().getCfop() : null)
                .signageEnabled(signageDTO != null ? signageDTO.getEnabled() : null)
                .signage(signageDTO)
                .build();
    }

    private ProdutoSignageDTO carregarSignageDTO(Long produtoId) {
        return productSignageRepository.findByProdutoId(produtoId)
                .map(this::converterSignageParaDTO)
                .orElse(null);
    }

    private ProdutoSignageDTO converterSignageParaDTO(ProductSignage signage) {
        if (signage == null) {
            return null;
        }

        return ProdutoSignageDTO.builder()
                .id(signage.getId())
                .enabled(signage.getEnabled())
                .templatePreference(signage.getTemplatePreference())
                .status(signage.getStatus())
                .renderHash(signage.getRenderHash())
                .mp4Url(signage.getMp4Url())
                .palette(parsePalette(signage.getPalette()))
                .phrases(signage.getPhrases())
                .templateApplied(signage.getTemplateApplied())
                .metadataSource(signage.getMetadataSource())
                .colorMapping(parseColorMapping(signage.getColorMapping()))
                .lastAttemptAt(signage.getLastAttemptAt())
                .lastResultAt(signage.getLastResultAt())
                .createdAt(signage.getCreatedAt())
                .updatedAt(signage.getUpdatedAt())
                .build();
    }

    public ProdutoSignagePreviewDTO carregarSignagePreview(Long produtoId) {
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + produtoId));

        ProductSignage signage = productSignageRepository.findByProdutoId(produtoId)
                .orElseThrow(() -> new EntityNotFoundException("Signage não encontrado para o produto: " + produtoId));

        Map<String, String> phrases = parsePhrases(signage.getPhrases());
        ProductSignagePalette palette = parsePalette(signage.getPalette());

        ProdutoSignagePreviewDTO.ProductInfo productInfo = ProdutoSignagePreviewDTO.ProductInfo.builder()
                .id(produto.getId())
                .nome(produto.getNome())
                .descricao(produto.getDescricao())
                .precoVenda(produto.getPrecoVenda())
                .promocao(produto.getPromocao())
                .destaque(produto.getDestaque())
                .badgeText(deriveBadgeText(produto, phrases))
                .promoText(Boolean.TRUE.equals(produto.getPromocao()) ? "Promoção" : null)
                .build();

        boolean wantsAiImage = Boolean.TRUE.equals(signage.getUseAiImage());
        boolean aiImageAvailable = false;
        String aiImageUrl = null;
        String aiHash = signage.getAiImageHash();

        if (aiHash != null && !aiHash.isBlank()) {
            aiImageAvailable = aiImageStorageService.exists(aiHash);
            if (aiImageAvailable) {
                aiImageUrl = aiImageStorageService.resolveUrl(aiHash);
            }
        }

        String fallbackImage = signage.getGeneratedImagePath();
        String imageUrl = (wantsAiImage && aiImageAvailable) ? aiImageUrl
                : (fallbackImage != null && !fallbackImage.isBlank()
                        ? fallbackImage
                        : produto.getImagemPrincipal());

        SignageColorMapping colorMapping = parseColorMapping(signage.getColorMapping());
        Map<String, String> resolvedColors = resolveColors(colorMapping, palette, signage.getTemplatePreference());

        return ProdutoSignagePreviewDTO.builder()
                .templatePreference(signage.getTemplatePreference())
                .palette(palette)
                .phrases(phrases)
                .product(productInfo)
                .imageUrl(imageUrl)
                .mp4Url(signage.getMp4Url())
                .aiImageUrl(aiImageUrl)
                .aiImageHash(aiHash)
                .aiRevision(signage.getAiRevision())
                .isUsingAiImage(wantsAiImage)
                .aiImageAvailable(aiImageAvailable)
                .colorMapping(colorMapping)
                .resolvedColors(resolvedColors)
                .build();
    }

    private ProdutoSKUDTO converterSKUParaDTO(ProdutoSKU sku) {
        // Estoque exibido do SKU: se produto for insumo, derivar do estoque central pela embalagem
        java.math.BigDecimal estoqueExibicao;
        if (sku.getProduto() != null && Boolean.TRUE.equals(sku.getProduto().getInsumo())) {
            Integer base = (sku.getProduto().getEstoqueProduto() != null) ? sku.getProduto().getEstoqueProduto().getQuantidadeBase() : 0;
            int fator = 1;
            if (sku.getEmbalagem() != null && sku.getEmbalagem().getFatorBase() != null && sku.getEmbalagem().getFatorBase() > 0) {
                fator = sku.getEmbalagem().getFatorBase();
            } else {
                // Fallback: usar embalagem principal do produto se existir
                Embalagem emb = obterEmbalagemPrincipal(sku.getProduto());
                if (emb != null && emb.getFatorBase() != null && emb.getFatorBase() > 0) {
                    fator = emb.getFatorBase();
                }
            }
            int qtdSku = (fator > 0) ? (base / fator) : 0;
            estoqueExibicao = java.math.BigDecimal.valueOf(qtdSku);
        } else {
            estoqueExibicao = (sku.getEstoque() != null && sku.getEstoque().getQuantidade() != null)
                    ? java.math.BigDecimal.valueOf(sku.getEstoque().getQuantidade())
                    : java.math.BigDecimal.ZERO;
        }

        // estoqueMinimo só para vendáveis (insumo=false)
        Integer estoqueMinimo = null;
        if (sku.getProduto() != null && Boolean.FALSE.equals(sku.getProduto().getInsumo())) {
            estoqueMinimo = (sku.getEstoque() != null) ? sku.getEstoque().getEstoqueMinimo() : null;
        }

        return ProdutoSKUDTO.builder()
                .id(sku.getId())
                .produtoId(sku.getProduto() != null ? sku.getProduto().getId() : null)
                .embalagemId(sku.getEmbalagem() != null ? sku.getEmbalagem().getId() : null)
                .sku(sku.getSku())
                .variacao(sku.getVariacao())
                .codigoBarras(sku.getCodigoBarras())
                .precoCusto(sku.getPrecoCusto())
                .precoVenda(sku.getPrecoVenda())
                .estoqueAtual(estoqueExibicao)
                .estoqueMinimo(estoqueMinimo)
                .ativo(sku.getAtivo())
                .principal(sku.getPrincipal())
                .criadoEm(sku.getCriadoEm())
                .atualizadoEm(sku.getAtualizadoEm())
                .build();
    }

    // Métodos de mídia
    public String uploadImagemPrincipal(Long produtoId, MultipartFile arquivo) {
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));

        if (arquivo.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio");
        }

        try {
            String originalName = arquivo.getOriginalFilename();
            String extension = originalName != null && originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf('.') + 1)
                    : "bin";

            String filename = UUID.randomUUID() + "." + extension;

            Path produtoDir = Paths.get(uploadProdutoDir, String.valueOf(produtoId));
            Files.createDirectories(produtoDir);
            Path filePath = produtoDir.resolve(filename);
            Files.copy(arquivo.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Definir permissões 644 (rw-r--r--)
            try {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-r--r--");
                Files.setPosixFilePermissions(filePath, perms);
            } catch (UnsupportedOperationException ignored) {}

            String url = "/media/produtos/" + produtoId + "/" + filename;
            produto.setImagemPrincipal(url);
            produtoRepository.save(produto);

            return url;
        } catch (IOException e) {
            throw new RuntimeException("Erro ao fazer upload da imagem: " + e.getMessage());
        }
    }

    public ProdutoMidiaDTO uploadImagemGaleria(Long produtoId, MultipartFile arquivo) {
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));

        if (arquivo.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio");
        }

        if (!arquivo.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("O arquivo deve ser uma imagem");
        }

        try {
            // Gerar nome único
            String originalName = arquivo.getOriginalFilename();
            String extension = originalName.substring(originalName.lastIndexOf('.'));
            String filename = produtoId + "_galeria_" + UUID.randomUUID() + extension;

            // Salvar arquivo
            Path produtoDir = Paths.get(uploadProdutoDir, String.valueOf(produtoId));
            Files.createDirectories(produtoDir);
            Path filePath = produtoDir.resolve(filename);
            Files.copy(arquivo.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Definir permissões 644 (rw-r--r--)
            try {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-r--r--");
                Files.setPosixFilePermissions(filePath, perms);
            } catch (UnsupportedOperationException ignored) {}

            // Determinar próxima ordem
            int proximaOrdem = produto.getMidias().size();

            // Criar registro no banco
            ProdutoMidia midia = ProdutoMidia.builder()
                    .produto(produto)
                    .tipo(TipoMidia.IMAGEM)
                    .url("/media/produtos/" + produtoId + "/" + filename)
                    .ordem(proximaOrdem)
                    .principal(false)
                    .ativo(true)
                    .build();

            produto.adicionarMidia(midia);
            produtoRepository.save(produto);

            return converterMidiaParaDTO(midia);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao fazer upload da imagem: " + e.getMessage());
        }
    }

    public void deletarImagemGaleria(Long produtoId, Long midiaId) {
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));

        ProdutoMidia midia = produto.getMidias().stream()
                .filter(m -> m.getId().equals(midiaId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Imagem não encontrada"));

        // Deletar arquivo físico
        try {
            String url = midia.getUrl();
            String filename = url.substring(url.lastIndexOf('/') + 1);
            Path filePath = Paths.get(uploadProdutoDir, String.valueOf(produtoId), filename);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // Log error but continue with database deletion
        }

        // Remover do banco
        produto.removerMidia(midia);
        produtoRepository.save(produto);
    }

    /**
     * Upload e processamento de vídeo do produto
     * - Valida tamanho e duração
     * - Comprime usando ffmpeg
     * - Salva no diretório do produto
     * - Cria registro na tabela produto_midia com tipo VIDEO
     */
    public ProdutoMidiaDTO uploadVideo(Long produtoId, MultipartFile arquivo) {
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));

        if (arquivo.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio");
        }

        // Validar tipo de arquivo
        String contentType = arquivo.getContentType();
        String originalFilename = arquivo.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase()
                : "";

        boolean looksLikeVideo = contentType != null && contentType.startsWith("video/");
        boolean isOctetMp4 = (contentType == null || "application/octet-stream".equalsIgnoreCase(contentType))
                && ".mp4".equals(extension);

        if (!looksLikeVideo && !isOctetMp4) {
            throw new IllegalArgumentException("Apenas arquivos de vídeo (MP4) são permitidos");
        }

        // Validar tamanho máximo (100MB)
        if (arquivo.getSize() > 100 * 1024 * 1024) {
            throw new IllegalArgumentException("Vídeo muito grande. Máximo: 100MB");
        }

        try {
            // 1. Salvar arquivo temporário
            Path tempVideoPath = Files.createTempFile("temp_video_", extension);
            Files.copy(arquivo.getInputStream(), tempVideoPath, StandardCopyOption.REPLACE_EXISTING);

            try {
                // 2. Validar vídeo (duração máx 90s)
                videoProcessingService.validateVideo(tempVideoPath);

                // 3. Criar path para vídeo comprimido
                Path compressedTempPath = Files.createTempFile("compressed_video_", ".mp4");

                // 4. Comprimir vídeo
                videoProcessingService.compressVideo(tempVideoPath, compressedTempPath);

                // 5. Gerar nome único e mover para diretório final
                String filename = produtoId + "_video_" + UUID.randomUUID() + ".mp4";
                Path produtoDir = Paths.get(uploadProdutoDir, String.valueOf(produtoId));
                Files.createDirectories(produtoDir);
                Path finalPath = produtoDir.resolve(filename);
                Files.move(compressedTempPath, finalPath, StandardCopyOption.REPLACE_EXISTING);

                // Definir permissões 644 (rw-r--r--) para que o nginx possa servir o arquivo
                try {
                    Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-r--r--");
                    Files.setPosixFilePermissions(finalPath, perms);
                } catch (UnsupportedOperationException e) {
                    // Sistema não suporta POSIX (ex: Windows), ignorar
                }

                // 6. Gerar thumbnail (opcional, mas recomendado)
                try {
                    String thumbFilename = produtoId + "_video_thumb_" + UUID.randomUUID() + ".jpg";
                    Path thumbPath = produtoDir.resolve(thumbFilename);
                    videoProcessingService.generateThumbnail(finalPath, thumbPath, 1.0);

                    // Definir permissões 644 para o thumbnail também
                    try {
                        Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-r--r--");
                        Files.setPosixFilePermissions(thumbPath, perms);
                    } catch (UnsupportedOperationException ignored) {
                        // Sistema não suporta POSIX
                    }
                } catch (Exception e) {
                    // Log mas não falha se thumbnail falhar
                    System.err.println("Aviso: Não foi possível gerar thumbnail do vídeo: " + e.getMessage());
                }

                // 7. Criar registro no banco
                int proximaOrdem = produto.getMidias().size();
                ProdutoMidia midia = ProdutoMidia.builder()
                        .produto(produto)
                        .tipo(TipoMidia.VIDEO)
                        .url("/media/produtos/" + produtoId + "/" + filename)
                        .ordem(proximaOrdem)
                        .principal(false)
                        .ativo(true)
                        .build();

                produto.adicionarMidia(midia);
                produtoRepository.save(produto);

                return converterMidiaParaDTO(midia);

            } finally {
                // Limpar arquivo temporário
                try {
                    Files.deleteIfExists(tempVideoPath);
                } catch (IOException e) {
                    // Log but don't fail
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Erro ao processar vídeo: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            // Re-throw validation errors
            throw e;
        }
    }

    private ProdutoMidiaDTO converterMidiaParaDTO(ProdutoMidia midia) {
        return ProdutoMidiaDTO.builder()
                .id(midia.getId())
                .produtoId(midia.getProduto() != null ? midia.getProduto().getId() : null)
                .tipo(midia.getTipo())
                .url(midia.getUrl())
                .titulo(midia.getTitulo())
                .descricao(midia.getDescricao())
                .ordem(midia.getOrdem())
                .principal(midia.getPrincipal())
                .ativo(midia.getAtivo())
                .criadoEm(midia.getCriadoEm())
                .build();
    }

    // ===== Métodos para Lookup =====

    public List<Map<String, Object>> buscarParaLookup(String search) {
        List<Produto> produtos;

        if (search == null || search.trim().isEmpty()) {
            // Se não há busca, retornar os primeiros 50 produtos ativos
            produtos = produtoRepository.findAll(
                    (root, query, cb) -> cb.equal(root.get("ativo"), true),
                    PageRequest.of(0, 50)
            ).getContent();
        } else {
            // Busca complexa incluindo código de barras nos SKUs
            String searchTerm = search.trim();

            Specification<Produto> spec = (root, query, criteriaBuilder) -> {
                // Join com SKUs para buscar código de barras
                Join<Produto, ProdutoSKU> skuJoin = root.join("skus", JoinType.LEFT);

                // Predicados de busca
                Predicate codigoInterno = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("codigoInterno")),
                        "%" + searchTerm.toLowerCase() + "%"
                );

                Predicate nome = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("nome")),
                        "%" + searchTerm.toLowerCase() + "%"
                );

                Predicate codigoBarras = criteriaBuilder.like(
                        criteriaBuilder.lower(skuJoin.get("codigoBarras")),
                        "%" + searchTerm.toLowerCase() + "%"
                );

                // Produto deve estar ativo
                Predicate ativo = criteriaBuilder.equal(root.get("ativo"), true);

                // Combinar predicados
                Predicate searchPredicate = criteriaBuilder.or(codigoInterno, nome, codigoBarras);

                // Evitar duplicados devido ao join
                query.distinct(true);

                return criteriaBuilder.and(ativo, searchPredicate);
            };

            produtos = produtoRepository.findAll(spec, PageRequest.of(0, 50)).getContent();
        }

        // Converter para DTOs com informações consolidadas
        return produtos.stream()
                .map(this::toProdutoLookupDTO)
                .map(ProdutoLookupDTO::toMap)
                .collect(Collectors.toList());
    }

    /**
     * Converte Produto para ProdutoLookupDTO com informações consolidadas
     */
    private ProdutoLookupDTO toProdutoLookupDTO(Produto produto) {
        // Calcular estoque total de todos os SKUs
        Integer estoqueTotal = produto.getSkus().stream()
                .filter(sku -> sku.getEstoque() != null)
                .mapToInt(sku -> sku.getEstoque().getQuantidade())
                .sum();

        // Obter preço (usar precoVenda do produto ou do primeiro SKU ativo)
        Double preco = null;
        if (produto.getPrecoVenda() != null) {
            preco = produto.getPrecoVenda().doubleValue();
        } else {
            preco = produto.getSkus().stream()
                    .filter(sku -> Boolean.TRUE.equals(sku.getAtivo()))
                    .findFirst()
                    .map(sku -> sku.getPrecoVenda() != null ? sku.getPrecoVenda().doubleValue() : null)
                    .orElse(null);
        }

        // Concatenar códigos de barras para referência
        String codigosBarras = produto.getSkus().stream()
                .map(ProdutoSKU::getCodigoBarras)
                .filter(cb -> cb != null && !cb.trim().isEmpty())
                .collect(Collectors.joining(", "));

        return ProdutoLookupDTO.builder()
                .id(produto.getId())
                .codigoInterno(produto.getCodigoInterno())
                .nome(produto.getNome())
                .descricao(produto.getDescricao())
                .estoqueTotal(estoqueTotal)
                .preco(preco)
                .temVariacoes(produto.getSkus().size() > 1)
                .qtdSkus(produto.getSkus().size())
                .codigosBarras(codigosBarras.isEmpty() ? null : codigosBarras)
                .insumo(Boolean.TRUE.equals(produto.getInsumo()))
                .build();
    }

    /**
     * Busca produto por ID para lookup
     */
    public Map<String, Object> buscarPorIdParaLookup(Long id) {
        return produtoRepository.findById(id)
                .filter(p -> Boolean.TRUE.equals(p.getAtivo()))
                .map(this::toProdutoLookupDTO)
                .map(ProdutoLookupDTO::toMap)
                .orElse(null);
    }

    public List<Map<String, Object>> buscarOptions() {
        return produtoRepository.findAll().stream()
                .map(p -> Map.of(
                        "value", (Object) p.getId(),
                        "label", p.getCodigoInterno() + " - " + p.getNome()
                ))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> buscarProdutosPorFornecedorCnpj(String cnpj) {
        // Primeiro buscar o fornecedor pelo CNPJ
        Optional<Fornecedor> fornecedorOpt = fornecedorRepository.findByCnpj(cnpj);

        if (fornecedorOpt.isEmpty()) {
            return List.of();
        }

        // Buscar produtos do fornecedor
        List<Produto> produtos = produtoRepository.findByFornecedorId(fornecedorOpt.get().getId());

        // Converter para o formato de lookup
        return produtos.stream()
                .filter(Produto::getAtivo)
                .map(produto -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", produto.getId());
                    map.put("codigoFornecedor", produto.getCodigoFornecedor()); // Código do fornecedor
                    map.put("descricao", produto.getNome());
                    map.put("preco", produto.getPrecoVenda() != null ? produto.getPrecoVenda().doubleValue() : null);
                    return map;
                })
                .collect(Collectors.toList());
    }

    private void mapearProdutoFiscal(Produto produto, ProdutoFiscalDTO dto) {
        if (dto == null) {
            return;
        }

        ProdutoFiscal fiscal = produto.getInformacoesFiscais();
        if (fiscal == null) {
            fiscal = new ProdutoFiscal();
            fiscal.setProduto(produto);
            produto.setInformacoesFiscais(fiscal);
        }

        fiscal.setNcm(dto.getNcm());
        fiscal.setCest(dto.getCest());
        fiscal.setOrigem(dto.getOrigem());
        fiscal.setCfop(dto.getCfop());
        fiscal.setUnidadeTributavel(dto.getUnidadeTributavel());
        fiscal.setQuantidadeTributavel(dto.getQuantidadeTributavel());
        fiscal.setValorUnitarioTributavel(dto.getValorUnitarioTributavel());
        
        fiscal.setCstIcms(dto.getCstIcms());
        fiscal.setCsosn(dto.getCsosn());
        fiscal.setAliquotaIcms(dto.getAliquotaIcms());
        fiscal.setAliquotaIcmsST(dto.getAliquotaIcmsST());
        fiscal.setMva(dto.getMva());
        fiscal.setMvast(dto.getMvast());
        fiscal.setReducaoBaseIcms(dto.getReducaoBaseIcms());
        fiscal.setReducaoBaseIcmsST(dto.getReducaoBaseIcmsST());
        fiscal.setModalidadeBcIcms(dto.getModalidadeBcIcms());
        fiscal.setModalidadeBcIcmsST(dto.getModalidadeBcIcmsST());
        
        fiscal.setCstPis(dto.getCstPis());
        fiscal.setAliquotaPis(dto.getAliquotaPis());
        fiscal.setAliquotaPisReais(dto.getAliquotaPisReais());
        
        fiscal.setCstCofins(dto.getCstCofins());
        fiscal.setAliquotaCofins(dto.getAliquotaCofins());
        fiscal.setAliquotaCofinsReais(dto.getAliquotaCofinsReais());
        
        fiscal.setCstIpi(dto.getCstIpi());
        fiscal.setAliquotaIpi(dto.getAliquotaIpi());
        fiscal.setCodigoEnquadramentoIpi(dto.getCodigoEnquadramentoIpi());
        fiscal.setTipoCalculoIpi(dto.getTipoCalculoIpi());
        
        fiscal.setAliquotaFcp(dto.getAliquotaFcp());
        fiscal.setAliquotaFcpST(dto.getAliquotaFcpST());
        
        fiscal.setInformacoesAdicionaisFisco(dto.getInformacoesAdicionaisFisco());
        fiscal.setCodigoBeneficioFiscal(dto.getCodigoBeneficioFiscal());
        fiscal.setSujeitoST(dto.getSujeitoST() != null ? dto.getSujeitoST() : false);
        fiscal.setPossuiBeneficio(dto.getPossuiBeneficio() != null ? dto.getPossuiBeneficio() : false);
    }

    private ProdutoFiscalDTO converterProdutoFiscalParaDTO(ProdutoFiscal fiscal) {
        if (fiscal == null) {
            return null;
        }

        return ProdutoFiscalDTO.builder()
                .ncm(fiscal.getNcm())
                .cest(fiscal.getCest())
                .origem(fiscal.getOrigem())
                .unidadeTributavel(fiscal.getUnidadeTributavel())
                .cfop(fiscal.getCfop())
                .quantidadeTributavel(fiscal.getQuantidadeTributavel())
                .valorUnitarioTributavel(fiscal.getValorUnitarioTributavel())
                .cstIcms(fiscal.getCstIcms())
                .csosn(fiscal.getCsosn())
                .aliquotaIcms(fiscal.getAliquotaIcms())
                .aliquotaIcmsST(fiscal.getAliquotaIcmsST())
                .mva(fiscal.getMva())
                .mvast(fiscal.getMvast())
                .reducaoBaseIcms(fiscal.getReducaoBaseIcms())
                .reducaoBaseIcmsST(fiscal.getReducaoBaseIcmsST())
                .modalidadeBcIcms(fiscal.getModalidadeBcIcms())
                .modalidadeBcIcmsST(fiscal.getModalidadeBcIcmsST())
                .cstPis(fiscal.getCstPis())
                .aliquotaPis(fiscal.getAliquotaPis())
                .aliquotaPisReais(fiscal.getAliquotaPisReais())
                .cstCofins(fiscal.getCstCofins())
                .aliquotaCofins(fiscal.getAliquotaCofins())
                .aliquotaCofinsReais(fiscal.getAliquotaCofinsReais())
                .cstIpi(fiscal.getCstIpi())
                .aliquotaIpi(fiscal.getAliquotaIpi())
                .codigoEnquadramentoIpi(fiscal.getCodigoEnquadramentoIpi())
                .tipoCalculoIpi(fiscal.getTipoCalculoIpi())
                .aliquotaFcp(fiscal.getAliquotaFcp())
                .aliquotaFcpST(fiscal.getAliquotaFcpST())
                .informacoesAdicionaisFisco(fiscal.getInformacoesAdicionaisFisco())
                .codigoBeneficioFiscal(fiscal.getCodigoBeneficioFiscal())
                .sujeitoST(fiscal.getSujeitoST())
                .possuiBeneficio(fiscal.getPossuiBeneficio())
                .build();
    }

    private void persistirSignage(Produto produto, ProdutoRequest request) {
        ProdutoSignageRequest signageRequest = obterSignageRequest(request);
        if (signageRequest == null) {
            return;
        }

        if (Boolean.TRUE.equals(signageRequest.getEnabled())) {
            validarCamposObrigatoriosParaSignage(produto);
        }

        ProductSignage signage = productSignageRepository.findByProdutoId(produto.getId())
                .orElse(ProductSignage.builder().produto(produto).build());

        if (signageRequest.getEnabled() != null) {
            signage.setEnabled(signageRequest.getEnabled());
        }

        if (signageRequest.getTemplatePreference() != null) {
            signage.setTemplatePreference(signageRequest.getTemplatePreference());
        }
        if (signageRequest.getUseAiImage() != null) {
            signage.setUseAiImage(signageRequest.getUseAiImage());
        }

        if (signage.getStatus() == null) {
            signage.setStatus(ProductSignageStatus.PENDING);
        }

        signage.setProduto(produto);
        productSignageRepository.save(signage);
    }

    /**
     * Verifica se o produto tem signage habilitado com vídeo renderizado e dispara
     * sincronização automática com signage-api.
     * Chamado após atualização do produto para sincronização reativa.
     */
    private void dispararSyncSignageSeNecessario(Produto produto) {
        productSignageRepository.findByProdutoId(produto.getId()).ifPresent(signage -> {
            if (Boolean.TRUE.equals(signage.getEnabled()) && signage.getMp4Url() != null) {
                log.info("Produto {} atualizado com signage habilitado e vídeo existente. Disparando sincronização com signage-api...",
                        produto.getId());
                eventPublisher.publishEvent(new VideoGeneratedEvent(this, signage));
            }
        });
    }

    public ProdutoSignageDTO atualizarSignage(Long produtoId, ProdutoSignageRequest request) {
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + produtoId));

        ProductSignage signage = productSignageRepository.findByProdutoId(produtoId)
                .orElse(ProductSignage.builder().produto(produto).build());

        if (Boolean.TRUE.equals(request.getUseAiImage())) {
            if (signage.getAiImageHash() == null || signage.getAiImageHash().isBlank()) {
                throw new IllegalArgumentException(
                        "Não é possível ativar imagem IA. Gere uma imagem primeiro."
                );
            }

            if (!aiImageStorageService.exists(signage.getAiImageHash())) {
                throw new IllegalArgumentException(
                        "Imagem IA não encontrada no storage. Gere novamente."
                );
            }
        }

        if (Boolean.TRUE.equals(request.getEnabled())) {
            validarCamposObrigatoriosParaSignage(produto);
        }

        validarPrecoParaTemplate(produto, request.getTemplatePreference());

        if (request.getEnabled() != null) {
            signage.setEnabled(request.getEnabled());
        }
        if (request.getTemplatePreference() != null) {
            signage.setTemplatePreference(request.getTemplatePreference());
        }
        if (request.getPhrases() != null) {
            signage.setPhrases(serializeToJson(request.getPhrases()));
        }
        if (request.getPalette() != null) {
            validarPalette(request.getPalette());
            signage.setPalette(serializeToJson(request.getPalette()));
        }
        if (request.getMetadataSource() != null) {
            var normalized = normalizeMetadataSource(request.getMetadataSource());
            validarMetadataSource(normalized);
            signage.setMetadataSource(normalized);
        }
        if (request.getUseAiImage() != null) {
            signage.setUseAiImage(request.getUseAiImage());
        }
        if (request.getColorMapping() != null) {
            // Atualiza o templateId no colorMapping para garantir consistência
            SignageColorMapping mapping = request.getColorMapping();
            if (mapping.getTemplateId() == null && request.getTemplatePreference() != null) {
                mapping.setTemplateId(request.getTemplatePreference());
            }
            signage.setColorMapping(serializeToJson(mapping));
        }

        if (signage.getStatus() == null) {
            signage.setStatus(ProductSignageStatus.PENDING);
        }

        signage.setProduto(produto);
        productSignageRepository.save(signage);
        
        // Dispara sincronização se signage habilitado e já possui vídeo
        if (Boolean.TRUE.equals(signage.getEnabled()) && signage.getMp4Url() != null) {
            log.info("Signage do produto {} atualizado. Disparando sincronização com signage-api...", produtoId);
            eventPublisher.publishEvent(new VideoGeneratedEvent(this, signage));
        }
        
        return converterSignageParaDTO(signage);
    }

    public SignageRenderResponseDTO renderSignageVideo(Long produtoId, ProdutoSignageRenderRequest request) {
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + produtoId));

        if (request == null || request.getHtml() == null || request.getHtml().isBlank()) {
            throw new IllegalArgumentException("HTML do template é obrigatório para renderização.");
        }

        String templateId = request.getTemplateId();
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("templateId é obrigatório para renderização.");
        }

        ProductSignage signage = productSignageRepository.findByProdutoId(produtoId)
                .orElse(ProductSignage.builder().produto(produto).build());

        signage.setTemplateApplied(templateId);
        signage.setTemplatePreference(templateId);
        signage.setLastAttemptAt(LocalDateTime.now());
        signage.setStatus(ProductSignageStatus.PENDING);
        productSignageRepository.save(signage);

        try {
            SignageRenderRequestDTO renderRequest = SignageRenderRequestDTO.builder()
                    .html(request.getHtml())
                    .width(request.getWidth())
                    .height(request.getHeight())
                    .fps(request.getFps())
                    .durationMs(request.getDurationMs())
                    .renderHash(null)
                    .build();

            SignageRenderResponseDTO renderResponse = signageRenderService.renderHtml(renderRequest);
            String storedUrl = signageVideoStorageService.storeFromUrl(
                    renderResponse.getUrl(),
                    produtoId,
                    renderResponse.getRenderHash()
            );

            signage.setMp4Url(storedUrl);
            signage.setRenderHash(renderResponse.getRenderHash());
            signage.setStatus(ProductSignageStatus.RENDERED);
            signage.setLastResultAt(LocalDateTime.now());
            productSignageRepository.save(signage);

            // Dispara evento para sincronização automática com signage-api
            eventPublisher.publishEvent(new VideoGeneratedEvent(this, signage));

            renderResponse.setUrl(storedUrl);
            return renderResponse;
        } catch (Exception e) {
            signage.setStatus(ProductSignageStatus.ERROR);
            signage.setLastResultAt(LocalDateTime.now());
            productSignageRepository.save(signage);
            throw e;
        }
    }

    private ProdutoSignageRequest obterSignageRequest(ProdutoRequest request) {
        ProdutoSignageRequest signageRequest = request.getSignage();
        Boolean signageEnabled = request.getSignageEnabled();

        // UI payloads often send both `signage` (nested) and `signageEnabled` (flat). `signageEnabled`
        // is meant to toggle enable/disable without needing to fully craft the nested object.
        //
        // Precedence: if `signageEnabled` is present, it overrides `signage.enabled`.
        if (signageRequest != null) {
            if (signageEnabled != null) {
                signageRequest.setEnabled(signageEnabled);
            }
            return signageRequest;
        }

        if (signageEnabled != null) {
            return ProdutoSignageRequest.builder()
                    .enabled(signageEnabled)
                    .build();
        }

        return null;
    }

    private void validarCamposObrigatoriosParaSignage(Produto produto) {
        if (produto.getNome() == null || produto.getNome().trim().isEmpty()
                || produto.getDescricao() == null || produto.getDescricao().trim().isEmpty()
                || produto.getImagemPrincipal() == null || produto.getImagemPrincipal().trim().isEmpty()) {
            throw new IllegalArgumentException("Para habilitar signage é necessário nome, descrição e imagem principal do produto.");
        }
    }

    private String serializeToJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Não foi possível serializar dados do signage: " + e.getMessage(), e);
        }
    }

    private ProductSignagePalette parsePalette(String paletteJson) {
        if (paletteJson == null || paletteJson.isBlank()) {
            return null;
        }
        try {
            var values = objectMapper.readValue(paletteJson, new TypeReference<Map<String, Object>>() {});
            return ProductSignagePalette.fromMap(values);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Map<String, String> parsePhrases(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }

    private SignageColorMapping parseColorMapping(String colorMappingJson) {
        if (colorMappingJson == null || colorMappingJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(colorMappingJson, SignageColorMapping.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Map<String, String> resolveColors(SignageColorMapping colorMapping, ProductSignagePalette palette, String templateId) {
        Map<String, String> resolved = new HashMap<>();
        
        if (palette == null) {
            return resolved;
        }
        
        // Se não há mapeamento customizado ou não quer usar, retorna cores padrão da paleta
        if (colorMapping == null || !Boolean.TRUE.equals(colorMapping.getUseCustomMapping()) 
                || colorMapping.getElementMappings() == null) {
            // Retorna mapeamento padrão baseado nas cores semânticas
            if (palette.getBackground() != null) resolved.put("background", palette.getBackground());
            if (palette.getText() != null) resolved.put("text", palette.getText());
            if (palette.getAccent() != null) resolved.put("accent", palette.getAccent());
            if (palette.getAccent2() != null) resolved.put("accent2", palette.getAccent2());
            return resolved;
        }
        
        // Resolve cada elemento baseado no mapeamento
        Map<String, String> mappings = colorMapping.getElementMappings();
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            String element = entry.getKey();
            String source = entry.getValue();
            String color = resolveColorFromSource(source, palette);
            if (color != null) {
                resolved.put(element, color);
            }
        }
        
        return resolved;
    }
    
    private String resolveColorFromSource(String source, ProductSignagePalette palette) {
        if (source == null || source.isBlank()) {
            return null;
        }
        
        // Formato: "palette:nomeDaCor" ou "custom:#RRGGBB"
        if (source.startsWith("palette:")) {
            String paletteKey = source.substring(8).toLowerCase();
            switch (paletteKey) {
                case "vibrant": return palette.getVibrant();
                case "muted": return palette.getMuted();
                case "lightvibrant": return palette.getLightVibrant();
                case "darkvibrant": return palette.getDarkVibrant();
                case "lightmuted": return palette.getLightMuted();
                case "darkmuted": return palette.getDarkMuted();
                case "background": return palette.getBackground();
                case "text": return palette.getText();
                case "accent": return palette.getAccent();
                case "accent2": return palette.getAccent2();
                default: return null;
            }
        } else if (source.startsWith("custom:")) {
            // Retorna a cor customizada diretamente (ex: "custom:#FF0000")
            String customColor = source.substring(7);
            if (ProductSignagePalette.isValidHex(customColor)) {
                return customColor.toUpperCase();
            }
        }
        
        return null;
    }

    private String deriveBadgeText(Produto produto, Map<String, String> phrases) {
        if (phrases != null) {
            String badge = phrases.get("badge");
            if (badge != null && !badge.isBlank()) {
                return badge;
            }
        }
        if (Boolean.TRUE.equals(produto.getPromocao())) {
            return "Promoção";
        }
        if (Boolean.TRUE.equals(produto.getDestaque())) {
            return "Destaque";
        }
        return null;
    }

    private void validarPalette(ProductSignagePalette palette) {
        if (palette == null) {
            return;
        }
        var colors = java.util.Arrays.asList(
                palette.getVibrant(),
                palette.getMuted(),
                palette.getLightVibrant(),
                palette.getDarkVibrant(),
                palette.getLightMuted(),
                palette.getDarkMuted(),
                palette.getBackground(),
                palette.getText(),
                palette.getAccent(),
                palette.getAccent2()
        );
        for (var color : colors) {
            if (color != null && !ProductSignagePalette.isValidHex(color)) {
                throw new IllegalArgumentException("Cores da paleta devem estar no formato HEX #RRGGBB");
            }
        }
    }

    private void validarMetadataSource(String source) {
        if (source == null || !VALID_METADATA_SOURCES.contains(source)) {
            throw new IllegalArgumentException("metadataSource inválido: " + source);
        }
    }

    private String normalizeMetadataSource(String source) {
        if (source == null) {
            return null;
        }
        return source.trim().toUpperCase();
    }

    private void validarPrecoParaTemplate(Produto produto, String templatePreference) {
        if (templatePreference == null || templatePreference.isBlank()) {
            return;
        }
        var normalized = templatePreference.trim();
        if (PRICE_REQUIRED_TEMPLATES.contains(normalized) && produto.getPrecoVenda() == null) {
            throw new IllegalArgumentException(
                    "O template " + normalized + " exige que o produto tenha preço de venda definido");
        }
    }

    /**
     * Verifica se já existe um produto com o código interno especificado (excluindo o produto com o ID fornecido)
     */
    @Transactional(readOnly = true)
    public boolean existePorCodigoInterno(String codigoInterno) {
        if (codigoInterno == null || codigoInterno.trim().isEmpty()) {
            return false;
        }
        return produtoRepository.existsByCodigoInterno(codigoInterno);
    }
}
