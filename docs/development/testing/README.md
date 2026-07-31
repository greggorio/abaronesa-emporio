# Testing

Estado atual: o projeto nao possui hoje uma estrutura consolidada de testes
unitarios, integracao e E2E em cobertura ampla.

O que existe de forma concreta:

- `quality/e2e/erp-backoffice/`: suite E2E hoje existente para fluxos pontuais do backoffice do ERP
- verificacoes manuais e scripts isolados de apoio operacional

Este diretorio deve ser tratado como base documental para a evolucao da
estrategia de testes, nao como reflexo de uma cobertura ja implantada.

Tambem faz parte do roadmap definir, de forma explicita, o escopo de testes do
projeto unificado ERP + site antes de consolidar uma estrutura fisica definitiva
para as suites.

## Objetivo

Organizar a implementacao gradual de uma estrategia de testes realista para os
modulos:

- ERP backend
- ERP frontend
- Site backend
- Site frontend

## Referencia principal

- `TESTING_ROADMAP.md`
