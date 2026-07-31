# Changelog de Documentação

## 2026-03-27 — Consolidação Estrutural Completa

### Resumo

Reorganização completa da estrutura de documentação para eliminar redundâncias, consolidar documentos legados do projeto Espresso e estabelecer critérios claros de classificação.

### Mudanças Estruturais

#### Novas Pastas Criadas

- `03-infrastructure/_transient/` — Documentos transitórios de migração (posteriormente movidos para `_archive/`)
- `04-modules/backoffice/signage/` — Agrupamento de 11 arquivos de Signage Digital
- `05-digital-experience/espresso/` — Documentação específica do app Espresso
- `06-integrations/_legacy/` — Integrações descontinuadas ou em transição
- `08-development/component-analysis/` — Análises técnicas de componentes Vue/JS
- `10-functional-scopes/` — Escopos funcionais transversais
- `10-functional-scopes/quiz/` — Módulo de Quiz/Perguntas
- `assets/reference/` — Mídia de referência (galeria de imagens)
- `assets/reference/galeria/` — Acervo de imagens (ambiente, bebidas, comidas)

#### Pastas Renomeadas

- `04-modules/escopo_funcionalidades/` → `04-modules/funcionalidades/`

#### Pasta Eliminada

- `espresso/` — Desmembrada para destinos corretos

---

### Limpeza Final (Pós-Consolidação)

#### Pastas Vazias Removidas

- `04-modules/cadastros/`
- `04-modules/gestao-central/`
- `04-modules/producao/`
- `05-digital-experience/gamification/`
- `05-digital-experience/site-app-espresso/`

#### Pastas Recriadas (com documentação de roadmap)

- `06-integrations/whatsapp/` — README com status "Planejado" e roadmap de 5 fases
- `06-integrations/openai/` — README com status "Parcialmente Implementado" e funcionalidades
- `06-integrations/payment-gateways/` — README com status "Implementado" e detalhes de gateways

**Justificativa**: Pastas fazem parte da estrutura definida e devem permanecer com documentação de status/roadmap.

#### Arquivos Renomeados

- `03-infrastructure/pragmatic_deployment_architecture.md` → `03-infrastructure/arquitetura_deployment_pragmatica.md`

#### Documentos Reescritos

- `05-digital-experience/espresso/README.md` — Reescrito para remover contexto "Viking Pub" e documentar app Espresso de forma genérica

---

### Movimentações de Arquivos

#### De `01-overview/`
- `ROOT_LAYOUT.md` → `08-development/` (é sobre organização de código)
- `COMPOSICAO_PROJETO.md` → **Fundido** em `ECOSSISTEMA_DETALHADO.md`

#### De `02-architecture/`
- `MAPEAMENTO_MELHORIAS_BASETABLENEW.md` → `08-development/component-analysis/` (análise de componente)

#### De `03-infrastructure/`
- `ESPRESSO_MOVE_PREP.md` → `_archive/` (transitório expirado)

#### De `04-modules/`
- `sample_escopo.md` → `04-modules/dashboard/` (é sobre relatório de vendas)
- Arquivos de Signage → `04-modules/backoffice/signage/`

#### De `08-development/`
- `opencode-runners.md` → `09-support/` (ferramenta específica)
- `fluxo_importacao_produtos.md` → **Fundido** em `guia_importacao_produtos.md`
- `INTERNACIONALIZACAO_CARDAPIO.md` → **Fundido** em `I18N_ENTITY_TRANSLATIONS.md`

#### De `09-support/`
- `birthday_notifications_scope.md` → `10-functional-scopes/` (é escopo funcional)
- `AcessoServidorVilla.txt` → `09-support/AcessoServidorVilla.md` (renomeado para .md)
- `vantagens_framework.txt` → `09-support/vantagens_framework.md` (renomeado para .md)

