# Arquitetura

> **Status**: 📋 Documentação completa (atual + alvo + roadmap)
> **Governança da seção**: atualizar `arquitetura_atual.md`, `roadmap.md`, `gaps.md` e ADRs sempre que houver mudança estrutural relevante

---

## Visão Geral

Esta seção documenta a arquitetura de software do ecossistema Bakery, incluindo:

- **Arquitetura atual** (o que está implementado)
- **Arquitetura alvo** (para onde queremos ir)
- **Roadmap** (fases de evolução)
- **Gaps** (o que falta implementar)
- **Decisões** (ADRs — Architecture Decision Records)

Esta documentação assume como válido que **frontends e backends permanecem separados**. Não há plano de unificação nessa seção.

Guia editorial da secao:
- [GUIA_ESTILO.md](./GUIA_ESTILO.md)

---

## Documentos Principais

| Documento | Descrição | Status |
|-----------|-----------|--------|
| [arquitetura_atual.md](./arquitetura_atual.md) | **Estado atual real** — Frontends, backends, integrações | ✅ Completo |
| [arquitetura_alvo.md](./arquitetura_alvo.md) | **Estado futuro desejado** — Para onde vamos | 🎯 Referência |
| [roadmap.md](./roadmap.md) | **Fases de evolução** — Timeline com gatilhos | 📅 Atualizado |
| [gaps.md](./gaps.md) | **O que falta implementar** — Inventário de gaps | 📋 Completo |

---

## Decisões Arquiteturais (ADRs)

| ADR | Título | Status |
|-----|--------|--------|
| [ADR-001](./decisions/ADR-001-frontend-duo.md) | Frontend Duo (Quasar + React) | ✅ Aceito |
| [ADR-002](./decisions/ADR-002-jwt-compartilhado.md) | JWT Compartilhado | ✅ Aceito |
| [ADR-003](./decisions/ADR-003-usuarios-centralizados.md) | Usuários Centralizados no ERP | ✅ Aceito |
| [ADR-004](./decisions/ADR-004-i18n-strategy.md) | i18n via Job Agendado + OpenAI | ✅ Aceito |

**Ver todas**: [`decisions/`](./decisions/)

---

## Resumo da Arquitetura

### Estado Atual (Q1 2026) 🟢

```
┌─────────────────────────────────────────────────────────────┐
│  Frontends (2)                                              │
│  ├── frontend/ (Quasar/Vue 3) — Backoffice                  │
│  └── espresso_front/ (React) — Site, App, Mesa, Delivery    │
└─────────────────────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────────┐
│  Backends (2)                                               │
│  ├── backend/ (ERP) — Core, cadastros, financeiro          │
│  └── espresso_back/ — Experiência do cliente, quiz         │
└─────────────────────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────────┐
│  Banco de Dados (PostgreSQL por instância)                  │
└─────────────────────────────────────────────────────────────┘
```

### Arquitetura Alvo (2027+) 🎯

```
┌─────────────────────────────────────────────────────────────┐
│  Frontends Separados                                         │
│  ├── frontend/ (Quasar/Vue 3) — Backoffice                  │
│  └── espresso_front/ (React) — Experiência do cliente       │
└─────────────────────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────────┐
│  Backends Separados                                          │
│  ├── backend/ (ERP) — Core, cadastros, financeiro          │
│  └── espresso_back/ — Experiência do cliente, tempo real    │
└─────────────────────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────────┐
│  Contratos explícitos, i18n padronizado, health checks      │
└─────────────────────────────────────────────────────────────┘
```

---

## Roadmap de Evolução

| Fase | Timeline | Descrição | Status |
|------|----------|-----------|--------|
| **Fase 0** | Q1 2026 | Manter arquitetura atual | 🟢 Completa |
| **Fase 1** | Q2 2026 | Padronização (i18n, API Gateway) | 🟡 Em andamento |
| **Fase 2** | Q3-Q4 2026 | Evolução independente do ERP | 🔴 Pendente |
| **Fase 3** | Q1 2027 | Evolução independente do Espresso | 🔴 Pendente |
| **Fase 4** | Q2 2027 | Otimização e observabilidade | ⏳ Aguardando gatilho |

**Ver roadmap completo**: [`roadmap.md`](./roadmap.md)

---

## Gaps Principais

| ID | Gap | Prioridade | Fase |
|----|-----|------------|------|
| **B-002** | Entidades duplicadas (clientes, produtos) | 🔴 Alta | Fase 2 |
| **I-002** | Contratos de API não documentados | 🔴 Alta | Fase 1 |
| **Q-001** | Testes de integração inexistentes | 🔴 Alta | Fase 2 |
| **F-001** | 2 frontends separados | 🟡 Média | Fase 3 |
| **B-003** | i18n implementado de forma diferente | 🟡 Média | Fase 1 |

**Ver todos**: [`gaps.md`](./gaps.md)

---

## Regra de Atualizacao

Esta secao deve ser atualizada quando ocorrer qualquer uma das situacoes abaixo:

- criacao ou desativacao de servico relevante
- mudanca de fronteira entre `backend`, `espresso_back`, `frontend` e `espresso_front`
- introducao de contrato novo entre stacks
- decisao arquitetural que altere padrao de autenticacao, integracao, i18n, observabilidade ou deploy

Quando a mudanca for decisoria e nao apenas descritiva, ela deve gerar ou atualizar uma ADR em [`decisions/`](./decisions/).

## Cobertura Atual

Leitura resumida do nucleo documental da secao:

- `arquitetura_atual.md`: estado real da arquitetura
- `arquitetura_alvo.md`: direcao estrutural desejada
- `roadmap.md`: fases e gatilhos de evolucao
- `gaps.md`: backlog estrutural priorizado
- `decisions/`: historico de decisoes arquiteturais aceitas

---

## Estrutura de Diretórios

```
architecture/
├── README.md                        ← Este arquivo
├── arquitetura_atual.md             ← Estado atual real
├── arquitetura_alvo.md              ← Estado futuro desejado
├── roadmap.md                       ← Fases de evolução
├── gaps.md                          ← O que falta implementar
└── decisions/
    ├── README.md                    ← Índice de ADRs
    ├── ADR-001-frontend-duo.md      ← Por que 2 frontends?
    ├── ADR-002-jwt-compartilhado.md ← Por que JWT compartilhado?
    ├── ADR-003-usuarios-centralizados.md ← Por que usuários no ERP?
    ├── ADR-004-i18n-strategy.md     ← Por que OpenAI + job?
```

---

## Links Relacionados

| Seção | Descrição |
|-------|-----------|
| [infrastructure](../infrastructure/README.md) | Infraestrutura (deploy, ops, monitoring) |
| [modules](../modules/README.md) | Módulos funcionais do ERP |
| [development](../development/README.md) | Desenvolvimento e contribuição |

---

**Última atualização**: Março 2026  
**Próxima revisão**: Abril 2026 (após padronização i18n)
