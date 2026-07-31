# Technical Overview

## Contexto

O sistema combina um ERP central com superfícies digitais voltadas ao cliente e
à operação em tempo real.

## Estrutura Lógica

```
┌─────────────────────────────────────────────────────────────┐
│  FRONTENDS (Superfícies de Experiência)                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ERP Frontend (Quasar/Vue 3)                                │
│  └── /frontend/                                             │
│      ├── DashboardPage.vue        ← Dashboard administrativo│
│      ├── ProducaoPage.vue         ← Produção (PCP)          │
│      ├── ValidadeDashboardPage.vue ← Controle de validade   │
│      ├── PedidosCompraPage.vue    ← Pedidos de compra       │
│      ├── AgendaExecucaoPage.vue   ← Agenda de execução      │
│      ├── admin/                   ← Admin (usuários, configs)│
│      └── mobile/                  ← Mobile (garçom)         │
│                                                             │
│  Digital Experience Frontend (React + Capacitor)            │
│  └── /website_front/                                       │
│      ├── Index.tsx                ← Site institucional      │
│      ├── MenuPage.tsx             ← Cardápio digital        │
│      ├── MesaPage.tsx             ← Mesa digital (QR)       │
│      ├── AreaCliente.tsx          ← Área do cliente         │
│      ├── DeliveryMenuPage.tsx     ← Delivery                │
│      ├── DeliveryOrderConfirmationPage.tsx ← Confirmação    │
│      ├── FavoritosPage.tsx        ← Favoritos               │
│      ├── KdsPage.tsx              ← KDS (cozinha)           │
│      ├── WaiterPage.tsx           ← Waiter app (garçom)     │
│      ├── QuizAdmin.tsx            ← Quiz admin              │
│      ├── QuizPlayer.tsx           ← Quiz player             │
│      ├── Login.tsx                ← Login OAuth2            │
│      └── OAuth2Handler.tsx        ← Handler OAuth2          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
           │                    │
           │ APIs REST          │ APIs REST
           ▼                    ▼
┌──────────────────┐  ┌──────────────────┐
│  ERP Backend     │  │  Espresso Backend│
│  /backend/       │  │  /website_back/ │
│  (Java/Spring)   │  │  (Java/Spring)   │
└──────────────────┘  └──────────────────┘
```

## Camadas Principais

- **ERP Frontend** (`/frontend/`): Quasar/Vue 3 — Backoffice administrativo
- **ERP Backend** (`/backend/`): Java/Spring — Núcleo transacional, cadastros, regras de negócio
- **Digital Experience Frontend** (`/website_front/`): React + Capacitor — Site, app, mesa digital, delivery
- **Digital Experience Backend** (`/website_back/`): Java/Spring — Experiência do cliente, quiz, temas
- **Operação em Tempo Real**: KDS, waiter, eventos SSE
- **Integrações**: Pagamentos, push, autenticação compartilhada, serviços externos

## Princípios Arquiteturais

- ERP como fonte principal de verdade para domínios centrais
- Dois frontends separados: backoffice (Quasar) vs. customer-facing (React)
- Superfícies customer-facing consumindo contratos explícitos, sem acesso direto a banco
- Eventos em tempo real para sincronizar status operacionais e experiência
- Separação entre backoffice, experiência digital e integrações
- Documentação detalhada por módulo fora desta seção

## Onde Aprofundar

- Integrações transversais: [VISAO_GERAL_INTEGRACOES.md](./VISAO_GERAL_INTEGRACOES.md)
- Módulos de negócio: [../modules/README.md](../modules/README.md)
- Superficies digitais: [../modules/consumo-digital/README.md](../modules/consumo-digital/README.md), [../modules/clientes/README.md](../modules/clientes/README.md) e [../modules/fidelizacao/README.md](../modules/fidelizacao/README.md)
- Runtime e setup local: [../development/QR_ORDERING_LOCAL_RUNTIME.md](../development/QR_ORDERING_LOCAL_RUNTIME.md)

---

## Funcionalidades por Sistema

### ERP Backend (`/backend/`)

| Funcionalidade | Descrição |
|----------------|-----------|
| **Cadastros** | Clientes, produtos, categorias, usuários |
| **Financeiro** | DRE, comissões, contas a pagar/receber |
| **Estoque** | Validade, lote, produção, ficha técnica |
| **Pedidos de Compra** | Requisição, aprovação, recebimento |
| **Gamificação** | Configuração, dashboard, regras |
| **Quiz** | IA, geração de perguntas, banco |
| **Eventos** | Agenda, couvert artístico |
| **i18n** | TranslationService, job OpenAI |
| **Autenticação** | JWT, OAuth2, usuários |

### ERP Frontend (`/frontend/` — Quasar/Vue 3)

| Funcionalidade | Descrição |
|----------------|-----------|
| **Dashboard** | Visão geral, KPIs |
| **Cadastros** | CRUD de clientes, produtos, categorias |
| **Financeiro** | DRE, comissões, contas |
| **Estoque** | Validade, produção |
| **Pedidos de Compra** | Fluxo completo |
| **Signage** | Templates, agendamento |
| **Gamificação** | Configuração, recompensas |
| **Quiz** | Banco de perguntas, IA |
| **Eventos** | Agenda, couvert |

### Espresso Backend (`/website_back/` — Java/Spring)

| Funcionalidade | Descrição |
|----------------|-----------|
| **Site** | Páginas institucionais (Hero, Quem Somos, Eventos, Galeria) |
| **Cardápio Digital** | Consulta, i18n (PT/EN/ES) |
| **Mesa Digital** | QR Code, sessões, self-checkout |
| **Delivery** | Pedido, Uber Direct, retirada |
| **Área do Cliente** | Pontos, rewards, favoritos |
| **Quiz Ao Vivo** | Sessões, WebSocket, ranking |
| **KDS** | Cozinha, expedição |
| **Waiter** | Garçom, chamados |
| **Temas** | White-label, assets |
| **Checkout Loja Física** | PDV/balcão (React) |

### Espresso Frontend (`/website_front/` — React + Capacitor)

| Funcionalidade | Descrição |
|----------------|-----------|
| **Site Institucional** | Hero, Quem Somos, Eventos, Galeria, Contato |
| **Cardápio Digital** | PT/EN/ES, categorias, produtos |
| **Mesa Digital** | QR Code, autoatendimento, pagamento |
| **Delivery** | Menu, carrinho, Uber, retirada |
| **Área do Cliente** | Pontos, rewards, favoritos, delivery em andamento |
| **Quiz** | Admin, player, ranking |
| **KDS** | Cozinha, filas, status |
| **Waiter** | Garçom, chamados, pagamentos |
| **Checkout Loja Física** | PDV/balcão (mesma tecnologia do cardápio digital) |
| **App Mobile** | Capacitor (Android/iOS) |

---

## Princípios de Separação

| Domínio | Onde Está | Por Que |
|---------|-----------|---------|
| **Backoffice Administrativo** | ERP (Quasar) | Produtividade, formulários complexos |
| **Experiência do Cliente** | Espresso (React) | Performance, UX, mobile |
| **Checkout Loja Física** | Espresso (React) | **Mesmo domínio que cardápio digital** — cliente e operador usam a mesma interface |
| **Dados Mestres** | ERP | Fonte da verdade |
| **Experiência, Tempo Real** | Espresso | WebSocket, performance |