#### Da pasta legada `espresso/` (desmembrada)
- `android_theme_scope.md` → `05-digital-experience/espresso/`
- `DATABASE_SETUP.md` → `05-digital-experience/espresso/`
- `delivery-i18n-inventory.md` → `05-digital-experience/delivery/`
- `import_questions_feature_spec.md` → `10-functional-scopes/quiz/`
- `sorteio_manual_rewards_scope.md` → `10-functional-scopes/`
- `ui_admin_rewards_scope.md` → `10-functional-scopes/`
- `uber_delivery_integration.md` → `06-integrations/_legacy/`
- `README.md` → `05-digital-experience/espresso/` (para reescrever)
- `galeria/` → `assets/reference/galeria/`
- `03-infrastructure/BAKERY_MOVE_PREP.md` → `_archive/`
- `REDEPLOY_EXECUTION_PLAN.md` → `_archive/`
- `REDEPLOY_PLAN.md` → `_archive/`
- `quiz-upgrade-notes.md` → `_archive/`
- `Screenshot*.png` → `_archive/evidence/`

---

### Novos Documentos Criados

#### `06-integrations/push-notifications.md`
Arquitetura de push notifications (FCM) para app Espresso. Reestruturado a partir de `espresso_push_rewards_context.md`.

#### `06-integrations/rewards-system.md`
Sistema de recompensas e sorteios manuais. Reestruturado a partir de `espresso_push_rewards_context.md`.

---

### Fusões Realizadas

1. **Visão do Ecossistema**
   - `COMPOSICAO_PROJETO.md` + `ECOSSISTEMA_DETALHADO.md` → `ECOSSISTEMA_DETALHADO.md` (atualizado com seção 12)

2. **Guias de Importação**
   - `fluxo_importacao_produtos.md` + `guia_importacao_produtos.md` → `guia_importacao_produtos.md` (consolidado)

3. **Internacionalização**
   - `INTERNACIONALIZACAO_CARDAPIO.md` + `I18N_ENTITY_TRANSLATIONS.md` → `I18N_ENTITY_TRANSLATIONS.md` (expandido)

4. **Push Notifications & Rewards**
   - `espresso_push_rewards_context.md` → `push-notifications.md` + `rewards-system.md` (dividido por domínio)

---

### Documentos Arquivados

- `ESPRESSO_MOVE_PREP.md` — Migração completada
- `BAKERY_MOVE_PREP.md` — Migração completada
- `REDEPLOY_EXECUTION_PLAN.md` — Plano antigo de redeploy
- `REDEPLOY_PLAN.md` — Planejamento antigo de redeploy
- `quiz-upgrade-notes.md` — Nota histórica de upgrade
- `Screenshot*.png` — Screenshot solta (evidência)

---

### Estatísticas

| Métrica | Antes | Depois |
|---------|-------|--------|
| Total de arquivos .md | 82 | 86 (+CHANGELOG.md + 3 READMEs) |
| Pastas | 24 | 29 |
| Documentos fundidos | — | 4 fusões |
| Documentos reestruturados | — | 1 dividido em 2 |
| Documentos arquivados | 2 | 11 |
| Pasta `espresso/` | 14 arquivos | Eliminada |
| Pastas vazias removidas | — | 5 |
| Pastas recriadas (com README) | — | 3 |
| Arquivos renomeados | — | 1 |
| READMEs reescritos | — | 1 |
| **Legado espresso/docs absorvido** | 12 .md + 30+ imagens | 100% integrado |

---

### Critérios de Classificação Estabelecidos

| Seção | O que pertence | O que não pertence |
|-------|----------------|-------------------|
| `01-overview` | Visão macro, ecossistema, roadmap, MVP | Detalhes técnicos, escopos específicos |
| `02-architecture` | ADRs, visão técnica geral | Análise de componente específico |
| `03-infrastructure` | Deploy, ops, monitoramento | Planos de migração (vão para `_archive`) |
| `04-modules` | Escopos de módulos específicos | Escopos transversais |
| `05-digital-experience` | App, site, cardápio digital, mesa digital | Backend/i18n |
| `06-integrations` | Integrações ativas e canônicas | Integrações legadas (`_legacy/`) |
| `08-development` | Guias, convenções, análises técnicas | Ferramentas específicas |
| `09-support` | Troubleshooting, FAQs, procedimentos | Escopos funcionais |
| `10-functional-scopes` | Escopos transversais não atrelados a módulo | Escopos de módulo específico |
| `_archive` | Documentos obsoletos, históricos | Documentação viva |

---

### Próximos Passos Sugeridos

