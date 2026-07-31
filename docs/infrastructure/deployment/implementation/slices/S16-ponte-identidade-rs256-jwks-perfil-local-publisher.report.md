# S16 — Ponte de identidade RS256/JWKS e perfil local seguro do publisher

## 1. Estado

`IN_PROGRESS — aguardando revisão do orquestrador`

Execução realizada em 29/07/2026, no diretório obrigatório
`/home/gregorio/git/baronesa/emporio`.

A ponte opt-in foi implementada no backend ERP, o publisher passou a possuir
o perfil `development` estritamente loopback e a matriz obrigatória foi
executada integralmente. Esta execução não declara aceite da slice.

## 2. Causa e resultado

O token de sessão do ERP continua sendo HS512 e não possui o contrato de
identidade exigido pelo publisher (`iss`, `aud`, `scope` e verificação por
JWKS). Reutilizá-lo diretamente quebraria a separação de credenciais definida
nas S06 e S15.

A implementação adiciona uma troca restrita:

```text
token ERP HS512 + ROLE_SYSTEM
  -> POST /api/release-control/identity/token
  -> token RS256 curto e específico
  -> publisher valida issuer, audience, scopes e JWKS
```

O token ERP, seu filtro HS512 e as rotas comerciais não foram modificados.

## 3. Arquivos alterados

### 3.1 Backend ERP

Criados:

- `backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/ReleaseControlIdentityConfiguration.java`;
- `backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/ReleaseControlIdentityController.java`;
- `backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/ReleaseControlIdentityKeyMaterial.java`;
- `backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/ReleaseControlIdentityService.java`;
- `backend/src/test/java/com/baronesa/emporio/releasecontrol/identity/ReleaseControlIdentityContractTest.java`;
- `backend/src/test/java/com/baronesa/emporio/releasecontrol/identity/ReleaseControlIdentityHttpSecurityTest.java`.

Alterados:

- `backend/src/main/java/com/baronesa/emporio/config/SecurityConfig.java`;
- `backend/src/main/resources/application.properties`;
- `backend/src/main/resources/application-dev.properties`;
- `backend/src/main/resources/application-prod.properties`;
- `backend/src/test/resources/application-test.properties`;
- `backend/.env.example`.

### 3.2 Publisher

Alterados:

- `release_control/src/emporio_release_control/config.py`;
- `release_control/.env.example`;
- `release_control/tests/conftest.py`;
- `release_control/tests/test_api.py`;
- `release_control/tests/test_config_security.py`.

`conftest.py`, `test_api.py` e `test_config_security.py` são os arquivos de
teste existentes que exercem diretamente `Settings`, o profile e o CORS. As
mudanças alinham fixtures e assertions às origens loopback do profile `test`.

### 3.3 Validação e documentação

Criados:

- `tools/releases/validate_publisher_identity_bridge.py`;
- `tools/releases/tests/test_publisher_identity_bridge_contract.py`;
- `docs/infrastructure/deployment/release-control/IDENTIDADE_PUBLISHER.md`;
- este relatório.

Alterados:

- `docs/infrastructure/deployment/release-control/README.md`;
- `docs/infrastructure/deployment/release-control/RUNTIME_PUBLISHER.md`;
- `docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md`;
- `docs/development/ONBOARDING_MINIMO.md`;
- `docs/development/README.md`.

A task S16, o tracker, frontend, workflows, Docker, Compose, migrations,
OpenAPI e schemas S06–S15 não foram alterados.

## 4. Contrato HTTP implementado

Quando `app.release-control.identity.enabled=true`, existem somente:

- `GET /api/release-control/identity/jwks`: público, `200
  application/json`, `Cache-Control: no-store`, exatamente uma chave pública
  com os campos `kty`, `use`, `alg`, `kid`, `n` e `e`;
- `POST /api/release-control/identity/token`: protegido por
  `hasRole("SYSTEM")` na cadeia HTTP e por
  `@PreAuthorize("hasRole('SYSTEM')")`.

