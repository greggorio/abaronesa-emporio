package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.FornecedorDTO;
import com.baronesa.emporio.dto.FornecedorOptionDTO;
import com.baronesa.emporio.dto.FornecedorRequest;
import com.baronesa.emporio.entity.Fornecedor;
import com.baronesa.emporio.repository.FornecedorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FornecedorService {

    private final FornecedorRepository fornecedorRepository;

    @Transactional(readOnly = true)
    public List<FornecedorDTO> listarTodos() {
        return fornecedorRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FornecedorDTO> listarAtivos() {
        return fornecedorRepository.findByAtivoTrue().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public FornecedorDTO buscarPorId(Long id) {
        return fornecedorRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Fornecedor não encontrado"));
    }

    @Transactional
    public FornecedorDTO criar(FornecedorRequest request) {
        if (request.cnpj() != null && !request.cnpj().trim().isEmpty()) {
            fornecedorRepository.findByCnpj(request.cnpj())
                    .ifPresent(f -> {
                        throw new RuntimeException("CNPJ já cadastrado");
                    });
        }

        Fornecedor fornecedor = Fornecedor.builder()
                .razaoSocial(request.razaoSocial())
                .nomeFantasia(request.nomeFantasia())
                .cnpj(request.cnpj())
                .telefone(request.telefone())
                .email(request.email())
                .contato(request.contato())
                .endereco(request.endereco())
                .cidade(request.cidade())
                .estado(request.estado())
                .cep(request.cep())
                .ativo(request.ativo() != null ? request.ativo() : true)
                .build();

        fornecedor = fornecedorRepository.save(fornecedor);
        log.info("Fornecedor criado: {}", fornecedor.getId());
        return toDTO(fornecedor);
    }

    @Transactional
    public FornecedorDTO editar(Long id, FornecedorRequest request) {
        Fornecedor fornecedor = fornecedorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fornecedor não encontrado"));

        // Validar CNPJ único se foi alterado
        if (request.cnpj() != null && !request.cnpj().equals(fornecedor.getCnpj())) {
            if (fornecedorRepository.existsByCnpjAndIdNot(request.cnpj(), id)) {
                throw new RuntimeException("CNPJ já cadastrado para outro fornecedor");
            }
        }

        fornecedor.setRazaoSocial(request.razaoSocial());
        fornecedor.setNomeFantasia(request.nomeFantasia());
        fornecedor.setCnpj(request.cnpj());
        fornecedor.setTelefone(request.telefone());
        fornecedor.setEmail(request.email());
        fornecedor.setContato(request.contato());
        fornecedor.setEndereco(request.endereco());
        fornecedor.setCidade(request.cidade());
        fornecedor.setEstado(request.estado());
        fornecedor.setCep(request.cep());
        if (request.ativo() != null) {
            fornecedor.setAtivo(request.ativo());
        }

        fornecedor = fornecedorRepository.save(fornecedor);
        log.info("Fornecedor atualizado: {}", fornecedor.getId());
        return toDTO(fornecedor);
    }

    @Transactional
    public void deletar(Long id) {
        if (!fornecedorRepository.existsById(id)) {
            throw new RuntimeException("Fornecedor não encontrado");
        }
        fornecedorRepository.deleteById(id);
        log.info("Fornecedor deletado: {}", id);
    }

    @Transactional(readOnly = true)
    public Optional<Fornecedor> buscarPorCnpj(String cnpj) {
        return fornecedorRepository.findByCnpj(cnpj);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> buscarParaLookup(String search) {
        List<Fornecedor> fornecedores;

        if (search == null || search.trim().isEmpty()) {
            // Se não há busca, retornar os primeiros 50 fornecedores ativos
            fornecedores = fornecedorRepository.findByAtivoTrue(
                    PageRequest.of(0, 50, Sort.by("razaoSocial"))
            ).getContent();
        } else {
            // Buscar por código, razão social ou CNPJ
            fornecedores = fornecedorRepository.buscarPorCodigoRazaoOuCnpj(search);

            // Limitar a 50 resultados
            if (fornecedores.size() > 50) {
                fornecedores = fornecedores.subList(0, 50);
            }
        }

        return fornecedores.stream()
                .map(f -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", f.getId());
                    item.put("value", f.getId());
                    item.put("label", f.getRazaoSocial() + " - " + formatarCnpj(f.getCnpj()));
                    item.put("codigo", f.getId());
                    item.put("razaoSocial", f.getRazaoSocial());
                    item.put("nomeFantasia", f.getNomeFantasia());
                    item.put("cnpj", formatarCnpj(f.getCnpj()));
                    item.put("ativo", true);

                    // Informações adicionais úteis
                    if (f.getTelefone() != null) {
                        item.put("telefone", f.getTelefone());
                    }
                    if (f.getEmail() != null) {
                        item.put("email", f.getEmail());
                    }

                    return item;
                })
                .collect(Collectors.toList());
    }

    private String formatarCnpj(String cnpj) {
        if (cnpj == null || cnpj.length() != 14) {
            return cnpj;
        }
        return cnpj.substring(0, 2) + "." +
                cnpj.substring(2, 5) + "." +
                cnpj.substring(5, 8) + "/" +
                cnpj.substring(8, 12) + "-" +
                cnpj.substring(12, 14);
    }

    @Transactional(readOnly = true)
    public List<FornecedorOptionDTO> listarOptions() {
        return fornecedorRepository.findAllAtivosOrdenados().stream()
                .map(f -> new FornecedorOptionDTO(
                        f.getId(),
                        f.getNomeExibicao(),
                        f.getCnpj()
                ))
                .toList();
    }

    private FornecedorDTO toDTO(Fornecedor fornecedor) {
        return new FornecedorDTO(
                fornecedor.getId(),
                fornecedor.getRazaoSocial(),
                fornecedor.getNomeFantasia(),
                fornecedor.getCnpj(),
                fornecedor.getTelefone(),
                fornecedor.getEmail(),
                fornecedor.getContato(),
                fornecedor.getEndereco(),
                fornecedor.getCidade(),
                fornecedor.getEstado(),
                fornecedor.getCep(),
                fornecedor.getAtivo(),
                fornecedor.getNomeExibicao()
        );
    }
}