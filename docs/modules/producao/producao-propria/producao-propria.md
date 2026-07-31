# Produção Própria — Especificação do Domínio

## Definição

Registro da fabricação de itens elaborados no estabelecimento (drinks, pratos, sobremesas). O ato de produzir consome automaticamente os insumos da ficha técnica (baixa no estoque) e entrada do produto acabado (também no estoque). Conecta a ficha técnica (composição) com a movimentação de estoque.

## Escopo

**Inclui:**
- Registro de produção de 1 unidade por vez
- Consumo automático de insumos via ficha técnica (movimento CONSUMO_PRODUCAO)
- Entrada do produto produzido em estoque (movimento PRODUCAO)
- Validação de pré-condições: produto configurado como produção própria, com ficha técnica, que controla estoque
- Resolução automática de SKU (único) ou exigência de seleção (múltiplos)
- Exibição dos movimentos de estoque gerados

**Não inclui:**
- Produção em lote (múltiplas unidades)
- Planejamento de produção (MRP)
- Apontamento de mão de obra
- Controle de qualidade
- KDS (Kitchen Display System)

## Endpoints

| Método | Path | Resumo |
|--------|------|--------|
| `POST` | `/api/producao` | Registrar produção |

### Request

```json
{
  "produtoId": 1,
  "skuId": null,
  "observacao": "Produção do dia 01/12"
}
```

### Response

```json
{
  "success": true,
  "data": {
    "produtoId": 1,
    "skuId": 5,
    "quantidade": 1,
    "movimentoProduto": {
      "id": 100,
      "skuId": 5,
      "tipoMovimento": "PRODUCAO",
      "quantidade": 1,
      ...
    },
    "movimentosInsumos": [
      {
        "id": 99,
        "skuId": 10,
        "tipoMovimento": "CONSUMO_PRODUCAO",
        "quantidade": 50.000,
        ...
      }
    ]
  },
  "message": "Produção registrada com sucesso"
}
```

## Fluxo de produção

```
1. Usuário acessa /producao
2. Seleciona um produto (LookupSelect)
3. Opcionalmente seleciona SKU
4. Sistema valida:
   ├── produto.producaoPropria = true?
   ├── produto.temFichaTecnica = true?
   ├── produto.controlaEstoque = true?
   └── ficha técnica tem itens?
5. Exibe tabela da ficha técnica para conferência
6. Usuário confirma
7. Sistema executa (transação):
   ├── Para cada ingrediente da ficha:
   │   └── MovimentoEstoqueService.movimentar(CONSUMO_PRODUCAO)
   └── MovimentoEstoqueService.movimentar(PRODUCAO, skuProduzido, quantidade=1)
8. Retorna movimentos gerados
```

## Regras

### Pré-condições (validadas em ProducaoService.produzir)

| Condição | Validação |
|----------|-----------|
| Produto informado | `request.produtoId != null` |
| Produto existe | `produtoRepository.findById()` |
| É produção própria | `produto.producaoPropria == true` |
| Tem ficha técnica | `produto.temFichaTecnica == true` |
| Controla estoque | `produto.controlaEstoque == true` |
| Ficha técnica tem itens | `ficha.itens != null && !ficha.itens.isEmpty()` |

### Resolução de SKU

- Se `request.skuId` foi informado: valida que pertence ao produto
- Se não foi informado e produto tem **apenas 1** SKU: usa automaticamente
- Se não foi informado e produto tem **múltiplos** SKUs: erro — obrigatório informar

### Movimentos de estoque gerados

1. **CONSUMO_PRODUCAO** (tipo 11) — para cada item da ficha técnica:
   - `skuId` = `ingrediente.insumoSku.id`
   - `quantidade` = `ingrediente.quantidade`
   - `documentoReferencia` = `"PRODUCAO PRODUTO #" + produto.id`
   - `observacao` = `"Consumo produção: " + produto.nome`

2. **PRODUCAO** (tipo 12) — para o produto acabado:
   - `skuId` = SKU resolvido do produto
   - `quantidade` = 1
   - `documentoReferencia` = `"PRODUCAO PRODUTO #" + produto.id`
   - `observacao` = observação informada ou `"Produção própria: " + produto.nome`

## Serviços

### ProducaoService

| Método | Descrição |
|--------|-----------|
| `produzir(ProducaoRequest)` | Valida pré-condições, resolve SKU, itera ficha técnica, cria movimentos de estoque |

Dependências:
- `ProdutoRepository` — busca e validação do produto
- `ProdutoSKURepository` — resolução e validação de SKU
- `FichaTecnicaRepository` — busca da ficha com itens
- `MovimentoEstoqueService` — criação dos movimentos de estoque

## Frontend

| Componente | Rota | Descrição |
|-----------|------|-----------|
| `ProducaoPage.vue` | `/producao` | LookupSelect de produto + SKU opcional, tabela de conferência da ficha técnica, botão de produzir, exibição de resultado |

## Integrações

| Módulo | Natureza |
|--------|----------|
| `produtos/` | Leitura: flags `producaoPropria`, `temFichaTecnica`, `controlaEstoque`; resolução de SKU |
| `ficha-tecnica/` | Leitura: composição do produto para consumo de insumos |
| `estoque/` | Escrita: movimentos CONSUMO_PRODUCAO e PRODUCAO via MovimentoEstoqueService |

## Decisões de domínio

- **Quantidade fixa de 1** — a produção sempre produz exatamente 1 unidade. Não há suporte a produção em lote ou quantidade variável na versão atual.
- **DocumentoReferencia como rastreio** — todos os movimentos gerados por uma produção compartilham o mesmo `documentoReferencia = "PRODUCAO PRODUTO #" + id`, permitindo rastrear o conjunto completo de movimentos de uma produção.
- **Baixa de insumos independente de estoque negativo** — o sistema não valida saldo de insumos antes de consumir; a validação é feita pelo `MovimentoEstoqueService` no momento da movimentação.
- **Sem rollback por insumo insuficiente** — se um dos movimentos de CONSUMO_PRODUCAO falhar (ex: estoque insuficiente), a transação `@Transactional` reverte todo o lote, incluindo a entrada do produto produzido.
- **i18n** — a mensagem de sucesso é resolvida por `MessageResolver` e traduzida para pt, en, es, fr.

## Status de implementação

**IMPLEMENTADO**. Registro de produção com consumo de insumos e entrada em estoque operacional. Gap: produção em lote (múltiplas unidades) não está no escopo atual.