O POST rejeita qualquer query string ou body não vazio com `400` antes de
emitir token. O subject é derivado exclusivamente de um `UserPrincipal` com
ID numérico positivo. ROLE_ADMIN isolado recebe `403`; anônimo recebe `401`.
Headers adicionais não controlam audience, scope, TTL ou algoritmo.

Com a ponte desabilitada, as classes operacionais não são instanciadas, as
rotas não existem e issuer/path/kid vazios não impedem o startup.

## 5. JWT, autorização e isolamento

Valores fixos no código:

```text
alg = RS256
aud = emporio-release-control
scope = release:read release:publish
ttl = 300 segundos
```

O cabeçalho contém somente o contrato `alg`, `kid` e `typ`. Os claims
emitidos são:

```text
iss = issuer configurado
aud = emporio-release-control
sub = erp-user:<id positivo>
scope = release:read release:publish
iat = nbf
exp = iat + 300
jti = UUID v4 novo
```

Não são incluídos email, nome, roles, grupos, scopes de deployer, dados de
infraestrutura ou refresh token. A ponte não muda o segredo ou o filtro HS512
do ERP e o novo token RS256 não autentica rotas comerciais do ERP.

## 6. Material criptográfico

Ao habilitar a ponte, o startup valida fail-closed:

- issuer HTTP(S) absoluto, sem userinfo/query/fragment e sem slash final;
- HTTP restrito a `localhost` ou `127.0.0.1`;
- `kid` conforme `^[A-Za-z0-9][A-Za-z0-9._-]{15,63}$`;
- path absoluto, não symlink, regular e não vazio;
- arquivo com no máximo 16 KiB;
- PEM PKCS#8 não criptografado com `BEGIN PRIVATE KEY`;
- chave RSA CRT com módulo mínimo de 3072 bits.

PKCS#1, PEM criptografado, não RSA, RSA 2048, path relativo, symlink,
arquivo ausente e arquivo grande são rejeitados. A chave é lida uma vez no
startup; a chave pública derivada da `RSAPrivateCrtKey` alimenta o JWKS.
`n` e `e` são serializados unsigned em base64url sem padding.

Nenhuma chave operacional foi criada. Os testes Java geraram chaves
exclusivamente em diretórios temporários gerenciados por JUnit e não
persistiram chave, token, módulo ou fingerprint no workspace ou neste
relatório.

## 7. Perfis do publisher

| Profile | Banco | GitHub API | issuer/JWKS | CORS |
| --- | --- | --- | --- | --- |
| `runtime` | `sslmode=require` | exatamente `https://api.github.com` | somente HTTPS | somente HTTPS |
| `development` | `disable`, host `localhost` ou `127.0.0.1` | exatamente `https://api.github.com` | HTTP loopback e JWKS igual a `issuer + /jwks` | HTTP loopback, porta explícita, sem path/query/fragment |
| `test` | sem SSL | somente HTTP loopback | somente HTTP loopback e JWKS igual a `issuer + /jwks` | somente HTTP loopback |

Os únicos valores aceitos para `Settings.profile` são `runtime`,
`development` e `test`. Wildcard CORS, audiência diferente, transportes não
permitidos e relações JWKS/issuer divergentes falham fechado.

## 8. Testes causais nominais

### 8.1 Backend

`ReleaseControlIdentityContractTest`:

- `disabledConfigurationCreatesNoOperationalBridgeBeans`;
- `acceptsHttpsAndLoopbackHttpIssuerWithValidRsa3072`;
- `rejectsIssuerAndKeyIdMutants`;
- `rejectsPathAndPemMutants`;
- `rejectsNonRsaAndRsa2048`;
- `jwksAndTokenHaveExactPublicContract`;
- `successiveTokensUseDistinctCanonicalUuidV4`;
- `bodyQueryOrNonSystemPrincipalNeverIssuesToken`.

`ReleaseControlIdentityHttpSecurityTest`:

- `anonymousIs401AndAdminIs403`;
- `systemReceivesExactResponseAndHeadersCannotChangeScope`;
- `bodyAndQueryAre400BeforeTokenEmission`.

