# ADR-001: Frontend Duo (Quasar + React)

**Status**: ✅ Aceito (atual)  
**Data**: Março 2026  
**Revisão**: Junho 2026 (ou quando houver mudança real de requisitos)

---

## Contexto

O ecossistema possui **2 frontends separados**:

1. **`frontend/`** — Quasar/Vue 3
   - Backoffice administrativo (ERP)
   - Dashboard, cadastros, financeiro, estoque

2. **`espresso_front/`** — React + Capacitor
   - Site institucional
   - App móvel (Android/iOS)
   - Mesa digital (QR Code)
   - Delivery
   - Área do cliente
   - Quiz ao vivo

## Decisão

**Manter 2 frontends separados** no estado atual (Q1 2026).

### Justificativa

| Fator | Decisão |
|-------|---------|
| **Histórico** | Espresso foi desenvolvido primeiro (React) |
| **Equipe** | Equipes diferentes trabalhando em paralelo |
| **Requisitos** | Backoffice vs. Customer-facing têm necessidades diferentes |
| **Capacitor** | React tem melhor suporte a Capacitor (mobile) |
| **Quasar** | Mais produtivo para backoffice (componentes prontos) |

## Consequências

### Positivas ✅

- Equipes podem trabalhar em paralelo
- Cada frontend usa tecnologia adequada ao propósito
- Deploy independente (ERP não bloqueia Espresso)
- Quasar acelera desenvolvimento de backoffice
- React + Capacitor é maduro para mobile

### Negativas 🔴

- Duplicação de componentes (forms, tabelas, modais)
- 2 pipelines de CI/CD para manter
- 2 conjuntos de dependências
- Curva de aprendizado para novos devs
- i18n precisa ser padronizado entre sistemas

### Neutras ⚪

- 2 repositórios de código
- 2 processos de build
- Necessidade de API Gateway futuro

---

## Quando Revisar

Esta decisão deve ser revisada quando:

- [ ] **6+ clientes ativos** (complexidade de deploy)
- [ ] **Equipe compartilhada** (mesmos devs atuando nos dois fronts)
- [ ] **Duplicação > 30%** (muitos componentes repetidos)
- [ ] **Deploy > 30 min** (complexidade operacional)

---

## Alternativas Consideradas

### Alternativa 1: Unificar para React

**Prós**:
- Código único
- Mesma equipe
- mesma estratégia de i18n

**Contras**:
- Perder produtividade do Quasar no backoffice
- Migração custosa
- Curva de aprendizado

### Alternativa 2: Unificar para Vue 3

**Prós**:
- Código único
- Quasar no backoffice mantido

**Contras**:
- React + Capacitor → Vue + Capacitor (menos maduro)
- Migração custosa do espresso_front
- Equipe React precisa aprender Vue

### Alternativa 3: Monorepo (manter tecnologias)

**Prós**:
- Código no mesmo lugar
- Compartilhamento de componentes
- Deploy coordenado

**Contras**:
- Complexidade de build
- Ainda 2 tecnologias
- Tooling complexo

---

## Status Atual

**Status**: ✅ **Aceito** (vigente)

Esta decisão é válida até que os gatilhos de revisão sejam atingidos.

**Próxima revisão**: Junho 2026

---

## Documentos Relacionados

| Documento | Descrição |
|-----------|-----------|
| [`arquitetura_atual.md`](../arquitetura_atual.md) | Estado atual |
| [`arquitetura_alvo.md`](../arquitetura_alvo.md) | Arquitetura alvo (separação permanente) |
| [`roadmap.md`](../roadmap.md) | Roadmap de padronização e evolução |
| [`gaps.md`](../gaps.md) | Gap F-001: 2 frontends separados |

---

**Decisão tomada por**: Tech Lead + Equipe  
**Data da decisão**: Março 2026  
**Próxima revisão**: Junho 2026
