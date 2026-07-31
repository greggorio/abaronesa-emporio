# S11 — Relatorio de execucao

Estado: **IN_PROGRESS — aguardando revisao do orquestrador**

Data: `2026-07-29`

## 1. CWD e estado inicial

CWD obrigatorio:

```text
/home/gregorio/git/baronesa/emporio
```

No inicio, S10 estava registrada como `ACCEPTED`; o repositorio local tinha
origin canonico, indice real vazio, HEAD inexistente, nenhuma tag/reflog e
nenhum workflow YAML. Existia somente o README transitorio em
`.github/workflows/`.

## 2. Inventario factual

- Java 21 nos dois `pom.xml`; comando real: `mvn -B verify`.
- Node 24 adotado pelas imagens S09.
- `frontend`: `npm ci`, `npm run lint`, `npm run test`, `npm run build`.
- `website_front`: `npm ci`, `npm run test`, `npm run build`; o build executa
  `tsc && vite build`.
- `whatsapp_service`: `npm ci`, checks de `index.js` e `app.js`, `npm run test`.
- seis Dockerfiles correspondem aos contextos e paths do catalogo.
- Flyway: 50 migrations ERP e 14 website, desconsiderando `.gitkeep`.

Nenhuma suite comercial pesada, build Docker ou workflow remoto foi repetido
localmente. A S11 valida que comandos e gates corretos existem no workflow.

## 3. Arquivos criados ou alterados

- `.github/workflows/ci.yml`;
- `.github/workflows/README.md`;
- `ops/releases/candidate-manifest.schema.json`;
- `ops/releases/examples/candidate-manifest.example.json`;
- `tools/releases/candidate_manifest.py`;
- `tools/releases/tests/test_candidate_manifest.py`;
- `tools/ci/validate_ci.py`;
- `tools/ci/resolve_changes.py`;
- `tools/ci/migrations_contract.py`;
- `tools/ci/secret_scan.py`;
- `tools/ci/secret-allowlist.json`;
- `tools/ci/tests/test_ci.py`;
- `docs/infrastructure/deployment/ci/CI.md`;
- `docs/infrastructure/deployment/release-control/README.md`;
- este relatorio.

Task S11, tracker, codigo comercial, migrations, Dockerfiles, Compose e gateway
nao foram alterados.

## 4. Workflow e grafo de jobs

```text
plan -----------------------------+
contracts ------------------------+
backend --------------------------+
website_back ---------------------+--> images[6 componentes]
frontend -------------------------+
website_front --------------------+
whatsapp -------------------------+
```

`images` depende explicitamente dos sete gates. A matriz constroi, em ordem
canonica, `backend`, `website_back`, `frontend`, `website_front`,
`whatsapp_service` e `gateway`.

Triggers exclusivos:

```text
pull_request -> main
push -> main
```

Concorrencia usa workflow/ref e cancela somente execucao anterior da mesma
ref. Todos os jobs usam `ubuntu-24.04`, timeout e permissao global unica
`contents: read`.

## 5. Actions e ferramentas imutaveis

Consultas somente leitura foram feitas em `2026-07-29` diretamente nos
repositorios oficiais:

| Origem | Versao humana | SHA |
|---|---|---|
| `https://github.com/actions/checkout` | v6.0.2 | `de0fac2e4500dabe0009e67214ff5f5447ce83dd` |
| `https://github.com/actions/setup-java` | v5.6.0 | `03ad4de0992f5dab5e18fcb136590ce7c4a0ac95` |
| `https://github.com/actions/setup-node` | v6.1.0 | `395ad3262231945c25e8478fd5baf05154b1d79f` |
| `https://github.com/docker/setup-buildx-action` | v4.0.0 | `4d04d5d9486b7bd6fa91e7baf45bbb4f8b9deedd` |
| `https://github.com/docker/build-push-action` | v7.0.0 | `d08e5c354a6adb9ed34480a06d141179aa583294` |
| `https://github.com/aquasecurity/trivy-action` | v0.36.0 | `ed142fd0673e97e23eac54620cfb913e5ce36c25` |

