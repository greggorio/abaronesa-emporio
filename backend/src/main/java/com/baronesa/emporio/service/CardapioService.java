package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.AdminCardapioProdutoDTO;
import com.baronesa.emporio.dto.CardapioCategoriaDTO;
import com.baronesa.emporio.dto.CardapioCategoriaV2DTO;
import com.baronesa.emporio.dto.CardapioProdutoDTO;
import com.baronesa.emporio.dto.CardapioProdutoV2DTO;
import com.baronesa.emporio.dto.CardapioSkuDTO;
import com.baronesa.emporio.dto.ProdutoMidiaDTO;
import com.baronesa.emporio.dto.ProdutoDisponibilidadeDTO; // Added
import com.baronesa.emporio.entity.Categoria;
import com.baronesa.emporio.entity.Produto;
import com.baronesa.emporio.entity.ProdutoDisponibilidade; // Added
import com.baronesa.emporio.entity.ProdutoPromocao;
import com.baronesa.emporio.entity.ProdutoSKU;
import com.baronesa.emporio.entity.GrupoClienteDesconto;
import com.baronesa.emporio.entity.PerfilCliente;
import com.baronesa.emporio.entity.SessaoConvidado;
import com.baronesa.emporio.enums.DiaSemana; // Added
import com.baronesa.emporio.enums.TipoPromocao;
import com.baronesa.emporio.repository.CategoriaRepository;
import com.baronesa.emporio.repository.GrupoClienteDescontoRepository;
import com.baronesa.emporio.repository.ProdutoDisponibilidadeRepository; // Added
import com.baronesa.emporio.repository.ProdutoHarmonizacaoRepository;
import com.baronesa.emporio.repository.ProdutoPromocaoRepository;
import com.baronesa.emporio.repository.ProdutoRepository;
import com.baronesa.emporio.repository.SessaoConvidadoRepository;
import com.baronesa.emporio.service.TranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek; // Added
import java.time.LocalDateTime; // Added
import java.time.LocalTime; // Added
import java.util.Comparator;
import java.util.List;
import java.util.Map; // Added
import java.util.Set; // Added
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CardapioService {

    private final CategoriaRepository categoriaRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoDisponibilidadeRepository produtoDisponibilidadeRepository;
    private final ProdutoHarmonizacaoRepository produtoHarmonizacaoRepository;
    private final ProdutoPromocaoRepository produtoPromocaoRepository;
    private final GrupoClienteDescontoRepository grupoClienteDescontoRepository;
    private final SessaoConvidadoRepository sessaoConvidadoRepository;
    private final TranslationService translationService;

    @Transactional(readOnly = true)
    public List<CardapioCategoriaDTO> buscarCardapioCompleto() {
        // Buscar todas as categorias com exibirNoCardapio = true
        List<Categoria> categorias = categoriaRepository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getExibirNoCardapio()))
                .sorted(Comparator.comparing(Categoria::getOrdem).thenComparing(Categoria::getNome))
                .collect(Collectors.toList());
        java.util.Locale locale = LocaleContextHolder.getLocale();

        // Para cada categoria, buscar produtos com exibirNoCardapio = true
        return categorias.stream()
                .map(categoria -> {
                    List<CardapioProdutoDTO> produtos = produtoRepository.findAll().stream()
                            .filter(p -> Boolean.TRUE.equals(p.getExibirNoCardapio()))
                            .filter(p -> p.getCategoria() != null && p.getCategoria().getId().equals(categoria.getId()))
                            .sorted(Comparator.comparing(Produto::getOrdem).thenComparing(Produto::getNome))
                            .map(produto -> converterProdutoParaCardapio(produto, locale))
                            .collect(Collectors.toList());

                    return CardapioCategoriaDTO.builder()
                            .id(categoria.getId())
                            .nome(translationService.translate("CATEGORY", categoria.getId(), "nome", categoria.getNome(), locale))
                            .icone(categoria.getIcone())
                            .cover(categoria.getCover())
                            .ordem(categoria.getOrdem())
                            .produtos(produtos)
                            .build();
                })
                // Retorna também categorias sem produtos (exibir no cardápio)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CardapioProdutoDTO> buscarProdutosDestaque() {
        // Buscar produtos com destaque = true E exibirNoCardapio = true
        List<Produto> produtosDestaque = produtoRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getDestaque()))
                .filter(p -> Boolean.TRUE.equals(p.getExibirNoCardapio()))
                .sorted(Comparator.comparing(Produto::getOrdem).thenComparing(Produto::getNome))
                .collect(Collectors.toList());

        // Se tiver menos de 6, completa com produtos normais
        if (produtosDestaque.size() < 6) {
            List<Produto> produtosNormais = produtoRepository.findAll().stream()
                    .filter(p -> Boolean.TRUE.equals(p.getExibirNoCardapio()))
                    .filter(p -> !Boolean.TRUE.equals(p.getDestaque()))
                    .sorted(Comparator.comparing(Produto::getOrdem).thenComparing(Produto::getNome))
                    .limit(6 - produtosDestaque.size())
                    .collect(Collectors.toList());

            produtosDestaque.addAll(produtosNormais);
        }

        java.util.Locale locale = LocaleContextHolder.getLocale();
        return produtosDestaque.stream()
                .limit(6)
                .map(produto -> converterProdutoParaCardapio(produto, locale))
                .collect(Collectors.toList());
    }

    private CardapioProdutoDTO converterProdutoParaCardapio(Produto produto, java.util.Locale locale) {
        return CardapioProdutoDTO.builder()
                .id(produto.getId())
                .nome(translationService.translate("PRODUCT", produto.getId(), "nome", produto.getNome(), locale))
                .descricao(translationService.translate("PRODUCT", produto.getId(), "descricao", produto.getDescricao(), locale))
                .preco(produto.getPrecoVenda())
                .imagemPrincipal(produto.getImagemPrincipal())
                .destaque(produto.getDestaque())
                .ordem(produto.getOrdem())
                .build();
    }

    private ProdutoDisponibilidadeDTO converterProdutoDisponibilidadeParaDTO(ProdutoDisponibilidade disponibilidade) {
        return ProdutoDisponibilidadeDTO.builder()
                .id(disponibilidade.getId())
                .diaSemana(disponibilidade.getDiaSemana())
                .horarioInicio(disponibilidade.getHorarioInicio())
                .horarioFim(disponibilidade.getHorarioFim())
                .ativo(disponibilidade.getAtivo())
                .build();
    }

    private BigDecimal calcularPrecoPromocional(BigDecimal precoBase, ProdutoPromocao promocao) {
        if (precoBase == null || promocao == null || promocao.getTipoPromocao() == null) {
            return null;
        }
        if (promocao.getTipoPromocao() == TipoPromocao.PERCENTUAL) {
            if (promocao.getPercentualDesconto() == null) {
                return null;
            }
            BigDecimal fator = BigDecimal.ONE.subtract(
                    promocao.getPercentualDesconto().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            BigDecimal precoCalculado = precoBase.multiply(fator);
            return precoCalculado.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }
        if (promocao.getTipoPromocao() == TipoPromocao.VALOR) {
            if (promocao.getValorPromocional() == null) {
                return null;
            }
            return promocao.getValorPromocional().setScale(2, RoundingMode.HALF_UP);
        }
        return null;
    }

    private ResultadoPromocao avaliarPromocaoAtiva(List<ProdutoPromocao> promocoes, BigDecimal precoBase, LocalDateTime now) {
        if (promocoes == null || promocoes.isEmpty() || precoBase == null) {
            return new ResultadoPromocao(false, null, null);
        }

        DiaSemana diaAtual = getDiaSemanaFromDayOfWeek(now.getDayOfWeek());
        LocalTime horarioAtual = now.toLocalTime();

        ProdutoPromocao melhorRegra = null;
        BigDecimal melhorPreco = null;

        for (ProdutoPromocao promo : promocoes) {
            if (!Boolean.TRUE.equals(promo.getAtivo())) {
                continue;
            }
            if (promo.getDiaSemana() == null || !promo.getDiaSemana().equals(diaAtual)) {
                continue;
            }
            if (promo.getHorarioInicio() == null || promo.getHorarioFim() == null) {
                continue;
            }
            if (horarioAtual.isBefore(promo.getHorarioInicio()) || horarioAtual.isAfter(promo.getHorarioFim())) {
                continue;
            }

            BigDecimal precoPromocional = calcularPrecoPromocional(precoBase, promo);
            if (precoPromocional == null) {
                continue;
            }
            if (melhorPreco == null || precoPromocional.compareTo(melhorPreco) < 0) {
                melhorPreco = precoPromocional;
                melhorRegra = promo;
            }
        }

        if (melhorRegra == null) {
            return new ResultadoPromocao(false, null, null);
        }
        return new ResultadoPromocao(true, melhorPreco, melhorRegra);
    }

    private boolean isProdutoAvailable(List<ProdutoDisponibilidade> disponibilidades, LocalDateTime now) {
        if (disponibilidades == null || disponibilidades.isEmpty()) {
            return true; // Se não há regras, o produto está sempre disponível
        }

        DayOfWeek currentDayOfWeek = now.getDayOfWeek();
        LocalTime currentTime = now.toLocalTime();

        for (ProdutoDisponibilidade disp : disponibilidades) {
            if (Boolean.TRUE.equals(disp.getAtivo())) { // Verifica se a regra está ativa
                DiaSemana diaDaRegra = disp.getDiaSemana();
                DiaSemana diaAtual = getDiaSemanaFromDayOfWeek(currentDayOfWeek);

                if (diaAtual != null && diaDaRegra.equals(diaAtual)) {
                    // Verifica se a hora atual está dentro do intervalo da regra
                    if (!currentTime.isBefore(disp.getHorarioInicio()) && !currentTime.isAfter(disp.getHorarioFim())) {
                        return true; // Produto disponível
                    }
                }
            }
        }
        return false; // Nenhuma regra ativa corresponde ao tempo atual
    }

    private DiaSemana getDiaSemanaFromDayOfWeek(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case SUNDAY -> DiaSemana.DOMINGO;
            case MONDAY -> DiaSemana.SEGUNDA;
            case TUESDAY -> DiaSemana.TERCA;
            case WEDNESDAY -> DiaSemana.QUARTA;
            case THURSDAY -> DiaSemana.QUINTA;
            case FRIDAY -> DiaSemana.SEXTA;
            case SATURDAY -> DiaSemana.SABADO;
        };
    }

    /**
     * Calcula o preço vigente para uso no pedido (mesma regra do cardápio público),
     * considerando promoções ativas na data/hora atual.
     */
    @Transactional(readOnly = true)
    public BigDecimal calcularPrecoAtualParaPedido(Produto produto, ProdutoSKU sku) {
        return calcularPrecoAtualParaPedido(produto, sku, (DescontoContext) null);
    }

    @Transactional(readOnly = true)
    public List<CardapioCategoriaV2DTO> buscarCardapioCompletoV2(String guestToken) {
        DescontoContext descontoContext = buildDescontoContext(guestToken);
        return buildCardapioV2(true, descontoContext);
    }

    @Transactional(readOnly = true)
    public List<CardapioCategoriaV2DTO> buscarCardapioCompletoV2Delivery(String guestToken) {
        DescontoContext descontoContext = buildDescontoContext(guestToken);
        return buildCardapioV2Delivery(true, descontoContext);
    }

    @Transactional(readOnly = true)
    public List<CardapioCategoriaV2DTO> buscarCardapioCompletoV2Admin() {
        return buildCardapioV2(false, null);
    }

    @Transactional(readOnly = true)
    public Page<AdminCardapioProdutoDTO> buscarProdutosAdminPaginado(String termo, Pageable pageable) {
        Specification<Produto> spec = buildSpec(termo);
        Page<Produto> page = produtoRepository.findAll(spec, pageable);

        Set<Long> produtoIds = page.getContent().stream().map(Produto::getId).collect(Collectors.toSet());
        Map<Long, List<ProdutoDisponibilidade>> disponibilidadesPorProduto = produtoIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : produtoDisponibilidadeRepository.findByProdutoIdIn(produtoIds).stream()
                        .collect(Collectors.groupingBy(disponibilidade -> disponibilidade.getProduto().getId()));

        Map<Long, List<ProdutoPromocao>> promocoesPorProduto = produtoIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : produtoPromocaoRepository.findByProdutoIdIn(produtoIds).stream()
                        .collect(Collectors.groupingBy(promocao -> promocao.getProduto().getId()));

        LocalDateTime now = LocalDateTime.now();

        return page.map(produto -> {
            List<ProdutoDisponibilidade> regrasDisponibilidade = disponibilidadesPorProduto.getOrDefault(produto.getId(), java.util.Collections.emptyList());
            boolean isAvailable = isProdutoAvailable(regrasDisponibilidade, now);

            List<ProdutoDisponibilidadeDTO> horariosDisponiveis = null;
            if (!isAvailable) {
                horariosDisponiveis = regrasDisponibilidade.stream()
                        .map(this::converterProdutoDisponibilidadeParaDTO)
                        .collect(Collectors.toList());
            }

            List<ProdutoPromocao> regrasPromocao = promocoesPorProduto.getOrDefault(produto.getId(), java.util.Collections.emptyList());
            ResultadoPromocao resultadoPromocao = avaliarPromocaoAtiva(regrasPromocao, produto.getPrecoVenda(), now);

            BigDecimal precoBaseProduto = produto.getPrecoVenda();
            BigDecimal precoPromocionalProduto = resultadoPromocao.getPrecoPromocional();

            List<CardapioSkuDTO> skus = produto.getSkus() == null ? java.util.List.of() : produto.getSkus().stream()
                    .filter(s -> s.getAtivo() == null || Boolean.TRUE.equals(s.getAtivo()))
                    .map(s -> {
                        BigDecimal precoSkuBase = s.getPrecoVenda() != null ? s.getPrecoVenda() : precoBaseProduto;
                        BigDecimal precoSkuPromocional = resultadoPromocao.isEmPromocao()
                                ? calcularPrecoPromocional(precoSkuBase, resultadoPromocao.getRegraAplicada())
                                : null;
                        return CardapioSkuDTO.builder()
                                .id(s.getId())
                                .variacao(s.getVariacao())
                                .preco(precoSkuBase)
                                .precoPromocional(precoSkuPromocional)
                                .principal(s.getPrincipal())
                                .ativo(s.getAtivo())
                                .build();
                    })
                    .collect(Collectors.toList());

            return AdminCardapioProdutoDTO.builder()
                    .id(produto.getId())
                    .nome(produto.getNome())
                    .descricao(produto.getDescricao())
                    .preco(precoBaseProduto)
                    .preco_promocional(precoPromocionalProduto)
                    .imagemPrincipal(produto.getImagemPrincipal())
                    .destaque(produto.getDestaque())
                    .ordem(produto.getOrdem())
                    .skus(skus)
                    .midias(produto.getMidias() == null ? java.util.Collections.emptyList() : produto.getMidias().stream()
                            .filter(m -> Boolean.TRUE.equals(m.getAtivo()))
                            .map(m -> ProdutoMidiaDTO.builder()
                                    .id(m.getId())
                                    .produtoId(produto.getId())
                                    .tipo(m.getTipo())
                                    .url(m.getUrl())
                                    .titulo(m.getTitulo())
                                    .descricao(m.getDescricao())
                                    .ordem(m.getOrdem())
                                    .principal(m.getPrincipal())
                                    .ativo(m.getAtivo())
                                    .criadoEm(m.getCriadoEm())
                                    .build())
                            .collect(Collectors.toList()))
                    .produto_disponivel(isAvailable)
                    .horarios_disponiveis(horariosDisponiveis)
                    .produto_em_promocao(resultadoPromocao.isEmPromocao())
                    .categoriaId(produto.getCategoria() != null ? produto.getCategoria().getId() : null)
                    .categoriaNome(produto.getCategoria() != null ? produto.getCategoria().getNome() : null)
                    .build();
        });
    }

    private List<CardapioCategoriaV2DTO> buildCardapioV2(boolean apenasExibirNoCardapio, DescontoContext descontoContext) {
        List<Categoria> categorias = categoriaRepository.findAll().stream()
                .filter(c -> !apenasExibirNoCardapio || Boolean.TRUE.equals(c.getExibirNoCardapio()))
                .sorted(Comparator.comparing(Categoria::getOrdem).thenComparing(Categoria::getNome))
                .collect(Collectors.toList());
        java.util.Locale locale = LocaleContextHolder.getLocale();

        Set<Long> produtoIds = produtoRepository.findAll().stream()
                .filter(p -> !apenasExibirNoCardapio || Boolean.TRUE.equals(p.getExibirNoCardapio()))
                .map(Produto::getId)
                .collect(Collectors.toSet());

        Map<Long, List<ProdutoDisponibilidade>> disponibilidadesPorProduto = produtoIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : produtoDisponibilidadeRepository.findByProdutoIdIn(produtoIds).stream()
                        .collect(Collectors.groupingBy(disponibilidade -> disponibilidade.getProduto().getId()));

        Map<Long, List<ProdutoPromocao>> promocoesPorProduto = produtoIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : produtoPromocaoRepository.findByProdutoIdIn(produtoIds).stream()
                        .collect(Collectors.groupingBy(promocao -> promocao.getProduto().getId()));

        Map<Long, Boolean> produtosComHarmonizacao = produtoIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : produtoHarmonizacaoRepository.findByProdutoPrincipalIdIn(produtoIds).stream()
                        .collect(Collectors.toMap(
                                harmonizacao -> harmonizacao.getProdutoPrincipal().getId(),
                                harmonizacao -> Boolean.TRUE,
                                (existing, replacement) -> existing
                        ));

        LocalDateTime now = LocalDateTime.now();

        return categorias.stream()
                .map(categoria -> {
                    List<CardapioProdutoV2DTO> produtos = produtoRepository.findAll().stream()
                            .filter(p -> !apenasExibirNoCardapio || Boolean.TRUE.equals(p.getExibirNoCardapio()))
                            .filter(p -> p.getCategoria() != null && p.getCategoria().getId().equals(categoria.getId()))
                            .sorted(Comparator.comparing(Produto::getOrdem).thenComparing(Produto::getNome))
                            .map(produto -> {
                                List<ProdutoDisponibilidade> regrasDisponibilidade = disponibilidadesPorProduto.getOrDefault(produto.getId(), java.util.Collections.emptyList());
                                boolean isAvailable = isProdutoAvailable(regrasDisponibilidade, now);

                                List<ProdutoDisponibilidadeDTO> horariosDisponiveis = null;
                                if (!isAvailable) {
                                    horariosDisponiveis = regrasDisponibilidade.stream()
                                            .map(this::converterProdutoDisponibilidadeParaDTO)
                                            .collect(Collectors.toList());
                                }

                                List<ProdutoPromocao> regrasPromocao = promocoesPorProduto.getOrDefault(produto.getId(), java.util.Collections.emptyList());
                                ResultadoPromocao resultadoPromocao = avaliarPromocaoAtiva(regrasPromocao, produto.getPrecoVenda(), now);

                                BigDecimal precoBaseProduto = produto.getPrecoVenda();
                                BigDecimal precoPromocionalProduto = resultadoPromocao.getPrecoPromocional();
                                BigDecimal precoDescontoGrupoProduto = calcularPrecoComDescontoGrupo(precoBaseProduto, produto, descontoContext);
                                BigDecimal precoFinalProduto = escolherMenorPreco(precoPromocionalProduto, precoDescontoGrupoProduto);
                                String origemDescontoProduto = resolverOrigemDesconto(precoFinalProduto, precoPromocionalProduto, precoDescontoGrupoProduto);

                                return CardapioProdutoV2DTO.builder()
                                        .id(produto.getId())
                                        .nome(translationService.translate("PRODUCT", produto.getId(), "nome", produto.getNome(), locale))
                                        .descricao(translationService.translate("PRODUCT", produto.getId(), "descricao", produto.getDescricao(), locale))
                                        .preco(precoBaseProduto)
                                        .imagemPrincipal(produto.getImagemPrincipal())
                                        .destaque(produto.getDestaque())
                                        .ordem(produto.getOrdem())
                                        .skus(produto.getSkus() == null ? java.util.List.of() : produto.getSkus().stream()
                                                .filter(s -> s.getAtivo() == null || Boolean.TRUE.equals(s.getAtivo()))
                                                .map(s -> {
                                                    BigDecimal precoSkuBase = s.getPrecoVenda() != null ? s.getPrecoVenda() : precoBaseProduto;
                                                    BigDecimal precoSkuPromocional = resultadoPromocao.isEmPromocao()
                                                            ? calcularPrecoPromocional(precoSkuBase, resultadoPromocao.getRegraAplicada())
                                                            : null;
                                                    BigDecimal precoSkuDescontoGrupo = calcularPrecoComDescontoGrupo(precoSkuBase, produto, descontoContext);
                                                    BigDecimal precoSkuFinal = escolherMenorPreco(precoSkuPromocional, precoSkuDescontoGrupo);
                                                    String origemDescontoSku = resolverOrigemDesconto(precoSkuFinal, precoSkuPromocional, precoSkuDescontoGrupo);
                                                    return CardapioSkuDTO.builder()
                                                            .id(s.getId())
                                                            .variacao(translationService.translate("SKU", s.getId(), "variacao", s.getVariacao(), locale))
                                                            .preco(precoSkuBase)
                                                            .precoPromocional(precoSkuFinal)
                                                            .origemDesconto(origemDescontoSku)
                                                            .principal(s.getPrincipal())
                                                            .ativo(s.getAtivo())
                                                            .build();
                                                })
                                                .collect(Collectors.toList()))
                                        .midias(produto.getMidias() == null ? java.util.Collections.emptyList() : produto.getMidias().stream()
                                                .filter(m -> Boolean.TRUE.equals(m.getAtivo()))
                                                .map(m -> ProdutoMidiaDTO.builder()
                                                        .id(m.getId())
                                                        .produtoId(produto.getId())
                                                        .tipo(m.getTipo())
                                                        .url(m.getUrl())
                                                        .titulo(m.getTitulo())
                                                        .descricao(m.getDescricao())
                                                        .ordem(m.getOrdem())
                                                        .principal(m.getPrincipal())
                                                        .ativo(m.getAtivo())
                                                        .criadoEm(m.getCriadoEm())
                                                        .build())
                                                .collect(Collectors.toList()))
                                        .produto_disponivel(isAvailable)
                                        .horarios_disponiveis(horariosDisponiveis)
                                        .produto_em_promocao("PROMOCAO".equals(origemDescontoProduto))
                                        .preco_promocional(precoFinalProduto)
                                        .origem_desconto(origemDescontoProduto)
                                        .temHarmonizacao(Boolean.TRUE.equals(produtosComHarmonizacao.get(produto.getId())))
                                        .build();
                            })
                            .collect(Collectors.toList());

                    return CardapioCategoriaV2DTO.builder()
                            .id(categoria.getId())
                            .nome(translationService.translate("CATEGORY", categoria.getId(), "nome", categoria.getNome(), locale))
                            .icone(categoria.getIcone())
                            .cover(categoria.getCover())
                            .ordem(categoria.getOrdem())
                            .produtos(produtos)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<CardapioCategoriaV2DTO> buildCardapioV2Delivery(boolean apenasExibirNoCardapio, DescontoContext descontoContext) {
        List<Categoria> categorias = categoriaRepository.findAll().stream()
                .filter(c -> !apenasExibirNoCardapio || Boolean.TRUE.equals(c.getExibirNoCardapio()))
                .sorted(Comparator.comparing(Categoria::getOrdem).thenComparing(Categoria::getNome))
                .collect(Collectors.toList());
        java.util.Locale locale = LocaleContextHolder.getLocale();

        Set<Long> produtoIds = produtoRepository.findAll().stream()
                .filter(p -> !apenasExibirNoCardapio || Boolean.TRUE.equals(p.getExibirNoCardapio()))
                .map(Produto::getId)
                .collect(Collectors.toSet());

        Map<Long, List<ProdutoDisponibilidade>> disponibilidadesPorProduto = produtoIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : produtoDisponibilidadeRepository.findByProdutoIdIn(produtoIds).stream()
                        .collect(Collectors.groupingBy(disponibilidade -> disponibilidade.getProduto().getId()));

        Map<Long, List<ProdutoPromocao>> promocoesPorProduto = produtoIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : produtoPromocaoRepository.findByProdutoIdIn(produtoIds).stream()
                        .collect(Collectors.groupingBy(promocao -> promocao.getProduto().getId()));

        Map<Long, Boolean> produtosComHarmonizacao = produtoIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : produtoHarmonizacaoRepository.findByProdutoPrincipalIdIn(produtoIds).stream()
                        .collect(Collectors.toMap(
                                harmonizacao -> harmonizacao.getProdutoPrincipal().getId(),
                                harmonizacao -> Boolean.TRUE,
                                (existing, replacement) -> existing
                        ));

        LocalDateTime now = LocalDateTime.now();

        return categorias.stream()
                .map(categoria -> {
                    List<CardapioProdutoV2DTO> produtos = produtoRepository.findAll().stream()
                            .filter(p -> !apenasExibirNoCardapio || Boolean.TRUE.equals(p.getExibirNoCardapio()))
                            .filter(p -> p.getCategoria() != null && p.getCategoria().getId().equals(categoria.getId()))
                            .filter(p -> shouldKeepProductInDelivery(p))
                            .sorted(Comparator.comparing(Produto::getOrdem).thenComparing(Produto::getNome))
                            .map(produto -> {
                                List<ProdutoDisponibilidade> regrasDisponibilidade = disponibilidadesPorProduto.getOrDefault(produto.getId(), java.util.Collections.emptyList());
                                boolean isAvailable = isProdutoAvailable(regrasDisponibilidade, now);

                                List<ProdutoDisponibilidadeDTO> horariosDisponiveis = null;
                                if (!isAvailable) {
                                    horariosDisponiveis = regrasDisponibilidade.stream()
                                            .map(this::converterProdutoDisponibilidadeParaDTO)
                                            .collect(Collectors.toList());
                                }

                                List<ProdutoPromocao> regrasPromocao = promocoesPorProduto.getOrDefault(produto.getId(), java.util.Collections.emptyList());
                                ResultadoPromocao resultadoPromocao = avaliarPromocaoAtiva(regrasPromocao, produto.getPrecoVenda(), now);

                                BigDecimal precoBaseProduto = produto.getPrecoVenda();
                                BigDecimal precoPromocionalProduto = resultadoPromocao.getPrecoPromocional();
                                BigDecimal precoDescontoGrupoProduto = calcularPrecoComDescontoGrupo(precoBaseProduto, produto, descontoContext);
                                BigDecimal precoFinalProduto = escolherMenorPreco(precoPromocionalProduto, precoDescontoGrupoProduto);
                                String origemDescontoProduto = resolverOrigemDesconto(precoFinalProduto, precoPromocionalProduto, precoDescontoGrupoProduto);

                                List<CardapioSkuDTO> skusFiltrados = produto.getSkus() == null ? java.util.List.of() : produto.getSkus().stream()
                                        .filter(s -> s.getAtivo() == null || Boolean.TRUE.equals(s.getAtivo()))
                                        .filter(this::skuTemEstoqueOuNaoControla)
                                        .map(s -> {
                                            BigDecimal precoSkuBase = s.getPrecoVenda() != null ? s.getPrecoVenda() : precoBaseProduto;
                                            BigDecimal precoSkuPromocional = resultadoPromocao.isEmPromocao()
                                                    ? calcularPrecoPromocional(precoSkuBase, resultadoPromocao.getRegraAplicada())
                                                    : null;
                                            BigDecimal precoSkuDescontoGrupo = calcularPrecoComDescontoGrupo(precoSkuBase, produto, descontoContext);
                                            BigDecimal precoSkuFinal = escolherMenorPreco(precoSkuPromocional, precoSkuDescontoGrupo);
                                            String origemDescontoSku = resolverOrigemDesconto(precoSkuFinal, precoSkuPromocional, precoSkuDescontoGrupo);
                                            return CardapioSkuDTO.builder()
                                                    .id(s.getId())
                                                    .variacao(translationService.translate("SKU", s.getId(), "variacao", s.getVariacao(), locale))
                                                    .preco(precoSkuBase)
                                                    .precoPromocional(precoSkuFinal)
                                                    .origemDesconto(origemDescontoSku)
                                                    .principal(s.getPrincipal())
                                                    .ativo(s.getAtivo())
                                                    .build();
                                        })
                                        .collect(Collectors.toList());

                                // Se controla estoque e não sobrou nenhum SKU com estoque, descartar o produto
                                if (Boolean.TRUE.equals(produto.getControlaEstoque()) && skusFiltrados.isEmpty()) {
                                    return null;
                                }

                                return CardapioProdutoV2DTO.builder()
                                        .id(produto.getId())
                                        .nome(translationService.translate("PRODUCT", produto.getId(), "nome", produto.getNome(), locale))
                                        .descricao(translationService.translate("PRODUCT", produto.getId(), "descricao", produto.getDescricao(), locale))
                                        .preco(precoBaseProduto)
                                        .imagemPrincipal(produto.getImagemPrincipal())
                                        .destaque(produto.getDestaque())
                                        .ordem(produto.getOrdem())
                                        .skus(skusFiltrados)
                                        .midias(produto.getMidias() == null ? java.util.Collections.emptyList() : produto.getMidias().stream()
                                                .filter(m -> Boolean.TRUE.equals(m.getAtivo()))
                                                .map(m -> ProdutoMidiaDTO.builder()
                                                        .id(m.getId())
                                                        .produtoId(produto.getId())
                                                        .tipo(m.getTipo())
                                                        .url(m.getUrl())
                                                        .titulo(m.getTitulo())
                                                        .descricao(m.getDescricao())
                                                        .ordem(m.getOrdem())
                                                        .principal(m.getPrincipal())
                                                        .ativo(m.getAtivo())
                                                        .criadoEm(m.getCriadoEm())
                                                        .build())
                                                .collect(Collectors.toList()))
                                        .produto_disponivel(isAvailable)
                                        .horarios_disponiveis(horariosDisponiveis)
                                        .produto_em_promocao("PROMOCAO".equals(origemDescontoProduto))
                                        .preco_promocional(precoFinalProduto)
                                        .origem_desconto(origemDescontoProduto)
                                        .temHarmonizacao(Boolean.TRUE.equals(produtosComHarmonizacao.get(produto.getId())))
                                        .build();
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    return CardapioCategoriaV2DTO.builder()
                            .id(categoria.getId())
                            .nome(translationService.translate("CATEGORY", categoria.getId(), "nome", categoria.getNome(), locale))
                            .icone(categoria.getIcone())
                            .cover(categoria.getCover())
                            .ordem(categoria.getOrdem())
                            .produtos(produtos)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private boolean shouldKeepProductInDelivery(Produto produto) {
        // Se não controla estoque, mantém
        if (Boolean.FALSE.equals(produto.getControlaEstoque())) {
            return true;
        }
        // Se controla estoque, usa estoqueOrdenacao (agregado) > 0
        Integer estoqueAgregado = produto.getEstoqueOrdenacao();
        return estoqueAgregado != null && estoqueAgregado > 0;
    }

    private boolean skuTemEstoqueOuNaoControla(ProdutoSKU sku) {
        // Se o produto não controla estoque, o SKU é válido por padrão
        Produto produto = sku.getProduto();
        if (produto != null && Boolean.FALSE.equals(produto.getControlaEstoque())) {
            return true;
        }
        // Caso contrário, requer estoque do SKU > 0
        if (sku.getEstoque() == null) return false;
        Integer qtd = sku.getEstoque().getQuantidade();
        return qtd != null && qtd > 0;
    }

    private Specification<Produto> buildSpec(String termo) {
        return (root, query, cb) -> {
            if (termo == null || termo.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + termo.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("nome")), like),
                    cb.like(cb.lower(root.get("descricao")), like)
            );
        };
    }

    private static class ResultadoPromocao {
        private final boolean emPromocao;
        private final BigDecimal precoPromocional;
        private final ProdutoPromocao regraAplicada;

        ResultadoPromocao(boolean emPromocao, BigDecimal precoPromocional, ProdutoPromocao regraAplicada) {
            this.emPromocao = emPromocao;
            this.precoPromocional = precoPromocional;
            this.regraAplicada = regraAplicada;
        }

        public boolean isEmPromocao() {
            return emPromocao;
        }

        public BigDecimal getPrecoPromocional() {
            return precoPromocional;
        }

        public ProdutoPromocao getRegraAplicada() {
            return regraAplicada;
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal calcularPrecoAtualParaPedido(Produto produto, ProdutoSKU sku, String guestToken) {
        DescontoContext descontoContext = buildDescontoContext(guestToken);
        return calcularPrecoAtualParaPedido(produto, sku, descontoContext);
    }

    private BigDecimal calcularPrecoAtualParaPedido(Produto produto, ProdutoSKU sku, DescontoContext descontoContext) {
        if (produto == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal precoBaseProduto = produto.getPrecoVenda();
        BigDecimal precoBase = (sku != null && sku.getPrecoVenda() != null)
                ? sku.getPrecoVenda()
                : precoBaseProduto;
        if (precoBase == null) {
            precoBase = BigDecimal.ZERO;
        }

        List<ProdutoPromocao> regrasPromocao = produtoPromocaoRepository.findByProdutoId(produto.getId());
        ResultadoPromocao resultadoPromocao = avaliarPromocaoAtiva(regrasPromocao, precoBaseProduto, LocalDateTime.now());
        BigDecimal precoPromocional = resultadoPromocao.isEmPromocao()
                ? calcularPrecoPromocional(precoBase, resultadoPromocao.getRegraAplicada())
                : null;

        BigDecimal precoDescontoGrupo = calcularPrecoComDescontoGrupo(precoBase, produto, descontoContext);
        BigDecimal precoFinal = escolherMenorPreco(precoPromocional, precoDescontoGrupo);

        if (precoFinal != null) {
            return precoFinal.setScale(2, RoundingMode.HALF_UP);
        }

        return precoBase.setScale(2, RoundingMode.HALF_UP);
    }

    private DescontoContext buildDescontoContext(String guestToken) {
        if (!StringUtils.hasText(guestToken)) {
            return null;
        }
        Long grupoClienteId = resolveGrupoClienteId(guestToken);
        if (grupoClienteId == null) {
            return null;
        }
        List<GrupoClienteDesconto> descontos = grupoClienteDescontoRepository.findByGrupoClienteIdAndAtivoTrue(grupoClienteId);
        if (descontos == null || descontos.isEmpty()) {
            return null;
        }
        Map<Long, BigDecimal> descontosCategoria = new java.util.HashMap<>();
        Map<Long, BigDecimal> descontosSubcategoria = new java.util.HashMap<>();
        for (GrupoClienteDesconto desconto : descontos) {
            if (desconto.getSubcategoria() != null) {
                descontosSubcategoria.put(desconto.getSubcategoria().getId(), desconto.getDescontoPercentual());
            } else if (desconto.getCategoria() != null) {
                descontosCategoria.put(desconto.getCategoria().getId(), desconto.getDescontoPercentual());
            }
        }
        return new DescontoContext(descontosCategoria, descontosSubcategoria);
    }

    private Long resolveGrupoClienteId(String guestToken) {
        return sessaoConvidadoRepository.findByGuestToken(guestToken)
                .map(SessaoConvidado::getUsuario)
                .map(usuario -> {
                    PerfilCliente perfilCliente = usuario.getPerfilCliente();
                    if (perfilCliente == null || perfilCliente.getGrupoCliente() == null) {
                        return null;
                    }
                    return perfilCliente.getGrupoCliente().getId();
                })
                .orElse(null);
    }

    private BigDecimal calcularPrecoComDescontoGrupo(BigDecimal precoBase, Produto produto, DescontoContext descontoContext) {
        if (precoBase == null || descontoContext == null || produto == null) {
            return null;
        }
        BigDecimal descontoPercentual = null;
        if (produto.getSubcategoria() != null) {
            descontoPercentual = descontoContext.subcategoriaDescontos().get(produto.getSubcategoria().getId());
        }
        if (descontoPercentual == null && produto.getCategoria() != null) {
            descontoPercentual = descontoContext.categoriaDescontos().get(produto.getCategoria().getId());
        }
        if (descontoPercentual == null) {
            return null;
        }
        BigDecimal fator = BigDecimal.ONE.subtract(
                descontoPercentual.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        return precoBase.multiply(fator).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal escolherMenorPreco(BigDecimal precoA, BigDecimal precoB) {
        if (precoA == null) {
            return precoB;
        }
        if (precoB == null) {
            return precoA;
        }
        return precoA.compareTo(precoB) <= 0 ? precoA : precoB;
    }

    private String resolverOrigemDesconto(BigDecimal precoFinal, BigDecimal precoPromocional, BigDecimal precoSocio) {
        if (precoFinal == null) {
            return null;
        }
        if (precoPromocional != null && precoFinal.compareTo(precoPromocional) == 0) {
            return "PROMOCAO";
        }
        if (precoSocio != null && precoFinal.compareTo(precoSocio) == 0) {
            return "SOCIO";
        }
        return null;
    }

    private record DescontoContext(Map<Long, BigDecimal> categoriaDescontos,
                                   Map<Long, BigDecimal> subcategoriaDescontos) {}
}
