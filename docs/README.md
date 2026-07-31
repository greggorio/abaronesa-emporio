# Contexto Operacional do Projeto

> **Projeto:** Empório A Baronesa
> **Tipo:** Ecossistema web para operação de cafeteria e empório (ERP + superfícies digitais + APIs REST + SSE)
> **Observabilidade principal:** Health checks HTTP, `docker compose ps`, `docker compose logs -f`, `docker stats`
> **Status:** Em desenvolvimento ativo com documentação operacional consolidada

Documento operacional consolidado a partir dos dados disponíveis nesta pasta de documentação para desenvolvimento local, operação em produção e referência rápida.

---

## 1. Identidade do Projeto

| Campo | Valor |
|-------|-------|
| **Nome** | Empório A Baronesa |
| **Objetivo** | Operar cafeteria e empório cobrindo backoffice, superfícies digitais, atendimento, cozinha, delivery, pagamentos, produção, estoque, financeiro e relacionamento com clientes |
| **Tipo** | Ecossistema web com APIs REST e eventos SSE |
| **Observabilidade principal** | `GET /api/health`, `GET /actuator/health`, `docker compose ps`, `docker compose logs -f`, `docker stats` |
| **Repositório local** | `~/git/baronesa/emporio` |
| **Branch padrão** | main |

---

## 2. Contexto Operacional

| Componente | Localização / Descrição |
|------------|-------------------------|
| **Workdir principal** | `/home/gregorio/git/baronesa/emporio/` |
| **Backend ERP** | `backend/` — Java 21 + Spring Boot (core transacional, cadastros, financeiro) |
| **Frontend ERP** | `frontend/` — Quasar/Vue 3 (backoffice) |
| **Frontend customer-facing** | `website_front/` — React + Vite + Capacitor (site/app/PWA/mesa/delivery) |
| **Backend customer-facing complementar** | `website_back/` — Java/Spring |
| **Documentação** | `docs/` |
| **Testes e qualidade** | `quality/` — suites de qualidade e testes E2E |
| **OS esperado para dev** | Linux |

---

## 3. Subida do Ambiente

### Backend ERP (local)

| Item | Detalhe |
|------|---------|
| **Pré-requisitos** | Linux, Java 21, Maven, PostgreSQL local |
| **Execução** | `cd backend && mvn spring-boot:run` |
| **Porta** | `8080` |
| **Healthcheck** | `GET http://localhost:8080/api/health` |
| **Banco local de referência** | `emporio_db` |

### Frontend ERP (local)

| Item | Detalhe |
|------|---------|
| **Stack** | Quasar/Vue 3 |
| **Caminho** | `frontend/` |
| **Execução** | `cd ~/git/baronesa/emporio/frontend && npm run dev` |
| **Porta** | `8084` |
| **URL local** | `http://localhost:8084` |
| **Node recomendado** | `20.x` ou `18.x` |

### Frontend Site/PWA (local)

| Item | Detalhe |
|------|---------|
| **Stack** | React + Vite + Capacitor |
| **Caminho** | `website_front/` |
| **Execução** | `cd ~/git/baronesa/emporio/website_front && npm run dev` |
| **Configuração necessária** | `VITE_ERP_API_URL=http://localhost:8080` em `website_front/.env` |
| **Porta** | `5173` |
| **URL local** | `http://localhost:5173` |
| **Node recomendado** | `18+` |

### Backend complementar do site (local, quando aplicável)

| Item | Detalhe |
|------|---------|
| **Stack** | Java/Spring |
| **Caminho** | `website_back/` |
| **Execução** | Não detalhada nesta pasta |
| **Porta** | Não documentada nesta pasta |

### Infra de produção (estado atual)

| Item | Detalhe |
|------|---------|
| **Servidor** | VPS única `31.97.251.16` |
| **Proxy** | Nginx + SSL via Certbot |
| **Orquestração** | Docker Compose por cliente |
| **Stacks em produção** | `cafe_erp` e `espresso` |

Portas públicas documentadas:
- `https://erp.smartdataerp.com.br` → backend ERP (`:8094`, serviço interno `:8080`)
- `https://app.smartdataerp.com.br` → backend Espresso (`:8093`, serviço interno `:8085`)
- frontend `cafe_erp` exposto em `:8098`
- frontend `espresso` exposto em `:7089`

---

## 4. Autenticação e Acesso

### UI

| Item | Detalhe |
|------|---------|
| **Fluxo principal de login** | Backoffice em `/#/` e superfícies React em `/login`; autenticação via `/api/auth/login` |
| **Credencial dev documentada** | `email: root@localhost` / `senha: 123456` |
| **Login visual `/login`** | Existe no `website_front`; no ERP Quasar o fluxo E2E entra por `/#/` |

### API

| Item | Detalhe |
|------|---------|
| **Base URL local** | `http://localhost:8080/api` |
| **Header principal** | `Authorization: Bearer <TOKEN>` |
| **Header auxiliar** | `X-User-ID: <ID_DO_USUARIO>` |
| **Padrão de segurança** | Autenticação obrigatória por padrão; rotas públicas liberadas explicitamente |
| **Campo JSON de login** | usar `password` no body de `/api/auth/login` |

