# WhatsApp

> **Status**: `planejado` | **Prioridade**: Media

## Visao Geral

A integracao `whatsapp` cobre o uso de mensageria externa para notificacoes transacionais, campanhas e eventual atendimento ao cliente via WhatsApp Business.

No estado atual, esta integracao ainda nao esta consolidada como fluxo implementado dentro da pasta `integrations`. O documento representa o recorte planejado e os pre-requisitos para sua futura implantacao.

## Papel da Integracao

### Inclui
- envio de mensagens transacionais via provedor WhatsApp Business
- templates aprovados pelo provedor
- webhooks de entrega, leitura ou resposta, quando adotados
- credenciais, consentimento e pre-requisitos operacionais

### Nao inclui
- a regra funcional de pedidos, pagamentos ou fidelizacao
- suporte ao cliente como processo de negocio completo
- push notifications nativas
- campanhas como modulo de produto autonomo

## Casos de Uso Alvo

| Caso de uso | Status | Observacao |
|-------------|--------|------------|
| Comprovantes e recibos | `planejado` | Pode complementar pagamentos e fechamento, mas ainda depende de decisao de provedor e templates |
| Notificacoes de pedido | `planejado` | Relacionado a delivery e estados operacionais |
| Campanhas promocionais | `planejado` | Exige governanca de consentimento e segmentacao |
| Atendimento e FAQ | `futuro` | Nao deve ser confundido com implementacao atual |

## Estado Atual

| Dimensao | Status | Observacao |
|----------|--------|------------|
| **Especificacao** | `basica` | O objetivo e os casos de uso ja estao identificados |
| **Implementacao** | `nao consolidada` | Nao ha nesta secao evidencia material suficiente para tratar a integracao como implementada |
| **Operacao** | `inexistente nesta pasta` | Ainda faltam provedor, credenciais, templates, webhook e governanca de uso |

## Pre-Requisitos Principais

| Item | Status | Observacao |
|------|--------|------------|
| Conta WhatsApp Business | `pendente` | Necessario definir conta e ownership operacional |
| Provedor alvo | `pendente` | Meta direta, BSP ou agregador ainda precisam ser decididos |
| Templates aprovados | `pendente` | Necessarios para mensagens transacionais e campanhas |
| Webhooks | `pendente` | Entrega, leitura e respostas dependem do provedor escolhido |
| Politica de consentimento | `pendente` | Necessaria para marketing e reengajamento |

## Fronteiras com Outros Modulos

| Area | Fronteira |
|------|-----------|
| [Consumo Digital](/home/gregorio/git/bakery/docs/modules/consumo-digital/README.md) | Mensagens de pedido, entrega e acompanhamento |
| [Fidelizacao](/home/gregorio/git/bakery/docs/modules/fidelizacao/README.md) | Campanhas, recompensas e reengajamento |
| [Eventos](/home/gregorio/git/bakery/docs/modules/eventos/README.md) | Lembretes e comunicacao de agenda |
| [Push Notifications](/home/gregorio/git/bakery/docs/integrations/push-notifications/README.md) | Canal complementar, nao substituto |

## Alternativas de Integracao

| Opcao | Papel | Risco principal |
|-------|-------|-----------------|
| **WhatsApp Business Platform (Meta)** | Integracao oficial | Complexidade operacional e aprovacao |
| **BSP/Twilio** | Camada intermediaria de integracao | Custo adicional e acoplamento ao intermediario |
| **Uso manual do WhatsApp Business App** | Operacao sem automacao | Nao resolve mensageria sistemica |

## Gaps Prioritarios

- decidir o provedor alvo da integracao
- definir o primeiro caso de uso de maior valor
- documentar consentimento, templates e compliance
- conectar a futura integracao a [../../api-reference/webhooks/README.md](../../api-reference/webhooks/README.md), se houver callbacks externos

## Proximos Passos

- escolher o recorte inicial da integracao, preferencialmente transacional
- registrar pre-requisitos operacionais reais do provedor escolhido
- criar `escopo.md` se a integracao entrar em fase de implementacao
- criar `implementacao-atual.md` apenas quando houver evidencia material suficiente
