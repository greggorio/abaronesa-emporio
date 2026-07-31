# Validade — Especificação

## Configuração de produto

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `produto.controla_validade` | BOOLEAN | Habilita sub-ledger de lotes e FEFO automático |
| `produto.vida_util_dias` | INTEGER | Vida útil total (base para cálculo de alertas) |

Configurado na aba de validade do cadastro de produto (`ProdutoValidadeTab.vue`, hospedada em `produtos/`).

## Status de alerta por lote

Calculado dinamicamente a partir de `data_validade` e `vida_util_dias`:

| Status | Condição |
|--------|----------|
| `VENCIDO` | `data_validade < hoje` |
| `CRITICO` | Dias restantes ≤ ~20% da vida útil |
| `ATENCAO` | Dias restantes ≤ ~40% da vida útil |
| `OK` | Dias restantes > limiar |
| `SEM_VIDA_UTIL` | `vida_util_dias` não configurado |
| `SEM_DATA_VALIDADE` | Lote sem data de vencimento |

## Endpoints de alertas e dashboard

| Método | Rota | Parâmetros | Dados retornados |
|--------|------|-----------|-----------------|
| `GET` | `/api/validade/alertas` | `somenteComSaldo`, `skuId`, `produtoId` | Lista de lotes por status de alerta |
| `GET` | `/api/validade/dashboard` | `somenteComSaldo` | Contadores por status |
| `GET` | `/api/validade/produtos/{produtoId}/lotes` | `skuId`, `status` | Lotes do produto com filtros |

## Gestão manual de lotes

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/validade/produtos/{produtoId}/lotes` | Criar lote (skuId, lote, dataValidade, quantidade) |
| `POST` | `/api/validade/lotes/{estoqueLoteId}/ajustar` | Ajustar quantidade: `SET`, `ADD` ou `REMOVE` |
| `POST` | `/api/validade/lotes/{estoqueLoteId}/zerar` | Zerar lote específico |
| `GET` | `/api/validade/lotes/{estoqueLoteId}/movimentos` | Histórico de movimentos do lote |

## Tarefas de contagem

Fluxo de verificação periódica de validade em campo:

1. **Criar tarefa** (`POST /api/validade/tarefas`): status inicial = `RASCUNHO`
2. **Registrar lotes verificados** (`POST /api/validade/tarefas/{id}/itens`):
   - Campos: `skuId`, `lote`, `dataValidade`, `quantidade`
   - Ação: `SET` (substituir), `ADD` (somar), `REMOVE` (subtrair)
3. **Finalizar** (`POST /api/validade/tarefas/{id}/finalizar`):
   - Aplica todas as ações nos `EstoqueLote`
   - Calcula divergências por SKU: `soma_lotes − estoque_agregado`
   - Cria `TarefaValidadeDivergencia` para cada SKU com diferença ≠ 0
4. **Tratar divergências** (`POST /api/validade/divergencias/{id}/tratar`):
   - `IGNORAR`: marca como resolvida sem ação
   - `CRIAR_AJUSTE`: cria `MovimentoEstoque(tipo=AJUSTE)` para sincronizar saldo agregado com sub-ledger

### Status da tarefa

`RASCUNHO` → `FINALIZADA` (terminal)
`RASCUNHO` → `CANCELADA` (terminal; só de `RASCUNHO`)

### Endpoints de tarefas

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/validade/tarefas` | Criar tarefa |
| `GET` | `/api/validade/tarefas` | Listar todas |
| `GET` | `/api/validade/tarefas/{id}` | Detalhe |
| `POST` | `/api/validade/tarefas/{id}/itens` | Adicionar item à tarefa |
| `DELETE` | `/api/validade/tarefas/{id}/itens/{itemId}` | Remover item |
| `PUT` | `/api/validade/tarefas/{id}/observacao` | Atualizar observação |
| `POST` | `/api/validade/tarefas/{id}/finalizar` | Finalizar e detectar divergências |
| `POST` | `/api/validade/tarefas/{id}/cancelar` | Cancelar (só em `RASCUNHO`) |
| `GET` | `/api/validade/divergencias` | Listar divergências |
| `POST` | `/api/validade/divergencias/{id}/tratar` | Tratar divergência |

## Frontend

| Componente | Tipo | Descrição |
|-----------|------|-----------|
| `ValidadeDashboardPage.vue` | Página | Dashboard com contadores por status |
| `ValidadeHistoricoPage.vue` | Página | Histórico de alertas |
| `ValidadeTarefaMobilePage.vue` | Página mobile | Contagem em campo com celular |
| `ValidadeTarefaExecPage.vue` | Página | Execução de tarefa (desktop) |
| `TarefaEditorMobile.vue` | Componente | Editor de itens de contagem |
| `DivergenciaListMobile.vue` | Componente | Visualização de divergências |
| `PainelValidade.vue` | Painel | Painel de validade no dashboard principal |

## Gaps

- **Exportação**: dashboard existe, sem exportação CSV/Excel ou PDF de relatório de validade
- **Relatórios agendados**: sem geração automática ou envio por e-mail de alertas de vencimento
- **Escolha manual de lote em saída**: FEFO é automático — operador não pode selecionar qual lote consumir
