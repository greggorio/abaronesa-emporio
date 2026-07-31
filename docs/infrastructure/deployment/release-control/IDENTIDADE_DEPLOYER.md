# Identidade do deployer

> **Estado:** ponte local RS256/JWKS implementada; credenciais operacionais e produção continuam fora de escopo.

## Por que a ponte do deployer é separada da ponte do publisher

A instância de produção do backend ERP é um artefato único: o mesmo JAR que roda no ambiente do desenvolvedor, quando implantado em produção, atua como emissor de tokens para ambos os modos (publisher e deployer).

A separação entre "quem pode publicar" e "quem pode implantar" não vem de um novo role ou papel, mas de **instâncias distintas do backend com bases de usuários distintas**:

- A instância de desenvolvimento emite tokens publisher (audiência: `emporio-release-control`).
- A instância de produção, quando explicitamente habilitada, emite tokens deployer (audiência: `emporio-release-control-deployer`).

Reutilizar credenciais, chaves ou audiences do publisher violaria a arquitetura de isolamento. Por isso, a ponte do deployer é:

- **Opt-in separadamente** via propriedade `RELEASE_CONTROL_DEPLOYER_IDENTITY_ENABLED`.
- **Completamente isolada** em rotas, audience, scope, chave privada e configuração.
- **Identicamente estruturada** ao publisher, sem abstrações prematuras nem reutilização de código.

## Fluxo de identidade

```text
┌─────────────────────────────────────────────────────────────────┐
│ Backend ERP (instância de produção, ROLE_SYSTEM autenticado)    │
│  Token de sessão HS512 (longo, comercial)                       │
│                                                                  │
│  POST /api/release-control/identity/deployer/token              │
│  Authorization: Bearer <token-erp-hs512>                        │
└─────────────────────────────────────────────────────────────────┘
                           ⬇
┌─────────────────────────────────────────────────────────────────┐
│ Controlador deployer                                            │
│ • Valida bearer ERP e ROLE_SYSTEM                               │
│ • Emite JWT RS256 de 300 segundos                               │
│ • Audience: emporio-release-control-deployer (fixa)             │
│ • Scopes: deployment:read deployment:execute (fixos)            │
└─────────────────────────────────────────────────────────────────┘
                           ⬇
┌─────────────────────────────────────────────────────────────────┐
│ Token deployer RS256 (curto, específico)                        │
│ {                                                               │
│   "iss": "http://...:8080/api/release-control/identity/deployer"
│   "aud": "emporio-release-control-deployer"                    │
│   "sub": "erp-user:<id>"                                       │
│   "scope": "deployment:read deployment:execute"                │
│   "exp": iat + 300, "jti": UUID v4                             │
│ }                                                               │
└─────────────────────────────────────────────────────────────────┘
                           ⬇
┌─────────────────────────────────────────────────────────────────┐
│ Release Control (modo deployer)                                 │
│ • Valida issuer, audience e JWKS público                        │
│ • Acessa rotas /api/deployment-control/v1/**                   │
│ • Escopo deployer nunca toca rotas do publisher                │
└─────────────────────────────────────────────────────────────────┘
```

## Rotas e autorização

As rotas da ponte deployer são simétricas ao publisher, mas isoladas:

| Método e path | Autorização | Resultado |
|---|---|---|
| `GET /api/release-control/identity/deployer/jwks` | pública quando a ponte está habilitada | uma chave RSA pública; `Cache-Control: no-store` |
| `POST /api/release-control/identity/deployer/token` | bearer ERP válido e `ROLE_SYSTEM` | token deployer curto (300s) |

O POST exige request sem query e sem body. `ROLE_ADMIN` isolado recebe 403; usuário anônimo recebe 401. Headers adicionais não controlam audience, scope, TTL ou algoritmo. A proteção `hasRole("SYSTEM")` existe no filtro HTTP e em `@PreAuthorize`.

Quando `RELEASE_CONTROL_DEPLOYER_IDENTITY_ENABLED=false` (padrão), que é o default, os componentes operacionais não são instanciados e as duas rotas não existem.

## Propriedades do emissor ERP (deployer)

```text
RELEASE_CONTROL_DEPLOYER_IDENTITY_ENABLED=false
RELEASE_CONTROL_DEPLOYER_IDENTITY_ISSUER=
RELEASE_CONTROL_DEPLOYER_IDENTITY_PRIVATE_KEY_PATH=
RELEASE_CONTROL_DEPLOYER_IDENTITY_KEY_ID=
```

Ao habilitar, o issuer precisa ser URL HTTP(S) absoluta, sem userinfo, query, fragmento ou slash final. HTTP é aceito apenas em `localhost` ou `127.0.0.1`.
O `kid` possui de 16 a 64 caracteres no formato fechado do contrato. O path deve ser absoluto, não symlink e apontar para chave privada RSA CRT de pelo menos 3072 bits, PKCS#8 PEM não criptografada, com no máximo 16 KiB.

Audience, scope, TTL e algoritmo não são propriedades configuráveis:

```text
audience = emporio-release-control-deployer
scope    = deployment:read deployment:execute
ttl      = 300 segundos
alg      = RS256
```

## JWT emitido

