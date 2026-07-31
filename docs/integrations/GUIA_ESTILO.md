# Guia de Estilo da Pasta `integrations`

## Objetivo

Esta pasta documenta integracoes externas, provedores e servicos conectados ao ecossistema.

Ela nao deve competir com:

- `modules`, que descreve comportamento funcional e dominio de produto
- `development`, que concentra guias tecnicos de implementacao e manutencao
- `api-reference`, que deve concentrar contratos publicos quando estiver preenchida

## Estrutura Recomendada por Integracao

Cada integracao relevante deve preferencialmente ter uma pasta propria com:

- `README.md` como porta editorial
- `implementacao-atual.md` quando a integracao tiver evidencia material suficiente
- `escopo.md` quando houver backlog relevante, planejado ou faseado
- `aderencia.md` apenas quando houver diferenca relevante entre o planejado e o implementado

## O Que Entra em `integrations`

- provedores externos
- autenticacao com terceiro
- webhooks
- credenciais e pre-requisitos
- mapeamento de status do provedor para o sistema
- limites e dependencias operacionais da integracao

## O Que Nao Deve Ficar Aqui

- descricao completa da jornada funcional do usuario
- especificacao de modulo de produto
- guias genericos de desenvolvimento
- backlog amplo de feature sem dependencia externa clara

## Padrao de `README.md`

Cada `README.md` da secao deve buscar esta ordem:

1. `Visao Geral`
2. `Papel da Integracao`
3. `Estado Atual`
4. `Evidencia Material Principal`
5. `Fronteiras com Outros Modulos`
6. `Gaps Prioritarios`
7. `Proximos Passos`

## Linguagem

- descrever primeiro o que ja existe materialmente
- separar claramente implementado, parcial e planejado
- evitar transformar o README em dump de configuracoes ou comparativo comercial
- evitar roadmap especulativo quando nao houver decisao real tomada

## Casos Transversais

Quando um documento depender de uma integracao, mas nao representar a integracao em si, ele deve preferencialmente ser movido para:

- `modules`, se o foco principal for dominio de produto
- `_archive`, se for apenas registro historico sem dono funcional ativo
