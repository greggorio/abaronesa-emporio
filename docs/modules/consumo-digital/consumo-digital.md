# Consumo Digital — Especificação do Domínio

## Definição

Domínio responsável pela jornada digital de consumo: acesso ao cardápio, registro de pedidos, acompanhamento da conta e pagamento digital. Cobre consumo em mesa (via QR), delivery (entrega e retirada) e as superfícies operacionais que sustentam esses canais (KDS, Waiter).

## Escopo

**Inclui:**
- Cardápio digital (público e autenticado)
- Sessão de mesa, convidados e host
- QR Ordering como protocolo de entrada na mesa
- Carrinho e pedido em mesa e delivery
- Conta individual e consolidada da mesa
- Pagamento digital via self-checkout
- Rastreamento de delivery (Uber Direct)
- KDS — fila unificada de cozinha
- Waiter — dashboard operacional para o salão

**Não inclui:**
- Cadastro e governança de produtos (pertence a `produtos/`)
- Fidelização e recompensas (pertence a `fidelizacao/`)
- Financeiro corporativo (pertence a `financeiro/`)
- Estoque e produção (pertence a `estoque/` e `producao/`)

## Entidades transversais

### Mesa

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | Long | Identificador |
| `slug` | String | Identificador único usado no QR Code |
| `rotulo` | String | Nome de exibição — ex: "Mesa 5", "Bar 12" |
| `referencia` | String | Dados adicionais de localização |
| `ativo` | Boolean | Controla se a mesa está em operação |

### SessaoMesa

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | Long | Identificador |
| `mesa` | FK | Mesa física |
| `status` | Enum | OPEN ou CLOSED |
| `abertaEm` | Timestamp | — |
| `fechadaEm` | Timestamp | — |
| `observacoes` | String | Marcações operacionais |
| `selfCheckoutLiberado` | Boolean | Quando `true`, convidados podem pagar sem staff |
| `selfCheckoutLiberadoEm` | Timestamp | Momento em que o self-checkout foi liberado |

### SessaoConvidado

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | Long | Identificador |
| `sessaoMesa` | FK | Sessão compartilhada (conta conjunta) |
| `usuario` | FK (nullable) | Cliente identificado — opcional |
| `nomeExibicao` | String | Como aparece na mesa para os outros convidados |
| `guestToken` | String | Token único de autenticação anônima |
| `deviceFingerprint` | String | Identificação do dispositivo |
| `host` | Boolean | Se é o anfitrião da mesa |
| `entrouEm` / `saiuEm` | Timestamp | Ciclo da presença na sessão |

## Arquitetura transversal

### Comunicação em tempo real (SSE)

Toda sincronização em tempo real opera via SSE (`GET /api/events/sse?sessaoMesaId={id}`). Os eventos publicados são:

| Evento | Quando é emitido |
|--------|-----------------|
| `guest.joined` | Novo convidado entrou na mesa |
| `item.added` | Item adicionado ao pedido |
| `item.canceled` | Item cancelado |
| `item.ready` | Item pronto para entrega |
| `payment.made` | Pagamento confirmado |
| `account.updated` | Conta recalculada |
| `chamado.criado` | Chamado ao garçom criado |
| `chamado.atendido` | Chamado atendido |

### Identidade anônima (guestToken)

O cliente não precisa de cadastro para consumir em mesa. A identidade é estabelecida via `guestToken` — um token único por `SessaoConvidado`, enviado no header `X-Guest-Token`. O `grupoClienteId` para aplicação de descontos é resolvido a partir deste token quando o convidado está identificado.

### Internacionalização (i18n)

A interface do cliente suporta três idiomas: português (PT), inglês (EN) e espanhol (ES). A detecção é automática via `Accept-Language`; o `LanguageDetectionService` no backend traduz automaticamente observações de chamados recebidas em idioma diferente do padrão.

### Configurações dinâmicas

O `ConfigManager` lê parâmetros do banco sem necessidade de redeploy:

| Configuração | Descrição |
|--------------|-----------|
| `taxa_servico_ativo` | Ativa/desativa sugestão de taxa de serviço |
| `taxa_servico_percentual` | Percentual sugerido (padrão 10%) |
| `couvert_artistico_ativo` | Ativa couvert artístico |
| `couvert_artistico_valor` | Valor por pessoa |
| `mesa_self_checkout_mesa_enabled` | Permite pagar a mesa inteira sem dividir |

## Integrações

| Módulo | Natureza |
|--------|----------|
| `produtos/` | Cardápio, disponibilidade por canal e horário, SKUs e promoções |
| `clientes/` | Identidade, grupo comercial e descontos aplicados ao convidado identificado |
| `estoque/` | Baixa automática por venda confirmada |
| `financeiro/` | Registro de pagamentos em movimento de caixa |
| `fidelizacao/` | Acúmulo de pontos por consumo confirmado |

## Decisões de domínio

- **Valores em centavos**: toda a camada de pagamento opera com `Integer` em centavos — sem `BigDecimal` no frontend, eliminando erros de ponto flutuante
- **QR Ordering é mesa digital**: o QR Code aponta para o slug da mesa; não existe um canal de "QR Ordering" separado da mesa
- **KDS unifica canais**: pedidos de mesa e de delivery chegam na mesma fila, diferenciados por `tipo` — a cozinha processa ambos sem distinção de interface
- **Self-checkout é permissão, não padrão**: por padrão, convidados não podem pagar sem staff; o flag `selfCheckoutLiberado` em `SessaoMesa` precisa ser habilitado explicitamente
