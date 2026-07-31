package com.baronesa.emporio.controller;

import com.baronesa.emporio.service.PermissaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/menu")
public class MenuController {
    private static final Logger logger = LoggerFactory.getLogger(MenuController.class);

    private final PermissaoService permissaoService;

    @Autowired
    public MenuController(PermissaoService permissaoService) {
        this.permissaoService = permissaoService;
    }

    @PostMapping("/cache/invalidate")
    public ResponseEntity<Map<String, String>> invalidateCache() {
        logger.info("Solicitação de invalidação do cache de permissões");
        permissaoService.invalidateCache();
        return ResponseEntity.ok(Map.of("message", "Cache de permissões invalidado com sucesso"));
    }

    @GetMapping("/{id_grupo}")
    public ResponseEntity<List<Map<String, Object>>> getMenu(@PathVariable String id_grupo) {
        logger.info("Iniciando montagem do menu para o grupo: {}", id_grupo);
        try {
            List<Map<String, Object>> menu = new ArrayList<>();

            // Converter string para Long, tratando casos especiais
            Long grupoId = null;
            boolean isRootUser = false;

            if (id_grupo == null || id_grupo.equals("null") || id_grupo.equals("0") || id_grupo.trim().isEmpty()) {
                isRootUser = true;
                grupoId = null;
                logger.info("Usuário ROOT detectado (id_grupo='{}'), concedendo acesso total", id_grupo);
            } else {
                try {
                    grupoId = Long.parseLong(id_grupo);
                    isRootUser = (grupoId == 0);
                } catch (NumberFormatException e) {
                    logger.warn("ID de grupo inválido: '{}', tratando como usuário ROOT", id_grupo);
                    isRootUser = true;
                    grupoId = null;
                }
            }

            // ——— Seção: Painel de Controle ———
            List<Map<String, Object>> itemsPainelControle = List.of(
                    createItem("Painel de Controle", "configuracoes", "o_settings", false, false),
                    createItem("Agenda de Execução", "agenda-execucao", "o_schedule", false, false)
            );
            menu.add(createSection("Painel de Controle", itemsPainelControle));

            // ——— Seção: Cadastros ———
            List<Map<String, Object>> itemsCadastros = new ArrayList<>();

            if (hasPermissionOrRoot(isRootUser, grupoId, "usuarios")) {
                itemsCadastros.add(createItem("Usuários", "usuarios-admin", "o_account_circle", null, false));
                itemsCadastros.add(createItem("Grupos de Usuários", "grupos-usuario", "o_groups_3", null, false));
            }

            if (hasPermissionOrRoot(isRootUser, grupoId, "clientes")) {
                itemsCadastros.add(createItem("Clientes", "clientes", "o_person", null, false));
                itemsCadastros.add(createItem("Grupos de Clientes", "grupos-clientes", "o_groups", null, false));
            }

            if (!itemsCadastros.isEmpty()) {
                menu.add(createSection("Cadastros", itemsCadastros));
            }

            // ——— Seção: Produtos ———
            List<Map<String, Object>> itemsProdutos = new ArrayList<>();

            if (hasPermissionOrRoot(isRootUser, grupoId, "produtos")) {
                itemsProdutos.add(createItem("Categorias", "categorias", "o_grid_on", null, false));
                itemsProdutos.add(createItem("Subcategorias", "subcategorias", "o_warehouse", null, false));
                itemsProdutos.add(createItem("Fornecedores", "fornecedores", "o_local_shipping", null, false));
                itemsProdutos.add(createItem("Produtos", "produtos", "o_inventory_2", null, false));
                // Módulo sem formulário dinâmico: Pedidos de Compra
                itemsProdutos.add(createItem("Pedidos de Compra", "pedidos-compra", "o_shopping_cart", null, false));
                itemsProdutos.add(createItem("Produção", "producao", "o_precision_manufacturing", null, false));
                itemsProdutos.add(createItem("Recebimento de Mercadoria", "recebimentos", "o_input", null, false));
                itemsProdutos.add(createItem("Movimento de Estoque", "movimento-estoque", "o_swap_horiz", null, false));
            }

            if (!itemsProdutos.isEmpty()) {
                menu.add(createSection("Produtos", itemsProdutos));
            }

            // ——— Seção: Operacional ———
            List<Map<String, Object>> itemsOperacional = new ArrayList<>();

            if (hasPermissionOrRoot(isRootUser, grupoId, "mesas")) {
                itemsOperacional.add(createItem("Mesas", "mesas", "o_table_restaurant", null, false));
            }

            if (!itemsOperacional.isEmpty()) {
                menu.add(createSection("Operacional", itemsOperacional));
            }

            // ——— Seção: Financeiro ———
            List<Map<String, Object>> itemsFinanceiro = new ArrayList<>();

            if (hasPermissionOrRoot(isRootUser, grupoId, "financeiro")) {
                itemsFinanceiro.add(createItem("Categorias de Despesa", "categorias-despesa", "o_category", null, false));
                itemsFinanceiro.add(createItem("Tipos de Receita", "tipos-receita", "o_attach_money", null, false));
                itemsFinanceiro.add(createItem("Contas a Pagar", "contas-pagar", "o_payments", null, false));
            }

            if (hasPermissionOrRoot(isRootUser, grupoId, "contas_receber")) {
                itemsFinanceiro.add(createItem("Contas a Receber", "contas-receber", "o_attach_money", null, false));
            }

            if (hasPermissionOrRoot(isRootUser, grupoId, "movimento_caixa")) {
                itemsFinanceiro.add(createItem("Movimento de Caixa", "movimento-caixa", "o_account_balance_wallet", null, false));
            }

            // Vendas (lista de pagamentos efetivados)
            if (hasPermissionOrRoot(isRootUser, grupoId, "vendas")) {
                itemsFinanceiro.add(createItem("Vendas", "vendas", "o_point_of_sale", null, false));
            }

            if (!itemsFinanceiro.isEmpty()) {
                menu.add(createSection("Financeiro", itemsFinanceiro));
            }

            logger.info("Menu montado para o grupo {}: {}", grupoId, menu);
            return ResponseEntity.ok(menu);
        } catch (Exception e) {
            logger.error("Erro ao montar menu para o grupo {}: {}", id_grupo, e.getMessage(), e);
            // Em caso de erro, devolve lista vazia com 200 OK
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    /**
     * Cria o mapa de uma seção (title + items).
     */
    private Map<String, Object> createSection(String title, List<Map<String, Object>> items) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("title", title);
        section.put("items", items);
        return section;
    }

    /**
     * Cria um item de menu.
     *
     * @param title  texto exibido
     * @param route  rota associada
     * @param icon   nome do ícone
     * @param menu   valor do campo "menu" (pode ser null)
     * @param ativo  valor do campo "ativo"
     */
    private Map<String, Object> createItem(String title,
                                           String route,
                                           String icon,
                                           Boolean menu,
                                           Boolean ativo) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", title);
        item.put("route", route);
        item.put("icon", icon);
        if (menu != null) {
            item.put("menu", menu);
        }
        item.put("ativo", ativo);
        return item;
    }

    /**
     * Verifica se o usuário tem permissão, considerando usuário ROOT com acesso total
     */
    private boolean hasPermissionOrRoot(boolean isRootUser, Long grupoId, String permissao) {
        return isRootUser || permissaoService.hasPermission(grupoId, permissao);
    }
}
