> **DNA**
>
> - **id**: `validade`
> - **type**: `seed+node`
> - **label**: `Validade`
> - **ancestors**: `[estoque, modules, docs]`
> - **maturity**: `seed`
> - **contract**: `submodulo-bakery@1.0`

# Validade

Validade é o sub-domínio que controla a data de vencimento dos produtos em estoque, detecta lotes próximos do vencimento e gerencia o processo de contagem física com detecção e resolução de divergências entre o sub-ledger e o saldo agregado.

Pertenço a [`estoque/`](../README.md).

## Domínio

- [`validade.md`](./validade.md) — alertas, dashboard, tarefas de contagem, divergências e endpoints

## Maturidade

Estado atual: `seed`. O contrato `submodulo-bakery@1.0` ainda não foi formalizado. Até sua definição em `docs/contracts/`, este sub-domínio permanece `seed`.

## Leitura contextual

A aba de validade no cadastro do produto (`ProdutoValidadeTab.vue`) é interface hospedada em `produtos/`, mas o domínio permanece aqui — FEFO, divergências, ajustes e tarefas pertencem a `estoque/validade`.

O ciclo de vida de uma divergência é completo: a contagem física (tarefa) detecta inconsistência entre `SUM(estoque_lote.quantidade)` e `estoque.quantidade`; o gestor escolhe ignorar ou criar um ajuste automático. O ajuste gera um `MovimentoEstoque(tipo=AJUSTE)` que sincroniza o saldo agregado com o sub-ledger.

O dashboard de validade (`ValidadeDashboardPage.vue`) existe no frontend Vue/Quasar. A interface mobile (`ValidadeTarefaMobilePage.vue`) cobre o caso de uso de operadores fazendo verificação periódica em campo com celular.

## Exploração

- Alertas, tarefas e endpoints completos → [`validade.md`](./validade.md)
- FEFO e sub-ledger de lotes → [`../movimentacao/README.md`](../movimentacao/README.md)
- Aba de validade no cadastro do produto → [`../../produtos/README.md`](../../produtos/README.md)