Esses testes cobrem também `exp - iat == 300`, verificação da assinatura pela
chave pública do JWKS, ausência de campos privados, UUID v4 distintos e
ausência de emissão nas falhas.

### 8.2 Publisher

Casos adicionados ou ampliados em `test_config_security.py`:

- `test_runtime_profile_fixes_github_and_ssl`;
- `test_development_profile_is_strictly_loopback`;
- `test_test_profile_rejects_every_non_loopback_transport`;
- `test_runtime_rejects_http_identity_and_wrong_audience`;
- `test_jwt_valid_and_scope_exclusively_from_scope_claim`;
- `test_jwt_rejects_identity_algorithm_and_claim_mutants`.

Eles comprovam a matriz dos três profiles e a rejeição causal de HS512,
`kid`, issuer, audience, expiração e scope incompatíveis.

### 8.3 Validador e mutantes

`test_publisher_identity_bridge_contract.py` executou:

- `test_real_bridge_is_valid`;
- `test_exact_route_mutant_fails`;
- `test_security_matcher_mutant_fails`;
- `test_method_authorization_mutant_fails`;
- `test_fixed_authority_mutants_fail`;
- `test_opt_in_mutant_fails`;
- `test_key_contract_mutants_fail`;
- `test_claim_contract_mutant_fails`;
- `test_exchange_contract_mutant_fails`;
- `test_jwks_contract_mutant_fails`;
- `test_profile_contract_mutants_fail`;
- `test_maven_dependency_mutant_fails`;
- `test_documentation_mutant_fails`.

Cada caso altera uma cópia temporária e exige a rejeição causal do validador.

## 9. Matriz obrigatória — evidências

### 9.1 Backend ERP

```text
CWD: /home/gregorio/git/baronesa/emporio/backend
comando exato: mvn -B verify
exit code: 0
resultado: BUILD SUCCESS; 51 testes, 0 failures, 0 errors, 0 skipped
interpretação: compilação, testes existentes e 11 provas da ponte aprovados
artefatos/resíduos: backend/target removido ao final
```

### 9.2 Publisher

```text
CWD: /home/gregorio/git/baronesa/emporio/release_control
comando exato: uv lock --check
exit code: 0
resultado: 57 packages resolvidos; lock consistente
interpretação: lock preservado e nenhuma alteração de dependência realizada
artefatos/resíduos: .venv criado/reutilizado pelo uv e removido ao final
```

```text
CWD: /home/gregorio/git/baronesa/emporio/release_control
comando exato: uv run ruff check .
exit code: 0
resultado: All checks passed
interpretação: lint aprovado
artefatos/resíduos: .ruff_cache removido ao final
```

```text
CWD: /home/gregorio/git/baronesa/emporio/release_control
comando exato: uv run mypy --strict src
exit code: 0
resultado: Success; 14 source files
interpretação: tipagem estrita aprovada
artefatos/resíduos: .mypy_cache removido ao final
```

```text
CWD: /home/gregorio/git/baronesa/emporio/release_control
comando exato: uv run pytest -q
exit code: 0
resultado: 124 passed; 5 warnings de depreciação/fixture mutante
interpretação: suíte completa do publisher aprovada
artefatos/resíduos: caches pytest/Python removidos ao final
```

```text
CWD: /home/gregorio/git/baronesa/emporio/release_control
comando exato: uv run pytest --cov=emporio_release_control --cov-branch --cov-fail-under=90
exit code: 0
resultado: 124 passed; cobertura total 91,12%
interpretação: cobertura de branches acima do mínimo de 90%
artefatos/resíduos: .coverage e caches removidos ao final
```

### 9.3 Contratos locais

```text
CWD: /home/gregorio/git/baronesa/emporio
comando exato: PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_publisher_identity_bridge.py
exit code: 0
resultado: publisher-identity-bridge:valid
interpretação: contrato estrutural fail-closed aprovado
artefatos/resíduos: nenhum
```

