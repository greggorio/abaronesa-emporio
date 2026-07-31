# S16 — Ponte de identidade RS256/JWKS e perfil local seguro do publisher

> **Estado:** `ACCEPTED` — 29/07/2026  
> **Tipo:** identidade, segurança de API e configuração local  
> **Executor previsto:** CLI  
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`  
> **Dependências:** S01 a S15 `ACCEPTED`  
> **Relatório de saída:** `S16-ponte-identidade-rs256-jwks-perfil-local-publisher.report.md`

## Instrução para delegação

Execute integralmente esta slice. Leia, nesta ordem:

1. esta task inteira;
2. a revisão terminal da S15;
3. `release-control/CONTRATO_API_ESTADOS_SEGURANCA.md`;
4. `release-control/RUNTIME_PUBLISHER.md`;
5. `release-control/api/publisher.openapi.yml`;
6. `release_control/src/emporio_release_control/config.py`;
7. `release_control/src/emporio_release_control/security.py`;
8. `backend/src/main/java/com/baronesa/emporio/security/JwtTokenProvider.java`;
9. `backend/src/main/java/com/baronesa/emporio/security/JwtAuthenticationFilter.java`;
10. `backend/src/main/java/com/baronesa/emporio/security/UserPrincipal.java`;
11. `backend/src/main/java/com/baronesa/emporio/config/SecurityConfig.java`;
12. `frontend/src/stores/userStore.js`, apenas como consumidor futuro.

O executor implementa as decisões abaixo. Não escolha rotas, claims, scopes,
algoritmo, TTL, formato de chave, perfil, política de autorização ou
alternativa de integração.

Não altere esta task nem o tracker:

```text
docs/infrastructure/deployment/implementation/README.md
```

## 1. Causa e resultado observável

O token de sessão atual do ERP é HS512 e não possui `iss`, `aud` e `scope`.
O publisher S15 aceita exclusivamente RS256, issuer/audience configurados e
chaves obtidas por JWKS. Reutilizar diretamente o token ERP violaria S06/S15.

Além disso, o Quasar local roda em `http://localhost:8084`, enquanto o perfil
`runtime` do publisher exige issuer, JWKS e CORS HTTPS. Relaxar o perfil de
produção também seria uma violação.

Ao final:

- o backend ERP pode, quando explicitamente habilitado, atuar como emissor
  restrito de tokens RS256 do publisher;
- existe exatamente um endpoint JWKS público e um endpoint autenticado de
  troca de token;
- somente um usuário ERP autenticado com `ROLE_SYSTEM` recebe o token;
- o token é curto, específico ao publisher e não reutiliza o segredo HS512;
- o publisher possui um perfil `development` estritamente loopback;
- os perfis `runtime` e `test` preservam sua segurança anterior;
- nenhuma UI é implementada nesta slice.

## 2. Fronteira autorizada

### 2.1 Backend ERP

Criar somente dentro de:

```text
backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/**
backend/src/test/java/com/baronesa/emporio/releasecontrol/identity/**
```

Alterar somente se necessário:

```text
backend/src/main/java/com/baronesa/emporio/config/SecurityConfig.java
backend/src/main/resources/application.properties
backend/src/main/resources/application-dev.properties
backend/src/main/resources/application-prod.properties
backend/src/test/resources/application-test.properties
backend/.env.example
```

### 2.2 Publisher

Alterar somente:

```text
release_control/src/emporio_release_control/config.py
release_control/.env.example
release_control/tests/test_config.py
release_control/tests/test_security.py
```

Se os testes existentes estiverem em arquivos com outros nomes, alterar
somente os arquivos de teste já existentes que exercem diretamente
`Settings` ou `JwtVerifier`.

### 2.3 Contrato, validação e documentação

Criar:

```text
tools/releases/validate_publisher_identity_bridge.py
tools/releases/tests/test_publisher_identity_bridge_contract.py
docs/infrastructure/deployment/release-control/IDENTIDADE_PUBLISHER.md
docs/infrastructure/deployment/implementation/slices/S16-ponte-identidade-rs256-jwks-perfil-local-publisher.report.md
```

