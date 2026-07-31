# Escalabilidade

> **Status Atual**: 2 clientes em produção | **Limite Prático**: ~10 clientes por VPS

---

## Limites Atuais

| Recurso | Configuração Atual | Limite Prático | Status |
|---------|-------------------|----------------|--------|
| **RAM** | 4-8 GB | ~10 clientes | 🟢 OK |
| **CPU** | 2-4 cores | ~10 clientes | 🟢 OK |
| **Disco** | 50-100 GB | ~20 clientes | 🟢 OK |
| **Clientes por VPS** | 2 | ~10 clientes | 🟢 OK |

---

## Quando Escalar

### Sinais de Alerta

| Sinal | Threshold | Ação |
|-------|-----------|------|
| **RAM > 80%** | Uso constante por 7 dias | Upgrade de RAM |
| **CPU > 70%** | Uso constante por 7 dias | Upgrade de CPU |
| **Disco > 70%** | Espaço livre < 30% | Limpeza ou upgrade |
| **Deploy > 5 min** | Tempo de deploy aumentando | Otimização ou nova VPS |
| **Clientes > 10** | 10+ clientes ativos | Migrar para multi-VPS |

### Métricas de Saúde

```bash
# Ver uso de RAM/CPU
docker stats --no-stream

# Ver uso de disco
df -h

# Ver tempo de deploy
time ./deploy-bakery.sh
```

---

## Estratégias de Escalabilidade

### Nível 1: 1-3 Clientes (Atual)

**Arquitetura**: VPS única, Docker Compose por cliente

```
┌─────────────────────────────────────┐
│         VPS Única                   │
│  ┌───────────┐  ┌───────────┐      │
│  │ Cliente 1 │  │ Cliente 2 │      │
│  │ Docker    │  │ Docker    │      │
│  │ Compose   │  │ Compose   │      │
│  └───────────┘  └───────────┘      │
│                                     │
│  Nginx (Edge Proxy)                 │
└─────────────────────────────────────┘
```

**Custo**: ~R$ 100-200/mês  
**Complexidade**: Baixa ✅

---

### Nível 2: 4-10 Clientes

**Arquitetura**: 2-3 VPS, load balancer

```
┌─────────────────────────────────────────────────────────┐
│              Load Balancer (Nginx/HAProxy)              │
└─────────────────────────────────────────────────────────┘
           │                    │                    │
           ▼                    ▼                    ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│    VPS 1         │  │    VPS 2         │  │    VPS 3         │
│  (5 clientes)    │  │  (5 clientes)    │  │  (Backup)        │
└──────────────────┘  └──────────────────┘  └──────────────────┘
```

**Custo**: ~R$ 300-600/mês  
**Complexidade**: Média

**Quando migrar**: 8+ clientes ativos

---

### Nível 3: 10+ Clientes

**Arquitetura**: Kubernetes ou orquestrador

```
┌─────────────────────────────────────────────────────────┐
│              Load Balancer (Cloud/HAProxy)              │
└─────────────────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────┐
│         Cluster Kubernetes (3-5 nodes)                  │
│  ┌─────────────────────────────────────────────────┐   │
│  │  Namespace por cliente (isolamento)             │   │
│  │  - Backend (Java/Spring)                        │   │
│  │  - Frontend (Quasar/React)                      │   │
│  │  - Database (Postgres per tenant)               │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

**Custo**: ~R$ 1000-3000/mês  
**Complexidade**: Alta

**Quando migrar**: 15+ clientes ativos

---

## Upgrade de VPS (Curto Prazo)

### Quando Fazer

- RAM > 80% constante
- CPU > 70% constante
- Clientes > 5

### Como Fazer

1. **Backup prévio**:
   ```bash
   # Backup de todos os bancos
   for dir in /opt/sistemas/*/; do
     cd $dir
     docker compose exec db pg_dump -U postgres > /tmp/backup-$(basename $dir).sql
   done
   ```

2. **Agendar downtime** (30 min)

3. **Upgrade na VPS** (via painel do provedor)

4. **Validar**:
   ```bash
   docker compose ps
   curl https://erp.smartdataerp.com.br/api/health
   ```

---

## Otimizações (Antes de Escalar)

### 1. Ajustar JVM

```yaml
# docker-compose.yml
backend:
  environment:
    JAVA_OPTS: -Xms512m -Xmx2048m  # Ajustar conforme RAM disponível
```

### 2. Limitar RAM por Container

```yaml
# docker-compose.yml
backend:
  deploy:
    resources:
      limits:
        memory: 2G
      reservations:
        memory: 1G
```

### 3. Cleanup Automático

```bash
# Cron semanal
0 3 * * 0 docker system prune -af > /var/log/docker-cleanup.log 2>&1
```

### 4. Banco de Dados

```sql
-- Vacuum automático (já é default no Postgres)
-- Mas pode ajustar frequência:

ALTER SYSTEM SET autovacuum_naptime = '30s';
ALTER SYSTEM SET autovacuum_max_workers = 4;
```

---

## Roadmap de Escalabilidade

| Fase | Clientes | Arquitetura | Timeline |
|------|----------|-------------|----------|
| **Atual** | 1-3 | VPS única | Q1 2026 |
| **Upgrade VPS** | 4-6 | VPS única (mais recursos) | Q2 2026 |
| **Multi-VPS** | 7-10 | 2-3 VPS + LB | Q3 2026 |
| **Kubernetes** | 10+ | Cluster K8s | 2027 |

---

## Custos Estimados

| Fase | VPS | Custo/mês | Clientes Máx. |
|------|-----|-----------|---------------|
| **Atual** | 1x 4GB RAM | R$ 100 | 3 |
| **Upgrade** | 1x 8GB RAM | R$ 200 | 6 |
| **Multi-VPS** | 3x 4GB RAM | R$ 300 | 10 |
| **Kubernetes** | 5x 8GB RAM | R$ 1000+ | 20+ |

---

## Monitoramento de Capacidade

### Script de Verificação

```bash
#!/bin/bash
# /opt/sistemas/check-capacidade.sh

echo "=== Uso de Recursos ==="
echo ""

# RAM
echo "RAM:"
free -h | grep Mem

# CPU
echo ""
echo "CPU (últimos 5 min):"
uptime

# Disco
echo ""
echo "Disco:"
df -h /

# Docker
echo ""
echo "Containers:"
docker ps --format "table {{.Names}}\t{{.Status}}"

# Clientes
echo ""
echo "Clientes ativos:"
ls -1 /opt/sistemas/ | grep -v "^redeploy"
```

### Adicionar ao Cron

```bash
# Rodar diariamente
0 8 * * * /opt/sistemas/check-capacidade.sh >> /var/log/capacidade.log 2>&1
```

---

## Gatilhos para Mudança de Arquitetura

| Gatilho | Ação |
|---------|------|
| **5 clientes** | Upgrade de VPS (8GB RAM) |
| **8 clientes** | Planejar multi-VPS |
| **10 clientes** | Migrar para 2-3 VPS |
| **15 clientes** | Planejar Kubernetes |
| **20 clientes** | Migrar para Kubernetes |

---

## Documentos Relacionados

| Documento | Descrição |
|-----------|-----------|
| [`../arquitetura_atual.md`](../arquitetura_atual.md) | Arquitetura atual |
| [`../roadmap.md`](../roadmap.md) | Roadmap de infraestrutura |
| [`../monitoring/README.md`](../monitoring/README.md) | Monitoramento |

---

**Última atualização**: Março 2026