1. **Validar links internos** — Verificar se todos os links entre documentos estão funcionando
2. **Preencher READMEs de seção** — Algumas pastas podem precisar de READMEs específicos
3. **Estabelecer processo de revisão** — Definir como nova documentação será revisada antes de merge

---

### ✅ Fase 2 Concluída: Absorção do Legado Espresso

**Data**: Março 2026

A documentação legada de `/home/gregorio/git/espresso/docs` foi **totalmente absorvida**:

| Categoria | Arquivos | Destino |
|-----------|----------|---------|
| **Movidos** | 12 arquivos .md | Várias seções do `bakery/docs` |
| **Fundidos** | 4 documentos | Consolidados em documentos canônicos |
| **Reestruturados** | 1 documento | Dividido em 2 novos arquivos |
| **Arquivados** | 4 documentos | `_archive/` (transitórios/históricos) |
| **Mídia** | 30+ imagens | `assets/reference/galeria/` |
| **Descartados** | `tmp/`, `Screenshot*.png` | Lixo removido |

**Resultado**: `espresso/docs` foi esvaziado e está pronto para remoção ou reutilização futura.

---

### ✅ Fase 3 Concluída: Estruturação por Domínio de Negócio

**Data**: Março 2026

Nova documentação criada para refletir funcionalidades transversais:

#### Novos Documentos de Arquitetura

| Documento | Descrição |
|-----------|-----------|
| `02-architecture/VISAO_GERAL_INTEGRACOES.md` | Como ERP ↔ Site ↔ App se conectam |

#### Novos Módulos Funcionais (04-modules/)

| Módulo | Documento | Status |
|--------|-----------|--------|
| **Gamificação** | `gamificacao/README.md` | ✅ Implementado |
| **Quiz** | `quiz/README.md` | ✅ Implementado |
| **Eventos** | `eventos/README.md` | ✅ Implementado |
| **Temas** | `temas/README.md` | ✅ Implementado |

#### Nova Estrutura de Site (05-digital-experience/)

| Documento | Descrição |
|-----------|-----------|
| `site/README.md` | Visão geral do site institucional |
| `site/estrutura-paginas.md` | Hero, Quem Somos, Eventos, Galeria, etc. |

---

### Estatísticas Atualizadas

| Métrica | Antes | Depois |
|---------|-------|--------|
| Total de arquivos .md | 82 | 92 (+10 documentos novos) |
| Pastas | 24 | 34 (+10 pastas de módulos) |
| Documentos fundidos | — | 4 fusões |
| Documentos reestruturados | — | 1 dividido em 2 |
| Documentos arquivados | 2 | 11 |
| Pasta `espresso/` | 14 arquivos | Eliminada |
| Pastas vazias removidas | — | 5 |
| Pastas recriadas (com README) | — | 3 (whatsapp, openai, payment-gateways) |
| **Novos módulos funcionais** | — | 4 (gamificação, quiz, eventos, temas) |
| **Documentação de site** | — | 2 documentos completos |
| **Legado espresso/docs absorvido** | 12 .md + 30+ imagens | 100% integrado |

---

### Notas

- Esta consolidação preparou a base para absorver documentação futura de forma organizada
- A estrutura agora segue critérios claros de classificação
- Documentos transitórios foram movidos para `_archive/` após conclusão da migração

---

### ✅ Fase 4 Concluída: Criação do Módulo Autoatendimento e Fidelização

**Data**: Março 2026

Criado novo módulo para consolidar o canal digital:

#### Novo Módulo: Autoatendimento e Fidelização

| Documento | Descrição |
|-----------|-----------|
| `05-digital-experience/autoatendimento-fidelizacao/README.md` | Visão geral do canal digital |
| `05-digital-experience/autoatendimento-fidelizacao/mvp/` | MVP Pedidos via QR |

#### Decisão de Estrutura

- `01-overview/` **não foi recriado** — conteúdo já está consolidado no README da raiz
- Visão de negócio está em `docs/README.md` (seção "Visão Inicial")
- Documentos históricos de visão foram para `_archive/`

#### Estrutura Final