Alterar somente:

```text
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/release-control/RUNTIME_PUBLISHER.md
docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md
docs/development/ONBOARDING_MINIMO.md
docs/development/README.md
```

## 3. Fora de escopo

Não:

- alterar o token HS512 usado pela sessão normal do ERP;
- aceitar HS256/HS512 no publisher;
- criar refresh token;
- criar, persistir ou revogar sessão do publisher;
- aceitar scopes solicitados pelo cliente;
- implementar frontend, tela, card, rota Vue ou cliente Axios;
- implementar modo deployer;
- criar Dockerfile, Compose, workflow ou migration;
- alterar OpenAPI, máquinas de estado ou schemas S06–S15;
- alterar rotas comerciais dos dois backends;
- gerar chave RSA operacional dentro do repositório;
- acessar GitHub, GHCR, VPS, DNS ou produção;
- executar commit, tag, push, publicação ou deploy;
- criar S17.

## 4. Contrato HTTP fechado

O prefixo é:

```text
/api/release-control/identity
```

Devem existir exatamente as duas rotas novas abaixo.

### 4.1 JWKS

```http
GET /api/release-control/identity/jwks
```

Regras:

- público, sem bearer token;
- disponível somente quando a ponte estiver habilitada;
- `200 application/json`;
- header `Cache-Control: no-store`;
- corpo fechado:

```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "<key-id-configurado>",
      "n": "<base64url-sem-padding>",
      "e": "<base64url-sem-padding>"
    }
  ]
}
```

- exatamente uma chave;
- nenhum campo privado RSA;
- ordem dos campos não é autoridade, mas o conjunto de campos é exato;
- `n` e `e` representam inteiros positivos unsigned, sem byte zero
  artificial;
- endpoint desabilitado resulta em ausência da rota, não em JWKS vazio.

### 4.2 Troca de token

```http
POST /api/release-control/identity/token
Authorization: Bearer <token-ERP-existente>
```

Regras:

- aceita somente request sem query string e sem bytes de body;
- qualquer query string ou body não vazio retorna `400` sem emitir token;
- headers arbitrários nunca alteram audience, scope, TTL ou algoritmo;
- exige autenticação ERP válida;
- exige `ROLE_SYSTEM` na configuração HTTP e também por autorização no
  método;
- `ROLE_ADMIN` isolado não basta;
- resposta de sucesso `200 application/json`:

```json
{
  "accessToken": "<jwt-rs256>",
  "tokenType": "Bearer",
  "expiresIn": 300,
  "scope": "release:read release:publish"
}
```

- propriedades extras são proibidas no DTO produzido;
- token/claims/chave/caminho nunca são registrados em log;
- falha de autenticação permanece `401`;
- usuário autenticado sem `ROLE_SYSTEM` recebe `403`;
- não criar uma segunda rota equivalente.

## 5. Contrato do JWT emitido

Cabeçalho obrigatório:

```json
{
  "alg": "RS256",
  "kid": "<key-id-configurado>",
  "typ": "JWT"
}
```

Claims obrigatórias:

```text
iss   = issuer configurado, sem slash final
aud   = "emporio-release-control"
sub   = "erp-user:<id-numérico-positivo>"
scope = "release:read release:publish"
iat   = instante de emissão em NumericDate
nbf   = mesmo instante de iat
exp   = iat + 300 segundos
jti   = UUID v4 canônico e novo a cada emissão
```

Não incluir:

- email, nome, roles ou grupo;
- segredo/chave;
- scopes deployer;
- claims de infraestrutura;
- refresh token.

O token deve ser assinado com RSASSA-PKCS1-v1_5 e SHA-256. Não aceitar
algoritmo vindo de configuração ou request.

## 6. Autorização e isolamento

Em `SecurityConfig`:

```text
GET  /api/release-control/identity/jwks  -> permitAll
POST /api/release-control/identity/token -> hasRole("SYSTEM")
```

A ordem desses matchers deve preceder `anyRequest().authenticated()`.

