# Arquitetura Alvo

> **Status**: 🎯 Arquitetura de Referência (Estado Futuro Desejado)  
> **Última atualização**: Março 2026

---

## Visão Geral

Este documento descreve a **arquitetura alvo** — para onde queremos evoluir sem romper a separação entre ERP e Digital Experience.

**Princípio Fundamental**: **Manter separação entre ERP e Digital Experience** — são domínios diferentes com propósitos diferentes.

Não existe cenário-alvo de frontend único, backend único ou monorepo.

**Para o estado atual**, ver [`arquitetura_atual.md`](./arquitetura_atual.md).

---

## Princípios Arquiteturais Alvo

| Princípio | Descrição | Status |
|-----------|-----------|--------|
| **Frontends Separados** | ERP (Quasar) ≠ Digital (React) — propósitos diferentes | ✅ Permanente |
| **Backends Separados** | ERP (Core) ≠ Espresso (Experiência) — domínios diferentes | ✅ Permanente |
| **Deploys Independentes** | Cada sistema evolui no seu ritmo | ✅ Permanente |
| **1 Instância por Cliente** | Isolamento total por cliente | ✅ Permanente |
| **JWT Compartilhado** | Autenticação única para ambos sistemas | ✅ Permanente |
| **APIs Bem Definidas** | Contratos claros entre ERP e Espresso | 🟡 Documentar |
| **i18n Padronizado** | Mesma estratégia, mesma fonte de verdade | 🟡 Padronizar |
| **Health Checks Padronizados** | Resposta consistente para monitoramento | 🟡 Padronizar |
| **Observabilidade Básica** | Logs, métricas e runbooks mínimos | 🟡 Evoluir |

---

## Arquitetura Alvo (Diagrama)

```
┌─────────────────────────────────────────────────────────────┐
│                    ERP Frontend (Quasar/Vue 3)              │
│                    Backoffice Administrativo                │
├─────────────────────────────────────────────────────────────┤
│  • Dashboard                                                │
│  • Cadastros (clientes, produtos, categorias)               │
│  • Financeiro (DRE, comissões, contas)                      │
│  • Estoque (validade, lote, produção)                       │
│  • Pedidos de Compra                                        │
│  • Signage Digital                                          │
│  • Gamificação (configuração)                               │
│  • Quiz (banco de perguntas, IA)                            │
│  • Eventos (agenda, couvert)                                │
│                                                             │
│  → Foco: Produtividade administrativa                       │
│  → Usuários: Equipe interna, administradores                │
│  → Tech: Quasar (componentes prontos, produtividade)        │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│          Digital Experience Frontend (React + Capacitor)    │
│          Experiência do Cliente (Customer-Facing)           │
├─────────────────────────────────────────────────────────────┤
│  • Site Institucional (Hero, Quem Somos, Eventos)           │
│  • Cardápio Digital (PT/EN/ES)                              │
│  • Mesa Digital (QR Code, self-checkout)                    │
│  • Delivery (pedido, rastreamento)                          │
│  • Área do Cliente (pontos, rewards, favoritos)             │
│  • Quiz Ao Vivo (ranking, WebSocket)                        │
│  • KDS / Waiter (cozinha, garçom)                           │
│                                                             │
│  → Foco: Experiência do cliente, engajamento                │
│  → Usuários: Clientes finais                                │
│  → Tech: React + Capacitor (PWA, mobile, performance)       │
└─────────────────────────────────────────────────────────────┘
           │                    │
           │ JWT Compartilhado  │
           │ APIs REST          │
           ▼                    ▼
┌─────────────────────────────────────────────────────────────┐
│                    ERP Backend (Java/Spring)                │
│                    Core de Negócio                          │
├─────────────────────────────────────────────────────────────┤
│  • Cadastros (clientes, produtos, categorias)               │
│  • Financeiro (DRE, comissões, contas a pagar/receber)      │
│  • Estoque (validade, lote, produção, ficha técnica)        │
│  • Pedidos de Compra                                        │
│  • Gamificação (configuração, dashboard)                    │
│  • Quiz (IA, geração de perguntas)                          │
│  • Eventos (agenda, couvert)                                │
│  • i18n (TranslationService, job OpenAI)                    │
│  • Autenticação (JWT, OAuth2)                               │
│                                                             │
│  → Fonte da verdade para dados mestres                      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│          Espresso Backend (Java/Spring)                     │
│          Experiência do Cliente                             │
├─────────────────────────────────────────────────────────────┤
│  • Site (páginas institucionais)                            │
│  • Cardápio (consulta, i18n)                                │
│  • Mesa (sessões, QR Code, self-checkout)                   │
│  • Delivery (pedido, Uber Direct)                           │
│  • Área do Cliente (pontos, rewards, favoritos)             │
│  • Quiz (sessões ao vivo, WebSocket, ranking)               │
│  • KDS (cozinha, expedição)                                 │
│  • Waiter (garçom, chamados)                                │
│  • Temas (white-label, assets)                              │
│  • i18n (I18nService, consumo do ERP)                       │
│                                                             │
│  → Foco: Experiência, performance, engajamento              │
│  → Não tem tabela de usuários (usa ERP)                     │
└─────────────────────────────────────────────────────────────┘
```

---

## Comparação: Atual vs. Alvo

