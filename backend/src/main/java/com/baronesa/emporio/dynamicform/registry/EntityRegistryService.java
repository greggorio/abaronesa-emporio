package com.baronesa.emporio.dynamicform.registry;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class EntityRegistryService {

    private final Map<String, Class<?>> entityMap = new HashMap<>();

    @PostConstruct
    public void init() {
        // Registre aqui manualmente os mapeamentos entre entityType e classes reais
        entityMap.put("grupos-usuario", com.baronesa.emporio.entity.GrupoUsuario.class);
        //entityMap.put("usuarios-admin", com.baronesa.emporio.entity.Usuario.class);

        // Categorias e Subcategorias
        entityMap.put("categorias", com.baronesa.emporio.entity.Categoria.class);
        entityMap.put("subcategorias", com.baronesa.emporio.entity.Subcategoria.class);

        // Fornecedores
        entityMap.put("fornecedores", com.baronesa.emporio.entity.Fornecedor.class);

        // Produtos: usar DTO dedicado ao FormBuilder para expor campos extras com segurança
        entityMap.put("produtos", com.baronesa.emporio.dto.formbuilder.ProdutoFormFields.class);

        // Grupos de Clientes
        entityMap.put("grupos-clientes", com.baronesa.emporio.entity.GrupoCliente.class);

        // Clientes (Usuario com role CLIENTE) - DTO plano para expor campos do perfil
        entityMap.put("clientes", com.baronesa.emporio.dto.formbuilder.ClienteFormFields.class);

        // Funcionários (Usuario com role FUNCIONARIO) - DTO plano para expor campos do perfil
        entityMap.put("usuarios-admin", com.baronesa.emporio.dto.formbuilder.FuncionarioFormFields.class);

        // Categorias de Despesa
        entityMap.put("categorias-despesa", com.baronesa.emporio.entity.CategoriaDespesa.class);

        // Tipos de Receita
        entityMap.put("tipos-receita", com.baronesa.emporio.entity.TipoReceita.class);

        // Contas a Pagar - usando DTO do form-builder para incluir campos derivados
        entityMap.put("contas-pagar", com.baronesa.emporio.dto.formbuilder.ContaPagarFormBuilderDTO.class);

        // Contas a Receber
        entityMap.put("contas-receber", com.baronesa.emporio.dto.ContaReceberListDTO.class);

        // Mesas
        entityMap.put("mesas", com.baronesa.emporio.entity.Mesa.class);

        // Movimentos de Caixa
        entityMap.put("movimento-caixa", com.baronesa.emporio.entity.MovimentoCaixa.class);

        // Movimento de Estoque
        entityMap.put("movimento-estoque", com.baronesa.emporio.entity.MovimentoEstoque.class);

        // Recebimento de Mercadoria
        entityMap.put("recebimentos", com.baronesa.emporio.entity.RecebimentoMercadoria.class);

        // Vendas (Pagamentos efetivados) — mapear para DTO usado no form-builder
        entityMap.put("vendas", com.baronesa.emporio.dto.formbuilder.VendaFormRequest.class);

        // TODO: Adicionar mapeamentos conforme novas entidades forem criadas
        // etc...

        log.info("EntityRegistryService inicializado com {} mapeamentos", entityMap.size());
    }

    public Class<?> resolveEntityClass(String entityType) {
        Class<?> entityClass = entityMap.get(entityType);

        if (entityClass == null) {
            log.warn("Nenhum mapeamento encontrado para entityType: {}", entityType);
        }

        return entityClass;
    }

    public Map<String, Class<?>> getAllEntities() {
        return new HashMap<>(entityMap); // Retorna uma cópia para evitar modificações externas
    }

    /**
     * Verifica se existe mapeamento para o entityType
     */
    public boolean hasMapping(String entityType) {
        return entityMap.containsKey(entityType);
    }

    /**
     * Adiciona um novo mapeamento dinamicamente
     * Útil para testes ou configurações runtime
     */
    public void addMapping(String entityType, Class<?> entityClass) {
        entityMap.put(entityType, entityClass);
        log.info("Novo mapeamento adicionado: {} -> {}", entityType, entityClass.getSimpleName());
    }
}