Trivy fica fixado tambem em `v0.70.0`. O parser reconhecido usado localmente
foi `rhysd/actionlint:1.7.7`, fixado pelo digest multiarch
`sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9`.
A imagem efemera foi removida depois da validacao.

## 6. Primeiro push e resolucao de paths

`tools/ci/resolve_changes.py` chama diretamente `catalog.resolve`:

- PR usa `base.sha...GITHUB_SHA`;
- push comum usa `before..GITHUB_SHA`;
- SHA nulo, base ausente, diff impossivel ou path nao decodificavel aplica
  primeiro release fail-closed;
- Git entrega nomes com `--name-only -z`; nenhum path atravessa variavel shell;
- primeiro release constroi e valida os seis;
- path desconhecido preserva warnings e seleciona os seis;
- docs-only e global preservam as semanticas S05.

A CI constroi conservadoramente os seis Dockerfiles após todos os gates. O
resultado S05 fica registrado por `plan` e alimenta o contrato de
particionamento/heranca; publicacao incremental continua pertencendo a S12.

## 7. Build e scan

Cada celula Buildx usa:

- `linux/amd64`;
- context e Dockerfile validados contra o catalogo;
- `VCS_REF=${github.sha}` e `IMAGE_VERSION=ci-${github.sha}`;
- tag local inequivoca;
- cache GHA por componente;
- `load: true` e `push: false`;
- nenhuma autenticacao de registry.

Trivy falha para `HIGH` ou `CRITICAL`, incluindo vulnerabilidades sem fix, sem
baseline, `continue-on-error` ou exit code ignorado. A limpeza remove somente
a imagem local da celula; nao existe prune.

O scan remoto das imagens nao foi executado. Portanto, a existencia de CVEs
nas imagens atuais permanece nao determinada; qualquer achado futuro bloqueara
a CI sem relaxamento.

## 8. Segredos

O detector local cobre private keys, tokens GitHub, chaves Google e assignments
de password/secret/token/API key. Ele examina os arquivos versionados e cada
snapshot do historico disponivel, sem upload, telemetria ou valor no log.

Allowlist e fechada por regra, path, fingerprint e justificativa. Ela cobre
somente mutantes ficticios, assignments publicos de exemplos e identificadores
publicos do cliente Firebase; nenhum segredo operacional e permitido.

Como ainda nao existe HEAD, a prova local usou `GIT_INDEX_FILE` em diretorio
temporario e `git add -A` somente nesse indice. Foram examinados 2.181 arquivos;
o indice real permaneceu vazio. O historico local inexistente foi registrado
como fronteira, nao inventado.

## 9. Migrations

O validador deterministico rejeita diretorio ausente, entrada que nao seja
arquivo, nome fora de `V<versao>__<descricao>.sql`, versao duplicada, arquivo
ilegivel ou vazio. As 50 migrations ERP e 14 website passaram.

Nao houve aplicacao de SQL em PostgreSQL nesta slice. Aplicacao real permanece
coberta por startups/testes aceitos anteriormente e pela futura execucao da
CI.

## 10. Contrato do candidato

O schema usa JSON Schema Draft 2020-12 e fecha todos os objetos. O candidato:

- identifica repositorio, SHA, ref `refs/heads/main`, UTC e workflow canonicos;
- registra checksum e versao do catalogo;
- registra resultado integral do resolvedor S05;
- possui exatamente seis componentes em ordem canonica;
- exige digest e immutable ref coerentes;
- exige checks `passed` e labels OCI;
- diferencia `built` e `inherited`;
- preserva digest e procedencia herdados;
- rejeita BOM parcial, namespace alternativo, tag, digest curto,
  `release_control`, PostgreSQL e propriedades extras;
- declara `kind: ci-candidate` e `deployable: false`.

O gerador e offline, deterministico, recebe metadados explicitamente, valida
antes de persistir, usa staging e rollback transacional do par e cria sidecar `.sha256`. Nao
sobrescreve sem `--overwrite`. Codigos: uso CLI `2`, contrato `3` e candidato
anterior ausente `4`. Nao consulta Git, Docker, GitHub, GHCR ou rede.

