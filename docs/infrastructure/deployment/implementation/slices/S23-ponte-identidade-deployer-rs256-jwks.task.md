# S23 — Ponte de identidade RS256/JWKS do deployer

> **Estado:** `PLANNED`
> **Tipo:** identidade, segurança de API e configuração local
> **Executor previsto:** CLI
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`
> **Dependências:** S01 a S22 `ACCEPTED`
> **Relatório de saída:** `S23-ponte-identidade-deployer-rs256-jwks.report.md`

## Instrução para delegação

Execute integralmente esta slice. Leia, nesta ordem:

1. esta task inteira;
2. a revisão terminal da S22 (seção final do relatório S22);
3. `docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md`;
4. `docs/infrastructure/deployment/release-control/RUNTIME_DEPLOYER.md`;
5. `docs/infrastructure/deployment/release-control/IDENTIDADE_PUBLISHER.md`;
6. `docs/infrastructure/deployment/release-control/api/deployer.openapi.yml`;
7. `release_control/src/emporio_release_control/config.py`;
8. `release_control/src/emporio_release_control/security.py`;
9. `release_control/src/emporio_release_control/deployer_schemas.py`;
10. `backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/ReleaseControlIdentityController.java`;
11. `backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/ReleaseControlIdentityConfiguration.java`;
12. `backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/ReleaseControlIdentityService.java`;
13. `backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/ReleaseControlIdentityKeyMaterial.java`;
14. `backend/src/main/java/com/baronesa/emporio/config/SecurityConfig.java`;
15. `backend/src/main/java/com/baronesa/emporio/security/UserPrincipal.java`, apenas como consumidor.

O executor implementa as decisões abaixo. Não escolha rotas, claims, scopes,
algoritmo, TTL, formato de chave, perfil, política de autorização, nome de
propriedade ou alternativa de integração.

Não altere esta task nem o tracker:

```text
docs/infrastructure/deployment/implementation/README.md
```

## 1. Causa e resultado observável

O deployer S22 (`RELEASE_CONTROL_MODE=deployer`) exige RS256, issuer/audience
configurados e chaves por JWKS, exatamente como o publisher S16. Hoje,
`Settings.jwt_audience` aceita apenas o literal `"emporio-release-control"`
para os dois modos — se o runtime deployer fosse ligado com as variáveis do
publisher, ele aceitaria tokens emitidos para o publisher. Isso viola a
arquitetura ("GitHub Apps publisher e deployer são distintas") e o objetivo
desta slice de não reutilizar credenciais publisher.

A instância de produção do `backend` é o mesmo artefato do BOM comercial que
roda no ambiente do desenvolvedor — ela também é implantada em produção como
um dos seis componentes. A separação entre "quem pode publicar" e "quem pode
mandar implantar" não vem de um papel novo, vem de serem *instâncias*
distintas do `backend` com bases de usuários distintas (a instância de
desenvolvimento emite tokens de publisher; a instância de produção, quando
habilitada, emite tokens de deployer). Por isso esta slice reaproveita
`ROLE_SYSTEM` já existente, sem criar papel novo.

Ao final:

- o backend, quando explicitamente habilitado, pode atuar como emissor
  restrito de tokens RS256 do deployer, com material criptográfico e
  audience própria, nunca compartilhados com a ponte do publisher S16;
- existe exatamente um endpoint JWKS público e um endpoint autenticado de
  troca de token para o deployer, distintos das rotas do publisher;
- somente um usuário `ROLE_SYSTEM` autenticado na instância corrente do
  backend recebe o token do deployer;
- o token é curto, específico ao deployer, anuncia somente
  `deployment:read deployment:execute` e nunca `deployment:rollback`;
- `Settings` do `release_control` liga `mode` e `jwt_audience` de forma
  fechada: `deployer` só aceita a audience do deployer, `publisher` só aceita
  a audience já existente;
- os perfis `runtime`, `development` e `test` do `release_control` continuam
  fail-closed para o modo deployer, replicando exatamente a matriz já aceita
  para o publisher em S16;
- nenhuma UI é implementada nesta slice;
- rollback continua indisponível e não é anunciado em nenhum artefato novo.

## 2. Decisões fechadas

```text
rota JWKS               GET  /api/release-control/identity/deployer/jwks
rota de troca           POST /api/release-control/identity/deployer/token
audience                emporio-release-control-deployer
scope emitido           deployment:read deployment:execute
sub                     erp-user:<id-numérico-positivo>   (mesmo formato do publisher)
ttl                     300 segundos
algoritmo               RS256 / RSASSA-PKCS1-v1_5 / SHA-256
autorização HTTP        hasRole("SYSTEM") + @PreAuthorize("hasRole('SYSTEM')")
propriedade habilitação app.release-control.deployer-identity.enabled
                        (env RELEASE_CONTROL_DEPLOYER_IDENTITY_ENABLED, default false)
