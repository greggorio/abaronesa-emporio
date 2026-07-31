# Roadmap de Arquitetura

> **Última atualização**: Março 2026  
> **Status**: Documento vivo — atualizar conforme progresso  
> **Legenda**: 🟢 Completo | 🟡 Em andamento | 🔴 Não iniciado

---

## Visão Geral

Este documento descreve a evolução planejada da arquitetura de software.

**Princípio Fundamental**: **Manter separação entre ERP e Digital Experience** — são domínios diferentes com propósitos diferentes.

**Modelo de Implantação**: **1 instância por cliente** — isolamento total, já implementado e permanente.

**Arquitetura atual**: [`arquitetura_atual.md`](./arquitetura_atual.md)  
**Arquitetura alvo**: [`arquitetura_alvo.md`](./arquitetura_alvo.md)

---

## Estado Atual (Q1 2026) 🟢

**Status da Fase**: 🟢 **COMPLETA**

### Características

| Dimensão | Configuração |
|----------|--------------|
| **Frontends** | 2 (Quasar/Vue + React) — **Permanente** |
| **Backends** | 2 (ERP + Espresso) — **Permanente** |
| **Instância por Cliente** | 1 instância isolada por cliente — **Permanente** |
| **Banco de Dados** | 1 por instância — **Permanente** |
| **Autenticação** | JWT compartilhado |
| **Comunicação** | REST + WebSocket |
| **Deploys** | Independentes (ERP e Espresso) |

### O Que Funciona Bem

- ✅ Separação clara de domínios (Backoffice vs. Customer-facing)
- ✅ JWT compartilhado entre sistemas
- ✅ Cada frontend tem tecnologia adequada ao propósito
- ✅ Deploy independente (ERP não bloqueia Espresso)
- ✅ Isolamento total por cliente (1 instância cada)
- ✅ Equipes podem trabalhar em paralelo

### Problemas Conhecidos (Gaps Reais)

- 🔴 APIs não documentadas (OpenAPI/Swagger)
- 🔴 i18n implementado de forma diferente
- 🔴 Testes de integração inexistentes
- 🟡 Health checks não padronizados
- 🟡 Rollback manual

---

## Fase 1: Padronização (Q2 2026) 🟡

**Status da Fase**: 🟡 **EM ANDAMENTO** (1/5 completos)  
**Previsão**: Junho 2026

### Entregáveis

- [x] **Documentar arquitetura atual**
  - `arquitetura_atual.md` ✅
  - `VISAO_GERAL_INTEGRACOES.md` ✅
  - Serviços mapeados ✅

- [ ] **Padronizar i18n entre sistemas**
  - Unificar `TranslationService` (ERP) e `I18nService` (Espresso)
  - Mesma estratégia de cache
  - Mesma API de consulta
  - Mesma tabela `entity_translation`

- [ ] **Documentar contratos de API (OpenAPI)**
  - ERP: `springdoc-openapi` para todos os endpoints
  - Espresso: `springdoc-openapi` para todos os endpoints
  - Versionamento de API definido (`/api/v1/...`)
  - Swagger UI publicado

- [ ] **Criar API Gateway simples**
  - Nginx como reverse proxy único
  - Rotas: `/api/erp/*` → ERP, `/api/app/*` → Espresso
  - SSL centralizado
  - Rate limiting básico

- [ ] **Padronizar health checks**
  - Endpoint `/health` em todos os backends
  - Mesma estrutura de resposta
  - Integração com monitoramento

**Critério de Conclusão**: Todos os itens marcados como ✅  
**Próxima Ação**: Padronizar i18n

---

## Fase 2: Melhorar ERP (Q3-Q4 2026) 🔴

**Status da Fase**: 🔴 **NÃO INICIADA**  
**Previsão**: Setembro-Dezembro 2026  
**Foco**: Melhorar ERP independentemente do Espresso

### Entregáveis