```
docs/
├── README.md                         ← Visão inicial do produto
├── 05-digital-experience/
│   ├── autoatendimento-fidelizacao/  ← NOVO: Canal digital completo
│   │   ├── README.md
│   │   └── mvp/
│   ├── site/
│   ├── delivery/
│   ├── mesa-digital/
│   ├── menu-digital/
│   └── espresso/
```

---

### Estatísticas Atualizadas (Fase 4)

| Métrica | Antes | Depois |
|---------|-------|--------|
| Total de arquivos .md | 92 | 93 |
| Pastas | 34 | 35 |
| **Novos módulos** | — | 1 (autoatendimento-fidelizacao) |
| **MVP movido** | 01-overview/mvp/ | 05-digital-experience/autoatendimento-fidelizacao/mvp/ |

---

### ✅ Fase 5 Concluída: Correção da Documentação de Arquitetura

**Data**: Março 2026

Documento `VISAO_GERAL_INTEGRACOES.md` atualizado para refletir implementação real:

#### Serviços Documentados (Adicionados)

| Serviço | Camada | Documentado Em |
|---------|--------|----------------|
| `TranslationService` | ERP | Seção 4 (i18n) |
| `TranslationJobService` | ERP | Seção 4 (i18n) |
| `I18nService` | espresso_back | Seção 4 (i18n) |
| `QuizGenerationService` | ERP | Seção 2 (Quiz) |
| `EventoEspressoService` | ERP | Seção 3 (Eventos) |
| `DeliveryOrderService` | ERP | Seção 7 (Delivery) |
| `DeliveryKdsService` | ERP | Seção 7 (Delivery) |
| `ThemeService` | espresso_back | Seção 5 (Temas) |
| `ThemeNotificationService` | espresso_back | Seção 5 (Temas) |

#### Divisões Esclarecidas

| Funcionalidade | Divisão Documentada |
|----------------|---------------------|
| **Quiz** | ERP (IA) + espresso_back (sessão/jogo) |
| **Eventos** | ERP (cadastro) + espresso_back (exibição) |
| **i18n** | ERP (traduções) + espresso_back (consumo) |
| **Temas** | ERP (tradução por tema) + espresso_back (gestão) |
| **Delivery** | ERP (pedidos, KDS, Uber) + espresso_back (frontend) |

#### Estrutura do Documento

```
VISAO_GERAL_INTEGRACOES.md
├── 1. ERP como Fonte da Verdade (tabela expandida)
├── 2. Quiz Ao Vivo (divisão IA ↔ jogo)
├── 3. Eventos (divisão cadastro ↔ exibição)
├── 4. Internacionalização (i18n) ← NOVO
├── 5. Temas White-Label (atualizado)
├── 6. Cardápio (atualizado)
├── 7. Delivery ← NOVO
└── Contratos de API Principais
```

---

### ✅ Fase 6 Concluída: Adição dos Frontends à Arquitetura

**Data**: Março 2026

Correção das lacunas identificadas na verificação de arquitetura:

#### Frontends Documentados

| Frontend | Tecnologia | Propósito | Caminho | Páginas Principais |
|----------|------------|-----------|---------|-------------------|
| **ERP Frontend** | Quasar/Vue 3 | Backoffice administrativo | `/frontend/` | Dashboard, Produção, Validade, Pedidos Compra, Admin, Mobile |
| **Digital Experience** | React + Capacitor | Site, app, mesa digital | `/espresso_front/` | Index, Menu, Mesa, AreaCliente, Delivery, KDS, Waiter, Quiz |

#### Documentos Atualizados

| Documento | Mudanças |
|-----------|----------|
| `TECHNICAL_OVERVIEW.md` | Adicionado diagrama completo com todos os frontends e páginas |
| `VISAO_GERAL_INTEGRACOES.md` | Adicionada seção "Frontends" e "Backends", diagrama atualizado |

#### Estrutura Completa Agora