propriedade issuer      app.release-control.deployer-identity.issuer
                        (env RELEASE_CONTROL_DEPLOYER_IDENTITY_ISSUER)
propriedade chave       app.release-control.deployer-identity.private-key-path
                        (env RELEASE_CONTROL_DEPLOYER_IDENTITY_PRIVATE_KEY_PATH)
propriedade kid         app.release-control.deployer-identity.key-id
                        (env RELEASE_CONTROL_DEPLOYER_IDENTITY_KEY_ID)
pacote Java novo         com.baronesa.emporio.releasecontrol.identity.deployer
```

O pacote Java é uma classe irmã do pacote `identity` já aceito em S16, não uma
generalização dele. Não modificar `ReleaseControlIdentityController`,
`ReleaseControlIdentityConfiguration`, `ReleaseControlIdentityService` ou
`ReleaseControlIdentityKeyMaterial` do publisher — apenas duplicar a forma já
aceita, com nomes, propriedades, audience e chave próprios do deployer. Isso
evita reabrir uma slice `ACCEPTED` para introduzir uma abstração prematura.

`RELEASE_CONTROL_JWT_AUDIENCE` do `release_control` deixa de ser um literal
único e passa a ser um dos dois valores acima, vinculado a `mode` por
validação cruzada — nenhum terceiro valor é aceito.

## 3. Fronteira autorizada

### 3.1 Backend ERP

Criar somente dentro de:

```text
backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/deployer/**
backend/src/test/java/com/baronesa/emporio/releasecontrol/identity/deployer/**
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

Não alterar nenhum arquivo do pacote `identity` (sem sufixo `deployer`) já
aceito em S16.

### 3.2 Release control

Alterar somente:

```text
release_control/src/emporio_release_control/config.py
release_control/.env.example
release_control/tests/test_config_security.py
release_control/tests/test_mode_isolation.py
```

Se os testes existentes de `Settings`/`JwtVerifier` estiverem em outro arquivo
já existente exercendo diretamente essas classes, alterar esse arquivo em vez
de criar um novo.

### 3.3 Contrato, validação e documentação

Criar:

```text
tools/deploy/validate_deployer_identity_bridge.py
tools/deploy/tests/test_deployer_identity_bridge_contract.py
docs/infrastructure/deployment/release-control/IDENTIDADE_DEPLOYER.md
docs/infrastructure/deployment/implementation/slices/S23-ponte-identidade-deployer-rs256-jwks.report.md
```

Alterar somente:

```text
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/release-control/RUNTIME_DEPLOYER.md
docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md
```

Não é necessário alterar `docs/development/ONBOARDING_MINIMO.md` nem
`docs/development/README.md`: a ponte do deployer é operada em produção, não
no ciclo local do desenvolvedor.

## 4. Fora de escopo

Não:

- alterar o token HS512 da sessão normal do ERP;
- alterar qualquer arquivo da ponte publisher S16 (`identity/**` sem sufixo
  `deployer`);
- aceitar HS256/HS512 no deployer;
- criar refresh token;
- criar, persistir ou revogar sessão do deployer;
- aceitar scopes solicitados pelo cliente;
- anunciar ou emitir `deployment:rollback` em qualquer resposta;
- implementar frontend, tela, card, rota Vue ou cliente Axios (isso é S24);
- criar Dockerfile, Compose, workflow ou migration;
- alterar OpenAPI, máquinas de estado ou schemas S06–S22;
- alterar rotas comerciais dos backends;
- gerar chave RSA operacional dentro do repositório;
- criar papel/role novo (`ROLE_DEPLOY` ou equivalente);
- acessar GitHub, GHCR, VPS, DNS ou produção real;
- executar commit, tag, push, publicação ou deploy;
- criar S24.

## 5. Contrato HTTP fechado

Prefixo:

```text
/api/release-control/identity/deployer
```

### 5.1 JWKS

```http
GET /api/release-control/identity/deployer/jwks
```

Regras idênticas ao contrato JWKS do publisher (S16 §4.1), aplicadas à chave
do deployer:

- público, sem bearer token;
- disponível somente quando a ponte deployer estiver habilitada;
- `200 application/json`, header `Cache-Control: no-store`;
- corpo fechado com exatamente uma chave:

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

- nenhum campo privado RSA;
- `n`/`e` sem byte zero artificial;
- ponte desabilitada resulta em ausência da rota, não em JWKS vazio.

### 5.2 Troca de token

```http
POST /api/release-control/identity/deployer/token
Authorization: Bearer <token-ERP-existente>
```

Regras idênticas ao contrato de troca do publisher (S16 §4.2):

- sem query string e sem bytes de body; qualquer um dos dois retorna `400`
  sem emitir token;
- headers arbitrários nunca alteram audience, scope, TTL ou algoritmo;
- exige `ROLE_SYSTEM` no matcher HTTP e por `@PreAuthorize` no método;
- `ROLE_ADMIN` isolado não basta;
- resposta de sucesso `200 application/json`:

```json
{
  "accessToken": "<jwt-rs256>",
  "tokenType": "Bearer",
  "expiresIn": 300,
  "scope": "deployment:read deployment:execute"
}
```

- propriedades extras proibidas no DTO;
- token/claims/chave/caminho nunca registrados em log;
- falha de autenticação `401`; autenticado sem `ROLE_SYSTEM` `403`;
- não criar rota equivalente adicional.

## 6. Contrato do JWT emitido

Cabeçalho:

```json
{ "alg": "RS256", "kid": "<key-id-configurado>", "typ": "JWT" }
```

Claims:

```text
iss   = issuer configurado do deployer, sem slash final
aud   = "emporio-release-control-deployer"
sub   = "erp-user:<id-numérico-positivo>"
scope = "deployment:read deployment:execute"
iat   = instante de emissão em NumericDate
nbf   = mesmo instante de iat
exp   = iat + 300 segundos
jti   = UUID v4 canônico e novo a cada emissão
```

Não incluir: email, nome, roles, grupo, segredo/chave, `deployment:rollback`,
claims de infraestrutura, refresh token.

Assinatura RSASSA-PKCS1-v1_5/SHA-256. Não aceitar algoritmo vindo de
configuração ou request.

## 7. Autorização e isolamento

Em `SecurityConfig`, os dois novos matchers devem preceder
`anyRequest().authenticated()` e ficar ao lado — não misturados — dos
matchers do publisher já existentes:

```text
GET  /api/release-control/identity/deployer/jwks  -> permitAll
POST /api/release-control/identity/deployer/token -> hasRole("SYSTEM")
```

O `sub` deriva de `UserPrincipal.getId()`. Se principal, ID ou autoridade não
coincidirem com o contrato, falhar sem emitir token.

O token RS256 do deployer não substitui, não modifica e não autentica as
rotas ERP via `JwtAuthenticationFilter` HS512, e não é aceito pelo verificador
do publisher — audiences distintas garantem essa rejeição por construção; um
teste causal deve prová-la.

## 8. Habilitação e propriedades do backend

```text
app.release-control.deployer-identity.enabled=${RELEASE_CONTROL_DEPLOYER_IDENTITY_ENABLED:false}
app.release-control.deployer-identity.issuer=${RELEASE_CONTROL_DEPLOYER_IDENTITY_ISSUER:}
app.release-control.deployer-identity.private-key-path=${RELEASE_CONTROL_DEPLOYER_IDENTITY_PRIVATE_KEY_PATH:}
app.release-control.deployer-identity.key-id=${RELEASE_CONTROL_DEPLOYER_IDENTITY_KEY_ID:}
```

Fixos no código, não configuráveis:

```text
audience = emporio-release-control-deployer
scope = deployment:read deployment:execute
ttl = 300 segundos
algoritmo = RS256
```

Quando `enabled=false`: classes operacionais da ponte deployer não são
instanciadas; issuer/path/kid vazios não quebram o backend; as duas rotas não
existem; nada muda para ERP ou publisher.

Quando `enabled=true`, falhar no startup exatamente pelas mesmas causas já
aceitas em S16 §7 (issuer inválido/com slash final/HTTP não loopback; `kid`
fora do padrão `^[A-Za-z0-9][A-Za-z0-9._-]{15,63}$`; path vazio/relativo/
symlink/não regular; arquivo > 16 KiB; PEM que não seja PKCS#8 não
criptografado; chave não RSA CRT; módulo < 3072 bits). Reutilizar a mesma
lógica de validação por composição ou cópia direta do padrão já aceito — a
decisão de não generalizar (§2) vale para as classes de produção, não impede
extrair um helper de teste puramente local ao novo pacote, se necessário.

Não suportar `BEGIN RSA PRIVATE KEY`, chave criptografada, JKS, PKCS#12,
download remoto ou conteúdo inline em variável. Não adicionar dependência
Maven nova.

## 9. Perfis do release_control (modo deployer)

`Settings.jwt_audience` passa a aceitar exatamente:

```text
Literal["emporio-release-control", "emporio-release-control-deployer"]
```

Validação cruzada obrigatória em `Settings`:

```text
mode == "publisher"  => jwt_audience == "emporio-release-control"
mode == "deployer"   => jwt_audience == "emporio-release-control-deployer"
```

Qualquer combinação divergente falha no startup. Os perfis `runtime`,
`development` e `test` continuam controlando exclusivamente transporte
(`db_sslmode`, `github_api_base`, esquema/loopback de `jwt_issuer` e
`jwt_jwks_url`, CORS) exatamente como já validado para o publisher — este
comportamento é mode-agnóstico e não deve ser duplicado nem divergir entre
`publisher` e `deployer`. Nenhum campo novo de perfil é criado; a única
mudança de `Settings` é a audience literal ampliada e o vínculo cruzado com
`mode`.

Exemplo canônico de desenvolvimento/teste local do deployer:

```text
RELEASE_CONTROL_MODE=deployer
RELEASE_CONTROL_PROFILE=test
RELEASE_CONTROL_JWT_ISSUER=http://127.0.0.1:8080/api/release-control/identity/deployer
RELEASE_CONTROL_JWT_JWKS_URL=http://127.0.0.1:8080/api/release-control/identity/deployer/jwks
RELEASE_CONTROL_JWT_AUDIENCE=emporio-release-control-deployer
```

## 10. Material criptográfico e ciclo local

Ler a chave uma única vez no startup; manter só os objetos de chave em
memória. Derivar a chave pública da `RSAPrivateCrtKey`, alimentando JWKS e
testes com a mesma instância.

Não criar chave real nesta slice. Documentar, fora do repositório:

```bash
openssl genpkey -algorithm RSA \
  -pkeyopt rsa_keygen_bits:3072 \
  -out /caminho-fora-do-repositorio/release-control-deployer-issuer.pem
chmod 600 /caminho-fora-do-repositorio/release-control-deployer-issuer.pem
```

Registrar que:

- a chave privada do deployer pertence somente à instância de produção do
  backend e nunca é a mesma chave usada pela ponte do publisher;
- o `release_control` em modo deployer recebe somente a URL JWKS;
- rotação de chave operacional em produção fica para a slice de preparação da
  VPS (S31);
- nenhuma chave fictícia do exemplo é adequada para produção.

## 11. Compatibilidade obrigatória

Os testes devem comprovar que:

1. o JWT emitido verifica com a chave pública publicada no JWKS do deployer;
2. o `JwtVerifier` Python, configurado em modo `deployer`, aceita um
   RS256/JWKS equivalente com issuer, audience `emporio-release-control-deployer`
   e scope exatos;
3. o mesmo `JwtVerifier` em modo `deployer` rejeita um token válido emitido
   pela ponte do publisher (audience `emporio-release-control`), e
   vice-versa;
4. HS512, `kid` desconhecido, issuer/audience divergentes, expiração e scope
   divergente continuam rejeitados;
5. nenhum token ERP HS512 é aceito diretamente;
6. body ou query string no endpoint de troca retornam `400`; headers extras
   nunca alteram o scope;
7. dois tokens sucessivos possuem `jti` diferentes;
8. `exp - iat == 300`;
9. chave pública não contém material privado;
10. ponte desabilitada preserva o contexto normal do backend;
11. `Settings` recusa `mode="deployer"` com `jwt_audience` do publisher e
    recusa `mode="publisher"` com `jwt_audience` do deployer.

## 12. Testes causais mínimos

### 12.1 Backend

Espelhar a lista já aceita em S16 §11.1 para o novo pacote `identity.deployer`,
incluindo: habilitação desligada; issuer HTTPS válido; issuer HTTP loopback
válido; issuer não loopback rejeitado; issuer com slash/query/userinfo
rejeitado; `kid` inválido; path relativo/symlink/ausente/grande; PEM
errado/PKCS#1/criptografado/não RSA/RSA 2048; RSA 3072 válida; JWKS exato sem
padding e sem campos privados; token e claims exatos (audience e scope do
deployer); `jti` único; anônimo `401`; ADMIN sem SYSTEM `403`; SYSTEM `200`;
body/query `400`; headers extras não ampliam scope; ausência de logs de
token/chave; **token do deployer rejeitado pelo verificador do publisher e
vice-versa** (prova cruzada específica desta slice).

Chaves de teste efêmeras, apenas em diretório temporário.

### 12.2 Release control

Cobrir a mesma matriz de perfis já aceita em S16 §11.2, agora também com
`mode=deployer`: positivos canônicos; `runtime` rejeita HTTP; `development`
rejeita DB/GitHub/issuer/JWKS/CORS não permitidos; `test` rejeita transporte
não loopback; `jwt_audience` fora do par fechado falha; JWKS diferente de
`issuer + "/jwks"` falha em development/test; **vínculo cruzado mode↔audience
falha nas duas direções** (deployer com audience do publisher, publisher com
audience do deployer).

### 12.3 Validador estrutural

`validate_deployer_identity_bridge.py` deve falhar fechado e verificar ao
menos: duas rotas exatas sob `/deployer`; matcher público e matcher SYSTEM;
`@PreAuthorize`; algoritmo, audience, scope e TTL fixos do deployer; opt-in
default false; PKCS#8/RSA 3072; claims e resposta exatas; nenhuma
dependência Maven nova; nenhuma alteração no pacote `identity` do publisher;
literal `jwt_audience` do `release_control` contendo exatamente os dois
valores fechados; documentação canônica.

A suíte mutante deve alterar/remover cada condição em cópia temporária e
provar falha causal — busca textual isolada não é suficiente.

## 13. Documentação obrigatória

`IDENTIDADE_DEPLOYER.md` deve conter:

- por que a ponte do deployer não reaproveita a ponte do publisher (chave,
  audience, instância);
- diagrama textual: ERP token (instância de produção) -> exchange -> token
  deployer -> release_control deployer;
- rotas e autorização;
- propriedades e defaults;
- claims, TTL e scopes (deixando explícito que `deployment:rollback` nunca é
  emitido);
- matriz `runtime/development/test` aplicada ao deployer;
- geração segura da chave fora do repo;
- exemplo de request sem token real;
- erros esperados;
- limites da slice (sem UI, sem VPS real) e itens futuros (S24 consome este
  token; S31 provisiona a chave real).

Atualizar:

- README do release control com link e estado real;
- `RUNTIME_DEPLOYER.md` com a seção de identidade;
- contrato de segurança marcando a ponte deployer como implementada.

Não registrar valores reais, tokens, modulus, paths do host ou fingerprints
operacionais.

## 14. Matriz obrigatória

Executar no CWD indicado e registrar comando, exit, resultado e interpretação:

```bash
cd backend
mvn -B verify
```

```bash
cd release_control
uv sync --frozen --group dev
uv run ruff check .
uv run mypy --strict src tests
uv run pytest --cov=emporio_release_control --cov-branch --cov-fail-under=90
```

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployer_identity_bridge.py
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools/deploy/tests/test_deployer_identity_bridge_contract.py -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployer_runtime.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_publisher_runtime.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate
git diff --check
git rev-parse --verify HEAD
git tag --list
git reflog show --all
find .github/workflows -maxdepth 1 -type f -printf '%f\n' | sort
```

Rodar também `validate_publisher_runtime.py` explicitamente: esta slice não
deve fazer o publisher regredir, e o validador já verifica isolamento de
router por leitura de `api.py`.

## 15. Higiene e estado protegido

Ao final:

- índice Git real vazio; HEAD inexistente; tags vazias; reflog inexistente;
- exatamente os quatro workflows já aceitos em S22 (nenhum novo);
- nenhum `.venv`, cache Python, coverage ou chave de teste residual;
- nenhum container/rede/volume criado por esta slice;
- nenhum PEM/DER/JKS/P12/PFX novo no workspace ao final;
- nenhum commit, tag, push ou acesso externo;
- nenhuma S24.

Não apagar ou alterar artefato preexistente alheio. Limpar somente recurso
criado pela própria execução e registrar alvo exato.

## 16. Relatório obrigatório

Conter:

- resumo e estado;
- arquivos criados/alterados;
- causa de isolamento publisher/deployer (por que audiences e chaves são
  distintas);
- rotas, autorização, claims e propriedades do deployer;
- validação do material criptográfico;
- matriz dos três perfis aplicada ao modo deployer;
- prova cruzada de rejeição mútua entre tokens publisher e deployer;
- lista nominal dos testes causais;
- comandos, exits, resultados e interpretação;
- falhas intermediárias e correções;
- evidência de nenhuma chave/token real;
- estado Git, workflows, caches e resíduos;
- divergências e itens não determinados.

Para cada validação persistir: CWD, comando exato, exit code, resultado,
interpretação, artefatos/resíduos.

Estado final obrigatório:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```

Não declarar `ACCEPTED` e não criar S24.

## 17. Critérios de aceite do orquestrador

A S23 só será aceita se:

- a ponte deployer for opt-in e ausente quando desabilitada;
- somente `ROLE_SYSTEM` obtiver token do deployer;
- JWKS e JWT do deployer coincidirem byte/semanticamente com o contrato;
- audience, scope, TTL e algoritmo não forem controláveis pelo cliente;
- um token do publisher for comprovadamente rejeitado pelo verificador
  configurado para o deployer, e vice-versa;
- nenhuma chave privada sair do backend;
- token ERP HS512 continuar separado;
- o pacote `identity` do publisher (S16) permanecer byte a byte inalterado;
- `deployment:rollback` nunca aparecer em capabilities, scope emitido ou
  documentação como disponível;
- os três perfis do `release_control` permanecerem fail-closed para os dois
  modos;
- testes causais, cobertura, lint, typing e validadores passarem, incluindo
  `validate_deployer_runtime.py` e `validate_publisher_runtime.py` sem
  regressão;
- documentação ensinar o ciclo local sem alegar UI pronta;
- fronteira, segredos e estado Git forem preservados.

## 18. Condições de bloqueio

Pare e documente, sem improvisar, se:

- for necessário reabrir o pacote `identity` do publisher para viabilizar o
  deployer;
- `UserPrincipal` autenticado não expuser ID e `ROLE_SYSTEM` na instância de
  produção da mesma forma que na instância de desenvolvimento;
- Spring Security não permitir proteger as quatro rotas (duas publisher, duas
  deployer) de forma distinta e simultânea;
- `JwtVerifier` do `release_control` exigir mudança de claim além da
  audience já prevista;
- algum teste tentar acessar rede não loopback;
- for necessário alterar frontend, OpenAPI deployer, workflow, Docker ou
  produção real.
