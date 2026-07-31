package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.CardapioProdutoDTO;
import com.baronesa.emporio.dto.ProdutoHarmonizacaoDTO;
import com.baronesa.emporio.entity.Produto;
import com.baronesa.emporio.entity.ProdutoHarmonizacao;
import com.baronesa.emporio.entity.ProdutoSKU;
import com.baronesa.emporio.exception.BusinessException;
import com.baronesa.emporio.exception.ResourceNotFoundException;
import com.baronesa.emporio.repository.ProdutoHarmonizacaoRepository;
import com.baronesa.emporio.repository.ProdutoRepository;
import com.baronesa.emporio.repository.ProdutoSKURepository;
import com.baronesa.emporio.service.TranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProdutoHarmonizacaoService {

    private final ProdutoHarmonizacaoRepository produtoHarmonizacaoRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoSKURepository produtoSKURepository;
    private final TranslationService translationService;

    @Transactional
    public ProdutoHarmonizacaoDTO criarHarmonizacao(Long produtoPrincipalId, ProdutoHarmonizacaoDTO dto) {
        if (produtoPrincipalId.equals(dto.getProdutoHarmonizadoId())) {
            throw new BusinessException("Um produto não pode ser harmonizado com ele mesmo.");
        }

        Produto produtoPrincipal = produtoRepository.findById(produtoPrincipalId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto principal não encontrado com ID: " + produtoPrincipalId));
        Produto produtoHarmonizado = produtoRepository.findById(dto.getProdutoHarmonizadoId())
                .orElseThrow(() -> new ResourceNotFoundException("Produto harmonizado não encontrado com ID: " + dto.getProdutoHarmonizadoId()));

        if (produtoHarmonizacaoRepository.findByProdutoPrincipalIdAndProdutoHarmonizadoId(produtoPrincipalId, dto.getProdutoHarmonizadoId()).isPresent()) {
            throw new BusinessException("Esta harmonização já existe.");
        }

        ProdutoSKU skuHarmonizado = null;
        if (dto.getSkuHarmonizadoId() != null) {
            skuHarmonizado = produtoSKURepository.findById(dto.getSkuHarmonizadoId())
                    .orElseThrow(() -> new ResourceNotFoundException("SKU não encontrado com ID: " + dto.getSkuHarmonizadoId()));

            if (!skuHarmonizado.getProduto().getId().equals(dto.getProdutoHarmonizadoId())) {
                throw new BusinessException("O SKU informado não pertence ao produto harmonizado.");
            }
        }

        ProdutoHarmonizacao harmonizacao = new ProdutoHarmonizacao();
        harmonizacao.setProdutoPrincipal(produtoPrincipal);
        harmonizacao.setProdutoHarmonizado(produtoHarmonizado);
        harmonizacao.setSkuHarmonizado(skuHarmonizado);
        harmonizacao.setTipo(dto.getTipo());
        harmonizacao.setDescricao(dto.getDescricao());
        harmonizacao.setOrdem(dto.getOrdem() != null ? dto.getOrdem() : 0);

        harmonizacao = produtoHarmonizacaoRepository.save(harmonizacao);
        return toDto(harmonizacao, LocaleContextHolder.getLocale());
    }

    @Transactional(readOnly = true)
    public List<ProdutoHarmonizacaoDTO> listarHarmonizacoes(Long produtoPrincipalId) {
        return produtoHarmonizacaoRepository.findByProdutoPrincipalId(produtoPrincipalId).stream()
                .map(h -> toDto(h, LocaleContextHolder.getLocale()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void removerHarmonizacao(Long produtoPrincipalId, Long harmonizacaoId) {
        ProdutoHarmonizacao harmonizacao = produtoHarmonizacaoRepository.findById(harmonizacaoId)
                .orElseThrow(() -> new ResourceNotFoundException("Harmonização não encontrada com ID: " + harmonizacaoId));

        if (!harmonizacao.getProdutoPrincipal().getId().equals(produtoPrincipalId)) {
            throw new BusinessException("A harmonização não pertence ao produto principal informado.");
        }

        produtoHarmonizacaoRepository.delete(harmonizacao);
    }

    private ProdutoHarmonizacaoDTO toDto(ProdutoHarmonizacao entity, java.util.Locale locale) {
        ProdutoSKU sku = entity.getSkuHarmonizado();

        if (sku == null) {
            sku = entity.getProdutoHarmonizado().getSkus().stream()
                    .filter(ProdutoSKU::getPrincipal)
                    .findFirst()
                    .orElse(null);
        }

        BigDecimal preco = sku != null ? sku.getPrecoVenda() : entity.getProdutoHarmonizado().getPrecoVenda();
        Long skuId = sku != null ? sku.getId() : null;
        String skuVariacao = sku != null ? sku.getVariacao() : null;

        CardapioProdutoDTO produtoHarmonizadoDto = CardapioProdutoDTO.builder()
                .id(entity.getProdutoHarmonizado().getId())
                .nome(translationService.translate("PRODUCT", entity.getProdutoHarmonizado().getId(), "nome", entity.getProdutoHarmonizado().getNome(), locale))
                .descricao(translationService.translate("PRODUCT", entity.getProdutoHarmonizado().getId(), "descricao", entity.getProdutoHarmonizado().getDescricao(), locale))
                .preco(preco)
                .imagemPrincipal(entity.getProdutoHarmonizado().getImagemPrincipal())
                .skuId(skuId)
                .skuVariacao(translationService.translate("SKU", skuId, "variacao", skuVariacao, locale))
                .build();

        return ProdutoHarmonizacaoDTO.builder()
                .id(entity.getId())
                .produtoPrincipalId(entity.getProdutoPrincipal().getId())
                .produtoHarmonizadoId(entity.getProdutoHarmonizado().getId())
                .skuHarmonizadoId(skuId)
                .tipo(entity.getTipo())
                .descricao(translationService.translate("HARMONIZATION", entity.getId(), "descricao", entity.getDescricao(), locale))
                .ordem(entity.getOrdem())
                .criadoEm(entity.getCriadoEm())
                .atualizadoEm(entity.getAtualizadoEm())
                .produtoHarmonizado(produtoHarmonizadoDto)
                .build();
    }
}