```
bakery/
├── backend/              ← ERP Backend (Java/Spring)
│   └── GamificacaoService, QuizGenerationService, ...
│
├── frontend/             ← ERP Frontend (Quasar/Vue 3)
│   └── DashboardPage, ProducaoPage, ValidadeDashboardPage, ...
│
├── espresso_back/        ← Digital Experience Backend (Java/Spring)
│   └── QuizSessionService, ThemeService, EventoService, ...
│
├── espresso_front/       ← Digital Experience Frontend (React + Capacitor)
│   ├── Index.tsx (Site)
│   ├── MenuPage.tsx (Cardápio)
│   ├── MesaPage.tsx (Mesa Digital)
│   ├── AreaCliente.tsx (Área do Cliente)
│   ├── DeliveryMenuPage.tsx (Delivery)
│   ├── KdsPage.tsx (KDS)
│   ├── WaiterPage.tsx (Waiter App)
│   ├── QuizAdmin.tsx, QuizPlayer.tsx (Quiz)
│   └── capacitor.config.ts (App Android/iOS)
│
├── ops/                  ← Infraestrutura (Docker Compose, configs)
└── deploy/               ← Scripts de deploy
```

#### Lacunas Preenchidas

| Lacuna | Status |
|--------|--------|
| `/frontend/` (Quasar) não era mencionado | ✅ Documentado |
| `espresso_front` dualidade Web/Mobile não clara | ✅ Esclarecido (React + Capacitor) |
| Mesa Digital sem implementação mapeada | ✅ Documentado como rota `/m/:slug` |
| Ops/Deploy sem referência | ✅ Em `03-infrastructure/` (correto) |

---

### ✅ Fase 7 Concluída: Documentação de Infraestrutura "Chão de Fábrica"

**Data**: Março 2026

Reescrita completa da documentação de infraestrutura para refletir a **implementação real**, removendo arquitetura multi-tenant (Traefik) que não existe em produção.

#### Documentos Criados

| Documento | Descrição |
|-----------|-----------|
| `arquitetura_atual.md` | Estado atual real (1 VPS, 2 clientes, Nginx + Docker Compose) |
| `deployment/deploy-bakery.md` | Deploy do ERP (script existente documentado) |
| `deployment/deploy-espresso.md` | Deploy do Espresso (script existente documentado) |
| `monitoring/README.md` | Monitoramento atual + roadmap |
| `security/ssl-certbot.md` | Gerenciamento de SSL/TLS |
| `scalability/README.md` | Limites atuais e plano de escalabilidade |
| `roadmap.md` | Roadmap de evolução (Fases 1-5) |

#### Documentos Arquivados

| Documento | Motivo |
|-----------|--------|
| `arquitetura_deployment_pragmatica.md` | Descrevia arquitetura multi-tenant (Traefik) não implementada |

#### Mudança de Filosofia

| Antes | Depois |
|-------|--------|
| Documentação como "arquitetura ideal" | Documentação como "estado real + roadmap" |
| Traefik + Nginx (multi-tenant) | Nginx + Docker Compose (single VPS) |
| 15+ instâncias (meta distante) | 2 clientes (atual) → 10 (limite prático) |
| Monitoramento "concluído via Cron" | Monitoramento básico + roadmap claro |

#### Roadmap Definido

| Fase | Timeline | Clientes | Arquitetura |
|------|----------|----------|-------------|
| **Atual** | Q1 2026 | 2 | 1 VPS, Docker Compose |
| **Fundações** | Q2 2026 | 2-4 | UptimeRobot, backup auto |
| **Resiliência** | Q3 2026 | 4-6 | Health checks + alertas |
| **Observabilidade** | Q4 2026 | 6-10 | Grafana, logs JSON |
| **Multi-VPS** | 2027 | 10+ | 2-3 VPS + LB |
| **Kubernetes** | 2027+ | 20+ | Cluster K8s |

#### Estrutura Final de `03-infrastructure/`

```
03-infrastructure/
├── README.md                        ← Atualizado
├── arquitetura_atual.md             ← NOVO (estado real)
├── roadmap.md                       ← NOVO (evolução planejada)
├── deployment/
│   ├── deploy-bakery.md             ← NOVO
│   └── deploy-espresso.md           ← NOVO
├── monitoring/
│   └── README.md                    ← NOVO
├── security/
│   └── ssl-certbot.md               ← NOVO
└── scalability/
    └── README.md                    ← NOVO
```

---

### Estatísticas Atualizadas (Geral)