O controller do POST também usa:

```text
@PreAuthorize("hasRole('SYSTEM')")
```

O `sub` é derivado de `UserPrincipal.getId()`. Se principal, ID ou autoridade
não coincidirem com o contrato, falhar sem emitir token.

O token RS256 novo não substitui, não modifica e não deve autenticar as rotas
ERP através do `JwtAuthenticationFilter` HS512.

## 7. Habilitação e propriedades do backend

A ponte é opt-in:

```text
app.release-control.identity.enabled=${RELEASE_CONTROL_IDENTITY_ENABLED:false}
app.release-control.identity.issuer=${RELEASE_CONTROL_IDENTITY_ISSUER:}
app.release-control.identity.private-key-path=${RELEASE_CONTROL_IDENTITY_PRIVATE_KEY_PATH:}
app.release-control.identity.key-id=${RELEASE_CONTROL_IDENTITY_KEY_ID:}
```

Decisões fixas no código, não configuráveis:

```text
audience = emporio-release-control
scope = release:read release:publish
ttl = 300 segundos
algoritmo = RS256
```

Quando `enabled=false`:

- classes operacionais da ponte não são instanciadas;
- issuer, path e kid vazios não quebram o backend;
- as duas rotas não existem;
- o funcionamento atual do ERP é preservado.

Quando `enabled=true`, falhar no startup se:

- issuer não for URL HTTP(S) absoluta sem userinfo, query ou fragment;
- issuer possuir slash final;
- issuer HTTP não for loopback;
- `kid` não obedecer `^[A-Za-z0-9][A-Za-z0-9._-]{15,63}$`;
- path estiver vazio, relativo, for symlink ou não apontar para arquivo
  regular;
- arquivo exceder 16 KiB;
- PEM não for exatamente chave privada PKCS#8 não criptografada:
  `-----BEGIN PRIVATE KEY-----`;
- chave não for RSA CRT;
- módulo tiver menos de 3072 bits.

Não suportar `BEGIN RSA PRIVATE KEY`, chave criptografada, JKS, PKCS#12,
download remoto ou conteúdo inline em variável.

Usar as dependências JJWT 0.12.6 e Java já existentes. Não adicionar
dependência Maven.

## 8. Perfis do publisher

`Settings.profile` passa a aceitar exatamente:

```text
runtime
development
test
```

### 8.1 `runtime`

Preservar integralmente:

- PostgreSQL com `sslmode=require`;
- GitHub API exatamente `https://api.github.com`;
- issuer, JWKS e origens CORS somente HTTPS;
- nenhum host loopback exigido.

### 8.2 `development`

Contrato exato:

- `db_sslmode=disable`;
- `db_host` somente `localhost` ou `127.0.0.1`;
- GitHub API exatamente `https://api.github.com`;
- issuer HTTP somente em `localhost` ou `127.0.0.1`;
- JWKS URL igual a `<issuer>/jwks`;
- CORS somente origens HTTP loopback com porta explícita;
- sem userinfo, path diferente de `/`, query ou fragment;
- `origin == "*"` proibido.

Exemplo canônico:

```text
RELEASE_CONTROL_PROFILE=development
RELEASE_CONTROL_JWT_ISSUER=http://127.0.0.1:8080/api/release-control/identity
RELEASE_CONTROL_JWT_JWKS_URL=http://127.0.0.1:8080/api/release-control/identity/jwks
RELEASE_CONTROL_CORS_ORIGINS=http://localhost:8084
```

O perfil `development` pode publicar no GitHub real quando credenciais reais
forem configuradas futuramente; esta slice não executa essa integração.

### 8.3 `test`

Preservar:

- PostgreSQL sem SSL;
- GitHub API apenas HTTP loopback;
- issuer/JWKS/CORS apenas HTTP loopback;
- nenhuma rede não loopback nos testes.

O contrato `JWKS URL == issuer + "/jwks"` também vale em `test`.

## 9. Material criptográfico e ciclo local

A implementação lê a chave uma única vez no startup e mantém apenas os
objetos de chave em memória. Não reler a cada request.