- [ ] **Módulo de Pedidos de Compra**
  - Fluxo completo de requisição → aprovação → recebimento
  - Integração com fornecedores
  - Histórico de pedidos

- [ ] **Módulo de Signage Digital**
  - Templates de vídeo MP4
  - Agendamento por loja
  - Integração com produtos/promoções

- [ ] **Dashboard de Gamificação**
  - KPIs de engajamento
  - Rankings de clientes
  - Histórico de resgates

- [ ] **Módulo de Quiz (banco de perguntas)**
  - CRUD de categorias e perguntas
  - Importação CSV/JSON
  - Geração por IA (OpenAI)

- [ ] **Testes de integração (ERP)**
  - Suite de testes para módulos críticos
  - Testes de contrato de API
  - Code coverage medido

**Critério de Conclusão**: Módulos do ERP estáveis e testados  
**Risco Principal**: Complexidade de regras de negócio

---

## Fase 3: Melhorar Espresso (Q3-Q4 2026) 🔴

**Status da Fase**: 🔴 **NÃO INICIADA**  
**Previsão**: Setembro-Dezembro 2026  
**Foco**: Melhorar Espresso independentemente do ERP

### Entregáveis

- [ ] **Mesa Digital (Self-Checkout)**
  - QR Code por mesa
  - Pagamento via Pix/Cartão
  - Fechamento individual ou por mesa

- [ ] **Delivery (Uber Direct)**
  - Integração completa com Uber
  - Rastreamento em tempo real
  - Webhooks de status

- [ ] **Quiz Ao Vivo (WebSocket)**
  - Sessões em tempo real
  - Ranking ao vivo
  - Premiação automática

- [ ] **KDS (Kitchen Display System)**
  - Filas de produção
  - Status de pedidos
  - Integração com Mesa/Delivery

- [ ] **Waiter App (Garçom)**
  - Chamados de mesa
  - Notificações de pedidos prontos
  - Gestão de pagamentos

- [ ] **Testes de integração (Espresso)**
  - Suite de testes para módulos críticos
  - Testes de contrato de API
  - Code coverage medido

**Critério de Conclusão**: Módulos do Espresso estáveis e testados  
**Risco Principal**: Performance em tempo real (WebSocket)

---

## Fase 4: Otimização (Q1 2027) 🔴

**Status da Fase**: 🔴 **NÃO INICIADA**  
**Previsão**: Janeiro-Março 2027  
**Gatilho**: Fases 2 e 3 completas

### Entregáveis

- [ ] **Logs estruturados (JSON)**
  - ERP: logs em JSON
  - Espresso: logs em JSON
  - Coleta via Promtail/Loki (opcional)
  - Busca e filtro

- [ ] **Contratos de API versionados**
  - `/api/v1/...` em ambos sistemas
  - Backward compatibility garantida
  - Depreciação com aviso prévio

- [ ] **Monitoramento centralizado**
  - Dashboard Grafana para ambos sistemas
  - Métricas de performance
  - Alertas configurados

- [ ] **Runbooks de incidente**
  - Procedimento para cada tipo de alerta
  - Tempo máximo de resposta: 30 min
  - Escalonamento definido

**Critério de Conclusão**: MTTR < 30 minutos  
**Risco Principal**: Complexidade de monitoramento

---

## Gatilhos para Mudança de Fase

| Gatilho | Fase | Ação |
|---------|------|------|
| **i18n padronizado** | Fase 1 completa | Iniciar Fases 2 e 3 |
| **APIs documentadas** | Fase 1 completa | Iniciar Fases 2 e 3 |
| **6+ clientes** | Fase 2-3 | Priorizar melhorias |
| **Fases 2 e 3 completas** | Fase 4 | Iniciar otimização |

---

## Resumo Visual do Progresso