| Métrica | Antes | Depois |
|---------|-------|--------|
| Total de arquivos .md | 93 | 100 |
| Pastas | 35 | 35 |
| **Documentos de arquitetura** | 3 | 4 (+arquitetura_atual.md) |
| **Documentos de deploy** | 0 | 2 |
| **Documentos de monitoring** | 0 | 1 |
| **Documentos de security** | 0 | 1 |
| **Documentos de scalability** | 0 | 1 |
| **Roadmap** | 0 | 1 |
| **Documentos arquivados** | 11 | 12 |

---

### ✅ Fase 8 Concluída: Reestruturação de Arquitetura de Software

**Data**: Março 2026

Reestruturação completa da documentação de arquitetura de software para seguir a mesma filosofia "chão de fábrica + roadmap" aplicada em infraestrutura.

#### Documentos Criados

| Documento | Descrição |
|-----------|-----------|
| `arquitetura_atual.md` | Renomeado de `TECHNICAL_OVERVIEW.md` |
| `arquitetura_alvo.md` | NOVO — Arquitetura alvo (frontend/backend unificado, multi-tenant) |
| `roadmap.md` | NOVO — Fases de evolução arquitetural (Fases 1-4) |
| `gaps.md` | NOVO — Inventário de gaps (18 gaps identificados) |
| `decisions/README.md` | Atualizado — Índice de ADRs com template |
| `decisions/ADR-001-frontend-duo.md` | NOVO — Por que 2 frontends? |
| `decisions/ADR-002-jwt-compartilhado.md` | NOVO — Por que JWT compartilhado? |
| `decisions/ADR-003-usuarios-centralizados.md` | NOVO — Por que usuários no ERP? |
| `decisions/ADR-004-i18n-strategy.md` | NOVO — Por que OpenAI + job agendado? |

#### Estrutura Final de `02-architecture/`

```
02-architecture/
├── README.md                        ← Atualizado (visão geral)
├── arquitetura_atual.md             ← Estado atual real
├── arquitetura_alvo.md              ← Estado futuro desejado
├── roadmap.md                       ← Fases de evolução (🟢🟡🔴⏳)
├── gaps.md                          ← 18 gaps identificados
└── decisions/
    ├── README.md                    ← Índice de ADRs
    ├── ADR-001-frontend-duo.md      ← Frontend Duo
    ├── ADR-002-jwt-compartilhado.md ← JWT Compartilhado
    ├── ADR-003-usuarios-centralizados.md ← Usuários no ERP
    ├── ADR-004-i18n-strategy.md     ← i18n OpenAI
```

#### Gaps Identificados

| Categoria | Gaps | Prioridade Alta |
|-----------|------|-----------------|
| **Frontend** | 4 gaps | 0 |
| **Backend** | 4 gaps | 2 (entidades duplicadas, APIs não padronizadas) |
| **Banco de Dados** | 3 gaps | 0 |
| **Integrações** | 4 gaps | 2 (API Gateway, contratos) |
| **DevOps** | 4 gaps | 1 (rollback manual) |
| **Qualidade** | 4 gaps | 2 (testes de integração, contrato) |

#### Roadmap de Arquitetura

| Fase | Timeline | Descrição | Status |
|------|----------|-----------|--------|
| **Fase 0** | Q1 2026 | Manter arquitetura atual | 🟢 Completa |
| **Fase 1** | Q2 2026 | Padronização (i18n, API Gateway) | 🟡 Em andamento |
| **Fase 2** | Q3-Q4 2026 | Unificar Backend | 🔴 Pendente |
| **Fase 3** | Q1 2027 | Unificar Frontend | 🔴 Pendente |
| **Fase 4** | Q2 2027 | Multi-Tenant | ⏳ Aguardando gatilho |

---

### Estatísticas Atualizadas (Geral)

| Métrica | Antes | Depois |
|---------|-------|--------|
| Total de arquivos .md | 100 | **110** |
| Pastas | 35 | 35 |
| **Documentos de arquitetura (infra)** | 8 | 8 |
| **Documentos de arquitetura (software)** | 3 | **8** |
| **ADRs escritos** | 0 | **5** |
| **Gaps identificados** | 0 | **18** |
| **Documentos arquivados** | 12 | 13 |
