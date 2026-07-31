# CI canonica do Emporio

## Fronteira

`.github/workflows/ci.yml` verifica o monorepo; ele não publica imagem,
release ou deploy. Possui somente `contents: read`, não autentica em registry
e não acessa produção. Em push para `main`, depois dos gates verdes, publica
somente o plano `candidate-plan`, vinculado ao SHA e à execução. PRs nunca
produzem esse artifact.

## Jobs e dependencias

`plan` usa checkout completo e o resolvedor S05. Base ausente, SHA anterior
nulo, path nao representavel ou primeiro push selecionam os seis. PR usa a
base da PR; push comum usa `before`. O transporte de paths ocorre como bytes
NUL-delimited entre Git e Python, preservando nomes ocultos, espacos e quebras
de linha.

Em paralelo, `contracts`, `backend`, `website_back`, `frontend`,
`website_front` e `whatsapp` executam seus gates. `images` depende
explicitamente de todos eles e de `plan`.

## Runtimes, comandos e caches

- Java 21/Temurin com cache Maven: `mvn -B verify` em `backend` e
  `website_back`.
- Node 24 com cache npm por lockfile.
- `frontend`: `npm ci`, lint, Vitest e build Quasar.
- `website_front`: `npm ci`, Vitest e build `tsc && vite`.
- `whatsapp_service`: `npm ci`, checks de `index.js`/`app.js` e `npm run test`.
- contratos: catálogo/readiness, candidato, release-control, bootstrap,
  imagens Java, Compose, gateway, migrations e seus unittests.

## Imagens e vulnerabilidades

Buildx constroi a matriz canônica dos seis Dockerfiles para `linux/amd64`,
com `VCS_REF` e `IMAGE_VERSION`, cache GHA, `load: true` e `push: false`.
Cada imagem local recebe scan Trivy `v0.70.0`; achados `HIGH` ou `CRITICAL`
falham o job. Nao existe baseline nesta etapa. A remocao final alcanca somente
a tag local daquela celula da matriz; nao usa prune.

## Segredos e migrations

`tools/ci/secret_scan.py --tracked` examina todos os bytes de cada arquivo
versionado e os snapshots do historico disponivel, inclusive arquivos grandes
e conteudo com NUL, sem telemetria, upload ou impressao do valor. A saida
separa arquivos examinados, achados allowlisted e conteudo nao suportado;
sucesso exige zero `unsupported`. Achados mostram regra, path e fingerprint.
A allowlist e fechada por
regra/path/fingerprint e justificativa; cobre somente mutantes ficticios,
assignments de exemplo e identificadores publicos do cliente Firebase. Segredo
operacional nunca e allowlisted.

`tools/ci/migrations_contract.py` valida nomes Flyway, unicidade de versao,
arquivos legiveis e nao vazios nos dois backends. Ele nao aplica SQL. Startup
e aplicacao real em PostgreSQL permanecem evidencias das slices anteriores e
da futura execucao da CI, nao uma alegacao desta validacao estatica.

## Pins e permissoes

Checkout, setup Java, setup Node, Buildx, build-push e Trivy usam SHAs completos
com a versao humana comentada. O parser reconhecido `actionlint` foi executado
localmente por imagem multiarch fixada por digest. Nenhum job amplia
`contents: read`.

## Diagnostico e reproducao local

```bash
python3 tools/ci/validate_ci.py
python3 -m unittest discover -s tools/ci/tests -v
python3 tools/ci/migrations_contract.py
python3 tools/releases/candidate_manifest.py validate \
  --manifest ops/releases/examples/candidate-manifest.example.json
python3 -m unittest discover -s tools/releases/tests -v
python3 tools/releases/catalog.py validate --require-release-ready
```

Esses comandos validam contratos, nao simulam runners remotos. A primeira
execucao real do workflow somente ocorrera quando o usuario criar commit e
fizer push.

## Candidato versus release

O schema e gerador locais descrevem um candidato intermediario,
`deployable: false`. O exemplo e fictício. O workflow separado
`publish-candidate.yml` poderá publicar imagens e o manifesto candidato apenas
após uma CI confiável: valida o plano, publica por digest com provenance
verificada, preserva digest e provenance herdidos e valida os seis componentes
em Compose efêmero. Ele não promove release, não cria manifesto global
implantável e não executa deploy.

A validação final recebe o candidato anterior já verificado quando há
componentes herdados; o primeiro candidato permanece independente. O job
integrado usa autenticação GHCR somente leitura e os mesmos arquivos Compose
em `config`, `up`, `ps`, probes e `down`. O upload final registra em summary os
outputs públicos de ID, URL e digest do artifact.

O plano original agora é schema v2, contém `baseCommitSha`, hash prefixado do
catálogo e lê o evento push/main por `GITHUB_EVENT_PATH`. Sua resolução é
evidência da CI; a matriz de build vem somente do plano efetivo cumulativo
gerado após seleção do predecessor por distância Git.

O plano efetivo também usa schema v2 e inclui `mode`. Somente `continue`
materializa a matriz do resolvedor e pode alcançar build, assemble e
integração. Estados terminais produzem matriz vazia e outcome sem invocar o
resolvedor com uma lista vazia de paths.

O workflow está somente configurado e validado estaticamente; nenhuma execução
remota ocorreu nesta slice.

A persistencia local do manifesto e do sidecar usa staging, fsync, verificacao
do checksum e rollback transacional do par. Nao existe rename atomico conjunto
no filesystem; a garantia comprovada e restauracao byte a byte em falha
intermediaria, ou ausencia dos dois arquivos na primeira escrita malsucedida.