Derivar a chave pública da chave privada `RSAPrivateCrtKey`. A mesma
instância pública alimenta o JWKS e a verificação dos testes.

Não criar chave real nesta slice. A documentação deve prescrever ao operador,
fora do repositório:

```bash
openssl genpkey -algorithm RSA \
  -pkeyopt rsa_keygen_bits:3072 \
  -out /caminho-fora-do-repositorio/release-control-issuer.pem
chmod 600 /caminho-fora-do-repositorio/release-control-issuer.pem
```

Registrar que:

- a chave privada pertence somente ao backend ERP emissor;
- o publisher recebe somente a URL JWKS;
- troca/rotação de chave operacional permanece para a implantação;
- nenhuma chave fictícia do exemplo é adequada para produção.

## 10. Compatibilidade obrigatória

Os testes devem comprovar que:

1. o JWT emitido verifica com a chave pública publicada no JWKS;
2. o `JwtVerifier` Python aceita um RS256/JWKS equivalente com issuer,
   audience e scopes exatos;
3. HS512, `kid` desconhecido, issuer/audience divergentes, expiração e scope
   divergente continuam rejeitados pelo publisher;
4. nenhum token ERP HS512 é aceito diretamente pelo publisher;
5. body ou query string no endpoint de troca retornam `400`, e headers
   adicionais nunca alteram o scope;
6. dois tokens sucessivos possuem `jti` diferentes;
7. `exp - iat == 300`;
8. chave pública não contém material privado;
9. ponte desabilitada preserva o contexto normal do backend.

## 11. Testes causais mínimos

### 11.1 Backend

Criar testes independentes para:

- configuração desabilitada;
- issuer HTTPS válido;
- issuer HTTP loopback válido;
- issuer HTTP não loopback rejeitado;
- issuer com slash/query/userinfo rejeitado;
- `kid` inválido;
- path relativo, symlink, ausente e arquivo grande;
- PEM errado, PKCS#1, criptografado, não RSA e RSA 2048;
- RSA 3072 válida;
- JWKS exato, sem padding e sem campos privados;
- token e claims exatos;
- `jti` único;
- anônimo `401`;
- ADMIN sem SYSTEM `403`;
- SYSTEM `200`;
- request com body ou query recebe `400` e não emite token;
- headers adicionais não ampliam scope;
- ausência de logs de token/chave.

As chaves de teste são efêmeras e criadas apenas em diretório temporário.

### 11.2 Publisher

Cobrir a matriz dos três perfis:

- positivos canônicos;
- runtime rejeita HTTP;
- development rejeita DB/GitHub/issuer/JWKS/CORS não permitidos;
- test rejeita transporte não loopback;
- `JWT_AUDIENCE` diferente de `emporio-release-control` falha;
- JWKS diferente de `issuer + /jwks` falha em development/test.

### 11.3 Validador estrutural

`validate_publisher_identity_bridge.py` deve falhar fechado e verificar ao
menos:

- duas rotas exatas;
- matcher público e matcher SYSTEM;
- `@PreAuthorize`;
- algoritmo, audience, scope e TTL fixos;
- opt-in default false;
- PKCS#8/RSA 3072;
- claims e resposta exatas;
- perfil development loopback;
- runtime HTTPS preservado;
- nenhuma dependência Maven nova;
- documentação canônica.

A suíte mutante deve alterar/remover cada uma dessas condições em cópia
temporária e provar falha causal. Busca textual isolada sem mutantes não é
suficiente.

## 12. Documentação obrigatória

`IDENTIDADE_PUBLISHER.md` deve conter:

- por que o JWT ERP não é reutilizado;
- diagrama textual ERP token -> exchange -> publisher token -> publisher;
- rotas e autorização;
- propriedades e defaults;
- claims, TTL e scopes;
- matriz `runtime/development/test`;
- geração segura da chave fora do repo;
- sequência manual para subir backend, publisher e frontend;
- exemplo de request sem token real;
- erros esperados;
- limites da slice e itens futuros.

