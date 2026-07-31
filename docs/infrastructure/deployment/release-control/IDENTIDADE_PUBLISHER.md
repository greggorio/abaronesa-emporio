# Identidade do publisher

> **Estado:** ponte local RS256/JWKS e cliente/UI de desenvolvimento
> implementados; credenciais operacionais, publicação remota e implantação
> continuam fora do escopo.

## Por que há uma troca de token

O JWT de sessão do ERP usa HS512, tem ciclo longo e não contém o contrato
`iss`/`aud`/`scope` exigido pelo publisher. Reutilizá-lo daria ao segredo
simétrico e à sessão comercial uma autoridade que não lhes pertence.

A ponte mantém os dois domínios separados:

```text
token ERP HS512
  -> POST autenticado de exchange, somente ROLE_SYSTEM
  -> token publisher RS256 de 300 segundos
  -> API publisher valida issuer, audience, scope e JWKS público
```

O token novo não autentica rotas comerciais do ERP e o
`JwtAuthenticationFilter` continua validando exclusivamente a sessão HS512.

## Rotas e autorização

As únicas rotas da ponte são:

| Método e path | Autorização | Resultado |
|---|---|---|
| `GET /api/release-control/identity/jwks` | pública quando a ponte está habilitada | uma chave RSA pública; `Cache-Control: no-store` |
| `POST /api/release-control/identity/token` | bearer ERP válido e `ROLE_SYSTEM` | token publisher curto |

O POST exige request sem query e sem body. `ROLE_ADMIN` isolado recebe 403;
usuário anônimo recebe 401. Headers adicionais não controlam audience, scope,
TTL ou algoritmo. A proteção `hasRole("SYSTEM")` existe no filtro HTTP e em
`@PreAuthorize`.

Quando `RELEASE_CONTROL_IDENTITY_ENABLED=false`, que é o default, os
componentes operacionais não são instanciados e as duas rotas não existem.

## Propriedades do emissor ERP

```text
RELEASE_CONTROL_IDENTITY_ENABLED=false
RELEASE_CONTROL_IDENTITY_ISSUER=
RELEASE_CONTROL_IDENTITY_PRIVATE_KEY_PATH=
RELEASE_CONTROL_IDENTITY_KEY_ID=
```

Ao habilitar, o issuer precisa ser URL HTTP(S) absoluta, sem userinfo, query,
fragmento ou slash final. HTTP é aceito apenas em `localhost` ou `127.0.0.1`.
O `kid` possui de 16 a 64 caracteres no formato fechado do contrato. O path
deve ser absoluto, não symlink e apontar para chave privada RSA CRT de pelo
menos 3072 bits, PKCS#8 PEM não criptografada, com no máximo 16 KiB.

Audience, scope, TTL e algoritmo não são propriedades configuráveis:

```text
audience = emporio-release-control
scope    = release:read release:publish
ttl      = 300 segundos
alg      = RS256
```

## JWT emitido

O header contém exatamente `alg=RS256`, `kid` configurado e `typ=JWT`. As
claims são:

```text
iss   issuer configurado
aud   emporio-release-control
sub   erp-user:<id numérico positivo>
scope release:read release:publish
iat   instante de emissão
nbf   mesmo instante de iat
exp   iat + 300 segundos
jti   UUID v4 novo
```

Email, nome, roles, grupos, segredo, chave, scopes deployer e refresh token não
são incluídos.

## JWKS

O JWKS contém exatamente uma chave com `kty`, `use`, `alg`, `kid`, `n` e `e`.
Módulo e expoente usam base64url sem padding e representam inteiros unsigned.
Nenhum parâmetro privado RSA sai do backend. A chave é lida uma vez no startup,
e a chave pública é derivada do mesmo objeto privado usado na assinatura.

## Perfis do publisher

| Regra | `runtime` | `development` | `test` |
|---|---|---|---|
| PostgreSQL | `sslmode=require` | sem SSL, host loopback | sem SSL, host loopback |
| GitHub API | `https://api.github.com` | `https://api.github.com` | HTTP loopback |
| issuer/JWKS | HTTPS | HTTP loopback | HTTP loopback |
| vínculo JWKS | configuração HTTPS | `<issuer>/jwks` | `<issuer>/jwks` |
| CORS | HTTPS explícito | HTTP loopback com porta | HTTP loopback |

O perfil `development` não enfraquece `runtime`. Ele apenas permite o ciclo
local entre frontend em `localhost:8084`, ERP em `127.0.0.1:8080` e publisher
em outra porta loopback.

## Gerar a chave fora do repositório

A chave privada pertence somente ao backend ERP emissor. Gere-a fora do
workspace e restrinja suas permissões:

```bash
openssl genpkey -algorithm RSA \
  -pkeyopt rsa_keygen_bits:3072 \
  -out /caminho-fora-do-repositorio/release-control-issuer.pem
chmod 600 /caminho-fora-do-repositorio/release-control-issuer.pem
```

O publisher recebe apenas a URL JWKS. Troca e rotação operacional do `kid` e da
chave permanecem para a implantação. Nenhuma chave de exemplo é adequada para
produção.

## Sequência local manual

1. Prepare PostgreSQL local para ERP e publisher.
2. Gere a chave fora do repositório e exporte as quatro propriedades do emissor.
3. Inicie o ERP:

   ```bash
   cd backend
   mvn spring-boot:run
   ```

4. Configure o publisher com `RELEASE_CONTROL_PROFILE=development`, execute a
   migration e inicie apenas em loopback:

   ```bash
   cd release_control
   uv sync --locked
   uv run alembic upgrade head
   uv run uvicorn emporio_release_control.main:app --host 127.0.0.1 --port 8090
   ```

5. Inicie o frontend ERP com o modo publisher local:

   ```bash
   cd frontend
   # .env: VITE_RELEASE_CONTROL_MODE=publisher
   # .env: VITE_RELEASE_PUBLISHER_URL=http://127.0.0.1:8090
   npm run dev
   ```

6. Entre como root `ROLE_SYSTEM` e abra **Painel de Controle ->
   Desenvolvimento -> Gerenciamento de Releases**. O cliente mantém o token
   publisher somente em memória. Consulte [UI_PUBLISHER.md](./UI_PUBLISHER.md).

Exemplo de troca, usando apenas um marcador e nunca um token real:

```bash
curl -X POST \
  -H 'Authorization: Bearer <token-erp>' \
  http://127.0.0.1:8080/api/release-control/identity/token
```

Respostas esperadas: 400 para query/body, 401 sem autenticação válida, 403 sem
`ROLE_SYSTEM` e 200 somente para SYSTEM. Não registre a resposta 200 em logs,
histórico de shell ou documentação.

## Limites e próximos passos

A UI não foi implementada na S16; a UI e o cliente de desenvolvimento foram
implementados posteriormente, na S17, com armazenamento do bearer publisher
somente em memória. Permanecem futuros a
rotação operacional de chaves, credenciais GitHub reais, publicação remota,
deployer e produção.