O header contém exatamente `alg=RS256`, `kid` configurado e `typ=JWT`. As claims são:

```text
iss   issuer configurado do deployer
aud   emporio-release-control-deployer
sub   erp-user:<id numérico positivo>
scope deployment:read deployment:execute
iat   instante de emissão
nbf   mesmo instante de iat
exp   iat + 300 segundos
jti   UUID v4 novo
```

Email, nome, roles, grupos, segredo, chave, scopes publisher, `deployment:rollback` e refresh token não são incluídos.

## JWKS

O JWKS contém exatamente uma chave com `kty`, `use`, `alg`, `kid`, `n` e `e`. Módulo e expoente usam base64url sem padding e representam inteiros unsigned. Nenhum parâmetro privado RSA sai do backend. A chave é lida uma vez no startup, e a chave pública é derivada do mesmo objeto privado usado na assinatura.

## Perfis do deployer

| Regra | `runtime` | `development` | `test` |
|---|---|---|---|
| PostgreSQL | `sslmode=require` | sem SSL, host loopback | sem SSL, host loopback |
| GitHub API | `https://api.github.com` | `https://api.github.com` | HTTP loopback |
| issuer/JWKS | HTTPS | HTTP loopback | HTTP loopback |
| vínculo JWKS | configuração HTTPS | `<issuer>/jwks` | `<issuer>/jwks` |
| CORS | HTTPS explícito | HTTP loopback com porta | HTTP loopback |

O perfil `development` não enfraquece `runtime`. Ele apenas permite o ciclo local do deployer em loopback.

## Isolamento de mode e audience

O `release_control` vincula mode e audience na configuração de bootstrap:

```text
mode == "publisher"  => jwt_audience == "emporio-release-control"
mode == "deployer"   => jwt_audience == "emporio-release-control-deployer"
```

Qualquer divergência falha no startup. Os três perfis (`runtime`, `development`, `test`) continuam controlando exclusivamente transporte (DB SSL, GitHub API, issuer loopback, CORS) de forma mode-agnóstica. Nenhum campo novo de perfil é criado.

## Gerar a chave fora do repositório

A chave privada pertence somente à instância de produção do backend ERP. Gere-a fora do workspace e restrinja suas permissões:

```bash
openssl genpkey -algorithm RSA \
  -pkeyopt rsa_keygen_bits:3072 \
  -out /caminho-fora-do-repositorio/release-control-deployer-issuer.pem
chmod 600 /caminho-fora-do-repositorio/release-control-deployer-issuer.pem
```

A chave privada do deployer **nunca é a mesma chave usada pelo publisher**. O `release_control` em modo deployer recebe apenas a URL JWKS. Rotação de chave operacional em produção fica para a slice de preparação da VPS (S31). Nenhuma chave fictícia do exemplo é adequada para produção.

## Sequência local manual — deployer

1. Prepare PostgreSQL local para o `release_control`.
2. Gere duas chaves RSA **distintas** (uma para publisher, uma para deployer) fora do repositório.
3. Inicie o backend em modo editor com a ponte deployer habilitada:

   ```bash
   cd backend
   RELEASE_CONTROL_DEPLOYER_IDENTITY_ENABLED=true \
   RELEASE_CONTROL_DEPLOYER_IDENTITY_ISSUER=http://127.0.0.1:8080/api/release-control/identity/deployer \
   RELEASE_CONTROL_DEPLOYER_IDENTITY_PRIVATE_KEY_PATH=/path/to/release-control-deployer-issuer.pem \
   RELEASE_CONTROL_DEPLOYER_IDENTITY_KEY_ID=deployer-key-2024-01 \
   mvn spring-boot:run
   ```

4. Configure o `release_control` com `RELEASE_CONTROL_MODE=deployer`, execute a migration e inicie em loopback:

   ```bash
   cd release_control
   uv sync --locked
   uv run alembic upgrade head
   RELEASE_CONTROL_PROFILE=development \
   RELEASE_CONTROL_MODE=deployer \
   RELEASE_CONTROL_JWT_ISSUER=http://127.0.0.1:8080/api/release-control/identity/deployer \
   RELEASE_CONTROL_JWT_AUDIENCE=emporio-release-control-deployer \
   RELEASE_CONTROL_JWT_JWKS_URL=http://127.0.0.1:8080/api/release-control/identity/deployer/jwks \
   uv run uvicorn emporio_release_control.main:app --host 127.0.0.1 --port 8091
   ```

5. Teste a troca de token (nunca com token real):

   ```bash
   curl -X POST \
     -H 'Authorization: Bearer <token-erp>' \
     http://127.0.0.1:8080/api/release-control/identity/deployer/token
   ```

Respostas esperadas: 400 para query/body, 401 sem autenticação válida, 403 sem `ROLE_SYSTEM` e 200 somente para SYSTEM. Não registre a resposta 200 em logs, histórico de shell ou documentação.

## Limites e próximos passos

A ponte deployer é implementada localmente nesta slice (S23) sem UI, VPS real nem credenciais operacionais.

Permanecem futuros:

- UI de produção para scopes deployer (S24);
- rotação operacional de chaves deployer em produção (S31);
- credenciais GitHub App reais para o deployer;
- implantação e operação em produção.
