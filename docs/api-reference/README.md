# API Reference

Documentacao de contratos tecnicos expostos pelo backend para autenticacao, consumo de endpoints e recebimento de webhooks.

## Estado da Secao

Esta secao ainda esta em consolidacao editorial.

Hoje a cobertura esta concentrada em:

- autenticacao compartilhada
- webhooks de integracoes externas
- grupos prioritarios de endpoints publicos e administrativos

A cobertura ampla de endpoints REST ainda nao foi consolidada.

## Estrutura

| Area | Status | Papel |
|------|--------|-------|
| [authentication/README.md](./authentication/README.md) | `parcial` | Autenticacao compartilhada, JWT e cabecalhos |
| [endpoints/README.md](./endpoints/README.md) | `parcial` | Porta editorial dos endpoints REST documentados |
| [webhooks/README.md](./webhooks/README.md) | `parcial` | Callbacks externos recebidos pelo backend |

## Cobertura Atual

| Documento | Papel |
|-----------|-------|
| [authentication/jwt.md](./authentication/jwt.md) | Contrato de autenticacao compartilhada |
| [endpoints/auth.md](./endpoints/auth.md) | Endpoints de autenticacao |
| [endpoints/admin-consumo-comprovante.md](./endpoints/admin-consumo-comprovante.md) | Endpoint de comprovante de consumo |
| [endpoints/cardapio-publico.md](./endpoints/cardapio-publico.md) | Catalogo publico |
| [endpoints/conta-e-mesa.md](./endpoints/conta-e-mesa.md) | Fluxo de sessao, convidados e conta |
| [endpoints/delivery-orders.md](./endpoints/delivery-orders.md) | Pedidos de delivery |
| [endpoints/pagamentos-publicos.md](./endpoints/pagamentos-publicos.md) | Intent, webhook e polling de pagamento |
| [endpoints/pedidos.md](./endpoints/pedidos.md) | Pedido em mesa e favoritos |
| [webhooks/README.md](./webhooks/README.md) | Indice dos webhooks documentados |

## Fronteiras Editoriais

- `modules` descreve comportamento funcional e dominio
- `integrations` descreve provedores externos e acoplamentos
- `api-reference` descreve contratos tecnicos expostos pelo backend
- `development` concentra guias de setup e implementacao

## Proximos Passos

- ampliar `endpoints/` com novos grupos de contrato de alto valor
- aprofundar payloads, exemplos e erros onde o contrato ja estiver estavel
- manter os contratos alinhados ao backend real, evitando documentacao aspiracional

## Navegacao

- [Arquitetura](../architecture/README.md)
- [Integracoes](../integrations/README.md)
- [Desenvolvimento](../development/README.md)
