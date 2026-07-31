# Arquitetura de Produção (Estado Atual)

> **Última atualização**: Março 2026  
> **Status**: ✅ Documentação do ambiente real em produção

---

## Visão Geral

| Dimensão | Configuração Atual |
|----------|-------------------|
| **Servidor** | VPS única (`31.97.251.16`) |
| **Clientes** | 2 instâncias (`cafe_erp`, `espresso`) |
| **Proxy** | Nginx (edge) + SSL via Certbot |
| **Orquestração** | Docker Compose por cliente |
| **Banco de Dados** | PostgreSQL 14 (por instância) |

---

## Diagrama de Infraestrutura

```
┌─────────────────────────────────────────────────────────────┐
│              Servidor de Produção (31.97.251.16)            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Nginx (Edge Proxy)                                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ SSL/TLS (Certbot)                                   │   │
│  │                                                     │   │
│  │ erp.smartdataerp.com.br → :8094 (backend)           │   │
│  │ app.smartdataerp.com.br → :8093 (espresso backend)  │   │
│  │ :8098 (cafe_erp frontend)                           │   │
│  │ :7089 (espresso frontend)                           │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  /opt/sistemas/cafe_erp/                                    │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │   db        │  │  backend    │  │    frontend         │ │
│  │  Postgres:  │←→│  Java/Spring│←→│   Quasar/Vue        │ │
│  │  :5441      │  │  :8080      │  │   :80               │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
│                                                             │
│  /opt/sistemas/espresso/                                    │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │   db        │  │  backend    │  │    frontend         │ │
│  │  Postgres   │←→│  Java/Spring│←→│   React/Capacitor   │ │
│  │  :5442      │  │  :8085      │  │   :80               │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Estrutura de Diretórios

```
/opt/sistemas/
├── cafe_erp/
│   ├── docker-compose.yml
│   ├── .env
│   └── uploads/
│
├── espresso/
│   ├── docker-compose.yml
│   ├── .env
│   └── uploads/
│
└── redeploy-monitor-v2-fixed.sh  ← Serviço de redeploy automatizado
```

---

## Serviços em Produção

### cafe_erp (ERP Principal)

| Serviço | Porta Interna | Porta Exposta | Status |
|---------|---------------|---------------|--------|
| `db` | 5432 | 5441 | ✅ |
| `backend` | 8080 | 8094 | ✅ |
| `frontend` | 80 | 8098 | ✅ |

**Comandos úteis**:
```bash
cd /opt/sistemas/cafe_erp
docker compose ps
docker compose logs -f backend
docker compose restart backend
```

### espresso (App/Site)

| Serviço | Porta Interna | Porta Exposta | Status |
|---------|---------------|---------------|--------|
| `db` | 5432 | 5442 | ✅ |
| `backend` | 8085 | 8093 | ✅ |
| `frontend` | 80 | 7089 | ✅ |

**Comandos úteis**:
```bash
cd /opt/sistemas/espresso
docker compose ps
docker compose logs -f backend
docker compose restart backend
```

---

## Deploy

### Script Principal

Local: `ops/deploy/deploy-bakery.sh`

**Fluxo**:
1. Build local das imagens Docker
2. Save para `/tmp/`
3. Transferência via SCP para servidor
4. Load das imagens no servidor
5. `docker compose up -d --force-recreate`

**Ver documentação completa**: [`deployment/deploy-bakery.md`](./deployment/deploy-bakery.md)

### Redeploy Automatizado

Existe um serviço systemd que monitora sinais de redeploy:

- **Serviço**: `docker-redeploy.service`
- **Script**: `/opt/sistemas/redeploy-monitor-v2-fixed.sh`
- **Gatilho**: Criar arquivo `.redeploy_signal` no diretório do cliente

```bash
# Acionar redeploy remoto
touch /opt/sistemas/cafe_erp/.redeploy_signal
```

---

## SSL/TLS

### Certbot

SSL é gerenciado via Certbot no Nginx.

**Ver documentação completa**: [`security/ssl-certbot.md`](./security/ssl-certbot.md)

### Comandos Úteis

```bash
# Listar certificados
sudo certbot certificates

# Renovar
sudo certbot renew

# Adicionar novo domínio
sudo certbot --nginx -d novo-cliente.smartdataerp.com.br
```

---

## Monitoramento Atual

| Tipo | Implementado? | Como Acessar |
|------|---------------|--------------|
| **Health Check** | ✅ (backend) | `/api/health` e `/actuator/health` |
| **Logs** | ✅ | `docker compose logs -f` |
| **Status Serviços** | ✅ | `docker compose ps` |
| **Uptime** | ❌ | A implementar (UptimeRobot) |
| **Alertas** | ❌ | A implementar (Slack/Email) |
| **Métricas** | ❌ | A implementar (Grafana) |

**Ver roadmap completo**: [`roadmap.md`](./roadmap.md)

---

## Limites e Escalabilidade

| Recurso | Configuração Atual | Limite Prático |
|---------|-------------------|----------------|
| **RAM** | 4-8 GB | ~10 clientes |
| **CPU** | 2-4 cores | ~10 clientes |
| **Disco** | 50-100 GB | ~20 clientes |
| **Clientes por VPS** | 2 | ~10 clientes |

**Quando escalar**: Ver [`scalability/README.md`](./scalability/README.md)

---

## Acesso ao Servidor

```bash
# SSH
ssh root@31.97.251.16

# Chave SSH já configurada via ssh-copy-id
```

---

## Documentos Relacionados

| Documento | Descrição |
|-----------|-----------|
| [`deployment/deploy-bakery.md`](./deployment/deploy-bakery.md) | Deploy do ERP |
| [`deployment/deploy-espresso.md`](./deployment/deploy-espresso.md) | Deploy do Espresso |
| [`security/ssl-certbot.md`](./security/ssl-certbot.md) | Gerenciamento de SSL |
| [`monitoring/README.md`](./monitoring/README.md) | Monitoramento atual |
| [`scalability/README.md`](./scalability/README.md) | Limites e escalabilidade |
| [`roadmap.md`](./roadmap.md) | Roadmap de infraestrutura |

---

**Nota**: Esta documentação reflete o **estado atual real** da infraestrutura. Para a arquitetura alvo futura, ver [`roadmap.md`](./roadmap.md).
