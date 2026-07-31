# OpenAI

> **Status**: `parcial` | **Prioridade**: Alta

## Visao Geral

A integracao `openai` cobre os pontos em que o ecossistema depende de modelos generativos externos para produzir traducoes e conteudo.

Hoje ela aparece principalmente em dois fluxos:

- traducao de entidades e cardapio
- geracao de perguntas para quiz

## Papel da Integracao

### Inclui
- chamadas ao provedor OpenAI
- configuracao de modelo e credenciais
- casos de uso baseados em traducao automatica
- geracao assistida de conteudo para quiz

### Nao inclui
- a estrategia funcional de i18n como um todo
- o dominio de quiz
- o catalogo de produtos
- suporte conversacional ao cliente

## Estado Atual

| Dimensao | Status | Observacao |
|----------|--------|------------|
| **Especificacao** | `basica` | A integracao ja tem casos de uso e fronteiras claras, mas ainda nao possui documentos separados por caso de uso |
| **Implementacao** | `parcial` | Traducoes e quiz possuem evidencia material; outras ideias ainda sao apenas backlog |
| **Operacao** | `ativa em escopo restrito` | O uso atual esta concentrado em i18n e quiz, nao em chatbot ou automacoes amplas |

## Casos de Uso Atuais

| Caso de uso | Status | Evidencia principal |
|-------------|--------|---------------------|
| Traducao de entidades e cardapio | `ativo` | [I18N_ENTITY_TRANSLATIONS.md](/home/gregorio/git/bakery/docs/development/I18N_ENTITY_TRANSLATIONS.md), [ADR-004-i18n-strategy.md](/home/gregorio/git/bakery/docs/architecture/decisions/ADR-004-i18n-strategy.md) |
| Geracao de perguntas para quiz | `ativo` | [QuizGenerationService.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/service/QuizGenerationService.java), [quizAiService.ts](/home/gregorio/git/bakery/espresso_front/src/services/quizAiService.ts) |
| Conteudo para signage e assistentes | `parcial ou experimental` | [AIAssistantService.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/service/AIAssistantService.java), configuracoes `signage.ai.provider=openai` |

## Evidencia Material Principal

| Evidencia | Papel atual |
|-----------|-------------|
| [OpenAiConfigService.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/service/OpenAiConfigService.java) | Fabrica/configuracao da integracao com OpenAI |
| [QuizGenerationService.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/service/QuizGenerationService.java) | Geracao de perguntas de quiz |
| [AIAssistantService.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/service/AIAssistantService.java) | Uso generico/assistido de IA no backend |
| [I18N_ENTITY_TRANSLATIONS.md](/home/gregorio/git/bakery/docs/development/I18N_ENTITY_TRANSLATIONS.md) | Workflow tecnico de traducao |
| [quizAiService.ts](/home/gregorio/git/bakery/espresso_front/src/services/quizAiService.ts) | Consumo do endpoint de quiz no frontend |

## Fronteiras com Outros Modulos

| Area | Fronteira |
|------|-----------|
| [Produtos](/home/gregorio/git/bakery/docs/modules/produtos/README.md) | Entidades traduzidas e possivel enriquecimento de catalogo |
| [Quiz](/home/gregorio/git/bakery/docs/modules/quiz/README.md) | Dominio funcional de perguntas e sessoes |
| [Temas](/home/gregorio/git/bakery/docs/modules/temas/README.md) | Potencial uso indireto em signage e conteudo tematico |

## Configuracao e Operacao

Os detalhes tecnicos de traducao e job agendado estao melhor documentados em:

- [I18N_ENTITY_TRANSLATIONS.md](/home/gregorio/git/bakery/docs/development/I18N_ENTITY_TRANSLATIONS.md)
- [ADR-004-i18n-strategy.md](/home/gregorio/git/bakery/docs/architecture/decisions/ADR-004-i18n-strategy.md)

Este README nao deve virar um dump de variaveis ou pricing. O foco aqui e o papel da integracao e seus casos de uso reais.

## Gaps Prioritarios

- separar o que ja esta implementado do que ainda e apenas ideia
- documentar melhor o fluxo real de configuracao e fallback por ambiente
- decidir se `AIAssistantService` e signage fazem parte desta integracao ou de outra frente editorial
- remover referencias a pastas inexistentes como `openai/decisions/`

## Proximos Passos

- criar `implementacao-atual.md` se o uso de OpenAI continuar se expandindo
- adicionar um documento separado para `i18n` caso a integracao fique mais complexa
- revisar se harmonizacao, descricoes criativas e chatbot merecem permanecer como backlog aqui ou migrar para outros escopos
