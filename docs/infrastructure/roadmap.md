# Roadmap de Infraestrutura

> **Última atualização**: Março 2026  
> **Status**: Documento vivo — atualizar conforme progresso  
> **Legenda**: 🟢 Completo | 🟡 Em andamento | 🔴 Não iniciado | ⏳ Aguardando gatilho

---

## Visão Geral

Este documento descreve a evolução planejada da infraestrutura, do estado atual até a arquitetura alvo.

---

## Estado Atual (Q1 2026) 🟢

**Status da Fase**: 🟢 **COMPLETA**

| Dimensão | Configuração |
|----------|--------------|
| **Clientes** | 2 (cafe_erp, espresso) |
| **VPS** | 1 única (31.97.251.16) |
| **Proxy** | Nginx + Certbot |
| **Orquestração** | Docker Compose por cliente |
| **Monitoramento** | Básico (logs, docker stats) |
| **Backup** | Manual |

---

## Fase 1: Fundações (Q2 2026) 🟡

**Status da Fase**: 🟡 **EM ANDAMENTO** (2/4 completos)  
**Previsão**: Junho 2026

### Entregáveis

- [x] **Documentar todos os scripts de deploy**
  - `deployment/deploy-bakery.md` ✅
  - `deployment/deploy-espresso.md` ✅
  - `deployment/procedimento-padrao.md` ⏳

- [x] **Criar procedimento de rollback**
  - Script de rollback automático ✅
  - Teste mensal de rollback ⏳

- [ ] **Configurar UptimeRobot (gratuito)**
  - Monitorar `erp.smartdataerp.com.br`
  - Monitorar `app.smartdataerp.com.br`
  - Alertas via email

- [ ] **Automatizar backup de banco**
  - Script diário `/opt/sistemas/backup-diario.sh`
  - Upload para S3/Google Drive
  - Teste de restore mensal

**Critério de Conclusão**: Todos os itens marcados como ✅  
**Próxima Ação**: Configurar UptimeRobot

---

## Fase 2: Resiliência (Q3 2026) 🔴

**Status da Fase**: 🔴 **NÃO INICIADA**  
**Previsão**: Setembro 2026  
**Gatilho**: Fase 1 completa

### Entregáveis

- [ ] **Health checks com notificação**
  - Endpoint `/health` em todos os backends
  - Notificação Telegram/Slack em caso de falha
  - Escalonamento automático (reiniciar serviço)

- [ ] **Backup automatizado validado**
  - Backup diário com verificação de integridade
  - Retenção: 7 diários, 4 semanais, 12 mensais
  - Alerta se backup falhar

- [ ] **Teste de restore trimestral**
  - Procedimento documentado
  - Tempo máximo de restore: < 1 hora
  - Relatório pós-teste

- [ ] **SSL com renovação validada**
  - Alerta 30 dias antes da expiração
  - Renovação automática testada

**Critério de Conclusão**: Downtime < 1% no trimestre

---

## Fase 3: Observabilidade (Q4 2026) 🔴

**Status da Fase**: 🔴 **NÃO INICIADA**  
**Previsão**: Dezembro 2026  
**Gatilho**: Fase 2 completa

### Entregáveis

- [ ] **Grafana Cloud (free tier)**
  - Dashboard de métricas (CPU, RAM, disco)
  - Dashboard de aplicação (requisições, erros, latência)
  - Alertas configurados

- [ ] **Logs estruturados (JSON)**
  - Backend: logs em JSON
  - Coleta via Promtail/Loki
  - Busca e filtro no Grafana

- [ ] **Dashboard de negócio**
  - Clientes ativos
  - Pedidos por hora
  - Tempo médio de resposta

- [ ] **Runbooks de incidente**
  - Procedimento para cada tipo de alerta
  - Tempo máximo de resposta: 30 min

**Critério de Conclusão**: MTTR < 30 minutos

---

## Gatilho para Fase 4

**Quando**: 10+ clientes ativos **OU** downtime recorrente

**Sinais**:
- [ ] RAM > 80% constante
- [ ] CPU > 70% constante
- [ ] Deploy > 5 minutos
- [ ] Clientes relatando lentidão

---

## Fase 4: Multi-VPS (2027) ⏳

**Status da Fase**: ⏳ **AGUARDANDO GATILHO**  
**Previsão**: 2027 (depende de crescimento)

### Entregáveis

- [ ] **2-3 VPS adicionais**
  - Provedor diferente (redundância)
  - Load balancer (HAProxy/Nginx)

- [ ] **Distribuição de clientes**
  - 5 clientes por VPS
  - Failover automático

- [ ] **Banco de dados externo (opcional)**
  - RDS ou similar
  - Backup automatizado
  - Read replicas

**Custo Estimado**: R$ 300-600/mês

---

## Fase 5: Orquestração (2027+) ⏳

**Status da Fase**: ⏳ **AGUARDANDO GATILHO**  
**Previsão**: 2027+ (15+ clientes)

