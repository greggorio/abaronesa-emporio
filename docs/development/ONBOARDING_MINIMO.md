# Onboarding minimo

> **Projeto:** Emporio A Baronesa
> **Tipo:** monorepo com ERP, site/PWA, APIs e integracao WhatsApp
> **Repositorio local:** `~/git/baronesa/emporio`

Este e o roteiro mais curto para compreender os componentes e iniciar o
ambiente de desenvolvimento manual.

## Componentes

| Diretorio | Papel | Tecnologia |
|---|---|---|
| `backend/` | Backend do ERP | Java 21, Spring Boot |
| `frontend/` | Backoffice do ERP | Quasar, Vue |
| `website_back/` | API publica | Java 21, Spring Boot |
| `website_front/` | Site e PWA | React, Vite |
| `whatsapp_service/` | Integracao WhatsApp | Node.js |

`docs/` concentra a documentacao e `quality/` contem suites e artefatos de
qualidade.

## Pre-requisitos

- Linux;
- Java 21 e Maven;
- Node.js e npm compativeis com os componentes;
- PostgreSQL local para os dois backends.
- Python 3.13 e `uv` para o runtime publisher, quando esse fluxo for necessário.

Nao mantenha senhas em arquivos Spring. Antes de iniciar os backends, siga
[CONFIGURACAO_LOCAL_SEGREDOS.md](./CONFIGURACAO_LOCAL_SEGREDOS.md) para criar
e carregar `.env.local`. O arquivo de exemplo nao fornece senha de banco.

## Iniciar o ambiente

Execute cada componente necessario em um terminal separado, a partir da raiz
do monorepo.

### Backend ERP

```bash
cd backend
mvn spring-boot:run
```

Porta configurada: `8080`.

### Backend do website

```bash
cd website_back
mvn spring-boot:run
```

Porta configurada: `8085`.

### Frontend ERP

```bash
cd frontend
npm run dev
```

Porta configurada no Quasar: `8084`.

### Site/PWA

```bash
cd website_front
npm run dev
```

Porta configurada no Vite: `5173`.

### Integracao WhatsApp

```bash
cd whatsapp_service
npm start
```

Inicie apenas os componentes necessarios ao fluxo em desenvolvimento. A
ordem usual e PostgreSQL, backends e, depois, as interfaces.

### Publisher local opcional

A UI publisher local exige a ponte opt-in `ROLE_SYSTEM`, chave RSA gerada fora
do repositório e o profile `development`, sempre em loopback. Em
`frontend/.env`:

```text
VITE_RELEASE_CONTROL_MODE=publisher
VITE_RELEASE_PUBLISHER_URL=http://127.0.0.1:8090
```

O publisher sobe com um comando, que já aplica as migrations pendentes:

```bash
cd release_control && uv run publisher
```

As variáveis vêm de `~/.config/emporio/release-control/publisher-runtime.env`,
começando por `RELEASE_CONTROL_PROFILE=development`. O que estiver exportado no
shell vence o arquivo, e faltando variável o launcher lista os nomes ausentes.

Suba ERP, publisher e frontend nessa ordem. Entre como root `ROLE_SYSTEM` e
acesse **Painel de Controle -> Desenvolvimento -> Gerenciamento de Releases**.
Não reutilize diretamente o JWT HS512 nem salve o token trocado. Os guias
completos estão em
[IDENTIDADE_PUBLISHER.md](../infrastructure/deployment/release-control/IDENTIDADE_PUBLISHER.md)
e
[UI_PUBLISHER.md](../infrastructure/deployment/release-control/UI_PUBLISHER.md).

## Onde aprofundar

- configuracao local segura:
  [CONFIGURACAO_LOCAL_SEGREDOS.md](./CONFIGURACAO_LOCAL_SEGREDOS.md);
- identidade local do publisher:
  [IDENTIDADE_PUBLISHER.md](../infrastructure/deployment/release-control/IDENTIDADE_PUBLISHER.md);
- UI publisher de desenvolvimento:
  [UI_PUBLISHER.md](../infrastructure/deployment/release-control/UI_PUBLISHER.md);
- runtime do fluxo QR:
  [QR_ORDERING_LOCAL_RUNTIME.md](./QR_ORDERING_LOCAL_RUNTIME.md);
- arquitetura:
  [../architecture/README.md](../architecture/README.md);
- modulos:
  [../modules/README.md](../modules/README.md);
- testes:
  [testing/README.md](./testing/README.md);
- APIs:
  [../api-reference/README.md](../api-reference/README.md).

## Limites deste guia

Este documento descreve somente o desenvolvimento local manual. A arquitetura
Docker/CI/CD e o controle de releases estao em implementacao incremental; sua
proposta aprovada nao deve ser confundida com infraestrutura ja implantada.
Consulte o
[tracker de implementacao](../infrastructure/deployment/implementation/README.md)
para o estado das slices.