```
Q1 2026          Q2 2026          Q3-Q4 2026       Q1 2027
│                │                │                │
├────────────────┼────────────────┼────────────────┼────────────────┤
│ 🟢 Estado      │ 🟡 Padroniza-  │ 🔴 Melhorias   │ 🔴 Otimização  │
│   Atual        │   ção (20%)    │   Independentes│                │
│                │                │                │                │
│ • 2 frontends  │ • Docs ✅      │ • ERP:         │ • Logs JSON    │
│ • 2 backends   │ • i18n ⏳      │   Pedidos      │ • APIs v1      │
│ • JWT shared   │ • OpenAPI ⏳   │   Compra ⏳     │ • Grafana ⏳   │
│ • 1 instância  │ • API Gateway  │   Signage ⏳    │ • Runbooks ⏳  │
│   por cliente  │   ⏳           │   Gamificação   │                │
│                │ • Health ⏳    │   ⏳           │                │
│                │                │ • Espresso:    │                │
│                │                │   Mesa ⏳       │                │
│                │                │   Delivery ⏳   │                │
│                │                │   Quiz ⏳       │                │
│                │                │   KDS ⏳        │                │
│                │                │                │                │
│                │ PRÓXIMA AÇÃO → Padronizar i18n entre ERP e Espresso               │
```

---

## Legenda de Status

| Ícone | Status | Significado |
|-------|--------|-------------|
| 🟢 | **COMPLETA** | Todos os entregáveis feitos |
| 🟡 | **EM ANDAMENTO** | Alguns entregáveis feitos, outros pendentes |
| 🔴 | **NÃO INICIADA** | Nenhum entregável iniciado |

---

## Próximas Ações Imediatas

| Prioridade | Ação | Responsável | Prazo |
|------------|------|-------------|-------|
| 🔴 Alta | Padronizar i18n (ERP + Espresso) | Backend Team | Abril 2026 |
| 🔴 Alta | Documentar APIs com OpenAPI | Backend Team | Maio 2026 |
| 🔴 Alta | Criar API Gateway (Nginx) | DevOps | Maio 2026 |
| 🟡 Média | Padronizar health checks | Backend Team | Junho 2026 |
| 🟡 Média | Iniciar módulo Pedidos de Compra | ERP Team | Julho 2026 |
| 🟡 Média | Iniciar Mesa Digital | Espresso Team | Julho 2026 |

---

## Riscos e Mitigações

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| **i18n inconsistente** | Média | Médio | Padronizar estratégia (Fase 1) |
| **APIs não documentadas** | Alta | Médio | OpenAPI/Swagger (Fase 1) |
| **Testes inexistentes** | Alta | Alto | Suite de testes (Fases 2-3) |
| **Downtime na migração** | Baixa | Alto | Deploy paralelo, rollback rápido |
| **Complexidade de código** | Média | Médio | Documentação, pair programming |

---

## Métricas de Sucesso

| Métrica | Atual | Meta Q2 2026 | Meta Q4 2026 | Meta Q2 2027 |
|---------|-------|--------------|--------------|--------------|
| **Frontends** | 2 | 2 | 2 | 2 |
| **Backends** | 2 | 2 | 2 | 2 |
| **Instâncias por cliente** | 1 | 1 | 1 | 1 |
| **APIs documentadas** | 0% | 100% | 100% | 100% |
| **Testes de integração** | 0% | 0% | 80% | 100% |
| **MTTR** | ~2 horas | ~2 horas | ~1 hora | < 30 min |

---

## Documentos Relacionados

| Documento | Descrição |
|-----------|-----------|
| [`arquitetura_atual.md`](./arquitetura_atual.md) | Estado atual da arquitetura |
| [`arquitetura_alvo.md`](./arquitetura_alvo.md) | Arquitetura alvo (manter separação) |
| [`gaps.md`](./gaps.md) | O que falta implementar |
| [`decisions/`](./decisions/) | Architecture Decision Records (4 ADRs) |
| [`../infrastructure/roadmap.md`](../infrastructure/roadmap.md) | Roadmap de infraestrutura |

---

**Última revisão**: Março 2026  
**Próxima revisão**: Abril 2026 (após padronização i18n)
