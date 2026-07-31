> **DNA**
>
> - **id**: `movimentacao`
> - **type**: `seed+node`
> - **label**: `Movimentação`
> - **ancestors**: `[estoque, modules, docs]`
> - **maturity**: `seed`
> - **contract**: `submodulo-bakery@1.0`

# Movimentação

Movimentação é o sub-domínio que governa todas as entradas, saídas, ajustes e estornos de estoque — e o mecanismo que garante rastreabilidade por lote via FEFO automático.

Pertenço a [`estoque/`](../README.md).

## Domínio

- [`movimentacao.md`](./movimentacao.md) — tipos de movimento, fluxos de baixa automática, FEFO, sub-ledger de lotes, endpoints

## Maturidade

Estado atual: `seed`. O contrato `submodulo-bakery@1.0` ainda não foi formalizado. Até sua definição em `docs/contracts/`, este sub-domínio permanece `seed`.

## Leitura contextual

O `MovimentoEstoqueService` é a peça central do módulo: toda baixa automática (venda, produção, recebimento) passa por ele. O FEFO é aplicado automaticamente a qualquer saída quando o produto tem `controla_validade=true`. Quando a saída excede o saldo de lotes conhecidos, a sobra é registrada em `EstoqueLote(SEM_LOTE)` com quantidade negativa — um red flag visível no sub-ledger que indica lastro de lote ausente.

O campo `movimentoOrigemId` torna estornos idempotentes: chamadas duplicadas não criam reversos duplicados. Isso é crítico para cancelamentos automáticos de pedidos em massa.

A transferência entre depósitos tem tipo definido na enum, mas não tem lógica de negócio implementada — os campos `local_origem_id` e `local_destino_id` em `movimento_estoque` foram adicionados antecipadamente e permanecem sem uso.

## Exploração

- Tipos, fluxos e endpoints completos → [`movimentacao.md`](./movimentacao.md)
- Modelo de saldo que alimenta os movimentos → [`../estoque.md`](../estoque.md)
- Como validade interage com FEFO → [`../validade/README.md`](../validade/README.md)
