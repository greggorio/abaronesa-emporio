package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.ProdutoHarmonizacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProdutoHarmonizacaoRepository extends JpaRepository<ProdutoHarmonizacao, Long> {
    List<ProdutoHarmonizacao> findByProdutoPrincipalId(Long produtoPrincipalId);
    Optional<ProdutoHarmonizacao> findByProdutoPrincipalIdAndProdutoHarmonizadoId(Long produtoPrincipalId, Long produtoHarmonizadoId);
    List<ProdutoHarmonizacao> findByProdutoPrincipalIdIn(Collection<Long> produtoPrincipalIds);
}
