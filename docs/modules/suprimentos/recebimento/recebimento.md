# Recebimento de Mercadorias — Especificação

## Entidades

### RecebimentoMercadoria (`RecebimentoMercadoria.java`, 142 linhas)

Tabela `recebimento_mercadoria`.

| Campo | Tipo | Detalhes |
|-------|------|----------|
| `id` | `Long` | PK, `@GeneratedValue(IDENTITY)` |
| `numeroNf` | `String(20)` | Número da nota fiscal, obrigatório |
| `chaveNfe` | `String(44)` | Chave de acesso da NF-e |
| `fornecedor` | `@ManyToOne(LAZY)` → `Fornecedor` | `fornecedor_id`, obrigatório |
| `dataRecebimento` | `LocalDateTime` | Data/hora do recebimento físico, obrigatório |
| `dataEmissaoNf` | `LocalDate` | Data de emissão da NF |
| `valorTotal` | `BigDecimal(15,2)` | Default `ZERO`, recalculado em `recalcularTotais()` |
| `quantidadeItens` | `Integer` | Default `0`, recalculado |
| `status` | `StatusRecebimento` | `PENDENTE, FINALIZADO, CANCELADO` |
| `observacao` | `String` | `TEXT` |
| `xmlNfe` | `String` | `TEXT` — XML original da NF-e |
| `usuario` | `@ManyToOne(LAZY)` → `Usuario` | Responsável pelo recebimento |
| `itens` | `List<RecebimentoItem>` | `@OneToMany(cascade = ALL, orphanRemoval = true, fetch = LAZY)` |
| `createdAt` | `LocalDateTime` | `updatable = false` |
| `updatedAt` | `LocalDateTime` | |

**Métodos de guarda**:
- `podeEditar()` → `status == PENDENTE`
- `podeFinalizar()` → `status == PENDENTE && !itens.isEmpty()`
- `podeCancelar()` → `status == PENDENTE || status == FINALIZADO`
- `finalizar()` → seta `status = FINALIZADO`
- `cancelar()` → seta `status = CANCELADO`
- `recalcularTotais()` → itera itens somando `quantidade` (count) e `valorTotal`

### RecebimentoItem (`RecebimentoItem.java`, 94 linhas)

Tabela `recebimento_item`.

| Campo | Tipo | Detalhes |
|-------|------|----------|
| `id` | `Long` | PK, `@GeneratedValue(IDENTITY)` |
| `recebimento` | `@ManyToOne(LAZY)` → `RecebimentoMercadoria` | `recebimento_id`, `nullable = false`, `@JsonBackReference` |
| `produto` | `@ManyToOne(LAZY)` → `Produto` | `produto_id`, obrigatório |
| `sku` | `@ManyToOne(LAZY)` → `ProdutoSKU` | `sku_id` |
| `quantidade` | `BigDecimal(15,3)` | Obrigatório |
| `custoUnitario` | `BigDecimal(15,4)` | Obrigatório |
| `valorTotal` | `BigDecimal(15,2)` | Calculado: `quantidade * custoUnitario` |
| `lote` | `String(50)` | Lote do fabricante |
| `dataValidade` | `LocalDate` | Data de validade |
| `codigoProdutoFornecedor` | `String(50)` | Código do produto no fornecedor |
| `descricaoNfe` | `String(255)` | Descrição conforme NF-e |
| `ncm` | `String(8)` | NCM |
| `cfop` | `String(4)` | CFOP |
| `unidade` | `String(10)` | Unidade de medida na NF |

**Ciclo de vida**: `@PrePersist` e `@PreUpdate` chamam `calcularValorTotal()`.

**Métodos**:
- `isValid()` → `quantidade > 0 && custoUnitario >= 0`
- `updateFromNfe(ItemNfeDTO)` → preenche `descricaoNfe, ncm, cfop, unidade`

### StatusRecebimento (enum, 28 linhas)

| Valor | Label | Cor | Ícone | Helpers |
|-------|-------|-----|-------|---------|
| `PENDENTE` | Pendente | `warning` | `o_hourglass_empty` | `isPendente()` |
| `FINALIZADO` | Finalizado | `positive` | `o_check_circle` | `isFinalizado()` |
| `CANCELADO` | Cancelado | `negative` | `o_cancel` | `isCancelado()` |

## DTOs

