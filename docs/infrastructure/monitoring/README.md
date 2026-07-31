# Monitoramento

> **Status**: ⚠️ Básico Implementado | **Prioridade**: Alta para melhorias

---

## O Que Está Implementado

| Tipo | Status | Como Acessar |
|------|--------|--------------|
| **Health Check (Backend)** | ✅ | `/actuator/health` |
| **Logs Docker** | ✅ | `docker compose logs -f` |
| **Status de Serviços** | ✅ | `docker compose ps` |
| **Uso de Recursos** | ✅ | `docker stats` |

---

## O Que Falta (Roadmap)

| Tipo | Prioridade | Solução Sugerida |
|------|------------|------------------|
| **Uptime Monitoring** | 🔴 Alta | UptimeRobot (free) |
| **Alertas de Downtime** | 🔴 Alta | Telegram/Slack webhook |
| **Backup Automático** | 🔴 Alta | Script + S3/Google Drive |
| **Logs Centralizados** | 🟡 Média | Grafana Loki (free tier) |
| **Métricas/Dashboards** | 🟡 Média | Grafana Cloud (free) |

**Ver roadmap completo**: [`../roadmap.md`](../roadmap.md)

---

## Comandos Úteis

### Status dos Serviços

```bash
# cafe_erp
cd /opt/sistemas/cafe_erp
docker compose ps

# espresso
cd /opt/sistemas/espresso
docker compose ps
```

### Logs em Tempo Real

```bash
# Backend do ERP
docker compose logs -f backend

# Frontend do ERP
docker compose logs -f frontend

# Todos os serviços
docker compose logs -f
```

### Uso de Recursos

```bash
# Ver uso de CPU/RAM em tempo real
docker stats

# Ver uso de disco
docker system df
```

### Health Check

```bash
# ERP Backend
curl https://erp.smartdataerp.com.br/api/health

# Espresso Backend
curl https://app.smartdataerp.com.br/api/health
```

---

## Procedimentos de Rotina

### Diário (Automático via Cron)

- [ ] Verificar se serviços estão rodando
- [ ] Verificar espaço em disco

```bash
# Script sugerido para cron diário
#!/bin/bash
cd /opt/sistemas/cafe_erp && docker compose ps > /var/log/docker-status-erp.log
cd /opt/sistemas/espresso && docker compose ps > /var/log/docker-status-espresso.log
df -h >> /var/log/docker-status.log
```

### Semanal (Manual)

- [ ] Revisar logs de erro
- [ ] Verificar uso de disco
- [ ] Testar restore de backup

```bash
# Ver logs de erro da semana
docker compose logs --since 7d backend | grep -i error

# Verificar disco
df -h

# Verificar tamanho de containers
docker system df -v
```

### Mensal (Manual)

- [ ] Testar restore completo de backup
- [ ] Revisar métricas de uso
- [ ] Atualizar dependências (security patches)

---

## Alertas Sugeridos (Implementar)

### Críticos (Imediato)

| Alerta | Gatilho | Ação |
|--------|---------|------|
| **Service Down** | Health check falha 3x | Telegram/SMS |
| **Disco > 90%** | `df -h` > 90% | Email |
| **RAM > 95%** | `docker stats` > 95% | Email |

### Atenção (Durante Horário Comercial)

| Alerta | Gatilho | Ação |
|--------|---------|------|
| **Disco > 80%** | `df -h` > 80% | Email |
| **RAM > 85%** | `docker stats` > 85% | Email |
| **Backup Falhou** | Script de backup falha | Email |

---

## Ferramentas Gratuitas Sugeridas

| Ferramenta | Propósito | Link |
|------------|-----------|------|
| **UptimeRobot** | Uptime monitoring | [uptimerobot.com](https://uptimerobot.com/) |
| **Grafana Cloud** | Métricas e dashboards (free 10k series) | [grafana.com/cloud](https://grafana.com/cloud) |
| **Healthchecks.io** | Monitoramento de cron jobs | [healthchecks.io](https://healthchecks.io/) |

---

## Backup (Estado Atual)

### Implementado

- [ ] Backup automático de banco de dados
- [ ] Backup de uploads/arquivos
- [ ] Teste de restore mensal

### Procedimento Manual Atual

```bash
# Backup de emergência
ssh root@31.97.251.16
cd /opt/sistemas/cafe_erp
docker compose exec db pg_dump -U postgres villa_erp_db > /tmp/backup-$(date +%Y%m%d).sql

# Download do backup
scp root@31.97.251.16:/tmp/backup-*.sql /local/backups/
```

### Script Sugerido (Implementar)

```bash
#!/bin/bash
# /opt/sistemas/backup-diario.sh

DATE=$(date +%Y%m%d-%H%M)
BACKUP_DIR="/opt/backups"

# Backup ERP
docker compose -f /opt/sistemas/cafe_erp/docker-compose.yml exec db pg_dump -U postgres villa_erp_db > ${BACKUP_DIR}/erp-${DATE}.sql

# Backup Espresso
docker compose -f /opt/sistemas/espresso/docker-compose.yml exec db pg_dump -U postgres espresso_db > ${BACKUP_DIR}/espresso-${DATE}.sql

# Manter apenas últimos 7 dias
find ${BACKUP_DIR} -name "*.sql" -mtime +7 -delete

# Upload para S3/Google Drive (implementar)
# aws s3 cp ${BACKUP_DIR}/ s3://meu-bucket/backups/ --recursive
```

---

## Troubleshooting

### Serviço Parou de Responder

```bash
# 1. Verificar status
docker compose ps

# 2. Ver logs
docker compose logs backend

# 3. Reiniciar
docker compose restart backend

# 4. Se persistir, fazer rollback
# Ver deployment/deploy-bakery.md#rollback
```

### Disco Cheio

```bash
# 1. Verificar uso
df -h
docker system df

# 2. Limpar logs antigos
docker compose logs --tail 1000 > logs-recentes.log

# 3. Limpar containers/images antigos
docker system prune -a

# 4. Verificar uploads
ls -lh /opt/sistemas/*/uploads/
```

### Lentidão

```bash
# 1. Verificar uso de recursos
docker stats
top

# 2. Verificar queries lentas no banco
docker compose exec db psql -U postgres -c "SELECT * FROM pg_stat_activity WHERE state != 'idle';"

# 3. Verificar logs de erro
docker compose logs backend | grep -i "slow\|timeout\|error"
```

---

## Documentos Relacionados

| Documento | Descrição |
|-----------|-----------|
| [`../arquitetura_atual.md`](../arquitetura_atual.md) | Arquitetura de produção |
| [`../roadmap.md`](../roadmap.md) | Roadmap de infraestrutura |
| [`../security/ssl-certbot.md`](../security/ssl-certbot.md) | SSL/TLS |
| [`deployment/deploy-bakery.md`](deployment/deploy-bakery.md) | Deploy do ERP |

---

**Última atualização**: Março 2026