Exemplo para obter token JWT:

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"root@localhost","password":"123456"}'
```

Resultado esperado: resposta `200` com `accessToken` e `tokenType: "Bearer"`.

### Fluxo Mesa Digital / Guest

| Item | Detalhe |
|------|---------|
| **Autenticação convidado** | `guest_token` mantido no navegador |
| **Header funcional recorrente** | `X-Guest-Token: <token>` |
| **Escopo** | Fluxos públicos de mesa digital, pedidos e pagamentos |

### UI Capture (E2E)

| Item | Detalhe |
|------|---------|
| **URL base do frontend ERP** | `http://localhost:8084` |
| **URL base do PWA/site** | `http://localhost:5173` |
| **Credencial E2E documentada** | `email: root@localhost` / `senha: 123456` |
| **Viewport recomendado** | `1516x768` |

> **Nota:** O repositório traz E2E do backoffice em `quality/e2e/erp-backoffice/` com `baseUrl` em `http://localhost:8084`, viewport `1516x768` e as mesmas credenciais root.

---

## 5. Banco de Dados

| Campo | Valor |
|-------|-------|
| **Tipo** | PostgreSQL |
| **Versão em produção** | PostgreSQL 14 por instância |
| **Desenvolvimento (referência)** | `emporio_db` |
| **Acesso local típico** | `postgresql://postgres:postgres@localhost:5432/emporio_db` |
| **ORM** | Hibernate/JPA |
| **DDL em dev** | `spring.jpa.hibernate.ddl-auto=update` |

---

## 6. Estrutura Relevante

```text
bakery/
|-- backend/          # ERP backend (Java/Spring)
|-- frontend/         # ERP frontend (Quasar/Vue 3)
|-- website_front/   # Site/App/PWA (React + Vite + Capacitor)
|-- website_back/    # Backend complementar do site
|-- docs/             # Documentacao do ecossistema
|-- quality/          # Suites de qualidade e testes E2E
```

---

## 7. Evidências e Validação

| Item | Detalhe |
|------|---------|
| **Suites de qualidade** | `quality/` |
| **Logs operacionais** | `docker compose logs -f` |
| **Status de serviços** | `docker compose ps` |
| **Uso de recursos** | `docker stats` |
| **Health checks** | `/api/health` e `/actuator/health` |

Comandos recorrentes:
- `cd /opt/sistemas/cafe_erp && docker compose ps`
- `cd /opt/sistemas/espresso && docker compose ps`
- `docker compose logs -f backend`
- `docker stats`
- `curl http://localhost:8080/api/health`
- `curl https://erp.smartdataerp.com.br/api/health`
- `curl https://app.smartdataerp.com.br/api/health`

---

## 8. Documentação de Referência

| Documento | Propósito |
|-----------|-----------|
| `README.md` | Porta de entrada documental do ecossistema |
| `architecture/README.md` | Arquitetura atual, alvo, gaps, ADRs e roadmap |
| `infrastructure/README.md` | Infraestrutura real, deploy, monitoramento, segurança e escalabilidade |
| `development/ONBOARDING_MINIMO.md` | Entrada rápida para novos desenvolvedores |
| `development/QR_ORDERING_LOCAL_RUNTIME.md` | Runtime local mínimo do fluxo QR/PWA |
| `api-reference/README.md` | Contratos técnicos expostos pelo sistema |
| `integrations/README.md` | Integrações externas ativas e planejadas |

---

## 9. Regras Operacionais Importantes

### Premissas de ambiente

- Linux como sistema operacional esperado para desenvolvimento.
- Java 21, Maven e PostgreSQL local para o backend ERP.
- Node.js `20.x` ou `18.x` para `frontend/`.
- Node.js `18+` para o fluxo documentado de `website_front/`.

### Regras de deploy

- O deploy documentado usa build local de imagens, transferência para o servidor e `docker compose up -d --force-recreate`.
- Há procedimento operacional padrão em `infrastructure/deployment/procedimento-padrao.md`.
- Sempre validar `docker compose ps`, health check e logs após deploy.

### Cuidados de operação

- O monitoramento atual é básico e centrado em health checks, logs, status e uso de recursos.
- Backup automático, uptime, alertas e métricas centralizadas ainda aparecem como lacunas/roadmap.
- Mudanças de topologia, host, domínio, portas ou monitoramento exigem atualização conjunta da documentação de infraestrutura.

### Dependências externas relevantes

- Pagamentos: MercadoPago e PagSeguro.
- Delivery terceirizado: Uber Direct.
- IA generativa: OpenAI.
- Push notifications: FCM.
- WhatsApp: planejado.

### Limites atuais de observabilidade

- Existe monitoramento básico com health checks, `docker compose ps`, logs Docker e `docker stats`.
- Uptime monitoring, alertas, logs centralizados e dashboards ainda não estão implementados como padrão operacional.

---

## 10. Comandos Rápidos (cola operacional)

```bash
# Backend ERP local
cd ~/git/baronesa/emporio/backend
mvn clean spring-boot:run

# Frontend ERP local
cd ~/git/baronesa/emporio/frontend
npm run dev

# Frontend PWA/site local
cd ~/git/baronesa/emporio/website_front
npm run dev

# Health local
curl http://localhost:8080/api/health

# Produção ERP
ssh root@31.97.251.16
cd /opt/sistemas/cafe_erp && docker compose ps
cd /opt/sistemas/cafe_erp && docker compose logs -f backend

# Produção Espresso
cd /opt/sistemas/espresso && docker compose ps
cd /opt/sistemas/espresso && docker compose logs -f backend
```
