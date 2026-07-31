# Guia de Estilo da Pasta `development`

## Objetivo

Esta pasta documenta como trabalhar no codigo e operar fluxos de desenvolvimento do projeto.

Ela nao deve competir com:

- `modules`, que descreve dominio funcional
- `integrations`, que descreve provedores externos
- `api-reference`, que descreve contratos de API

## Tipos de Documento

### Setup e Runtime

- documentos para subir ambiente local
- rotas, portas, dependencias e topologia de execucao

### Guia Tecnico Recorrente

- procedimentos reaproveitaveis de alteracao ou extensao de codigo

### Testing

- estado atual, estrategia e roadmap de testes

### Analise Tecnica

- estudos e diagnosticos de componentes ou estrutura interna

### Nota Contextual

- documento fortemente ligado a uma implementacao especifica
- deve ser mantido apenas enquanto continuar util ao fluxo de trabalho

## O Que Deve Entrar Aqui

- setup local
- detalhes de runtime e ambiente dev
- padroes tecnicos recorrentes
- guias de manutencao e extensao
- roadmap de testes
- analises tecnicas de apoio

## O Que Nao Deve Ficar Aqui

- especificacao funcional de produto
- contrato de API voltado a consumidores externos
- escopo funcional transversal
- historico obsoleto sem uso para desenvolvimento atual

## Padrao de `README.md`

Quando uma subarea da pasta tiver `README.md`, ele deve preferir:

1. explicar o papel daquela subarea
2. explicitar o estado atual do material
3. separar o que e referencia recorrente do que e nota contextual

## Linguagem

- priorizar objetividade tecnica
- evitar prometer estruturas inexistentes
- explicitar quando um documento e parcial, contextual ou provisoriamente util
- manter coerencia entre nome do documento e seu papel real
