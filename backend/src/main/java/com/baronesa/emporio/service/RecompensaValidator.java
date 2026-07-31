package com.baronesa.emporio.service;

import com.baronesa.emporio.entity.Recompensa;
import com.baronesa.emporio.entity.TipoRecompensa;
import com.baronesa.emporio.exception.BusinessException;

import java.math.BigDecimal;

public class RecompensaValidator {

    public static void validate(Recompensa recompensa) {
        if (recompensa == null) {
            throw new BusinessException("Recompensa não fornecida");
        }

        if (recompensa.getPontosNecessarios() == null || recompensa.getPontosNecessarios() <= 0) {
            throw new BusinessException("Pontos necessários deve ser maior que zero");
        }

        if (recompensa.getAtivo() == null) {
            throw new BusinessException("Flag 'ativo' é obrigatória");
        }

        if (recompensa.getEstoque() != null && recompensa.getEstoque() < 0) {
            throw new BusinessException("Estoque não pode ser negativo");
        }

        if (recompensa.getValidadeInicio() != null && recompensa.getValidadeFim() != null &&
                recompensa.getValidadeInicio().isAfter(recompensa.getValidadeFim())) {
            throw new BusinessException("Validade início não pode ser posterior à validade fim");
        }

        TipoRecompensa tipo = recompensa.getTipo();
        if (tipo == null) {
            throw new BusinessException("Tipo de recompensa é obrigatório");
        }

        switch (tipo) {
            case PRODUTO -> validateProduto(recompensa);
            case DESCONTO_PERCENTUAL -> validateDescontoPercentual(recompensa);
            case DESCONTO_VALOR -> validateDescontoValor(recompensa);
            case BRINDE_GENERICO -> validateBrinde(recompensa);
            default -> throw new BusinessException("Tipo de recompensa desconhecido");
        }
    }

    private static void validateProduto(Recompensa recompensa) {
        if (recompensa.getProdutoId() == null) {
            throw new BusinessException("Produto obrigatório para recompensas do tipo PRODUTO");
        }
        ensureDescontosNull(recompensa, "Produto");
    }

    private static void validateDescontoPercentual(Recompensa recompensa) {
        if (recompensa.getDescontoPercentual() == null) {
            throw new BusinessException("Desconto percentual obrigatório para o tipo DESCONTO_PERCENTUAL");
        }
        BigDecimal percentual = recompensa.getDescontoPercentual();
        if (percentual.compareTo(BigDecimal.ZERO) <= 0 || percentual.compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessException("Desconto percentual deve ser maior que 0 e menor ou igual a 100");
        }
        if (recompensa.getDescontoValor() != null) {
            throw new BusinessException("Desconto em valor não deve ser informado para desconto percentual");
        }
        if (recompensa.getDescontoValorMaximo() != null && recompensa.getDescontoValorMaximo().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Valor máximo do desconto deve ser maior ou igual a zero");
        }
        if (recompensa.getProdutoId() != null) {
            throw new BusinessException("Produto não deve ser informado para desconto percentual");
        }
    }

    private static void validateDescontoValor(Recompensa recompensa) {
        if (recompensa.getDescontoValor() == null) {
            throw new BusinessException("Desconto em valor obrigatório para o tipo DESCONTO_VALOR");
        }
        if (recompensa.getDescontoValor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Desconto em valor deve ser maior que zero");
        }
        if (recompensa.getDescontoPercentual() != null) {
            throw new BusinessException("Desconto percentual não deve ser informado para desconto em valor");
        }
        if (recompensa.getDescontoValorMaximo() != null) {
            throw new BusinessException("Valor máximo não é utilizado com desconto em valor");
        }
        if (recompensa.getProdutoId() != null) {
            throw new BusinessException("Produto não deve ser informado para desconto em valor");
        }
    }

    private static void validateBrinde(Recompensa recompensa) {
        ensureDescontosNull(recompensa, "Brinde");
        if (recompensa.getProdutoId() != null) {
            throw new BusinessException("Produto não deve ser informado para brindes genéricos");
        }
    }

    private static void ensureDescontosNull(Recompensa recompensa, String context) {
        if (recompensa.getDescontoPercentual() != null) {
            throw new BusinessException(context + ": percentual deve estar vazio");
        }
        if (recompensa.getDescontoValor() != null) {
            throw new BusinessException(context + ": valor deve estar vazio");
        }
        if (recompensa.getDescontoValorMaximo() != null) {
            throw new BusinessException(context + ": valor máximo deve estar vazio");
        }
    }
}
