package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.ProdutoPromocaoDTO;
import com.baronesa.emporio.dto.ProdutoPromocaoRequest;
import com.baronesa.emporio.entity.Produto;
import com.baronesa.emporio.entity.ProdutoPromocao;
import com.baronesa.emporio.enums.TipoPromocao;
import com.baronesa.emporio.repository.ProdutoPromocaoRepository;
import com.baronesa.emporio.repository.ProdutoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProdutoPromocaoService {

    private final ProdutoPromocaoRepository promocaoRepository;
    private final ProdutoRepository produtoRepository;

    public ProdutoPromocaoDTO criar(ProdutoPromocaoRequest request) {
        validarRequest(request);

        Produto produto = produtoRepository.findById(request.getProdutoId())
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + request.getProdutoId()));

        ProdutoPromocao entidade = ProdutoPromocao.builder()
                .produto(produto)
                .diaSemana(request.getDiaSemana())
                .horarioInicio(request.getHorarioInicio())
                .horarioFim(request.getHorarioFim())
                .tipoPromocao(request.getTipoPromocao())
                .percentualDesconto(request.getPercentualDesconto())
                .valorPromocional(request.getValorPromocional())
                .ativo(request.getAtivo() != null ? request.getAtivo() : true)
                .build();

        validarCamposPromocao(entidade);
        validarSobreposicao(entidade, null);

        entidade = promocaoRepository.save(entidade);
        return converterParaDTO(entidade);
    }

    public ProdutoPromocaoDTO atualizar(Long id, ProdutoPromocaoRequest request) {
        ProdutoPromocao entidade = promocaoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Regra de promoção não encontrada: " + id));

        if (request.getProdutoId() != null && !request.getProdutoId().equals(entidade.getProduto().getId())) {
            Produto produto = produtoRepository.findById(request.getProdutoId())
                    .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + request.getProdutoId()));
            entidade.setProduto(produto);
        }

        if (request.getDiaSemana() != null) entidade.setDiaSemana(request.getDiaSemana());
        if (request.getHorarioInicio() != null) entidade.setHorarioInicio(request.getHorarioInicio());
        if (request.getHorarioFim() != null) entidade.setHorarioFim(request.getHorarioFim());
        if (request.getTipoPromocao() != null) entidade.setTipoPromocao(request.getTipoPromocao());
        if (request.getPercentualDesconto() != null) entidade.setPercentualDesconto(request.getPercentualDesconto());
        if (request.getValorPromocional() != null) entidade.setValorPromocional(request.getValorPromocional());
        if (request.getAtivo() != null) entidade.setAtivo(request.getAtivo());

        validarCamposPromocao(entidade);
        validarSobreposicao(entidade, id);

        entidade = promocaoRepository.save(entidade);
        return converterParaDTO(entidade);
    }

    @Transactional(readOnly = true)
    public List<ProdutoPromocaoDTO> listarPorProduto(Long produtoId) {
        if (!produtoRepository.existsById(produtoId)) {
            throw new EntityNotFoundException("Produto não encontrado: " + produtoId);
        }
        return promocaoRepository.findByProdutoId(produtoId).stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProdutoPromocaoDTO buscarPorId(Long id) {
        ProdutoPromocao entidade = promocaoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Regra de promoção não encontrada: " + id));
        return converterParaDTO(entidade);
    }

    public void deletar(Long id) {
        if (!promocaoRepository.existsById(id)) {
            throw new EntityNotFoundException("Regra de promoção não encontrada: " + id);
        }
        promocaoRepository.deleteById(id);
    }

    private void validarRequest(ProdutoPromocaoRequest request) {
        if (request.getProdutoId() == null) {
            throw new IllegalArgumentException("ID do produto é obrigatório");
        }
        if (request.getDiaSemana() == null) {
            throw new IllegalArgumentException("Dia da semana é obrigatório");
        }
        if (request.getHorarioInicio() == null || request.getHorarioFim() == null) {
            throw new IllegalArgumentException("Horários de início e fim são obrigatórios");
        }
        if (request.getTipoPromocao() == null) {
            throw new IllegalArgumentException("Tipo da promoção é obrigatório");
        }
        validarHorarios(request.getHorarioInicio(), request.getHorarioFim());
        validarCamposPromocao(ProdutoPromocao.builder()
                .produto(Produto.builder().id(request.getProdutoId()).build())
                .diaSemana(request.getDiaSemana())
                .horarioInicio(request.getHorarioInicio())
                .horarioFim(request.getHorarioFim())
                .tipoPromocao(request.getTipoPromocao())
                .percentualDesconto(request.getPercentualDesconto())
                .valorPromocional(request.getValorPromocional())
                .ativo(request.getAtivo() != null ? request.getAtivo() : true)
                .build());
    }

    private void validarCamposPromocao(ProdutoPromocao promocao) {
        if (promocao.getDiaSemana() == null) {
            throw new IllegalArgumentException("Dia da semana é obrigatório");
        }
        validarHorarios(promocao.getHorarioInicio(), promocao.getHorarioFim());

        if (promocao.getTipoPromocao() == null) {
            throw new IllegalArgumentException("Tipo da promoção é obrigatório");
        }

        if (promocao.getTipoPromocao() == TipoPromocao.PERCENTUAL) {
            if (promocao.getPercentualDesconto() == null) {
                throw new IllegalArgumentException("Percentual de desconto é obrigatório para promoções percentuais");
            }
            if (promocao.getPercentualDesconto().compareTo(BigDecimal.ZERO) <= 0
                    || promocao.getPercentualDesconto().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("Percentual de desconto deve ser maior que 0 e no máximo 100");
            }
            promocao.setValorPromocional(null);
        } else if (promocao.getTipoPromocao() == TipoPromocao.VALOR) {
            if (promocao.getValorPromocional() == null) {
                throw new IllegalArgumentException("Valor promocional é obrigatório para promoções por valor");
            }
            if (promocao.getValorPromocional().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Valor promocional deve ser maior que zero");
            }
            promocao.setPercentualDesconto(null);
        }
    }

    private void validarHorarios(LocalTime inicio, LocalTime fim) {
        if (inicio == null || fim == null) {
            throw new IllegalArgumentException("Horários de início e fim são obrigatórios");
        }
        if (fim.isBefore(inicio) && !fim.equals(inicio)) {
            throw new IllegalArgumentException("Horário final deve ser posterior ao horário inicial.");
        }
    }

    private void validarSobreposicao(ProdutoPromocao promocao, Long excluirId) {
        if (promocao.getProduto() == null || promocao.getDiaSemana() == null
                || promocao.getHorarioInicio() == null || promocao.getHorarioFim() == null) {
            return;
        }

        List<ProdutoPromocao> sobrepostas = promocaoRepository.findSobreposicao(
                promocao.getProduto().getId(),
                promocao.getDiaSemana(),
                promocao.getHorarioInicio(),
                promocao.getHorarioFim(),
                excluirId
        );

        if (!sobrepostas.isEmpty()) {
            throw new IllegalArgumentException(
                    "Já existe uma promoção cadastrada para este produto no dia "
                            + promocao.getDiaSemana().name()
                            + " no horário de "
                            + sobrepostas.get(0).getHorarioInicio()
                            + " às "
                            + sobrepostas.get(0).getHorarioFim()
                            + ". Ajuste o horário para não haver sobreposição."
            );
        }
    }

    private ProdutoPromocaoDTO converterParaDTO(ProdutoPromocao entidade) {
        return ProdutoPromocaoDTO.builder()
                .id(entidade.getId())
                .produtoId(entidade.getProduto().getId())
                .produtoNome(entidade.getProduto().getNome())
                .diaSemana(entidade.getDiaSemana())
                .horarioInicio(entidade.getHorarioInicio())
                .horarioFim(entidade.getHorarioFim())
                .tipoPromocao(entidade.getTipoPromocao())
                .percentualDesconto(entidade.getPercentualDesconto())
                .valorPromocional(entidade.getValorPromocional())
                .ativo(entidade.getAtivo())
                .criadoEm(entidade.getCriadoEm())
                .atualizadoEm(entidade.getAtualizadoEm())
                .build();
    }
}
