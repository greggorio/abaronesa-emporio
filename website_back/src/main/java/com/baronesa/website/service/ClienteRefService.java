package com.baronesa.website.service;

import com.baronesa.website.dto.ClienteRefSyncRequest;
import com.baronesa.website.entity.ClienteRef;
import com.baronesa.website.repository.ClienteRefRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClienteRefService {

    private final ClienteRefRepository clienteRefRepository;

    @Transactional
    public ClienteRef upsert(Long id, String nome, String email) {
        ClienteRef cliente = clienteRefRepository.findById(id).orElse(new ClienteRef());
        cliente.setId(id);
        cliente.setNome(nome);
        cliente.setEmail(email);
        return clienteRefRepository.save(cliente);
    }

    @Transactional
    public ClienteRef syncFromErp(ClienteRefSyncRequest request) {
        ClienteRef cliente = clienteRefRepository.findById(request.id()).orElse(new ClienteRef());

        // Campos obrigatórios
        cliente.setId(request.id());
        cliente.setNome(request.nome());
        cliente.setEmail(request.email());

        // Campos opcionais
        cliente.setTelefone(request.telefone());
        cliente.setCpf(request.cpf());
        cliente.setDataNascimento(request.dataNascimento());
        cliente.setAtivo(request.ativo());
        cliente.setErpUpdatedAt(request.erpUpdatedAt());

        // Atualizar updatedAt sempre
        cliente.setUpdatedAt(LocalDateTime.now());

        // createdAt só é definido se for novo registro
        if (cliente.getCreatedAt() == null) {
            cliente.setCreatedAt(LocalDateTime.now());
        }

        return clienteRefRepository.save(cliente);
    }

    @Transactional(readOnly = true)
    public List<ClienteRef> search(String query) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        return clienteRefRepository.searchByNameOrEmailLimited(query);
    }

    @Transactional(readOnly = true)
    public ClienteRef findById(Long id) {
        return clienteRefRepository.findById(id).orElse(null);
    }
}