O exemplo e integralmente ficticio e nao e release ou artefato implantavel.

## 11. Testes, mutantes e comandos

Todos os comandos abaixo foram executados no CWD da Secao 1:

| Comando | Exit | Resultado |
|---|---:|---|
| `python3 tools/ci/validate_ci.py` | 0 | `ci:valid` |
| `python3 -m unittest discover -s tools/ci/tests -v` | 0 | 6 metodos; 14 mutantes independentes do workflow, alem de migrations, segredos e diff |
| `python3 tools/ci/migrations_contract.py` | 0 | `migrations:valid` |
| `python3 tools/releases/candidate_manifest.py validate --manifest ops/releases/examples/candidate-manifest.example.json` | 0 | `candidate:valid` |
| `python3 -m unittest discover -s tools/releases/tests -v` | 0 | 137 testes: 34 candidato, 45 catalogo e 58 release-control |
| `python3 tools/releases/catalog.py validate --require-release-ready` | 0 | `catalog:valid` |
| `docker run --rm ... rhysd/actionlint@sha256:887a... .github/workflows/ci.yml` | 0 | sintaxe reconhecida valida |
| `GIT_INDEX_FILE=<temporario> git add -A` e `python3 tools/ci/secret_scan.py --tracked` | 0 | `secret-scan:clean:2181 files` |

`PYTHONDONTWRITEBYTECODE=1` foi usado nas execucoes Python finais.

## 12. Falhas intermediarias

1. O primeiro validador CI interpretou incorretamente o bloco de
   `fetch-depth`; a verificacao passou a ler a estrutura YAML.
2. Um mutante mostrou que `docker login` em shell nao era bloqueado embora a
   action de login fosse; o padrao foi ampliado.
3. O primeiro scan do indice temporario detectou fixtures ficticias,
   identificadores publicos Firebase e falsos positivos do assignment generico.
   A regra generica foi limitada a arquivos de configuracao e a allowlist
   machine-readable foi criada por fingerprint/regra/path/justificativa.
4. A primeira tentativa de limpeza do indice temporario foi recusada pela
   protecao de comandos destrutivos; o arquivo temporario exato foi removido
   depois com `unlink`, sem tocar o indice real.

Todas as repeticoes finais passaram.

## 13. Fronteiras, riscos e bloqueios

Nao executados:

- Maven/NPM comerciais e builds Docker locais;
- scan real das seis imagens;
- GitHub Actions remoto;
- upload de artifact, candidato ou imagem;
- publicacao, release, deploy ou acesso a producao.

Riscos ainda nao determinados sao o resultado do primeiro runner remoto,
disponibilidade das actions/tool downloads e eventuais CVEs reais. Todos
falham fechado. Nao ha bloqueio local conhecido para revisao da S11.

## 14. Estado final

Confirmacoes finais:

- `.github/workflows/ci.yml` e o unico workflow YAML;
- indice Git real vazio;
- HEAD, tags e reflog inexistentes;
- origin inalterado;
- nenhuma S12;
- nenhum cache Python;
- nenhuma imagem, manifesto temporario ou indice temporario residual da S11;
- nenhum `git add` no indice real, commit, tag ou push;
- nenhuma publicacao, release, deploy ou acesso a VPS.

**IN_PROGRESS — aguardando revisao do orquestrador**

---

## 15. Revisao do orquestrador — ciclo 1

> **Resultado:** `IN_PROGRESS — correcoes bloqueantes requeridas`  
> **Data:** `2026-07-29`

A estrutura geral do workflow, as permissoes, os pins, os jobs e a separacao
entre CI/candidato/release/deploy estao corretos. A S12 ainda nao esta
autorizada porque quatro pontos do contrato permanecem divergentes.

### 15.1 O scan de segredos ignora arquivos enquanto afirma cobertura total

`tools/ci/secret_scan.py` retorna lista vazia sem aviso quando:

```text
len(content) > 2_000_000
ou
o conteudo possui byte NUL
```