```text
CWD: /home/gregorio/git/baronesa/emporio
comando exato: PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools/releases/tests/test_publisher_identity_bridge_contract.py -v
exit code: 0
resultado: 13 testes, OK
interpretação: ponte real válida e todos os grupos mutantes rejeitados
artefatos/resíduos: somente cópias temporárias, removidas pela suíte
```

```text
CWD: /home/gregorio/git/baronesa/emporio
comando exato: PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'
exit code: 0
resultado: 258 testes, OK
interpretação: suíte completa de contratos das releases preservada
artefatos/resíduos: nenhum bytecode por PYTHONDONTWRITEBYTECODE
```

```text
CWD: /home/gregorio/git/baronesa/emporio
comando exato: PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate
exit code: 0
resultado: release-control-contract:valid
interpretação: contratos S06 continuam válidos
artefatos/resíduos: nenhum
```

### 9.4 Estado Git

```text
CWD: /home/gregorio/git/baronesa/emporio
comando exato: git diff --check
exit code: 0
resultado: sem saída
interpretação: nenhuma violação de whitespace
artefatos/resíduos: nenhum
```

```text
CWD: /home/gregorio/git/baronesa/emporio
comando exato: git diff --cached --name-only
exit code: 0
resultado: sem saída
interpretação: índice Git real vazio
artefatos/resíduos: nenhum
```

```text
CWD: /home/gregorio/git/baronesa/emporio
comando exato: git rev-parse --verify HEAD
exit code: 128
resultado: fatal: Needed a single revision
interpretação: HEAD inexistente, conforme estado protegido esperado
artefatos/resíduos: nenhum
```

```text
CWD: /home/gregorio/git/baronesa/emporio
comando exato: git tag --list
exit code: 0
resultado: sem saída
interpretação: nenhuma tag
artefatos/resíduos: nenhum
```

```text
CWD: /home/gregorio/git/baronesa/emporio
comando exato: git reflog
exit code: 128
resultado: branch main ainda não possui commits
interpretação: reflog inexistente, conforme estado protegido esperado
artefatos/resíduos: nenhum
```

## 10. Falhas intermediárias e correções

### 10.1 Teste MVC focado

As primeiras execuções focadas falharam por lacunas do harness: ausência de
`HandlerMappingIntrospector`, propriedades OAuth fictícias, tokens congelados
já expirados e ausência do conversor Jackson. O teste foi corrigido para
importar somente as autoconfigurações necessárias, usar instante corrente e
propriedades exclusivamente de teste. O contrato de produção não foi
relaxado.

### 10.2 Primeiro `mvn verify`

```text
CWD: /home/gregorio/git/baronesa/emporio/backend
comando exato: mvn -B verify
exit code: 1
resultado: 51 testes; 27 errors de startup
causa inicial: INTEGRATION_SYSTEM_TOKEN_SECRET obrigatório não estava definido no profile test
correção: segredo efêmero formado por dois random.uuid somente em application-test.properties
```

### 10.3 Segundo `mvn verify`

```text
CWD: /home/gregorio/git/baronesa/emporio/backend
comando exato: mvn -B verify
exit code: 1
resultado: 51 testes; 27 errors de startup
causa: app.cors.allowed-origins obrigatório não estava definido no profile test
correção: origem HTTP loopback explícita somente em application-test.properties
```

A repetição final passou com 51/51. Esses dois valores de teste são
efêmeros/não operacionais e não alteram os profiles base, dev ou prod.

### 10.4 Primeiro pytest completo

O primeiro `uv run pytest -q` após introduzir o profile falhou porque uma
assertion preexistente esperava uma origem HTTPS fixa, embora o profile
`test` agora exija loopback HTTP. A assertion passou a usar a origem validada
da própria fixture `Settings`. A repetição integral passou com 124/124.

## 11. Documentação e operação local

`IDENTIDADE_PUBLISHER.md` documenta a incompatibilidade, as duas rotas,
claims, propriedades/defaults, profiles, sequência local, erros e limites.
A geração de RSA 3072 é prescrita fora do repositório, com modo `0600`; o
publisher recebe somente a URL JWKS.

