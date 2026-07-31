# Produtos — Especificação do Domínio

## Definição

Módulo central que representa os itens vendidos, consumidos como insumo, compostos em ficha técnica ou expostos em canais digitais. O produto é a entidade raiz que conecta cadastro, classificação comercial, parametrização operacional, composição técnica, variações, promoções e exposição digital.

## Escopo

**Inclui:**
- Cadastro base do produto (nome, descrição, código interno, código de barras, código do fornecedor)
- Tipo de produto (CERVEJA, CHOPP, DRINK, DOSE, VINHO, PRATO, PETISCO, PORÇÃO, SOBREMESA, COMBO, etc.)
- Categoria e subcategoria
- Flags operacionais: vendável, insumo, controla estoque, controla validade, produção própria, exibe no cardápio
- Preço de custo e venda, tipo de precificação (SIMPLES, UNIFICADA, INDIVIDUAL)
- SKUs e variações
- Embalagens com fator de conversão
- Imagens e galeria (mídias)
- Informação fiscal (NCM, CEST, CFOP, CST, alíquotas)
- Unidade de medida e unidade base para estoque
- Local de preparação (BAR, COZINHA)

**Não inclui:**
- Governança completa de estoque (pertence a `estoque/`)
- Recebimento de mercadoria (pertence a `suprimentos/`)
- Pedidos de compra (pertence a `suprimentos/`)
- Execução operacional de produção (pertence a `producao/`)
- Fidelização e recompensas (pertence a `fidelizacao/`)
- Fluxo transacional completo de pedidos digitais (pertence a `consumo-digital/`)

## Modelo de dados

### Produto

Tabela `produto` — entidade raiz.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | BIGSERIAL PK | Identificador |
| `nome` | VARCHAR(120) | Nome do produto |
| `descricao` | TEXT | Descrição detalhada |
| `setor` | VARCHAR(50) | Setor do estabelecimento |
| `codigoInterno` | VARCHAR(50) | Código interno do produto |
| `codigoBarras` | VARCHAR(50) | Código de barras principal |
| `codigoFornecedor` | VARCHAR(50) | Código do fornecedor |
| `tipo` | VARCHAR(20) | TipoProduto: CERVEJA, CHOPP, DRINK, DOSE, VINHO, REFRIGERANTE, SUCO, AGUA, CAFE, PRATO, PETISCO, PORCAO, LANCHE, SOBREMESA, COMBO, CIGARRO, OUTRO |
| `categoria_id` | BIGINT FK | Categoria |
| `subcategoria_id` | BIGINT FK | Subcategoria |
| `fornecedor_id` | BIGINT FK | Fornecedor padrão |
| `precoCusto` | DECIMAL(10,2) | Preço de custo |
| `precoVenda` | DECIMAL(10,2) | Preço de venda |
| `tipoPrecificacao` | VARCHAR(15) | SIMPLES, UNIFICADA, INDIVIDUAL |
| `controlaEstoque` | BOOLEAN | DEFAULT FALSE |
| `controlaValidade` | BOOLEAN | DEFAULT FALSE |
| `vidaUtilDias` | INTEGER | Dias de vida útil |
| `vendavel` | BOOLEAN | Pode ser vendido |
| `insumo` | BOOLEAN | Usado como ingrediente |
| `temFichaTecnica` | BOOLEAN | Possui composição |
| `producaoPropria` | BOOLEAN | Fabricado internamente |
| `exibirNoCardapio` | BOOLEAN | Visível no cardápio digital |
| `promocao` | BOOLEAN | Está em promoção |
| `destaque` | BOOLEAN | Produto em destaque |
| `necessitaPreparacao` | BOOLEAN | Requer preparo (cozinha/bar) |
| `localPreparacao` | VARCHAR(10) | BAR, COZINHA |
| `unidadeMedida` | VARCHAR(10) | UN, L, ML, KG, G, DOSE, GARRAFA, LATA, CX, PCT, PORCAO |
| `unidadeBase` | VARCHAR(10) | UNIDADE, MILILITRO, GRAMA |
| `ordem` | INTEGER | Ordem de exibição |
| `imagemPrincipal` | VARCHAR(500) | URL da imagem principal |
| `ativo` | BOOLEAN | Soft delete |

FKs: `categoria_id` → `categoria(id)`, `subcategoria_id` → `subcategoria(id)`, `fornecedor_id` → `fornecedor(id)`.

### Categoria

Tabela `categoria`. Campos: `id`, `nome`, `icone`, `cover`, `exibirNoCardapio`, `ordem`. Relacionamento OneToMany com `Subcategoria`.

### Subcategoria

Tabela `subcategoria`. Campos: `id`, `nome`, `categoria_id` (FK), `cover`.

### ProdutoMidia

Tabela `produto_midia`. Campos: `id`, `produto_id` (FK), `tipo` (IMAGEM, VIDEO), `url`, `titulo`, `descricao`, `ordem`, `principal`, `ativo`.

### ProdutoFiscal

Tabela `produto_fiscal`. Campos: `id`, `produto_id` (FK 1:1), `ncm`, `cest`, `origem`, `cfop`, `cstIcms`, `csosn`, `aliquotaIcms`, `aliquotaIcmsST`, `mva`, `cstPis`, `cstCofins`, `aliquotaPis`, `aliquotaCofins`, `cstIpi`, `aliquotaIpi`, `aliquotaFcp`, `aliquotaFcpST`.

## Enums

