# Webhooks

Documentacao dos callbacks recebidos pelo backend a partir de provedores externos.

## Cobertura Inicial

Os webhooks mais relevantes no estado atual do sistema sao:

- [mercadopago.md](./mercadopago.md)
- [pagseguro.md](./pagseguro.md)
- [uber-direct.md](./uber-direct.md)

## Papel da Pasta

Esta pasta deve documentar:

- endpoint exposto
- provedor emissor
- tipo de evento recebido
- autenticacao ou assinatura esperada
- acao interna disparada pelo backend

## Relacao com Outras Secoes

- `integrations` descreve o provedor e a integracao como um todo
- `api-reference/webhooks` descreve o contrato do callback recebido

## Proximos Passos

- expandir os contratos quando houver validacao mais clara de assinatura, payload e persistencia
- ligar esta pasta a `integrations` sem duplicar detalhes do provedor
