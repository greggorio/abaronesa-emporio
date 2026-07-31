package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.GrupoClienteDescontoDTO;
import com.baronesa.emporio.dto.GrupoClienteDescontoRequest;
import com.baronesa.emporio.entity.Categoria;
import com.baronesa.emporio.entity.GrupoCliente;
import com.baronesa.emporio.entity.GrupoClienteDesconto;
import com.baronesa.emporio.entity.Subcategoria;
import com.baronesa.emporio.repository.CategoriaRepository;
import com.baronesa.emporio.repository.GrupoClienteDescontoRepository;
import com.baronesa.emporio.repository.GrupoClienteRepository;
import com.baronesa.emporio.repository.SubcategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GrupoClienteDescontoService {

    private final GrupoClienteDescontoRepository descontoRepository;
    private final GrupoClienteRepository grupoClienteRepository;
    private final CategoriaRepository categoriaRepository;
    private final SubcategoriaRepository subcategoriaRepository;

    public List<GrupoClienteDescontoDTO> listar(Long grupoClienteId) {
        return descontoRepository.findByGrupoClienteIdAndAtivoTrue(grupoClienteId).stream()
                .map(this::toDTO)
                .toList();
    }

    public List<GrupoClienteDescontoDTO> salvar(Long grupoClienteId, List<GrupoClienteDescontoRequest> requests) {
        GrupoCliente grupoCliente = grupoClienteRepository.findById(grupoClienteId)
                .orElseThrow(() -> new IllegalArgumentException("Grupo de cliente não encontrado"));

        List<GrupoClienteDescontoRequest> safeRequests = requests == null ? List.of() : requests;
        Map<DescontoKey, GrupoClienteDescontoRequest> incoming = new LinkedHashMap<>();
        Map<Long, Categoria> categorias = new HashMap<>();
        Map<Long, Subcategoria> subcategorias = new HashMap<>();

        for (GrupoClienteDescontoRequest request : safeRequests) {
            DescontoKey key = new DescontoKey(request.categoriaId(), request.subcategoriaId());
            if (incoming.containsKey(key)) {
                throw new IllegalArgumentException("Desconto duplicado para a mesma categoria/subcategoria");
            }
            validarRelacionamento(request, categorias, subcategorias);
            incoming.put(key, request);
        }

        List<GrupoClienteDesconto> existentes = descontoRepository.findByGrupoClienteIdAndAtivoTrue(grupoClienteId);
        Map<DescontoKey, GrupoClienteDesconto> existentesMap = new HashMap<>();
        for (GrupoClienteDesconto existente : existentes) {
            existentesMap.put(new DescontoKey(
                    existente.getCategoria().getId(),
                    existente.getSubcategoria() != null ? existente.getSubcategoria().getId() : null
            ), existente);
        }

        List<GrupoClienteDesconto> paraExcluir = new ArrayList<>();
        for (GrupoClienteDesconto existente : existentes) {
            DescontoKey key = new DescontoKey(
                    existente.getCategoria().getId(),
                    existente.getSubcategoria() != null ? existente.getSubcategoria().getId() : null
            );
            if (!incoming.containsKey(key)) {
                paraExcluir.add(existente);
            }
        }

        if (!paraExcluir.isEmpty()) {
            descontoRepository.deleteAll(paraExcluir);
        }

        List<GrupoClienteDesconto> paraSalvar = new ArrayList<>();
        for (Map.Entry<DescontoKey, GrupoClienteDescontoRequest> entry : incoming.entrySet()) {
            GrupoClienteDescontoRequest request = entry.getValue();
            GrupoClienteDesconto existente = existentesMap.get(entry.getKey());
            if (existente == null) {
                Categoria categoria = categorias.computeIfAbsent(request.categoriaId(), this::buscarCategoria);
                Subcategoria subcategoria = null;
                if (request.subcategoriaId() != null) {
                    subcategoria = subcategorias.computeIfAbsent(request.subcategoriaId(), this::buscarSubcategoria);
                }
                existente = GrupoClienteDesconto.builder()
                        .grupoCliente(grupoCliente)
                        .categoria(categoria)
                        .subcategoria(subcategoria)
                        .descontoPercentual(request.descontoPercentual())
                        .ativo(true)
                        .build();
            } else {
                existente.setDescontoPercentual(request.descontoPercentual());
                existente.setAtivo(true);
            }
            paraSalvar.add(existente);
        }

        if (!paraSalvar.isEmpty()) {
            descontoRepository.saveAll(paraSalvar);
        }

        return descontoRepository.findByGrupoClienteIdAndAtivoTrue(grupoClienteId).stream()
                .map(this::toDTO)
                .toList();
    }

    private void validarRelacionamento(
            GrupoClienteDescontoRequest request,
            Map<Long, Categoria> categorias,
            Map<Long, Subcategoria> subcategorias
    ) {
        Categoria categoria = categorias.computeIfAbsent(request.categoriaId(), this::buscarCategoria);
        if (request.subcategoriaId() == null) {
            return;
        }
        Subcategoria subcategoria = subcategorias.computeIfAbsent(request.subcategoriaId(), this::buscarSubcategoria);
        if (!subcategoria.getCategoria().getId().equals(categoria.getId())) {
            throw new IllegalArgumentException("Subcategoria não pertence à categoria informada");
        }
    }

    private Categoria buscarCategoria(Long categoriaId) {
        return categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada"));
    }

    private Subcategoria buscarSubcategoria(Long subcategoriaId) {
        return subcategoriaRepository.findById(subcategoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Subcategoria não encontrada"));
    }

    private GrupoClienteDescontoDTO toDTO(GrupoClienteDesconto desconto) {
        return new GrupoClienteDescontoDTO(
                desconto.getId(),
                desconto.getGrupoCliente().getId(),
                desconto.getCategoria().getId(),
                desconto.getSubcategoria() != null ? desconto.getSubcategoria().getId() : null,
                desconto.getDescontoPercentual(),
                desconto.getAtivo()
        );
    }

    private record DescontoKey(Long categoriaId, Long subcategoriaId) {}
}
