package com.baronesa.emporio.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;

/**
 * Controller responsável por fornecer opções estáticas para campos select
 */
@RestController
@RequestMapping("/api/options")
public class OptionsController {

    /**
     * Retorna as opções de roles/perfis disponíveis no sistema
     */
    @GetMapping("/roles")
    public ResponseEntity<List<Map<String, Object>>> getRoles() {
        List<Map<String, Object>> roles = List.of(
                Map.of("value", "ADMIN", "label", "Administrador"),
                Map.of("value", "FUNCIONARIO", "label", "Funcionário"),
                Map.of("value", "CLIENTE", "label", "Cliente"),
                Map.of("value", "SYSTEM", "label", "Sistema"),
                Map.of("value", "KDS", "label", "KDS"),
                Map.of("value", "WAITER", "label", "Garçom"),
                Map.of("value", "CAIXA", "label", "Caixa")
        );
        return ResponseEntity.ok(roles);
    }

    /**
     * Retorna os tipos de pessoa (PF/PJ)
     */
    @GetMapping("/tipos-pessoa")
    public ResponseEntity<List<Map<String, Object>>> getTiposPessoa() {
        List<Map<String, Object>> tipos = List.of(
                Map.of("value", "PF", "label", "Pessoa Física"),
                Map.of("value", "PJ", "label", "Pessoa Jurídica")
        );
        return ResponseEntity.ok(tipos);
    }

    /**
     * Retorna os status genéricos
     */
    @GetMapping("/status")
    public ResponseEntity<List<Map<String, Object>>> getStatus() {
        List<Map<String, Object>> status = List.of(
                Map.of("value", "ATIVO", "label", "Ativo"),
                Map.of("value", "INATIVO", "label", "Inativo"),
                Map.of("value", "PENDENTE", "label", "Pendente"),
                Map.of("value", "SUSPENSO", "label", "Suspenso")
        );
        return ResponseEntity.ok(status);
    }

    /**
     * Retorna os estados brasileiros
     */
    @GetMapping("/estados")
    public ResponseEntity<List<Map<String, Object>>> getEstados() {
        List<Map<String, Object>> estados = Arrays.asList(
                Map.of("value", "AC", "label", "Acre"),
                Map.of("value", "AL", "label", "Alagoas"),
                Map.of("value", "AP", "label", "Amapá"),
                Map.of("value", "AM", "label", "Amazonas"),
                Map.of("value", "BA", "label", "Bahia"),
                Map.of("value", "CE", "label", "Ceará"),
                Map.of("value", "DF", "label", "Distrito Federal"),
                Map.of("value", "ES", "label", "Espírito Santo"),
                Map.of("value", "GO", "label", "Goiás"),
                Map.of("value", "MA", "label", "Maranhão"),
                Map.of("value", "MT", "label", "Mato Grosso"),
                Map.of("value", "MS", "label", "Mato Grosso do Sul"),
                Map.of("value", "MG", "label", "Minas Gerais"),
                Map.of("value", "PA", "label", "Pará"),
                Map.of("value", "PB", "label", "Paraíba"),
                Map.of("value", "PR", "label", "Paraná"),
                Map.of("value", "PE", "label", "Pernambuco"),
                Map.of("value", "PI", "label", "Piauí"),
                Map.of("value", "RJ", "label", "Rio de Janeiro"),
                Map.of("value", "RN", "label", "Rio Grande do Norte"),
                Map.of("value", "RS", "label", "Rio Grande do Sul"),
                Map.of("value", "RO", "label", "Rondônia"),
                Map.of("value", "RR", "label", "Roraima"),
                Map.of("value", "SC", "label", "Santa Catarina"),
                Map.of("value", "SP", "label", "São Paulo"),
                Map.of("value", "SE", "label", "Sergipe"),
                Map.of("value", "TO", "label", "Tocantins")
        );
        return ResponseEntity.ok(estados);
    }

    /**
     * Retorna os gêneros
     */
    @GetMapping("/generos")
    public ResponseEntity<List<Map<String, Object>>> getGeneros() {
        List<Map<String, Object>> generos = List.of(
                Map.of("value", "M", "label", "Masculino"),
                Map.of("value", "F", "label", "Feminino"),
                Map.of("value", "O", "label", "Outro"),
                Map.of("value", "N", "label", "Prefiro não informar")
        );
        return ResponseEntity.ok(generos);
    }

    /**
     * Retorna os tipos de endereço
     */
    @GetMapping("/tipos-endereco")
    public ResponseEntity<List<Map<String, Object>>> getTiposEndereco() {
        List<Map<String, Object>> tipos = List.of(
                Map.of("value", "RESIDENCIAL", "label", "Residencial"),
                Map.of("value", "COMERCIAL", "label", "Comercial"),
                Map.of("value", "ENTREGA", "label", "Entrega"),
                Map.of("value", "COBRANCA", "label", "Cobrança")
        );
        return ResponseEntity.ok(tipos);
    }

    /**
     * Retorna as formas de pagamento
     */
    @GetMapping("/formas-pagamento")
    public ResponseEntity<List<Map<String, Object>>> getFormasPagamento() {
        List<Map<String, Object>> formas = List.of(
                Map.of("value", "DINHEIRO", "label", "Dinheiro"),
                Map.of("value", "CARTAO_CREDITO", "label", "Cartão de Crédito"),
                Map.of("value", "CARTAO_DEBITO", "label", "Cartão de Débito"),
                Map.of("value", "PIX", "label", "PIX"),
                Map.of("value", "BOLETO", "label", "Boleto"),
                Map.of("value", "TRANSFERENCIA", "label", "Transferência Bancária")
        );
        return ResponseEntity.ok(formas);
    }

    /**
     * Endpoint genérico para buscar opções por tipo
     * Útil para adicionar novos tipos sem criar novos endpoints
     */
    @GetMapping("/{tipo}")
    public ResponseEntity<List<Map<String, Object>>> getOptionsByType(@PathVariable String tipo) {
        switch (tipo.toLowerCase()) {
            case "sim-nao":
                return ResponseEntity.ok(List.of(
                        Map.of("value", "S", "label", "Sim"),
                        Map.of("value", "N", "label", "Não")
                ));

            case "dias-semana":
                return ResponseEntity.ok(Arrays.asList(
                        Map.of("value", "1", "label", "Segunda-feira"),
                        Map.of("value", "2", "label", "Terça-feira"),
                        Map.of("value", "3", "label", "Quarta-feira"),
                        Map.of("value", "4", "label", "Quinta-feira"),
                        Map.of("value", "5", "label", "Sexta-feira"),
                        Map.of("value", "6", "label", "Sábado"),
                        Map.of("value", "0", "label", "Domingo")
                ));

            case "meses":
                return ResponseEntity.ok(Arrays.asList(
                        Map.of("value", "1", "label", "Janeiro"),
                        Map.of("value", "2", "label", "Fevereiro"),
                        Map.of("value", "3", "label", "Março"),
                        Map.of("value", "4", "label", "Abril"),
                        Map.of("value", "5", "label", "Maio"),
                        Map.of("value", "6", "label", "Junho"),
                        Map.of("value", "7", "label", "Julho"),
                        Map.of("value", "8", "label", "Agosto"),
                        Map.of("value", "9", "label", "Setembro"),
                        Map.of("value", "10", "label", "Outubro"),
                        Map.of("value", "11", "label", "Novembro"),
                        Map.of("value", "12", "label", "Dezembro")
                ));

            default:
                return ResponseEntity.notFound().build();
        }
    }
}
