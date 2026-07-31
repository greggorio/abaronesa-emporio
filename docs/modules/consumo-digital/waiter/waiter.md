# Waiter — Especificação

## Definição

Dashboard operacional do staff de salão. Centraliza chamados de clientes, itens prontos para entrega, pagamentos pendentes de validação e gerenciamento de mesas — tudo sincronizado em tempo real via SSE.

## Entidade Chamado

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | Long | Identificador |
| `sessaoMesa` | FK | Mesa que originou o chamado |
| `sessaoConvidado` | FK | Convidado que chamou (quando identificado) |
| `tipo` | Enum | GARCOM, CONTA ou AJUDA |
| `status` | Enum | PENDENTE → ATENDIDO / CANCELADO |
| `observacao` | String | Detalhe livre (ex: "não consigo pagar") |
| `criadoEm` | Timestamp | — |
| `atendidoPor` | String | Nome do staff que atendeu |
| `atendidoEm` | Timestamp | — |

**Tipos de chamado:**

| Tipo | Quando usar |
|------|-------------|
| GARCOM | Cliente quer atenção do garçom |
| CONTA | Cliente quer pagar (sem self-checkout) |
| AJUDA | Problema genérico com pedido ou sistema |

## Dashboard do Waiter

O dashboard (`WaiterPage`) consolida quatro visões em tempo real:

### 1. Chamados pendentes
- Lista ordenada por tempo de criação (`criadoEm`)
- Card exibe: mesa, tipo, observação e tempo de espera
- Ação: marcar como atendido (`PUT /api/chamados/{id}` → status ATENDIDO)
- SSE: `chamado.criado` para novos chamados; `chamado.atendido` quando resolvido

### 2. Itens prontos para entrega
- Itens com status READY no KDS aguardando entrega física
- Card exibe: mesa, produto, quantidade
- Ação: confirmar entrega → muda status para DELIVERED no KDS

### 3. Pagamentos pendentes de validação
- Pagamentos com status PENDING que não foram confirmados automaticamente
- Casos: pagamento em dinheiro, problemas de gateway
- Ação: `POST /api/waiter/pagamentos/{pagamentoId}/resolver` → marca como PAID sem gateway

### 4. Mesas ativas
- Grid visual com todas as mesas abertas e seu status
- Ações disponíveis por mesa:
  - Ver conta em tempo real
  - Liberar self-checkout (`selfCheckoutLiberado = true`)
  - Fechar mesa (`POST /api/waiter/mesas/{sessaoMesaId}/fechar`)
  - Gerar NFCe
  - Exportar recibo em PDF

## Tradução automática de observações

O `LanguageDetectionService` detecta o idioma do campo `observacao` dos chamados e traduz automaticamente para o idioma configurado no sistema. Relevante para estabelecimentos com clientela internacional que usam a interface em PT/EN/ES.

## Escopo

**Inclui:**
- Chamados de três tipos (GARCOM, CONTA, AJUDA) com fila em tempo real
- Confirmação de entrega de itens prontos
- Validação manual de pagamentos
- Gerenciamento e fechamento de mesas
- Liberação de self-checkout por mesa
- Geração de NFCe
- Exportação de recibo em PDF
- Tradução automática de observações de chamados

**Não inclui:**
- Priorização automática de chamados por SLA (gap identificado)
- Gestão de escalas ou turnos do staff