| Dimensão | Atual | Alvo | Gap |
|----------|-------|------|-----|
| **Frontends** | 2 (Quasar + React) | 2 (Quasar + React) | ✅ Permanente |
| **Backends** | 2 (ERP + Espresso) | 2 (ERP + Espresso) | ✅ Permanente |
| **Repositórios** | 2 (bakery, espresso) | 2 (bakery, espresso) | ✅ Permanente |
| **Deploys** | 2 deploys separados | 2 deploys separados | ✅ Permanente |
| **Instância por Cliente** | 1 instância | 1 instância | ✅ Permanente |
| **Banco de Dados** | 1 por instância | 1 por instância | ✅ Permanente |
| **Autenticação** | JWT compartilhado | JWT compartilhado | ✅ Permanente |
| **APIs** | Não documentadas | OpenAPI/Swagger | 🟡 Documentar |
| **i18n** | Implementações diferentes | Estratégia padronizada | 🟡 Padronizar |
| **Health Check** | Parcialmente padronizado | `/health` consistente | 🟡 Padronizar |
| **Observabilidade** | Básica | Logs e métricas mínimas | 🟡 Evoluir |

---

## O Que **NÃO** Vamos Fazer

| Item | Por Que Não |
|------|-------------|
| **Unificar frontends** | ERP (backoffice) ≠ Digital (customer-facing) — públicos e requisitos diferentes |
| **Unificar backends** | Domínios diferentes: Core de Negócio ≠ Experiência do Cliente |
| **Monorepo** | 2 repositórios = independência de deploy e evolução |
| **Microserviços** | Complexidade desnecessária para 2-10 clientes |
| **Kubernetes** | Overkill para estado atual (Docker Compose funciona bem) |
| **Monolito modular** | Mistura domínios e elimina a fronteira entre ERP e experiência digital |

---

## Critérios para Manter Separação

### Frontends Separados ✅

| Critério | ERP Frontend | Digital Frontend |
|----------|--------------|------------------|
| **Público** | Equipe interna | Clientes finais |
| **Requisitos** | Produtividade, formulários | Performance, UX, mobile |
| **Tech** | Quasar (componentes prontos) | React (flexibilidade, Capacitor) |
| **Deploy** | Independente | Independente |
| **Equipe** | Pode ser diferente | Pode ser diferente |

### Backends Separados ✅

| Critério | ERP Backend | Espresso Backend |
|----------|-------------|------------------|
| **Domínio** | Core de negócio | Experiência do cliente |
| **Dados** | Fonte da verdade | Consome do ERP |
| **Complexidade** | Regras fiscais, estoque | Performance, WebSocket |
| **Escalabilidade** | 1 instância por cliente | Pode escalar separado |
| **Usuários** | Tabela própria | Usa ERP (sem tabela) |

---

## Onde Focar (Gaps Reais)

### Prioridade Alta (Q2-Q3 2026)

| Gap | Descrição | Sistema |
|-----|-----------|---------|
| **APIs não documentadas** | OpenAPI/Swagger | Ambos |
| **i18n diferente** | Unificar estratégia | ERP + Espresso |
| **Testes de integração** | Suite de testes | Ambos |
| **Rollback manual** | Automatizar | Infra |
| **Health checks** | Padronizar | Ambos |

### Prioridade Média (Q4 2026)

| Gap | Descrição | Sistema |
|-----|-----------|---------|
| **API Gateway** | Nginx como reverse proxy único | Infra |
| **Contratos de API** | Versionamento, backward compatibility | Ambos |
| **Logs estruturados** | JSON, centralizado | Ambos |

### Prioridade Baixa (2027+)

| Gap | Descrição | Sistema |
|-----|-----------|---------|
| **Observabilidade** | Grafana, métricas | Infra |

---

## Roadmap de Evolução

| Fase | Timeline | Descrição | Status |
|------|----------|-----------|--------|
| **Fase 0** | Q1 2026 | Manter arquitetura atual (separação) | 🟢 Completa |
| **Fase 1** | Q2 2026 | Padronização (i18n, APIs, health checks) | 🟡 Em andamento |
| **Fase 2** | Q3-Q4 2026 | Evolução independente do ERP | 🔴 Pendente |
| **Fase 3** | Q3-Q4 2026 | Evolução independente do Espresso | 🔴 Pendente |
| **Fase 4** | Q1 2027 | Otimização, logs e monitoramento | 🔴 Pendente |

**Ver roadmap completo**: [`roadmap.md`](./roadmap.md)

---

## Decisões Arquiteturais (ADRs)

| ADR | Título | Status |
|-----|--------|--------|
| `ADR-001` | Frontend Duo (Quasar + React) | ✅ Aceito |
| `ADR-002` | JWT Compartilhado | ✅ Aceito |
| `ADR-003` | Usuários Centralizados no ERP | ✅ Aceito |
| `ADR-004` | i18n via Job Agendado + OpenAI | ✅ Aceito |

**Ver decisões completas**: [`decisions/`](./decisions/)

---

## Riscos da Arquitetura Alvo

| Risco | Impacto | Mitigação |
|-------|---------|-----------|
| **Duplicação de esforço** | Médio | Comunicação entre equipes, documentação |
| **i18n inconsistente** | Baixo | Padronizar estratégia (Fase 1) |
| **APIs não documentadas** | Médio | OpenAPI/Swagger (Fase 1) |
| **Deploys manuais** | Baixo | Automatizar scripts (Infra Fase 2) |

---

## Documentos Relacionados

| Documento | Descrição |
|-----------|-----------|
| [`arquitetura_atual.md`](./arquitetura_atual.md) | Estado atual real |
| [`roadmap.md`](./roadmap.md) | Roadmap de evolução |
| [`gaps.md`](./gaps.md) | O que falta implementar |
| [`decisions/`](./decisions/) | Architecture Decision Records |
| [`../infrastructure/arquitetura_atual.md`](../infrastructure/arquitetura_atual.md) | Infraestrutura atual |

---

**Última atualização**: Março 2026  
**Próxima revisão**: Junho 2026 (após Fase 1 de Padronização)
