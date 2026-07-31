package com.baronesa.emporio.controller;

import com.baronesa.emporio.entity.Permissoes;
import com.baronesa.emporio.entity.PermissoesGrupos;
import com.baronesa.emporio.repository.PermissoesRepositorio;
import com.baronesa.emporio.repository.PermissoesGruposRepositorio;
import com.baronesa.emporio.service.PermissaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/permissoes")
public class PermissoesController {

    @Autowired
    private PermissoesRepositorio permissoesRepositorio;

    @Autowired
    private PermissoesGruposRepositorio permissoesGruposRepositorio;

    @Autowired
    private PermissaoService permissaoService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Permissoes adicionar(@RequestBody Permissoes permissoes) {
        return permissoesRepositorio.save(permissoes);
    }

    @GetMapping
    public List<Permissoes> listar() {
        return permissoesRepositorio.findAll();
    }

    @GetMapping(path = {"/{id}"})
    public ResponseEntity<Permissoes> findById(@PathVariable Long id){
        return permissoesRepositorio.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping(path ={"/{id}"})
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return permissoesRepositorio.findById(id)
                .map(record -> {
                    permissoesRepositorio.deleteById(id);
                    return ResponseEntity.ok().build();
                }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(path = {"/setarpermissao/{permissao}/{valor}/{id_grupo}"})
    public ResponseEntity<Void> setarPermissao(
            @PathVariable String permissao,
            @PathVariable boolean valor,
            @PathVariable Long id_grupo) {
        if (valor) {
            // Adiciona permissão ao grupo se não existir
            Optional<PermissoesGrupos> existente = permissoesGruposRepositorio.findByIdGrupoAndPermissao(id_grupo, permissao);
            if (existente.isEmpty()) {
                PermissoesGrupos pg = new PermissoesGrupos();
                pg.setIdGrupo(id_grupo);
                pg.setPermissao(permissao);
                permissoesGruposRepositorio.save(pg);
            }
        } else {
            // Remove permissão do grupo
            permissoesGruposRepositorio.deleteByIdGrupoAndPermissao(id_grupo, permissao);
        }

        // Invalidar cache após modificar permissões
        permissaoService.invalidateCache();

        return ResponseEntity.ok().build();
    }

    @GetMapping(path = {"/permissaoporgrupo/{id_grupo}"})
    public List<Map<String, Object>> getGruposPermissoes(@PathVariable("id_grupo") Long id_grupo) {
        List<Permissoes> permissoesList = permissoesRepositorio.findAllDistinct();
        List<PermissoesGrupos> permissoesGrupos = permissoesGruposRepositorio.findByIdGrupo(id_grupo);
        Set<String> permissoesDoGrupo = new HashSet<>();
        for (PermissoesGrupos pg : permissoesGrupos) {
            permissoesDoGrupo.add(pg.getPermissao());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Permissoes p : permissoesList) {
            Map<String, Object> map = new HashMap<>();
            map.put("has_permissao", permissoesDoGrupo.contains(p.getPermissao()));
            map.put("permissao", p.getPermissao());
            map.put("descricao", p.getDescricao());
            result.add(map);
        }
        return result;
    }

    @GetMapping(path = {"/haspermissao/{id_grupo}/{permissao}"})
    public boolean hasPermission(@PathVariable Long id_grupo, @PathVariable String permissao) {
        return permissoesGruposRepositorio.findByIdGrupoAndPermissao(id_grupo, permissao).isPresent();
    }

}