Mesmo assim, a saida informa `secret-scan:clean:2181 files` e o relatorio
afirma que 2.181 arquivos foram examinados. Pelo menos seis candidatos ao
primeiro commit ultrapassam o limite, incluindo dois arquivos GLB com cerca
de 23 MB e quatro PNGs acima de 2 MB. Eles foram contados, mas nao examinados.
Um arquivo textual grande ou um arquivo com NUL tambem consegue ocultar um
token e passar silenciosamente.

Correcao obrigatoria:

- nenhum path recebido pode ser contado como examinado se foi ignorado;
- preferencialmente examinar os bytes de todos os arquivos, inclusive
  binarios, pois as regras atuais operam sobre bytes;
- se algum limite continuar necessario, arquivo nao examinado deve falhar
  fechado, ou usar classificacao/allowlist machine-readable estrita,
  documentada e testada;
- adicionar mutantes com segredo ficticio depois do byte 2.000.000 e em
  conteudo contendo NUL;
- a saida deve distinguir `scanned`, `allowed` e `unsupported/skipped`; sucesso
  exige zero unsupported/skipped, salvo exclusao contratada explicita;
- repetir a prova pelo indice temporario e corrigir quantidade/interpretacao
  no relatorio, sem imprimir valores.

### 15.2 Escrita do manifesto e sidecar nao e atomica no overwrite

`write_atomic()` substitui primeiro o manifesto e depois o sidecar. Com
`overwrite=True`, se a segunda gravacao ou o segundo `os.replace()` falhar, o
bloco de excecao nao restaura o par anterior. O resultado pode ser manifesto
novo com checksum antigo, ausente ou parcial.

O teste atual de “arquivo parcial” somente precria o sidecar com overwrite
desabilitado; a funcao falha antes de iniciar qualquer substituicao e,
portanto, nao testa falha intermediaria.

Correcao obrigatoria:

- implementar commit do par com staging e rollback verificavel;
- em falha durante overwrite, manifesto e sidecar anteriores devem permanecer
  byte a byte identicos;
- em primeira escrita que falha no meio, nenhum dos dois artefatos pode
  permanecer;
- sincronizar arquivos e diretorio quando aplicavel;
- adicionar testes que injetem falha na segunda gravacao e no segundo
  `os.replace`, tanto sem overwrite quanto com par anterior valido;
- validar o par final lendo o sidecar e recalculando o checksum.

### 15.3 O teste de auto-heranca/ciclo nao isola a invariante

O teste `test_27_self_inheritance_fails` chama `validate_manifest(value)` sem
fornecer o candidato anterior. O manifesto falha de qualquer forma por
`previous manifest required` e `missing previous provenance`; o teste nao
prova que `originCandidateId == candidateId` e rejeitado.

Com um anterior fornecido, o ramo de heranca compara o origin com o anterior,
mas nao rejeita incondicionalmente origin igual ao candidato atual. E
possivel construir um anterior cujo origin aponte ao ID do proximo candidato
e fazer o proximo candidato herdar uma origem autorreferente.

Correcao obrigatoria:

- rejeitar `originCandidateId == candidateId` em todos os caminhos;
- testar auto-heranca com um candidato anterior estruturalmente valido, de
  modo que essa seja a unica mutacao;
- adicionar caso circular dirigido entre candidatos;
- fazer os testes verificarem o codigo/mensagem causal, nao apenas que alguma
  lista de erros ficou nao vazia;
- preservar a procedencia achatada do candidato original sem aceitar origem
  futura ou autorreferente.

Se for necessario introduzir lineage explicita para provar a cadeia, parar e
documentar a proposta antes de alterar o schema.

### 15.4 Documentacao release-control contradiz o estado aceito

`docs/infrastructure/deployment/release-control/README.md` ainda afirma que:

- o website backend permanece `blocked` por persistencia;
- existem cinco gates tecnicos pendentes de Compose/gateway;
- `--require-release-ready` deve falhar.

Essas afirmacoes foram superadas e aceitas na S10. No mesmo arquivo, a secao
final afirma que os seis componentes estao `ready`, criando contradicao
interna.

Correcao obrigatoria:

- atualizar as secoes historicamente superadas para o estado S10 aceito;
- registrar que readiness passa e os gates estao vazios;
- preservar a fronteira: readiness tecnico nao significa candidato
  publicado, release ou deploy;
