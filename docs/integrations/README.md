# Integrações

Documentação de integrações com serviços externos e APIs de terceiros.

## Papel da Seção

Esta pasta documenta dependências externas, provedores e serviços conectados ao ecossistema.

Ela nao e a fonte principal de dominio funcional. O comportamento de produto continua descrito em `modules`; aqui ficam os contratos, provedores e acoplamentos externos que esses modulos consomem.

O padrao editorial desta secao esta em [GUIA_ESTILO.md](./GUIA_ESTILO.md).

## Integracoes Ativas

| Integracao | Status | Papel atual |
|------------|-----------|
| [Push Notifications](./push-notifications/README.md) | `parcial` | FCM, subscricoes, vinculo dispositivo x usuario |
| [Uber Direct](./uber-direct/README.md) | `parcial` | Delivery terceirizado, quote, webhook e tracking |
| [Payment Gateways](./payment-gateways/README.md) | `implementado` | MercadoPago e PagSeguro |
| [OpenAI](./openai/README.md) | `parcial` | Traducoes e geracao de conteudo |
| [WhatsApp](./whatsapp/README.md) | `planejado` | Mensageria transacional e campanhas |

## Legado

| Documento | Papel |
|-----------|-------|
| [_legacy/uber_delivery_integration.md](./_legacy/uber_delivery_integration.md) | Registro historico do ciclo inicial de delivery e Uber Direct |

## Fronteiras Editoriais

- `modules` descreve o comportamento do produto por dominio
- `integrations` descreve provedores externos, webhooks, credenciais e contratos
- `api-reference` deve descrever contratos publicos quando a secao for preenchida
- `_legacy` guarda artefatos historicos que nao representam mais o estado editorial alvo

## Proxima Estruturacao Recomendada

- Criar `README.md` para toda integracao relevante
- Separar `escopo` de `implementacao-atual` quando a integracao for grande
- Aplicar o [GUIA_ESTILO.md](./GUIA_ESTILO.md) aos proximos documentos da secao

## Navegação

- [Consumo Digital](../modules/consumo-digital/README.md) - Principal fluxo customer-facing
- [Fidelizacao](../modules/fidelizacao/README.md) - Programa de pontos, recompensas e resgates
- [API Reference](../api-reference/README.md) - Nossa API
- [Módulos](../modules/README.md) - Módulos do ERP