| DTO | Tipo | Campos |
|-----|------|--------|
| `RecebimentoDTO` | record | `id, numeroNf, chaveNfe, fornecedorId, fornecedorNome, fornecedorCnpj, dataRecebimento, dataEmissaoNf, valorTotal, quantidadeItens, status, statusLabel, statusColor, observacao, itens (List<RecebimentoItemDTO>), podeEditar, podeFinalizar, podeCancelar` |
| `RecebimentoRequest` | record | `numeroNf, fornecedorId, dataRecebimento, dataEmissaoNf, observacao, itens (List<RecebimentoItemRequest>)` |
| `RecebimentoItemDTO` | record | `id, produtoId, skuId, embalagemId, produtoCodigo, produtoDescricao, quantidade, custoUnitario, valorTotal, lote, dataValidade, codigoProdutoFornecedor, descricaoNfe` |
| `RecebimentoItemRequest` | record | `produtoId, skuId, embalagemId, quantidade, custoUnitario, lote, dataValidade` |
| `RecebimentoListDTO` | record | `id, numeroNf, fornecedor, dataRecebimento, valorTotal, status, statusColor` |
| `RecebimentoOptionDTO` | record | `value, label` |
| `RecebimentoHojeDTO` | class | `dataVencimento, dataRecebto, nomeCliente, valor` |
| `ImportacaoNfeDTO` | record | `numeroNf, chaveNfe, dataEmissao, fornecedor (FornecedorNfeDTO), itens (List<ItemNfeDTO>), valorTotal` |
| `ImportacaoNfeResponse` | record | `success, dados (ImportacaoNfeDTO), avisos (List<String>), erros (List<String>)` |
| `ItemNfeDTO` | record | `codigo, descricao, ncm, cfop, unidade, quantidade, valorUnitario, valorTotal, produtoId, cadastrado` |

## Repositório

`RecebimentoMercadoriaRepository` (72 linhas) — estende `JpaRepository` e `JpaSpecificationExecutor`.

| Método | Descrição |
|--------|-----------|
| `findByNumeroNf(String)` | Busca por número NF |
| `findByChaveNfe(String)` | Busca por chave NF-e |
| `findByFornecedorId(Long)` | Recebimentos de um fornecedor |
| `findByStatus(StatusRecebimento)` | Por status |
| `findByDataRecebimentoBetween(LocalDateTime, LocalDateTime)` | Por período |
| `existsByNumeroNfAndFornecedorId(String, Long)` | Verifica NF duplicada por fornecedor |
| `existsByNumeroNfAndFornecedorCnpj(String, String)` | Verifica NF duplicada por CNPJ |
| `existsByFornecedorAndNumeroNf(Fornecedor, String)` | Idem (por entidade) |
| `findByIdWithFornecedor(Long)` | JPQL: JOIN FETCH fornecedor |
| `findByIdWithAllRelations(Long)` | JPQL: LEFT JOIN FETCH fornecedor, itens, produto |
| `countByStatus(StatusRecebimento)` | Total por status |
| `findPendentesAntigos(LocalDateTime)` | Pendentes criados antes da data limite |

`RecebimentoItemRepository` (38 linhas) — estende `JpaRepository`.

| Método | Descrição |
|--------|-----------|
| `findByRecebimentoId(Long)` | Itens de um recebimento |
| `findByProdutoId(Long)` | Itens de um produto |
| `findByLote(String)` | Itens por lote |
| `findByRecebimentoIdWithProduto(Long)` | JPQL: JOIN FETCH produto |
| `deleteByRecebimentoId(Long)` | Remove itens de um recebimento |
| `existsByProdutoIdInRecebimentoPendente(Long)` | Verifica se produto está em recebimento pendente (JPQL) |

## Serviço

`RecebimentoMercadoriaService` (530 linhas) — maior service do módulo.

| Método | Descrição |
|--------|-----------|
| `buscarPorId(Long)` | Retorna `RecebimentoDTO` (vazio se id=0) |
| `criar(RecebimentoRequest)` | Valida NF única, cria com status PENDENTE |
| `editar(Long, RecebimentoRequest)` | Só permite se PENDENTE |
| `deletar(Long)` | Só permite se PENDENTE |
| `finalizar(Long)` | Valida lotes/validade, cria `EstoqueLote`, gera `MovimentoEstoque` (ENTRADA), registra `MovimentoEstoqueLote`, incrementa `estoque_produto.quantidade_base` |
| `cancelar(Long)` | Se FINALIZADO: gera `MovimentoEstoque` (ESTORNO_ENTRADA), decrementa `estoque_lote.saldo` e `estoque_produto.quantidade_base`; se PENDENTE: só muda status |

### Fluxo de finalização