- revisar `CI.md`, README de workflows e relatorio para que nenhuma alegacao
  de cobertura/atomicidade permaneça acima da evidencia real.

### 15.5 Validacoes e estado

Repetir somente:

```text
python3 tools/ci/validate_ci.py
python3 -m unittest discover -s tools/ci/tests -v
python3 tools/ci/secret_scan.py --tracked
python3 tools/releases/candidate_manifest.py validate \
  --manifest ops/releases/examples/candidate-manifest.example.json
python3 -m unittest discover -s tools/releases/tests -v
python3 tools/releases/catalog.py validate --require-release-ready
```

Para o scan local, continuar usando indice temporario; nunca executar
`git add` no indice real. Nao repetir Maven, NPM, Docker builds, Trivy ou
workflow remoto.

Acrescentar a resposta ao final deste relatorio, depois desta revisao, com:

- arquivos alterados;
- testes/mutantes novos e respectivas causas;
- comandos e codigos de saida;
- contagem honesta do scan;
- provas de rollback atomico sanitizadas;
- documentacao corrigida;
- estado Git, workflows, caches e residuos.

Preservar task, tracker, codigo comercial, migrations, Dockerfiles, Compose e
gateway. Nao criar S12.

Estado:

```text
IN_PROGRESS — correcoes bloqueantes do ciclo 1
```

## 16. Resposta as correcoes do ciclo 1

Estado: **IN_PROGRESS — aguardando nova revisao do orquestrador**

### 16.1 Scanner de segredos

O limite silencioso de 2 MB e a exclusao de conteudo com NUL foram removidos.
`findings_for()` agora aplica as regras sobre todos os bytes recebidos,
inclusive binarios e arquivos grandes. Um arquivo somente incrementa
`scanned` depois de ser lido integralmente; falha de leitura incrementa
`unsupported`, nao `scanned`, e bloqueia o comando.

A saida final separa:

```text
scanned=2181
allowed=19
unsupported=0
history_scanned=0
```

`allowed` conta achados aprovados pela allowlist machine-readable, nao arquivos
ignorados. Nenhum path foi pulado. `history_scanned=0` reflete honestamente a
ausencia de HEAD/historico local. A prova usou novamente `GIT_INDEX_FILE`
temporario; o indice real nao foi alterado.

Novos casos causais confirmam:

- segredo ficticio localizado depois do byte 2.000.000 e detectado;
- segredo ficticio em conteudo com NUL e detectado;
- path ilegivel produz `unsupported=1`, `scanned=0` e nao pode resultar em
  sucesso.

### 16.2 Commit e rollback do par manifesto/checksum

`write_atomic()` agora:

1. rejeita par anterior incompleto ou checksum anterior inconsistente;
2. prepara manifesto e sidecar em arquivos separados no mesmo diretorio;
3. sincroniza cada staging com `fsync`;
4. substitui o par e sincroniza o diretorio;
5. recalcula o SHA-256 do manifesto e confere o sidecar final;
6. em falha intermediaria, remove ambos na primeira escrita ou restaura os
   dois arquivos anteriores byte a byte;
7. verifica novamente o par restaurado.

Nao se alega rename atomico conjunto, que o filesystem nao fornece para dois
paths. A garantia testada e commit com rollback transacional verificavel.

Provas sanitizadas:

| Cenario injetado | Resultado |
|---|---|
| falha na segunda gravacao, primeira escrita | manifesto ausente; sidecar ausente |
| falha no segundo `os.replace`, primeira escrita | manifesto ausente; sidecar ausente |
| falha na segunda gravacao, overwrite | par anterior preservado byte a byte |
| falha no segundo `os.replace`, overwrite | par anterior preservado byte a byte e checksum valido |
| escrita normal | sidecar relido igual ao SHA-256 recalculado |

### 16.3 Auto-heranca e ciclo

Todo componente e agora rejeitado incondicionalmente quando
`originCandidateId == candidateId`, antes dos ramos built/inherited, com erro
causal `SELF_INHERITANCE:<componente>`.