O onboarding registra pré-requisitos e sequência manual sem afirmar que há
UI. Rotação operacional, provisionamento de chave, implantação e futura
interface permanecem explicitamente fora desta slice.

## 12. Higiene e estado protegido final

- índice Git real vazio;
- HEAD inexistente;
- tags vazias e reflog inexistente;
- exatamente três workflows ativos:
  `ci.yml`, `publish-candidate.yml` e `publish-release.yml`;
- `backend/target` removido;
- `.venv`, `.coverage`, `.pytest_cache`, `.mypy_cache`, `.ruff_cache`,
  `__pycache__` e `.pyc` criados na execução removidos;
- nenhuma chave PEM/DER/JKS/P12/PFX foi criada;
- o certificado PFX preexistente sob uploads foi somente classificado pelo
  nome durante a verificação de higiene; não foi aberto, movido, alterado ou
  removido;
- o pytest usou PostgreSQL efêmero via testcontainers e realizou sua limpeza;
  não restou container, rede ou volume desta execução;
- quatro containers testcontainers já parados, criados entre 06/06/2026 e
  15/07/2026, foram identificados como preexistentes e não foram alterados;
- nenhum acesso a GitHub, GHCR, VPS, DNS ou produção;
- nenhum `git add`, commit, tag, push, publicação ou deploy;
- nenhuma S17 criada;
- task S16 e tracker preservados.

Comandos de limpeza, todos com exit code `0`:

```text
CWD: /home/gregorio/git/baronesa/emporio
comando exato: find backend/target -depth -delete
resultado: target Maven criado pela execução removido

CWD: /home/gregorio/git/baronesa/emporio
comando exato: find release_control/.venv release_control/.mypy_cache release_control/.ruff_cache release_control/.pytest_cache release_control/.coverage release_control/src/emporio_release_control/__pycache__ release_control/migrations/__pycache__ release_control/tests/__pycache__ -depth -delete
resultado: ambiente e caches conhecidos removidos

CWD: /home/gregorio/git/baronesa/emporio
comando exato: find release_control/migrations/versions/__pycache__ -depth -delete
resultado: último bytecode gerado pela suíte removido
```

## 13. Divergências e itens não determinados

Não houve decisão arquitetural adicional nem divergência conhecida em
relação ao contrato. Provisionamento, rotação da chave operacional,
integração remota e UI continuam para slices futuras.

## 14. Estado final

`IN_PROGRESS — aguardando revisão do orquestrador`

---

## 15. Revisão terminal do orquestrador

> **Resultado:** `ACCEPTED`  
> **Data:** `2026-07-29`

O orquestrador revisou o relatório e as superfícies causais Java/Python sem
repetir as suítes do executor.

A implementação coincide com o contrato S16:

- os componentes operacionais e as duas rotas são opt-in;
- JWKS contém somente a chave pública e usa `Cache-Control: no-store`;
- o exchange possui proteção `ROLE_SYSTEM` na cadeia HTTP e no método;
- body/query são rejeitados antes da emissão e headers não controlam
  autoridade;
- algoritmo, audience, scopes e TTL permanecem constantes no servidor;
- claims, subject e `jti` são derivados e validados conforme o contrato;
- PKCS#8, RSA CRT, tamanho mínimo e fronteira do arquivo falham fechado;
- `development` e `test` permanecem loopback, enquanto `runtime` preserva
  HTTPS, SSL do banco e GitHub API canônica;
- o JWT ERP HS512 não foi alterado nem passou a ser aceito pelo publisher.

A evidência persistida registra 51 testes Maven, 124 testes do publisher com
91,12% de cobertura branch, 258 testes dos contratos e 13 mutantes
específicos. Índice, HEAD, tags, reflog, workflows, chaves e resíduos
permaneceram no estado protegido.

Não foi encontrada divergência residual nem escolha arquitetural transferida
ao executor.

Decisão:

```text
S16 ACCEPTED — 29/07/2026
S17 autorizada
```
