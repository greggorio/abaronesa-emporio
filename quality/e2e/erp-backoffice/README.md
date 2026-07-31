# E2E ERP Backoffice

Esta suite concentra os cenarios E2E hoje existentes para o backoffice do ERP.

## Escopo atual

Cobertura parcial e pontual de fluxos do ERP:

- categoria de despesa
- fornecedor
- contas a pagar

## Estrutura atual

```text
quality/e2e/erp-backoffice/
├── categoria-despesa/
├── fornecedor/
├── contas-pagar/
├── docs/
├── logs/
├── reports/
├── .ai-workflow/
├── run_tests.sh
├── run_tests_with_report.sh
└── test-config.json
```

## Leitura correta desta suite

- o diretorio representa uma suite E2E existente, mas limitada
- ele nao representa ainda uma estrategia consolidada de qualidade do sistema
- a expansao da cobertura deve seguir o roadmap em `docs/development/testing/`
- a propria estrutura interna desta area pode ser revista quando o escopo formal
  de testes do projeto for definido

## Execucao

Os scripts atuais permanecem como estao por compatibilidade operacional local.
A reorganizacao detalhada desta suite deve acontecer apenas junto da definicao
formal da estrategia de testes do projeto.