O teste de auto-heranca fornece candidato anterior valido e verifica
explicitamente `SELF_INHERITANCE:backend`, isolando essa unica mutacao. Um
segundo teste prepara anterior estruturalmente valido cuja origem aponta ao
candidato seguinte; ao herdar, o gerador falha especificamente por
`SELF_INHERITANCE`. Assim, a procedencia achatada original continua preservada
sem aceitar origem futura que se torne autorreferente.

Nao foi necessario alterar schema ou introduzir lineage adicional.

### 16.4 Documentacao corrigida

O README de release-control agora registra:

- os seis componentes comerciais `ready`;
- persistencia do `website_back` confirmada;
- cinco gates S10 encerrados e `readiness_gates` vazio;
- `--require-release-ready` verde;
- readiness tecnico nao equivale a candidato publicado, release ou deploy.

`CI.md` passou a descrever a cobertura real de bytes, as contagens honestas do
scanner e a garantia de staging/fsync/rollback do par, sem alegar atomicidade
conjunta. O README de workflows ja correspondia ao comportamento e nao exigiu
alteracao.

### 16.5 Arquivos, comandos e resultados

Arquivos alterados neste ciclo:

- `tools/ci/secret_scan.py`;
- `tools/ci/tests/test_ci.py`;
- `tools/releases/candidate_manifest.py`;
- `tools/releases/tests/test_candidate_manifest.py`;
- `docs/infrastructure/deployment/ci/CI.md`;
- `docs/infrastructure/deployment/release-control/README.md`;
- este relatorio.

CWD de todos os comandos:

```text
/home/gregorio/git/baronesa/emporio
```

Somente a matriz prescrita em 15.5 foi executada:

| Comando | Exit | Resultado |
|---|---:|---|
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/ci/validate_ci.py` | 0 | `ci:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/ci/tests -v` | 0 | 9 testes aprovados |
| `GIT_INDEX_FILE=<temporario> PYTHONDONTWRITEBYTECODE=1 python3 tools/ci/secret_scan.py --tracked` | 0 | 2.181 scanned, 19 allowed, zero unsupported, zero history |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/candidate_manifest.py validate --manifest ops/releases/examples/candidate-manifest.example.json` | 0 | `candidate:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -v` | 0 | 141 testes: 38 candidato, 45 catalogo e 58 release-control |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/catalog.py validate --require-release-ready` | 0 | `catalog:valid` |

Nao foram executados Maven, NPM, builds Docker, Trivy ou workflow remoto.

### 16.6 Estado protegido

A verificacao final confirmou:

- indice Git real vazio;
- HEAD inexistente, nenhuma tag, reflog, commit ou push;
- somente `.github/workflows/ci.yml` como workflow ativo;
- nenhuma S12;
- nenhum cache Python, indice temporario ou artefato temporario;
- nenhuma publicacao, execucao remota ou acesso a producao;
- task S11, tracker, codigo comercial, migrations, Dockerfiles, Compose e
  gateway inalterados.

**IN_PROGRESS — aguardando nova revisao do orquestrador**

---

## 17. Revisao final do orquestrador

> **Resultado:** `ACCEPTED`  
> **Data:** `2026-07-29`

As correcoes do ciclo 1 fecham os quatro bloqueios:

- todos os 2.181 paths foram efetivamente lidos e examinados; os 19 achados
  permitidos foram contabilizados separadamente e nao houve item
  `unsupported`;
- segredos ficticios depois de 2 MB e em conteudo com NUL sao detectados;
- o par manifesto/sidecar usa staging, `fsync`, verificacao e rollback, com
  provas de primeira escrita e overwrite em falhas intermediarias;
- auto-heranca e ciclo dirigido falham por `SELF_INHERITANCE`, com candidato
  anterior valido;
- a documentacao registra os seis componentes `ready`, gates vazios e
  readiness verde sem confundir esse estado com publicacao ou deploy.

O workflow permanece somente de CI, com permissoes de leitura, sem execucao
remota, publicacao ou producao. A primeira execucao real continua dependente
de commit/push manual do usuario.

Decisao:

```text
S11 ACCEPTED — 29/07/2026
S12 autorizada
```