```
finalizar(id)
  → carrega RecebimentoMercadoria com itens
  → valida podeFinalizar() (status PENDENTE && itens não vazios)
  → para cada item:
      → valida lote e validade se produto.controlaValidade == true
      → busca ou cria EstoqueLote (produto + lote + dataValidade)
      → atualiza saldo = saldo + quantidade
      → gera MovimentoEstoque (tipo ENTRADA, origem RECEBIMENTO)
      → gera MovimentoEstoqueLote (auditoria por lote)
      → incrementa estoque_produto.quantidade_base
  → chama finalizar() na entidade (status = FINALIZADO)
  → salva
```

### Fluxo de cancelamento

```
cancelar(id)
  → carrega RecebimentoMercadoria com itens
  → valida podeCancelar()
  → if status == FINALIZADO:
      → para cada item:
          → verifica se estoque_lote.saldo >= quantidade (bloqueia se insuficiente)
          → decrementa estoque_lote.saldo
          → gera MovimentoEstoque (tipo ESTORNO_ENTRADA)
          → decrementa estoque_produto.quantidade_base
  → chama cancelar() na entidade (status = CANCELADO)
  → salva
```

## Controller

`RecebimentoMercadoriaController` (260 linhas) — `@RequestMapping("/api/recebimentos")`.

Também estende `BaseListController<RecebimentoMercadoriaListService>` e implementa `FormConfigurableController`.

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/recebimentos/list` | Lista paginada |
| `GET` | `/api/recebimentos/form-config` | Config dinâmica |
| `GET` | `/api/recebimentos/{id}` | Buscar por ID |
| `POST` | `/api/recebimentos` | Criar |
| `PUT` | `/api/recebimentos/{id}` | Atualizar |
| `DELETE` | `/api/recebimentos/{id}` | Excluir (só PENDENTE) |
| `GET` | `/api/recebimentos/verificar-nfe` | Verifica duplicidade `numeroNf + cnpj` |
| `POST` | `/api/recebimentos/{id}/finalizar` | Finalizar + entrada estoque |
| `POST` | `/api/recebimentos/{id}/cancelar` | Cancelar + estorno |
| `POST` | `/api/recebimentos/parse-nfe` | Importar XML NF-e (multipart) |
| `GET` | `/api/recebimentos/options` | Dropdown |
| `GET` | `/api/recebimentos/produtos/search` | Busca de produtos (stub) |
| `GET` | `/api/recebimentos/fornecedores/search` | Busca de fornecedores (stub) |

### ListService

`RecebimentoMercadoriaListService` (76 linhas) — estende `BaseListService<RecebimentoMercadoria>`.
Mapeia: `id, numeroNf, fornecedor, dataRecebimento, dataEmissaoNf, valorTotal, quantidadeItens, status`.
Adiciona metadados frontend: `_statusColor, _statusLabel, _statusIcon, _podeEditar, _podeFinalizar, _podeCancelar`.

## Frontend

### Componentes

| Componente | Linhas | Descrição |
|-----------|--------|-----------|
| `RecebimentoForm.vue` | 820 | Dialog "Entrada de Mercadoria" / "Editar Recebimento". Campos: NF, importar NF-e, fornecedor (lookup modal), tabela de produtos com quantidade/custo/total |
| `RecebimentoItensTab.vue` | 302 | Tabela de itens: produto (lookup), SKU, embalagem, quantidade, custo unitário, total, lote, validade. Usa `LookupSelect`, `SkuLookupSelect`, `DateInput` |
| `RecebimentosTab.vue` | 716 | Geração de parcelas financeiras. Grid: vencimento, valor, data recebimento, forma. Badges: Recebido, Aguardando, Vencido, Em aberto. Resumo: total parcelas, valor total, valor pendente |
| `ImportNFeDialog.vue` | (existente) | Dialog para upload de XML NF-e, parse e preenchimento automático do formulário |

## Regras de negócio

1. **NF duplicada**: o par `(numeroNf, fornecedor)` deve ser único — verificação via `existsByNumeroNfAndFornecedorId` no backend e endpoint `verificar-nfe` no frontend
2. **Lote e validade**: produtos com flag `controlaValidade = true` exigem lote e data de validade em cada item recebido
3. **Entrada em estoque** na finalização: cria/atualiza `estoque_lote` (rastreabilidade por lote), gera `movimento_estoque` (tipo ENTRADA, origem RECEBIMENTO) e incrementa `estoque_produto.quantidade_base`
4. **Estorno no cancelamento**: se já finalizado, reverte cada item (decrementa lote e produto), gera `movimento_estoque` tipo ESTORNO\_ENTRADA. Bloqueia se saldo do lote for insuficiente
5. **Imutabilidade pós-finalização**: itens não podem ser alterados após finalização; apenas cancelamento total é permitido
6. **Importação NF-e**: upload do XML preenche automaticamente dados da NF, fornecedor (por CNPJ) e itens (NCM, CFOP, unidade, descrição)