Atualizar:

- README do release control com link e estado real;
- RUNTIME_PUBLISHER com o perfil development;
- contrato de segurança marcando a ponte como implementada;
- onboarding local com os pré-requisitos do publisher, sem afirmar que a UI
  já existe;
- índice de desenvolvimento com o novo guia.

Não registrar valores reais, tokens, modulus, paths do host ou fingerprints
operacionais.

## 13. Matriz obrigatória

Executar no CWD indicado e registrar comando, exit, resultado e interpretação:

```bash
cd backend
mvn -B verify
```

```bash
cd release_control
uv lock --check
uv run ruff check .
uv run mypy --strict src
uv run pytest -q
uv run pytest --cov=emporio_release_control --cov-branch --cov-fail-under=90
```

```bash
PYTHONDONTWRITEBYTECODE=1 \
python3 tools/releases/validate_publisher_identity_bridge.py

PYTHONDONTWRITEBYTECODE=1 \
python3 -m unittest \
  tools/releases/tests/test_publisher_identity_bridge_contract.py -v

PYTHONDONTWRITEBYTECODE=1 \
python3 -m unittest discover \
  -s tools/releases/tests \
  -p 'test_*.py'

PYTHONDONTWRITEBYTECODE=1 \
python3 tools/releases/release_control_contract.py validate

git diff --check
git diff --cached --name-only
git rev-parse --verify HEAD
git tag --list
git reflog
```

Não repetir actionlint: workflow YAML está fora da fronteira.

## 14. Higiene e estado protegido

Ao final:

- índice Git real vazio;
- HEAD inexistente;
- tags vazias e reflog inexistente;
- exatamente os três workflows S11–S14;
- nenhum `.venv`, cache Python, coverage ou chave de teste residual;
- nenhum container/rede/volume criado por esta slice;
- nenhum arquivo PEM/DER/JKS/P12/PFX novo no workspace;
- nenhum commit, tag, push ou acesso externo;
- nenhuma S17.

Não apagar ou alterar artefato preexistente alheio. Limpar somente recurso
criado pela própria execução e registrar alvo exato.

## 15. Relatório obrigatório

Criar o relatório previsto com:

- resumo e estado;
- arquivos alterados;
- causa de incompatibilidade HS512 versus RS256/JWKS;
- rotas, autorização, claims e propriedades;
- validação do material criptográfico;
- matriz dos três perfis;
- lista nominal dos testes causais;
- comandos, exits, resultados e interpretação;
- falhas intermediárias e correções;
- evidência de nenhuma chave/token real;
- estado Git, workflows, caches e resíduos;
- divergências e itens não determinados.

Para cada validação persistir:

```text
CWD
comando exato
exit code
resultado
interpretação
artefatos/resíduos
```

Estado final obrigatório:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```

Não declarar `ACCEPTED` e não criar S17.

## 16. Critérios de aceite do orquestrador

A S16 só será aceita se:

- a ponte for opt-in e ausente quando desabilitada;
- somente SYSTEM obtiver token;
- JWKS e JWT coincidirem byte/semanticamente com o contrato;
- RS256, audience, scopes e TTL não forem controláveis pelo cliente;
- nenhuma chave privada sair do backend;
- token ERP HS512 continuar separado;
- development for estritamente loopback sem relaxar runtime;
- testes causais, cobertura, lint, typing e validadores passarem;
- documentação ensinar o ciclo local sem alegar UI pronta;
- fronteira, segredos e estado Git forem preservados.

## 17. Condições de bloqueio

Pare e documente, sem improvisar, se:

- JJWT 0.12.6 não conseguir emitir RS256 com `kid` sem nova dependência;
- `UserPrincipal` autenticado não expuser ID e ROLE_SYSTEM;
- Spring Security não permitir proteger as duas rotas de forma distinta;
- `JwtVerifier` exigir claim diferente do contrato escrito;
- o perfil development exigir transporte não loopback;
- algum teste tentar acessar rede não loopback;
- for necessário alterar frontend, OpenAPI, workflow, Docker ou produção.