### Entregáveis

| Opção | Prós | Contras |
|-------|------|---------|
| **Kubernetes** | Escalabilidade, resiliência | Complexidade alta |
| **Docker Swarm** | Simples, nativo do Docker | Limitado |
| **Nomad** | Flexível, HashiCorp ecosystem | Menos maduro |

**Kubernetes (Recomendado)**:

- [ ] **Cluster K8s (3-5 nodes)**
- [ ] **Namespace por cliente**
- [ ] **CI/CD com GitOps**
- [ ] **Helm charts por serviço**

**Custo Estimado**: R$ 1000-3000/mês

---

## Resumo Visual do Progresso

```
Q1 2026          Q2 2026          Q3 2026          Q4 2026          2027
│                │                │                │                │
├────────────────┼────────────────┼────────────────┼────────────────┤
│ 🟢 Estado      │ 🟡 Fundações   │ 🔴 Resiliência │ 🔴 Observab.   │ ⏳ Multi-VPS   │
│   Atual        │   (50%)        │                │                │   → K8s        │
│                │                │                │                │
│ • 2 clientes   │ • Docs ✅      │ • Health       │ • Grafana      │ • 2-3 VPS      │
│ • 1 VPS        │ • Rollback ✅  │   checks       │ • Logs JSON    │ • LB           │
│ • Nginx        │ • Uptime ⏳    │ • Backup       │ • Dashboards   │ • K8s          │
│ • Deploy       │ • Backup ⏳    │   validado     │ • Runbooks     │ • 20+ clientes │
│   manual       │                │ • SSL          │ • MTTR<30min   │                │
│                │                │   testado      │                │                │
│                │                │                │                │
│                │ PRÓXIMA AÇÃO → Configurar UptimeRobot            │
```

---

## Legenda de Status

| Ícone | Status | Significado |
|-------|--------|-------------|
| 🟢 | **COMPLETA** | Todos os entregáveis feitos |
| 🟡 | **EM ANDAMENTO** | Alguns entregáveis feitos, outros pendentes |
| 🔴 | **NÃO INICIADA** | Nenhum entregável iniciado |
| ⏳ | **AGUARDANDO GATILHO** | Dependente de condição externa |

---

## Próximas Ações Imediatas

| Prioridade | Ação | Responsável | Prazo |
|------------|------|-------------|-------|
| 🔴 Alta | Configurar UptimeRobot | DevOps | Abril 2026 |
| 🔴 Alta | Script de backup automático | DevOps | Maio 2026 |
| 🟡 Média | Teste de rollback | DevOps | Maio 2026 |
| 🟡 Média | Documentar procedimento padrão | Tech Lead | Junho 2026 |

---

## Histórico de Atualizações

| Data | Mudança | Autor |
|------|---------|-------|
| Março 2026 | Criação do documento + indicadores visuais | Infra Team |
| Março 2026 | Documentação de deploy concluída (2/4) | Infra Team |

---

**Última revisão**: Março 2026  
**Próxima revisão**: Abril 2026 (após UptimeRobot)

---

## Métricas de Sucesso

| Métrica | Atual | Meta Q4 2026 |
|---------|-------|--------------|
| **Uptime** | ~95% | > 99% |
| **MTTR** | ~2 horas | < 30 min |
| **Backup** | Manual | Automático + validado |
| **Clientes/VPS** | 2 | 10 |
| **Deploy** | 10 min | < 5 min |

---

## Responsabilidades

| Atividade | Responsável | Frequência |
|-----------|-------------|------------|
| **Deploy** | Dev/DevOps | Sob demanda |
| **Monitoramento** | DevOps | Diário (automático) |
| **Backup** | DevOps | Diário (automático) |
| **Teste de Restore** | DevOps | Mensal |
| **Review de Capacidade** | Tech Lead | Trimestral |
| **Atualização Roadmap** | Tech Lead | Trimestral |

---

## Riscos e Mitigações

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| **VPS única falha** | Baixa | Alto | Backup diário, restore testado |
| **SSL expira** | Baixa | Alto | Alerta 30 dias antes |
| **Disco cheio** | Média | Médio | Monitoramento + cleanup auto |
| **RAM insuficiente** | Média | Alto | Upgrade planejado (Fase 2) |
| **Deploy falha** | Alta | Médio | Rollback automatizado |

---

## Documentos Relacionados

| Documento | Descrição |
|-----------|-----------|
| [`arquitetura_atual.md`](./arquitetura_atual.md) | Estado atual da infraestrutura |
| [`scalability/README.md`](./scalability/README.md) | Limites e escalabilidade |
| [`monitoring/README.md`](./monitoring/README.md) | Monitoramento atual |
| [`deployment/deploy-bakery.md`](./deployment/deploy-bakery.md) | Deploy do ERP |

---

**Última revisão**: Março 2026  
**Próxima revisão**: Abril 2026 (após UptimeRobot)
