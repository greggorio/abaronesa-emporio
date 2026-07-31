# Push Notifications

> **Status**: `parcial` | **Prioridade**: Alta

## Visao Geral

A integracao `push-notifications` cobre o uso de FCM e da infraestrutura de notificacoes do app para envio de mensagens, persistencia de subscriptions e vinculo entre dispositivo e usuario autenticado.

Hoje ela e uma integracao parcialmente consolidada: a base de subscricao e envio existe, mas o vinculo completo `usuario x dispositivo` ainda aparece no proprio documento como evolucao necessaria.

## Papel da Integracao

### Inclui
- subscricao de dispositivos
- geracao e armazenamento de token FCM
- envio de notificacoes push
- historico de notificacoes
- vinculo entre autenticacao e subscription

### Nao inclui
- campanhas como dominio funcional
- recompensas e sorteios como produto
- mensageria via canais externos como WhatsApp
- regra funcional de fidelizacao

## Estado Atual

| Dimensao | Status | Observacao |
|----------|--------|------------|
| **Especificacao** | `parcial` | O documento define bem a arquitetura e os passos de evolucao do MVP |
| **Implementacao** | `parcial` | A base de subscribe e historico existe, mas o vinculo completo com `user_id` ainda e tratado como alvo do desenho |
| **Operacao** | `ativa` | O fluxo de token e notificacao ja existe no app e no backend |

## Evidencia Material Principal

| Evidencia | Papel atual |
|-----------|-------------|
| `notification_subscriptions` | Persistencia das subscriptions de dispositivo |
| `notification_history` | Historico de notificacoes enviadas |
| `POST /api/notifications/subscribe` | Registro e futura associacao de token ao usuario |
| `NativePushBootstrap` | Bootstrap de push no app nativo |
| `JwtAuthenticationFilter` | Base de autenticacao para associacao do usuario ao token |

## Fronteiras com Outros Modulos

| Area | Fronteira |
|------|-----------|
| [Fidelizacao](/home/gregorio/git/bakery/docs/modules/fidelizacao/README.md) | Consome push para campanhas, recompensas e reengajamento |
| [Clientes](/home/gregorio/git/bakery/docs/modules/clientes/README.md) | Usa autenticacao e area logada como contexto de usuario |
| [WhatsApp](/home/gregorio/git/bakery/docs/integrations/whatsapp/README.md) | Canal complementar, nao substituto |

## Arquitetura e Fluxo Atual

### Aplicacoes Envolvidas

- **espresso_front**: app web React encapsulado com Capacitor
- **espresso_back**: backend responsavel por notificacoes, historico e envio
- **ERP backend**: autenticacao centralizada via OAuth2 e JWT

Cada restaurante roda em sua propria instancia, sem banco compartilhado para notificacoes.

### Fluxo Atual

1. O app inicia e executa `NativePushBootstrap`
2. O dispositivo solicita permissao
3. O token FCM e gerado
4. O token e enviado para `POST /api/notifications/subscribe`
5. O backend registra a subscription

No estado atual documentado, isso ainda pode ocorrer antes do login, portanto o token pode existir sem `user_id`.

### Direcao de Evolucao

- manter o token como entidade do dispositivo
- associar `user_id` apos autenticacao
- adicionar `last_seen_at`
- evitar tabela adicional ou FK para usuarios do ERP neste estagio

## Gaps Prioritarios

- consolidar a associacao `usuario x dispositivo` no registro de subscription
- confirmar no documento o estado real da migracao de `user_id` e `last_seen_at`
- separar melhor o que ja esta efetivamente entregue do que ainda e alvo do MVP
- decidir se este README ja deve ser complementado por `implementacao-atual.md`

## Proximos Passos

- revisar o estado real da tabela `notification_subscriptions`
- registrar a implementacao final do vinculo autenticado, quando concluida
- avaliar extracao de detalhes tecnicos mais densos para `implementacao-atual.md`
