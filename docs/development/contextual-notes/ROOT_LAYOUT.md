# Layout da raiz

Este documento descreve a organizacao intencional da raiz de `bakery`.

## Modulos principais mantidos na raiz

- `backend/`: backend do ERP
- `frontend/`: frontend do ERP
- `espresso_back/`: backend do site
- `espresso_front/`: frontend do site
- `docs/`: documentacao consolidada
- `quality/`: ativos de qualidade e suites transversais
- `ops/`: deploy, compose, envs e utilitarios operacionais
- `tools/`: utilitarios de desenvolvimento e exportacao
- `uploads/`: volume/local de arquivos do ERP em ambiente local e docker

## Estruturas de apoio

- `ops/db/`: rotinas SQL e scripts de manutencao de banco
- `ops/manual/`: verificacoes operacionais manuais
- `ops/sql/`: SQL avulso de verificacao e suporte
- `tools/dev/`: helpers de desenvolvimento local
- `tools/export/`: utilitarios de exportacao e apoio funcional

## Itens que nao devem voltar para a raiz

- logs avulsos
- SQL avulso
- scripts de teste manual
- utilitarios genericos soltos fora de `ops/` ou `tools/`
- arquivos de ambiente de producao fora de `ops/env/`
- exports soltos fora de `backend/outputs/`

## Observacao

Os quatro modulos principais foram mantidos na raiz por estabilidade operacional.
Mover esses diretorios para `apps/` exigiria uma segunda fase com ajuste amplo de
deploy, compose, docs e caminhos de execucao.
