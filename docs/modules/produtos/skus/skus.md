# SKUs — Especificação do Domínio

## Definição

Variação identificável de um produto. Permite que o mesmo produto seja operado em apresentações, variações, embalagens ou condições comerciais distintas. Ex: um drink pode ter SKUs de 300ml e 500ml; um insumo pode ter SKUs de embalagem "Garrafa 990ml" e "Dose 30ml".

## Escopo

**Inclui:**
- Identificador único de SKU (gerado automaticamente)
- Variação nominal (ex: "355ml", "Garrafa", "Dose 30ml")
- Código de barras por variação
- Preço de custo e venda por SKU
- Vinculação de SKU a embalagem (com fator de conversão)
- Definição de SKU principal
- Ativação e desativação de variações
- Autocomplete e busca paginada de SKUs

**Não inclui:**
- Cadastro raiz do produto (pertence a `produtos/`)
- Ficha técnica (pertence a `producao/ficha-tecnica/`)
- Estoque por SKU (pertence a `estoque/`)

## Modelo de dados

### ProdutoSKU

Tabela `produto_sku`.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | BIGSERIAL PK | Identificador |
| `produto_id` | BIGINT FK | Produto pai |
| `sku` | VARCHAR(50) UNIQUE | Código SKU único (auto-gerado) |
| `variacao` | VARCHAR(100) | Nome da variação (ex: "355ml") |
| `codigoBarras` | VARCHAR(50) | Código de barras específico |
| `precoCusto` | DECIMAL(10,2) | Preço de custo do SKU |
| `precoVenda` | DECIMAL(10,2) | Preço de venda do SKU |
| `embalagem_id` | BIGINT FK | Embalagem associada |
| `ativo` | BOOLEAN | DEFAULT TRUE |
| `principal` | BOOLEAN | SKU principal do produto |

FK: `produto_id` → `produto(id)`, `embalagem_id` → `embalagem(id)`.

### Embalagem

Tabela `embalagem`.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | BIGSERIAL PK | Identificador |
| `produto_id` | BIGINT FK | Produto dono da embalagem |
| `nome` | VARCHAR(50) | Nome (ex: "Garrafa", "Dose") |
| `fatorBase` | INTEGER | Quantidade em unidade base (ex: 990 ml) |
| `codigoBarras` | VARCHAR(50) | Código de barras |
| `permiteVenda` | BOOLEAN | Pode ser vendida avulsa |
| `principal` | BOOLEAN | Embalagem padrão |
| `ativo` | BOOLEAN | Soft delete |

FK: `produto_id` → `produto(id)`.

## Comportamento por modelo de produto

No estoque, dois modelos coexistem:

| Modelo | Flag | Comportamento do SKU |
|--------|------|----------------------|
| Vendável | `vendavel=true, insumo=false` | SKU tem estoque próprio (tabela `estoque`) |
| Insumo | `insumo=true` | SKU é apresentação derivada do estoque centralizado (`estoque_produto.quantidade_base`). O saldo disponível é `quantidade_base / fatorBase` |

## Endpoints

### SKUs

| Método | Path | Resumo |
|--------|------|--------|
| `GET` | `/api/skus/options` | Options para select |
| `GET` | `/api/skus/search-options?search=&page=&size=` | Autocomplete paginado |
| `GET` | `/api/skus/{id}` | Buscar SKU por ID |
| `PUT` | `/api/skus/{id}/embalagem` | Atualizar embalagem do SKU |

### Embalagens

| Método | Path | Resumo |
|--------|------|--------|
| `GET` | `/api/embalagens?produtoId=` | Listar embalagens do produto |
| `GET` | `/api/embalagens/by-sku/{skuId}` | Buscar por SKU |
| `GET` | `/api/embalagens/{id}` | Buscar por ID |
| `POST` | `/api/embalagens` | Criar embalagem |
| `PUT` | `/api/embalagens/{id}` | Atualizar embalagem |
| `DELETE` | `/api/embalagens/{id}` | Remover embalagem |

## Serviços

### ProdutoSKUService

| Método | Descrição |
|--------|-----------|
| `listarOptions()` | Lista de opções para selects |
| `buscarOptions(String)` | Busca por termo |
| `buscarOptionsPaginado(String, Pageable)` | Autocomplete com paginação |
| `buscarPorId(Long)` | SKU completo |
| `atualizarEmbalagem(Long, AtualizarEmbalagemSKURequest)` | Troca embalagem do SKU |

### EmbalagemService

| Método | Descrição |
|--------|-----------|
| `listarPorProduto(Long)` | Embalagens de um produto |
| `buscarPorId(Long)` | — |
| `criar(EmbalagemRequest)` | Criar com validações |
| `atualizar(Long, EmbalagemRequest)` | — |
| `deletar(Long)` | — |

## Frontend

| Componente | Descrição |
|-----------|-----------|
| `ProdutoVariacoesTab.vue` | Aba no formulário do produto: tabela com edição inline de variação, código SKU, código de barras, preços e embalagem |

## Decisões de domínio

- **SKU auto-gerado** — o campo `sku` é gerado automaticamente (método `gerarSKU()`), não editado manualmente.
- **Embalagem compartilhada** — uma embalagem pertence a um produto, mas pode ser atribuída a múltiplos SKUs do mesmo produto.
- **Fator de conversão** — o `fatorBase` na embalagem define a equivalência em unidade base (ml, g). Crítico para insumos onde o estoque é centralizado.
- **Preço por SKU** — o tipo de precificação do produto (SIMPLES, UNIFICADA, INDIVIDUAL) determina se o `precoVenda` do SKU é usado ou ignorado.
- **SKU como chave de integração** — o SKU é a unidade usada por todos os subsistemas (estoque, vendas, produção, suprimentos).

## Status de implementação

**IMPLEMENTADO**. Cadastro de SKUs e embalagens completo. Gaps conhecidos: regras de precificação detalhadas por SKU.
