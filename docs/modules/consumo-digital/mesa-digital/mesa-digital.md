# Mesa Digital — Especificação

## Definição

Jornada de consumo em mesa compartilhada. O cliente entra via QR Code, faz pedidos individuais, acompanha a conta em tempo real e paga de forma autônoma quando o self-checkout está liberado.

## Fluxo principal

1. Cliente escaneia QR Code da mesa → `GET /api/mesas/{mesaSlug}/sessao` valida sessão ativa
2. Cliente informa nome de exibição → `POST /api/mesas/{mesaSlug}/convidados` cria `SessaoConvidado` com `guestToken`
3. SSE publica `guest.joined` para notificar outros convidados e o staff
4. Cliente navega pelo cardápio e adiciona itens ao carrinho
5. Pedido enviado → itens vão para a fila do KDS; SSE publica `item.added`
6. KDS atualiza status dos itens: QUEUED → ACCEPTED → PREPARING → READY
7. Cliente acompanha conta em tempo real via `GET /api/mesas/{sessaoMesaId}/conta`
8. Quando self-checkout está liberado: cliente cria intent de pagamento e paga digitalmente
9. Pagamento confirmado via webhook → SSE publica `payment.made`
10. Staff fecha a mesa → `POST /api/waiter/mesas/{sessaoMesaId}/fechar`

## Entidades principais

Todas as entidades transversais (`Mesa`, `SessaoMesa`, `SessaoConvidado`) estão descritas em [`consumo-digital.md`](../consumo-digital.md).

### SessaoConvidado — detalhe

O `guestToken` é o mecanismo de identidade anônima. Ele é criado na entrada e enviado em todas as requisições via header `X-Guest-Token`. O `deviceFingerprint` permite resgatar a sessão em caso de reconexão no mesmo dispositivo.

O campo `host: true` identifica o anfitrião — quem iniciou ou foi designado como responsável pela mesa. Somente um convidado por sessão é host.

## Conta compartilhada

`GET /api/mesas/{sessaoMesaId}/conta` retorna `ContaMesaResponse`:

| Campo | Descrição |
|-------|-----------|
| `subtotalCentavos` | Consumo puro sem taxas |
| `taxaServicoCentavos` | Taxa de serviço sugerida |
| `couvertCentavos` | Couvert artístico (se ativo) |
| `descontosCentavos` | Descontos aplicados |
| `pagoCentavos` | Total já pago |
| `devidoCentavos` | Saldo a pagar |
| `totalMesaCentavos` | Total geral |
| `pessoas` | Breakdown individual por convidado |
| `selfCheckoutLiberado` | Se convidados podem pagar sem staff |

## Sincronização real-time

| Evento SSE | Quando ocorre |
|------------|---------------|
| `guest.joined` | Novo convidado entra na sessão |
| `item.added` | Item adicionado a um pedido |
| `item.canceled` | Item cancelado |
| `payment.made` | Pagamento confirmado pelo gateway |
| `account.updated` | Conta recalculada |

## Escopo

**Inclui:**
- Identificação funcional da mesa via slug (usado no QR Code)
- Sessão ativa de mesa com ciclo OPEN / CLOSED
- Múltiplos convidados com identidade anônima por guestToken
- Anfitrião (host) com visão consolidada
- Reentrada na sessão via deviceFingerprint
- Conta compartilhada com breakdown individual
- Self-checkout liberável por staff
- Fechamento de mesa

**Não inclui:**
- Cadastro e precificação de produtos
- Processamento de pagamento (pertence a `pagamentos/`)
- Preparação e status de itens na cozinha (pertence a `kds/`)
