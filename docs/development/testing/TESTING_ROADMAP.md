# Testing Roadmap

## Situacao atual

Hoje nao existe uma malha de testes coerente o suficiente para justificar uma
estrutura fisica dedicada em `tests/` na raiz do repositorio.

O estado atual e:

- `quality/e2e/erp-backoffice/` cobre apenas cenarios pontuais
- nao ha suite unitaria consolidada no backend do ERP
- nao ha suite unitaria consolidada no backend do site
- nao ha suite consolidada de frontend para ERP e site
- existem verificacoes manuais isoladas para fluxos especificos

## Meta

Construir uma cobertura ampla e progressiva, com prioridade para regressao
funcional e fluxos de negocio criticos.

## Fase 1

- Definir escopo formal de testes do projeto unificado ERP + site
- Definir piramide de testes por modulo
- Escolher stack de testes para cada app
- Mapear fluxos criticos que exigem regressao automatizada
- Padronizar convencoes de nomenclatura e execucao

## Decisao estrutural pendente

A estrutura fisica definitiva da area de testes nao deve ser considerada fechada
antes da conclusao da fase de definicao de escopo. A organizacao atual serve
apenas para dar lugar coerente ao material existente hoje.

## Fase 2

- Backend ERP: smoke tests e integracao de endpoints criticos
- Frontend ERP: testes de componentes e fluxos essenciais
- Site backend: integracao de endpoints publicos e autenticados
- Site frontend: smoke tests de rotas criticas

## Fase 3

- Consolidar E2E por dominio
- Adicionar coverage minima por modulo
- Integrar execucao em CI

## Prioridades iniciais sugeridas

- autenticacao
- cardapio publico
- pedidos/chamados
- pagamento
- sincronizacao ERP/site
- configuracoes de tenant e tema

## Artefatos manuais

Scripts manuais de verificacao devem viver em `ops/manual/` com nome que
explicite seu uso operacional, e nao em uma pasta `tests/` que sugira uma suite
formal inexistente.
