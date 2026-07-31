package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.EmbalagemDTO;
import com.baronesa.emporio.dto.EmbalagemRequest;
import com.baronesa.emporio.entity.Embalagem;
import com.baronesa.emporio.entity.Produto;
import com.baronesa.emporio.repository.EmbalagemRepository;
import com.baronesa.emporio.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmbalagemService {

    private final EmbalagemRepository embalagemRepository;
    private final ProdutoRepository produtoRepository;

    public List<EmbalagemDTO> listarPorProduto(Long produtoId) {
        return embalagemRepository.findByProdutoId(produtoId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public EmbalagemDTO buscarPorId(Long id) {
        Embalagem embalagem = embalagemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Embalagem não encontrada"));
        return toDTO(embalagem);
    }

    @Transactional
    public EmbalagemDTO criar(EmbalagemRequest request) {
        validar(request);
        Produto produto = produtoRepository.findById(request.getProdutoId())
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        Embalagem embalagem = Embalagem.builder()
                .produto(produto)
                .nome(request.getNome())
                .fatorBase(request.getFatorBase())
                .codigoBarras(request.getCodigoBarras())
                .permiteVenda(Boolean.TRUE.equals(request.getPermiteVenda()))
                .principal(Boolean.TRUE.equals(request.getPrincipal()))
                .ativo(request.getAtivo() == null ? Boolean.TRUE : request.getAtivo())
                .build();

        if (embalagem.getPrincipal() != null && embalagem.getPrincipal()) {
            desmarcarOutrasPrincipais(produto.getId());
        }

        embalagem = embalagemRepository.save(embalagem);
        return toDTO(embalagem);
    }

    @Transactional
    public EmbalagemDTO atualizar(Long id, EmbalagemRequest request) {
        validar(request);
        Embalagem embalagem = embalagemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Embalagem não encontrada"));

        if (!embalagem.getProduto().getId().equals(request.getProdutoId())) {
            Produto produto = produtoRepository.findById(request.getProdutoId())
                    .orElseThrow(() -> new RuntimeException("Produto não encontrado"));
            embalagem.setProduto(produto);
        }

        embalagem.setNome(request.getNome());
        embalagem.setFatorBase(request.getFatorBase());
        embalagem.setCodigoBarras(request.getCodigoBarras());
        embalagem.setPermiteVenda(Boolean.TRUE.equals(request.getPermiteVenda()));
        embalagem.setPrincipal(Boolean.TRUE.equals(request.getPrincipal()));
        embalagem.setAtivo(request.getAtivo() == null ? Boolean.TRUE : request.getAtivo());

        if (embalagem.getPrincipal() != null && embalagem.getPrincipal()) {
            desmarcarOutrasPrincipais(embalagem.getProduto().getId());
        }

        embalagem = embalagemRepository.save(embalagem);
        return toDTO(embalagem);
    }

    @Transactional
    public void deletar(Long id) {
        embalagemRepository.deleteById(id);
    }

    private void validar(EmbalagemRequest request) {
        if (request.getProdutoId() == null) {
            throw new RuntimeException("produtoId é obrigatório");
        }
        if (request.getNome() == null || request.getNome().trim().isEmpty()) {
            throw new RuntimeException("nome é obrigatório");
        }
        if (request.getFatorBase() == null || request.getFatorBase() <= 0) {
            throw new RuntimeException("fatorBase deve ser um inteiro > 0");
        }
    }

    private void desmarcarOutrasPrincipais(Long produtoId) {
        List<Embalagem> embalagens = embalagemRepository.findByProdutoId(produtoId);
        for (Embalagem e : embalagens) {
            if (Boolean.TRUE.equals(e.getPrincipal())) {
                e.setPrincipal(false);
            }
        }
        embalagemRepository.saveAll(embalagens);
    }

    private EmbalagemDTO toDTO(Embalagem embalagem) {
        return EmbalagemDTO.builder()
                .id(embalagem.getId())
                .produtoId(embalagem.getProduto() != null ? embalagem.getProduto().getId() : null)
                .nome(embalagem.getNome())
                .fatorBase(embalagem.getFatorBase())
                .codigoBarras(embalagem.getCodigoBarras())
                .permiteVenda(embalagem.getPermiteVenda())
                .principal(embalagem.getPrincipal())
                .ativo(embalagem.getAtivo())
                .build();
    }
}
