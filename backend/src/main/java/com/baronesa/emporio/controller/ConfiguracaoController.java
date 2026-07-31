package com.baronesa.emporio.controller;

import com.baronesa.emporio.entity.Configuracao;
import com.baronesa.emporio.repository.ConfiguracaoRepository;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/configs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
public class ConfiguracaoController {

    private final ConfiguracaoRepository configuracaoRepository;
    private final ConfigManager configManager;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Configuracao adicionar(@RequestBody Configuracao configuracao) {
        return configuracaoRepository.save(configuracao);
    }

    @GetMapping
    public List<Configuracao> listar() {
        return configuracaoRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Configuracao> findById(@PathVariable Long id) {
        return configuracaoRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/config/{configName}")
    public ResponseEntity<String> getConfigByName(@PathVariable String configName) {
        try {
            String configValue = configuracaoRepository.findValorByChave(configName);
            if (configValue != null && !configValue.isEmpty()) {
                return ResponseEntity.ok(configValue);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Configuracao> update(@PathVariable("id") Long id, @RequestBody Configuracao configuracao) {
        return configuracaoRepository.findById(id)
                .map(record -> {
                    record.setChave(configuracao.getChave());
                    record.setValor(configuracao.getValor());
                    record.setNome(configuracao.getNome());
                    record.setDescricao(configuracao.getDescricao());
                    Configuracao updated = configuracaoRepository.save(record);
                    // Limpar cache para a chave alterada
                    configManager.clearCacheForKey(updated.getChave());
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return configuracaoRepository.findById(id)
                .map(record -> {
                    configuracaoRepository.deleteById(id);
                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/config/{configName}")
    public ResponseEntity<?> updateByKey(@PathVariable("configName") String configName,
                                         @RequestBody Map<String, String> payload) {
        try {
            String valor = payload != null ? payload.get("valor") : null;
            if (valor == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Campo 'valor' obrigatório"
                ));
            }

            Configuracao configuracao = configuracaoRepository.findByChave(configName).orElseGet(() -> {
                return Configuracao.builder()
                        .chave(configName)
                        .nome(configName.replace("_", " "))
                        .descricao("Configuração criada via endpoint por chave")
                        .build();
            });

            configuracao.setValor(valor);
            Configuracao saved = configuracaoRepository.save(configuracao);
            // Limpar cache para a chave alterada
            configManager.clearCacheForKey(configName);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", saved
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