**TipoProduto:** `CERVEJA`, `CHOPP`, `DRINK`, `DOSE`, `VINHO`, `REFRIGERANTE`, `SUCO`, `AGUA`, `CAFE`, `PRATO`, `PETISCO`, `PORCAO`, `LANCHE`, `SOBREMESA`, `COMBO`, `CIGARRO`, `OUTRO`.

**TipoPrecificacao:** `SIMPLES` (preço único), `UNIFICADA` (preço único mesmo com múltiplos SKUs), `INDIVIDUAL` (cada SKU tem seu preço).

**UnidadeMedida:** `UN`, `L`, `ML`, `KG`, `G`, `DOSE`, `GARRAFA`, `LATA`, `CX`, `PCT`, `PORCAO`.

**UnidadeBase:** `UNIDADE`, `MILILITRO`, `GRAMA`.

**LocalPreparacao:** `BAR`, `COZINHA`.

## Flags de comportamento

| Flag | Default | Impacto |
|------|---------|---------|
| `vendavel` | false | Produto pode ser vendido |
| `insumo` | false | Produto pode ser ingrediente de ficha técnica |
| `controlaEstoque` | true | Produto tem saldo em estoque |
| `controlaValidade` | true | Produto tem controle de lote e validade |
| `temFichaTecnica` | false | Produto possui composição (receita) |
| `producaoPropria` | false | Produto é fabricado internamente |
| `exibirNoCardapio` | false | Produto aparece no cardápio digital |
| `necessitaPreparacao` | false | Produto precisa ser preparado (bar/cozinha) |

## Endpoints

### Produtos

| Método | Path | Auth | Resumo |
|--------|------|------|--------|
| `GET` | `/api/produtos` | — | Listar produtos |
| `POST` | `/api/produtos` | ADMIN | Criar produto |
| `GET` | `/api/produtos/{id}` | — | Buscar por ID |
| `PUT` | `/api/produtos/{id}` | ADMIN | Atualizar produto |
| `DELETE` | `/api/produtos/{id}` | ADMIN | Soft delete |
| `GET` | `/api/produtos/options` | — | Options para select |
| `GET` | `/api/produtos?search=` | — | Busca por termo |
| `GET` | `/api/produtos?categoriaId=` | — | Filtrar por categoria |
| `GET` | `/api/produtos?subcategoriaId=` | — | Filtrar por subcategoria |
| `GET` | `/api/produtos?fornecedorId=` | — | Filtrar por fornecedor |

### Categorias

| Método | Path | Resumo |
|--------|------|--------|
| `GET` | `/api/categorias` | Listar categorias |
| `POST` | `/api/categorias` | Criar categoria |
| `PUT` | `/api/categorias/{id}` | Atualizar categoria |
| `DELETE` | `/api/categorias/{id}` | Remover categoria |

### Subcategorias

| Método | Path | Resumo |
|--------|------|--------|
| `GET` | `/api/subcategorias` | Listar subcategorias |
| `POST` | `/api/subcategorias` | Criar subcategoria |
| `PUT` | `/api/subcategorias/{id}` | Atualizar subcategoria |
| `DELETE` | `/api/subcategorias/{id}` | Remover subcategoria |

## Serviços

### ProdutoService

~1700 linhas. Principais métodos:

| Método | Descrição |
|--------|-----------|
| `criar(ProdutoRequest)` | Cria produto com validações, inicializa estoque se controlaEstoque=true |
| `atualizar(Long, ProdutoRequest)` | Atualiza dados, gerencia flags, SKUs, mídias |
| `buscarPorId(Long)` | Produto completo com relações |
| `deletar(Long)` | Soft delete (ativo=false) |
| `search(String)` | Busca textual com JPASpecificationExecutor |
| `salvarImagem(Long, MultipartFile)` | Upload de imagem principal |
| `atualizarSignage(Long, ProdutoSignageRequest)` | Configura signage do produto |
| `carregarSignagePreview(Long)` | Preview do signage |
| `renderSignageVideo(Long, ProdutoSignageRenderRequest)` | Aciona renderização de vídeo |

## Integrações

| Módulo | Natureza |
|--------|----------|
| `estoque/` | Leitura/escrita: saldos, movimentos por SKU |
| `producao/` | Leitura: flags temFichaTecnica, producaoPropria; escrita: ficha técnica |
| `vendas/` | Leitura: preços, promoções, disponibilidade |
| `consumo-digital/` | Leitura: cardápio, preços, imagens, disponibilidade |
| `clientes/` | Leitura: preço com desconto de grupo |
| `suprimentos/` | Leitura: produtos para pedidos de compra |

## Decisões de domínio

- **Flags como comportamento** — flags booleanas no produto (vendavel, insumo, controlaEstoque, etc.) determinam como cada subsistema trata o produto. Não há herança de tipos.
- **Preço de custo atualizado pela ficha técnica** — quando uma ficha técnica é salva em `producao/ficha-tecnica/`, o `precoCusto` do produto é atualizado automaticamente.
- **SKU sempre vinculado a um produto** — não existe SKU órfão. A relação é ManyToOne.
- **Estoque inicializado no create** — se `controlaEstoque=true`, um registro `Estoque` com quantidade 0 é criado automaticamente ao criar o produto.
- **Imagens por mídia** — produtos podem ter múltiplas imagens via `ProdutoMidia`, com flag `principal` para a imagem de exibição padrão.
- **Categoria com exibição em cardápio** — categorias têm flag `exibirNoCardapio` para controle de visibilidade no cardápio digital.
- **Soft delete** — produtos são inativados via `ativo=false`, nunca removidos fisicamente.

## Status de implementação

**ESTÁVEL**. Domínio maduro com múltiplos sub-domínios bem implementados.
