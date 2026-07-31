# Ficha Técnica — Especificação do Domínio

## Definição

Composição de produtos acabados por meio de ingredientes/insumos. A ficha técnica (receita) define o que é necessário para produzir cada item: quais insumos, em que quantidade, em que ordem e com que observações. É a fonte do cálculo de custo do produto e a base para a baixa de insumos tanto na produção própria quanto na venda.

## Escopo

**Inclui:**
- Cadastro de ficha técnica vinculada a um produto (relação 1:1)
- Itens de insumo por SKU, com quantidade (DECIMAL 10,3), ordem de exibição e observação
- Cálculo automático de custo unitário por item (preçoCusto × quantidade)
- Cálculo de custo total da ficha (somatório dos itens)
- Rendimento (porções que a receita produz)
- Atualização do preço de custo do produto baseado na ficha
- Busca de insumos disponíveis (apenas produtos marcados como insumo)
- Substituição completa de itens ao salvar (sem merge parcial)

**Não inclui:**
- Modo de preparo detalhado (apenas observações textuais por item e gerais)
- Versões ou revisões de ficha (apenas a versão corrente)
- Histórico de alterações da ficha

## Modelo de dados

### FichaTecnica

Tabela `ficha_tecnica`.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | BIGSERIAL PK | Identificador |
| `produto_id` | BIGINT FK UNIQUE | Produto acabado |
| `custo_total` | DECIMAL(10,2) | Custo total calculado |
| `rendimento` | INTEGER | Porções produzidas (default 1) |
| `observacoes` | TEXT | Anotações da receita |
| `criado_em` | TIMESTAMP | — |
| `atualizado_em` | TIMESTAMP | — |

FK: `produto_id` → `produto(id)`.

### FichaTecnicaItem

Tabela `ficha_tecnica_item`.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | BIGSERIAL PK | Identificador |
| `ficha_tecnica_id` | BIGINT FK | Ficha técnica |
| `insumo_sku_id` | BIGINT FK | SKU do insumo |
| `quantidade` | DECIMAL(10,3) | Quantidade necessária |
| `ordem` | INTEGER | Ordem de exibição |
| `observacao` | TEXT | Instrução específica |

FKs: `ficha_tecnica_id` → `ficha_tecnica(id)`, `insumo_sku_id` → `produto_sku(id)`.

## Endpoints

| Método | Path | Resumo |
|--------|------|--------|
| `GET` | `/api/ficha-tecnica/produto/{produtoId}` | Buscar ficha (ou DTO vazio se não existir) |
| `POST` | `/api/ficha-tecnica` | Salvar ficha (cria ou atualiza) |
| `GET` | `/api/ficha-tecnica/produto/{produtoId}/custo` | Calcular custo total |
| `GET` | `/api/ficha-tecnica/buscar-insumos?search=` | Buscar insumos para composição |

### Request (POST)

```json
{
  "produtoId": 1,
  "rendimento": 1,
  "observacoes": "Receita tradicional",
  "itens": [
    {
      "insumoSkuId": 10,
      "quantidade": 50.000,
      "ordem": 1,
      "observacao": "Bem gelada"
    }
  ]
}
```

### Response (GET /produto/{id})

```json
{
  "id": 1,
  "produtoId": 1,
  "produtoNome": "Caipirinha",
  "custoTotal": 3.50,
  "rendimento": 1,
  "observacoes": "Receita tradicional",
  "itens": [
    {
      "id": 1,
      "insumoSkuId": 10,
      "insumoSkuCodigo": "CAC-500",
      "insumoProdutoId": 5,
      "insumoProdutoNome": "Cachaça",
      "insumoVariacao": "500ml",
      "embalagemId": 2,
      "embalagemNome": "Garrafa",
      "fatorBase": 500,
      "quantidade": 50.000,
      "custoUnitario": 0.05,
      "custoTotal": 2.50,
      "ordem": 1,
      "observacao": null,
      "estoqueDisponivel": 1000
    }
  ],
  "criadoEm": "2025-01-01T10:00:00",
  "atualizadoEm": "2025-01-01T10:00:00"
}
```

## Regras

- **1:1 com produto** — cada produto pode ter no máximo uma ficha técnica. A constraint `UNIQUE` em `produto_id` é enforced no banco.
- **Substituição completa** — ao salvar, `ficha.getItens().clear()` + `addAll(novos)`. Não há merge.
- **Cálculo de custo** — cada item calcula `custo = precoCusto × quantidade`. O `precoCusto` vem do SKU; se o SKU não tiver, usa o `precoCusto` do produto; se nenhum existir, assume zero.
- **Atualização do produto** — ao salvar a ficha, `produto.precoCusto = ficha.custoTotal` e `produto.temFichaTecnica = true`.
- **Apenas insumos** — a busca filtra SKUs de produtos com `insumo=true`. Produtos acabados não entram como ingredientes.
- **Flag `temFichaTecnica`** — usada pelo `PedidoService.processarBaixaInsumos` para decidir se deve consumir insumos na venda.

## Serviços

### FichaTecnicaService

| Método | Descrição |
|--------|-----------|
| `buscarPorProduto(Long)` | Retorna ficha ou DTO vazio (para formulário de criação) |
| `salvar(FichaTecnicaRequest)` | Cria/atualiza ficha, limpa e reinsere itens, recalcula custo, persiste, atualiza produto |
| `calcularCusto(Long)` | Soma dos custos dos itens |
| `buscarInsumosDisponiveis(String)` | SKUs ativos de insumos, filtro por nome/SKU/código/variacão, limit 50 |

### FichaTecnicaRepository

| Método | Descrição |
|--------|-----------|
| `findByProdutoId(Long)` | Ficha sem itens |
| `findByProdutoIdWithItens(Long)` | Ficha com itens, SKUs, produtos e embalagens eagerly |
| `existsByProdutoId(Long)` | Se produto tem ficha |

### FichaTecnicaItemRepository

| Método | Descrição |
|--------|-----------|
| `findByFichaTecnicaIdOrderByOrdemAsc(Long)` | Itens ordenados |
| `deleteByFichaTecnicaId(Long)` | Limpeza de itens |

## Frontend

| Componente | Descrição |
|-----------|-----------|
| `ProdutoFichaTecnicaTab.vue` | Aba no formulário do produto: tabela editável de ingredientes com busca de insumo por SKU, campos de quantidade/ordem/observação, exibição de custo unitário, custo total e estoque disponível |

## Decisões de domínio

- **Rendimento informacional** — o campo `rendimento` existe na entidade, mas o sistema não valida quantidade produzida vs. rendimento. A produção sempre produz 1 unidade.
- **Sem suporte a sub-receitas** — um item de ficha técnica não pode referenciar outra ficha técnica. Todos os ingredientes são SKUs de insumo atômicos.
- **Substituição atômica** — a estratégia de salvar (clear + insert) simplifica a lógica e evita inconsistências de itens órfãos, mas exige que o frontend envie o conjunto completo de itens a cada salvamento.

## Status de implementação

**IMPLEMENTADO**. Cadastro completo com busca de insumos, cálculo de custo, atualização do preço de custo do produto e integração com produção e venda.
