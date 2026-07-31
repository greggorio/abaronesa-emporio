# Harmonização — Especificação do Domínio

## Definição

Vínculo entre produtos que harmonizam entre si (ex: vinho + comida, café + doce), usado para sugerir combinações ao cliente no cardápio digital. A harmonização pode ser entre produtos (ex: vinho tinto + filé) ou entre produto e SKU específico (ex: vinho + SKU específico de sobremesa).

## Escopo

**Inclui:**
- Cadastro de harmonizações entre produtos
- Vínculo opcional a SKU específico do produto harmonizado
- Tipo de harmonização (COMPLEMENTAR, CONTRASTE, SEMELHANCA)
- Descrição da harmonização
- Ordem de exibição
- Prevenção de auto-harmonização e duplicidade

**Não inclui:**
- Cadastro raiz do produto (pertence a `produtos/`)
- Regras automáticas de sugestão (apenas vínculo manual)

## Modelo de dados

### ProdutoHarmonizacao

Tabela `produto_harmonizacao`.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | BIGSERIAL PK | Identificador |
| `produto_principal_id` | BIGINT FK | Produto de origem |
| `produto_harmonizado_id` | BIGINT FK | Produto que harmoniza |
| `sku_harmonizado_id` | BIGINT FK | SKU específico (opcional) |
| `tipo` | VARCHAR(20) | Tipo de harmonização |
| `descricao` | TEXT | Descrição da sugestão |
| `ordem` | INTEGER | Ordem de exibição |

FKs: `produto_principal_id` → `produto(id)`, `produto_harmonizado_id` → `produto(id)`, `sku_harmonizado_id` → `produto_sku(id)`.

## Regras

- **Auto-harmonização proibida** — um produto não pode harmonizar com ele mesmo
- **Duplicidade impedida** — não pode haver duas harmonizações com o mesmo par (produto_principal, produto_harmonizado)
- **SKU opcional** — quando informado, o SKU deve pertencer ao produto_harmonizado

## Endpoints

| Método | Path | Resumo |
|--------|------|--------|
| `GET` | `/api/produtos/{produtoPrincipalId}/harmonizacoes` | Listar harmonizações de um produto |
| `POST` | `/api/produtos/{produtoPrincipalId}/harmonizacoes` | Criar harmonização |
| `DELETE` | `/api/produtos/{produtoPrincipalId}/harmonizacoes/{harmonizacaoId}` | Remover harmonização |

## Serviços

### ProdutoHarmonizacaoService

| Método | Descrição |
|--------|-----------|
| `criarHarmonizacao(Long, ProdutoHarmonizacaoDTO)` | Valida auto-harmonização, duplicidade e SKU; persiste |
| `listarHarmonizacoes(Long)` | Lista com dados completos dos produtos harmonizados |
| `removerHarmonizacao(Long, Long)` | Remove vínculo |

## Frontend

| Componente | Descrição |
|-----------|-----------|
| `ProdutoHarmonizacaoTab.vue` | Aba no formulário do produto: cards de harmonizações com busca de produto, tipo, descrição e ordem (drag-and-drop) |

## Decisões de domínio

- **Vínculo manual** — harmonizações são cadastradas manualmente pelo admin. Não há sugestão automática.
- **SKU específico opcional** — sem SKU, a harmonização vale para qualquer variação do produto; com SKU, é específica.
- **N:M entre produtos** — um produto pode harmonizar com vários outros; um produto pode ser harmonizado por vários outros.
- **Ordem editável** — a ordem de exibição é configurável por drag-and-drop no frontend.

## Status de implementação

**IMPLEMENTADO**. Cadastro de harmonizações com validações e exibição em cardápio digital.
