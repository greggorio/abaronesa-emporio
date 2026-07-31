# Fidelização — Especificação do Domínio

## Definição

Módulo responsável por pontos, recompensas, resgates e benefícios recorrentes ligados ao cliente. É o subsistema de fidelidade que incentiva o consumo recorrente: em vez de desconto imediato, o cliente acumula pontos e os troca por vantagens.

## Escopo

**Inclui:**
- Gamificação: saldo de pontos, movimentos de crédito (compra gera pontos) e débito (resgate), histórico de pontos, regras de pontuação, expiração configurável de pontos
- Recompensas: catálogo de recompensas disponíveis (cadastro, tipos: desconto, produto gratuito, experiência), custo em pontos, disponibilidade, validade
- Resgates: solicitação de resgate, validação de saldo, desconto de pontos, registro do resgate, entrega da recompensa (desconto na conta, produto gratuito, etc.)

**Não inclui:**
- Cadastro base do cliente (pertence a `clientes/`)
- Jornada de consumo digital (pertence a `consumo-digital/`)
- Financeiro corporativo (pertence a `financeiro/`)

## Fluxo principal

1. Cliente faz um pedido → ganha pontos (ex.: R$ 1 = 1 ponto, taxa configurável)
2. Pontos ficam acumulados na conta do cliente
3. Cliente escolhe uma recompensa no catálogo
4. Cliente usa os pontos para solicitar a recompensa
5. Sistema valida saldo suficiente, debita os pontos e registra o resgate
6. Benefício é aplicado (desconto na conta, produto gratuito, experiência)

## Regras de domínio

- Consumo gera pontos (taxa de conversão configurável)
- Resgate consome pontos
- Pontos expiram (prazo configurável)

## Sub-domínios

| Sub-domínio | Descrição |
|-------------|-----------|
| `gamificacao/` | Saldo de pontos, movimentos de crédito e débito, histórico e regras de pontuação |
| `recompensas/` | Catálogo de recompensas: cadastro, tipos, custo em pontos, disponibilidade e validade |
| `resgates/` | Solicitação de resgate, validação de saldo, débito de pontos, registro e entrega |

## Integrações

| Módulo | Natureza |
|--------|----------|
| `clientes/area-do-cliente` | Leitura: expõe saldo de pontos e histórico ao perfil do cliente |
| `vendas/` | Escrita: o consumo (pedidos) é a origem dos créditos de pontos |
| `financeiro/` | Escrita: registra o custo dos benefícios concedidos |

## Decisões de domínio

- **Três submódulos independentes** — gamificação (pontos), recompensas (catálogo) e resgates (troca) são separados por responsabilidade
- **Tipos de recompensa** — o catálogo suporta três tipos: desconto, produto gratuito e experiência
- **Parâmetros configuráveis** — taxa de conversão e expiração de pontos são configuráveis, sem valor fixo em código

## Status de implementação

**EM_DESENVOLVIMENTO**. O módulo está mapeado conceitualmente com seus três sub-domínios especificados, mas a materialização no banco e nas APIs ainda não foi implementada.
