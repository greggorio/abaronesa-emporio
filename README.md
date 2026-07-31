# Emporio A Baronesa

Monorepo do Emporio A Baronesa, reunindo o ERP, as superficies publicas e os
servicos de integracao.

## Componentes

| Diretorio | Componente |
|---|---|
| `backend/` | ERP em Java 21 e Spring Boot |
| `frontend/` | Interface do ERP em Quasar e Vue |
| `website_back/` | API publica em Java 21 e Spring Boot |
| `website_front/` | Site/PWA em React e Vite |
| `whatsapp_service/` | Integracao WhatsApp em Node.js |

## Desenvolvimento local

O desenvolvimento local e manual e nao depende da infraestrutura Docker de
producao proposta.

```text
backend:          mvn spring-boot:run
website_back:     mvn spring-boot:run
frontend:         npm run dev
website_front:    npm run dev
whatsapp_service: npm start
```

Consulte o [onboarding minimo](docs/development/ONBOARDING_MINIMO.md) e o
[guia de configuracao local de segredos](docs/development/CONFIGURACAO_LOCAL_SEGREDOS.md)
antes de iniciar os backends.

## Documentacao

- [Indice da documentacao](docs/README.md)
- [Arquitetura aprovada de deploy](docs/infrastructure/deployment/proposta-docker-ci-cd-producao-emporio.md)
- [Tracker da implementacao](docs/infrastructure/deployment/implementation/README.md)

A arquitetura Docker/CI/CD e o controle de releases estao em implementacao
incremental. Os artefatos existentes nao devem ser interpretados como uma
plataforma de producao ja implantada ou aprovada.

Arquivos `.env`, credenciais, uploads, dumps e demais dados locais ou sensiveis
nao devem ser commitidos.
