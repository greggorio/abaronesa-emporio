# Desenvolvimento

Documentacao voltada a setup, runtime local, guias tecnicos recorrentes, testes e notas de implementacao para quem trabalha no codigo.

## Papel da Secao

Esta pasta nao descreve dominio funcional nem contratos de API.

Ela deve concentrar:

- guias tecnicos de implementacao
- setup e runtime local
- estrategia e estado atual de testes
- analises tecnicas de componentes
- notas de continuidade quando ainda forem uteis para desenvolvimento ativo

## Estrutura Atual

| Area | Papel | Status |
|------|-------|--------|
| [CONFIGURACAO_LOCAL_SEGREDOS.md](./CONFIGURACAO_LOCAL_SEGREDOS.md) | Variaveis sensiveis e inicializacao local segura dos dois backends | `ativo` |
| [Bootstrap administrativo e seeds sensiveis](../infrastructure/deployment/BOOTSTRAP_ADMIN_E_CONFIGURACOES_SENSIVEIS.md) | Operacao opt-in do bootstrap e limites dos seeds | `ativo` |
| [ONBOARDING_MINIMO.md](./ONBOARDING_MINIMO.md) | Primeiro roteiro para novo desenvolvedor levantar o ambiente | `ativo` |
| [Identidade local do publisher](../infrastructure/deployment/release-control/IDENTIDADE_PUBLISHER.md) | Ponte opt-in RS256/JWKS e perfil development loopback | `ativo` |
| [UI publisher de desenvolvimento](../infrastructure/deployment/release-control/UI_PUBLISHER.md) | Publicação local, idempotência recuperável e polling | `ativo` |
| [QR_ORDERING_LOCAL_RUNTIME.md](./QR_ORDERING_LOCAL_RUNTIME.md) | Setup e runtime local do fluxo QR | `ativo` |
| [I18N_ENTITY_TRANSLATIONS.md](./I18N_ENTITY_TRANSLATIONS.md) | Padrao tecnico de internacionalizacao no backend | `ativo` |
| [DELIVERY_I18N_INVENTORY.md](./DELIVERY_I18N_INVENTORY.md) | Inventario tecnico de i18n do delivery | `ativo` |
| [PLANO_I18N_MESA_FASE1.md](./PLANO_I18N_MESA_FASE1.md) | Plano tecnico de i18n da mesa digital | `ativo` |
| [guia_adicionar_campo_entidade_principal.md](./guia_adicionar_campo_entidade_principal.md) | Guia tecnico recorrente de extensao de entidades | `ativo` |
| [guia_importacao_produtos.md](./guia_importacao_produtos.md) | Guia tecnico para fluxo de importacao de produtos | `ativo` |
| [component-analysis/](./component-analysis/) | Analises tecnicas pontuais de componentes | `ativo` |
| [contextual-notes/README.md](./contextual-notes/README.md) | Notas contextuais e de continuidade | `contextual` |
| [CHANGELOG.md](./CHANGELOG.md) | Historico de evolucao da estrutura de documentacao | `registro` |
| [convencoes-documentacao.md](./convencoes-documentacao.md) | Convencoes de nomenclatura e contribuicao para documentacao | `ativo` |
| [FORMSTUDIO_DEFINITIONS_MAPPING.md](./FORMSTUDIO_DEFINITIONS_MAPPING.md) | Referencia tecnica do motor FormStudio (formularios dinamicos) | `ativo` |
| [testing/README.md](./testing/README.md) | Estado atual e roadmap de testes | `ativo` |

## Taxonomia Editorial

### Setup e Runtime

- documentos para subir ambiente local e entender dependencias de execucao

### Guias Tecnicos Recorrentes

- documentos reutilizaveis para alteracoes comuns no codigo

### Testing

- estrategia, estado atual e roadmap de testes

### Analises Tecnicas

- diagnosticos e estudos pontuais de componentes ou arquitetura de codigo

### Notas Contextuais

- documentos de continuidade ou de implementacao muito especifica
- devem permanecer na pasta apenas enquanto ainda forem uteis para o trabalho ativo
- devem preferencialmente ficar em uma subarea propria, nao na raiz

## Lacunas Atuais

- a linguagem da secao ainda nao esta totalmente uniforme

## Guia Editorial

O padrao editorial desta secao esta em [GUIA_ESTILO.md](./GUIA_ESTILO.md).

## Navegacao

- [Arquitetura](../architecture/README.md)
- [Infraestrutura](../infrastructure/README.md)
- [API Reference](../api-reference/README.md)
