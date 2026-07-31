# S12 — Workflow de publicacao do candidato e proveniencia

> **Estado:** `ACCEPTED` — 29/07/2026  
> **Tipo:** GitHub Actions, GHCR, candidato OCI e supply chain  
> **Executor previsto:** CLI  
> **Diretorio de trabalho obrigatorio:** `/home/gregorio/git/baronesa/emporio`  
> **Dependencias:** S01 a S11 `ACCEPTED`  
> **Contrato arquitetural:** [proposta-docker-ci-cd-producao-emporio.md](../../proposta-docker-ci-cd-producao-emporio.md)  
> **Contrato de componentes:** [release-control/README.md](../../release-control/README.md)  
> **Relatorio de saida:** `S12-publicacao-candidato-e-proveniencia.report.md`

## Instrucao para delegacao

Execute integralmente esta slice no diretorio indicado. Leia primeiro:

1. esta task;
2. a revisao final da S11;
3. as secoes `Modelo de release global`, `Candidato`,
   `Seguranca de supply chain`, `Registry`, `Credenciais dos dois papeis` e
   `Estrutura de arquivos alvo` da arquitetura;
4. `.github/workflows/ci.yml` e sua documentacao;
5. catalogo, resolvedor, schema/gerador do candidato e testes aceitos;
6. Compose/gateway/harness S10;
7. contratos de seguranca e estados S06.

Nao altere esta task nem o tracker
`docs/infrastructure/deployment/implementation/README.md`.

Esta slice implementa localmente o workflow que, depois de uma CI remota
verde em `main`, publicara imagens afetadas e um candidato completo. Como o
repositorio ainda nao possui `HEAD`, esta slice nao executa o workflow, nao
faz o primeiro push e nao alega publicacao real. A ativacao remota sera uma
slice acompanhada posterior, depois de commit/push manual do usuario.

## 1. Objetivo observavel

Ao final:

- `ci.yml` continua sendo somente CI e publica apenas o plano machine-readable
  necessario ao workflow seguinte;
- existe `publish-candidate.yml`, separado de CI, release e deploy;
- ele somente pode prosseguir apos uma execucao `push` de CI verde para a
  `main` do repositorio canonico;
- o commit e revalidado como pertencente a `origin/main`;
- a resolucao usada e exatamente a produzida pela CI disparadora;
- primeiro candidato constroi os seis componentes;
- candidato incremental constroi somente `buildComponents`;
- componentes nao afetados herdam digest e procedencia do ultimo candidato
  valido;
- cada imagem nova e construida uma vez, examinada antes do push, publicada
  no GHCR por digest e atestada;
- o conjunto completo passa por validacao integrada efemera;
- o manifesto final registra seis digests e proveniencia;
- manifesto e checksum sao armazenados como artifact imutavel e consultavel;
- nenhuma tag Git, release global ou deploy de producao ocorre;
- nenhuma capacidade de escrita e concedida antes de o evento ser
  classificado como confiavel.

## 2. Artefatos permitidos

Criar ou atualizar somente:

```text
.github/workflows/ci.yml
.github/workflows/publish-candidate.yml
.github/workflows/README.md
ops/releases/candidate-manifest.schema.json
ops/releases/examples/candidate-manifest.example.json
ops/compose/testing/compose.candidate.yml
tools/ci/resolve_changes.py
tools/ci/validate_ci.py
tools/ci/tests/test_ci.py
tools/candidates/**
tools/releases/candidate_manifest.py
tools/releases/tests/test_candidate_manifest.py
docs/infrastructure/deployment/ci/CI.md
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/release-control/CANDIDATOS.md
docs/infrastructure/deployment/implementation/slices/S12-publicacao-candidato-e-proveniencia.report.md
```

`tools/candidates/**` pode conter somente validadores, resolvedores de
artifacts, montagem do BOM, harness integrado e testes desta slice.

Se outro arquivo for necessario, parar e registrar a justificativa antes de
expandir o escopo.

## 3. Fora de escopo

Nao:

- criar `publish-release.yml` ou `deploy-production.yml`;
- criar tag Git, GitHub Release ou versao semantica;
- implementar publisher/deployer ou UI;
- publicar ou alterar `release_control`;
- aceitar input manual de tag, digest, repositorio, workflow ou componente;
- usar `workflow_dispatch`, `repository_dispatch`, `pull_request_target`,
  `schedule` ou trigger de producao;
- acessar SSH, SCP, rsync, VPS, DNS ou Nginx do host;
- alterar codigo comercial, migrations, Dockerfiles, Compose de producao ou
  gateway;
- abrir `.env.production`;
- executar `git add` no indice real, commit, tag ou push;
- executar localmente login/push no GHCR;
- executar o novo workflow remotamente nesta slice;
- inventar evidencias de artifact, digest, attestation ou candidato remoto.

## 4. Extensao controlada da CI

`ci.yml` permanece com os gatilhos exclusivos aceitos na S11.

O job `plan` deve persistir um artifact pequeno, por exemplo
`candidate-plan`, somente quando:

```text
github.event_name == push
github.ref == refs/heads/main
```

O artifact deve conter:

- schema/version do plano;
- repositorio canonico;
- commit SHA;
- ref;
- workflow run ID/attempt;
- checksum do catalogo;
- JSON integral da resolucao S05.

Requisitos:

- arquivo gerado deterministicamente por ferramenta local;
- validado antes do upload;
- nenhuma interpolacao shell insegura;
- artifact com nome fixo dentro do run e retencao explicita;
- upload action fixada por SHA;
- nenhum plano em PR;
- nenhum segredo ou environment;
- falha no upload bloqueia CI de `main`;
- o job continua com `contents: read`, sem `packages: write`.

O resultado textual em `GITHUB_OUTPUT` pode permanecer somente se pequeno e
necessario. O artifact e a autoridade entre workflows.

## 5. Trigger e classificacao de confianca

Criar:

```text
.github/workflows/publish-candidate.yml
```

Trigger unico:

```yaml
on:
  workflow_run:
    workflows: [CI]
    types: [completed]
    branches: [main]
```

Antes de qualquer job com permissao de escrita, um job somente leitura deve
provar:

- `conclusion == success`;
- workflow disparador e exatamente `CI`;
- evento do run disparador e `push`, consultado pela API canonica;
- `head_repository.full_name == greggorio/abaronesa-emporio`;
- `head_branch == main`;
- `head_sha` e SHA lowercase completo;
- o commit esta contido em `origin/main`;
- artifact `candidate-plan` pertence exatamente ao run disparador;
- plano declara o mesmo repositorio, SHA, ref e run;
- checksum do catalogo do checkout coincide;
- resolucao revalidada coincide com o catalogo atual;
- run nao e PR/fork e nao aceita payload de repositorio externo.

O checkout deve usar explicitamente o `head_sha` confiavel, nunca uma ref
fornecida livremente. Usar historico completo.

Qualquer divergencia encerra o workflow antes de conceder escrita.

`workflow_run` recebe privilegios diferentes do workflow originador. Os
testes devem tratar PR/fork, nome semelhante, evento divergente e artifact de
outro run como ataques, nao como erros comuns.

## 6. Permissoes e actions

Permissoes globais:

```text
contents: read
actions: read
packages: read
```

Somente o job que publica imagem pode receber:

```text
packages: write
id-token: write
attestations: write
contents: read
actions: read
```

O job que publica o artifact do candidato nao recebe `packages: write` nem
permissoes de deploy.

Requisitos:

- todas as actions por SHA completo, com versao humana comentada;
- confirmar SHAs em repositorios oficiais, leitura somente;
- sem action por tag/branch;
- runner GitHub-hosted `ubuntu-24.04`;
- timeout em todos os jobs;
- concurrency por commit, sem cancelar candidato valido de outro commit;
- nenhum environment GitHub de producao;
- nenhuma credencial de VPS;
- `GITHUB_TOKEN` somente no step/job que precisa;
- nunca imprimir token ou headers de autenticacao.

## 7. Ultimo candidato valido

O workflow deve localizar o ultimo artifact `candidate-manifest` valido de
uma execucao anterior bem-sucedida de `publish-candidate.yml`.

Requisitos:

- consultar somente API do repositorio canonico;
- ignorar artifact expirado, run falho, cancelado ou do commit atual;
- baixar por ID pertencente ao run selecionado;
- validar manifesto e sidecar antes de usar;
- validar `kind`, `deployable: false`, repositorio, ref, workflow e catalogo;
- verificar cada immutable ref e procedencia;
- limitar paginacao e falhar explicitamente se o limite for atingido sem
  decisao;
- nao escolher por nome de arquivo, data local ou input do usuario;
- registrar somente IDs/URLs publicos, nunca token.

Sem candidato anterior:

- `firstRelease == true` e build dos seis: permitido;
- qualquer heranca: falhar com codigo distinto.

O artifact anterior e dado nao confiavel ate passar pelo validador local.

## 8. Build seletivo e publicacao OCI

Derivar matriz somente de `resolution.buildComponents`, na ordem canonica.
Context, Dockerfile e repositorio vem do catalogo, nunca do artifact.

Tratar matriz vazia:

- job de build fica `skipped` de forma deliberada;
- montagem final continua;
- todos os seis componentes devem vir do candidato anterior;
- skip inesperado quando ha builds bloqueia.

Para cada componente afetado:

1. checkout do SHA confiavel;
2. Buildx `linux/amd64`;
3. build unico com `load: true`, `push: false`;
4. labels OCI:
   - `org.opencontainers.image.source`;
   - `org.opencontainers.image.revision`;
   - `org.opencontainers.image.version`;
   - `org.opencontainers.image.created`;
5. tag de navegacao `sha-<commit>`;
6. scan Trivy local para `HIGH,CRITICAL`, inclusive sem fix;
7. somente apos scan verde, login em `ghcr.io`;
8. push da mesma imagem local examinada, sem rebuild;
9. obter digest remoto `sha256:<64 hex>`;
10. validar repositorio/digest;
11. gerar attestation GitHub de build provenance para o subject por digest;
12. verificar a attestation publicada;
13. emitir artifact pequeno de resultado do componente.

Nao usar `docker/build-push-action` com `push: true` antes do scan. Nao
reconstruir depois do scan.

O resultado do componente deve conter somente:

- component ID;
- repositorio;
- digest/immutable ref;
- SHA/run/attempt/data;
- labels esperadas;
- IDs/URLs de attestation;
- checks efetivamente aprovados.

Fixar login, upload/download artifact, attestation, Buildx, build-push e Trivy
por SHA. Se usar ferramenta auxiliar em container, fixar digest.

Tags `latest`, `main` e tags avulsas fornecidas por input sao proibidas.

## 9. Proveniencia no contrato candidato

Como nenhum candidato real foi publicado, o schema v1 pode ser estendido sem
migracao externa, desde que schema, exemplo, gerador, validador e testes
mudem juntos.

Cada componente construido deve registrar:

- provider `github-artifact-attestations`;
- subject repository e digest;
- attestation ID;
- URL HTTPS canonica;
- predicate type de build provenance;
- workflow repository/ref/SHA/run/attempt;
- estado de verificacao `verified`.

Componente herdado preserva toda a proveniencia original byte a byte.

O validador deve rejeitar:

- attestation ausente;
- subject divergente;
- URL/repo/run divergentes;
- estado diferente de verified;
- provenance atual em componente herdado;
- provenance antiga em componente construido;
- campos extras ou valores arbitrarios.

Nao armazenar token, identidade OIDC bruta, headers, environment ou bundle
integral no manifesto.

## 10. Montagem e validacao do conjunto completo

Depois dos builds:

- baixar somente os resultados do run atual;
- rejeitar artifact duplicado, componente extra ou ausente;
- combinar builds atuais com heranca anterior usando
  `candidate_manifest.py`;
- validar particoes contra o plano da CI;
- validar seis repositorios e digests;
- gerar ID opaco ligado a SHA/run/attempt;
- preservar determinismo com data explicita do workflow;
- nao aceitar checks sinteticos antes das respectivas provas.

O gerador pode ser ajustado para receber a proveniencia e checks estruturados.
Nao usar `eval`, shell sourcing de artifact ou JSON convertido em comando.

## 11. Validacao integrada efemera

Antes de marcar o candidato valido e fazer upload do manifesto, executar o
conjunto completo por digest:

```text
backend
website_back
frontend
website_front
whatsapp_service
gateway
```

Usar `ops/compose/compose.prod.yml` com override exclusivo
`ops/compose/testing/compose.candidate.yml`.

O override:

- nao possui `build:`;
- nao troca digest por tag;
- usa nomes de rede/volumes prefixados pelo run;
- usa bind gateway somente em `127.0.0.1`;
- usa porta loopback efemera;
- desabilita inicializacao externa do WhatsApp apenas no harness;
- usa banco, tokens, bootstrap e dominios `.invalid` estritamente efemeros;
- nao acessa servico externo real.

Provar:

- sete servicos healthy;
- gateway e unico bind;
- host desconhecido fechado;
- roots dos dois frontends;
- ao menos uma API de cada backend;
- `/api/whatsapp/status` passa pelo backend e alcanca Node interno;
- control routes continuam 404;
- nenhuma porta de banco/backend/frontend/WhatsApp publicada.

Depois da prova:

- derrubar stack com volumes e orphans do prefixo exato;
- remover somente imagens locais relacionadas ao run;
- confirmar zero container, volume e rede do prefixo;
- nunca usar prune.

Se qualquer imagem for privada, autenticar somente com `packages: read` no
job integrado.

## 12. Artifact candidato

Somente depois de build/scan/push/attestation e validacao integrada verdes:

- validar novamente o manifesto;
- conferir sidecar SHA-256;
- fazer upload com action fixada;
- nome logico fixo `candidate-manifest` por run;
- `if-no-files-found: error`;
- `overwrite: false`;
- incluir somente manifesto, sidecar e metadado publico minimo;
- retencao explicita e documentada;
- registrar artifact ID/digest/URL retornados;
- nao publicar como GitHub Release;
- nao criar tag Git;
- nao marcar `deployable: true`.

O artifact e imutavel dentro do run, mas sujeito a retencao. Documentar a
politica e o comportamento quando o anterior expirou: falhar fechado, nunca
reconstruir heranca silenciosamente.

## 13. Validadores e testes

Criar validador semantico do workflow e mutantes independentes. Cobrir no
minimo:

1. trigger extra ou manual;
2. execucao com CI nao verde;
3. run originado por PR/fork;
4. repositorio/head branch/SHA divergente;
5. commit fora de `origin/main`;
6. plano ausente, duplicado, de outro run ou adulterado;
7. permissao de escrita global;
8. `packages: write` fora do build;
9. action mutavel;
10. login antes do scan;
11. push no Buildx antes do scan;
12. rebuild depois do scan;
13. tag `latest`/`main`;
14. matriz fora do resolvedor;
15. matriz vazia deliberada e inesperada;
16. primeiro candidato parcial;
17. candidato anterior ausente/invalido/expirado;
18. artifact anterior de run falho;
19. digest, repo ou componente de resultado divergente;
20. attestation ausente/divergente/nao verificada;
21. provenance herdada alterada;
22. conjunto parcial ou componente duplicado;
23. Compose candidato com build/tag/bind indevido;
24. falha de health/probe;
25. cleanup dirigido;
26. upload anterior a validacao;
27. artifact com arquivo extra;
28. tentativa de release/tag/deploy/SSH;
29. schema/propriedade extra;
30. nenhuma publicacao local durante os testes.

Testes de helpers GitHub devem usar fixtures/doubles HTTP locais ou respostas
JSON salvas, sem chamar API real. Nao registrar token ficticio com formato
funcional fora das fixtures allowlisted.

## 14. Documentacao

Atualizar:

### `.github/workflows/README.md`

- dois workflows ativos e responsabilidades;
- triggers;
- permissoes;
- CI nao publica imagem;
- candidato nao publica release nem deploy;
- ativacao remota ainda nao executada.

### `CI.md`

- artifact de plano;
- encadeamento por `workflow_run`;
- fronteira de confianca;
- jobs/permissoes;
- build-scan-push-attest;
- diferenca entre build CI e build candidato.

### `CANDIDATOS.md`

- ciclo de vida;
- primeiro/incremental/docs-only;
- plano, artifacts e retencao;
- heranca/proveniencia;
- validacao integrada;
- estados de falha;
- como localizar artifact;
- candidato nao implantavel;
- fronteira entre candidato e release;
- procedimento de diagnostico sem rerun inseguro.

### Release-control README

Registrar contrato e workflow implementados localmente, mas deixar claro:

- nenhuma publicacao remota ocorreu;
- lista de candidatos futura consumira artifacts verificados;
- release global e deploy continuam ausentes.

## 15. Validacao local

Executar somente validadores/testes locais e parser reconhecido:

```text
python3 tools/ci/validate_ci.py
python3 -m unittest discover -s tools/ci/tests -v
python3 tools/candidates/validate_candidate_workflow.py
python3 -m unittest discover -s tools/candidates/tests -v
python3 tools/releases/candidate_manifest.py validate \
  --manifest ops/releases/examples/candidate-manifest.example.json
python3 -m unittest discover -s tools/releases/tests -v
python3 tools/releases/catalog.py validate --require-release-ready
```

Executar `actionlint` efemero fixado por digest sobre os dois workflows.

Nao executar:

- Maven/NPM;
- Docker builds;
- Trivy real;
- Compose candidato;
- login/push GHCR;
- attestation real;
- upload/download artifact real;
- workflow remoto.

As provas de build/publicacao/integracao pertencem a futura ativacao remota.
Esta slice deve dizer `CONFIGURADO, NAO EXECUTADO`.

## 16. Estado protegido

Preservar:

- indice Git real vazio;
- HEAD inexistente;
- nenhuma tag/reflog/commit/push;
- origin canonico inalterado;
- exatamente dois workflows YAML ativos;
- nenhuma S13;
- nenhum cache Python;
- nenhum artifact, indice temporario, container, volume, rede ou imagem local
  residual da S12;
- nenhuma publicacao, GitHub Release ou acesso a producao.

Consultas somente leitura a repositorios oficiais de actions sao permitidas
para confirmar pins. Nao acessar API privada do projeto ou GHCR.

## 17. Relatorio obrigatorio

Criar:

```text
docs/infrastructure/deployment/implementation/slices/S12-publicacao-candidato-e-proveniencia.report.md
```

Registrar:

1. CWD e estado inicial;
2. arquivos criados/alterados;
3. grafo de jobs;
4. fronteira `workflow_run`;
5. matriz de permissoes;
6. actions/pins/fontes;
7. artifact do plano;
8. resolucao e matriz seletiva;
9. descoberta/validacao do anterior;
10. build-scan-push;
11. attestation e verificacao;
12. contrato de proveniencia;
13. conjunto completo e harness;
14. artifact/retencao;
15. testes/mutantes e quantidades;
16. comandos/exit codes;
17. falhas intermediarias;
18. documentacao;
19. fronteiras nao executadas;
20. estado Git/workflows/caches/residuos;
21. riscos e itens nao determinados;
22. bloqueios.

Nao reproduzir workflow integral, token, headers, bundle de attestation,
manifestos de ferramentas ou logs extensos.

Estado final:

```text
IN_PROGRESS — aguardando revisao do orquestrador
```

Nao declarar `ACCEPTED`.

## 18. Criterios de aceite do orquestrador

A S12 somente podera ser aceita localmente se:

- S11 estiver aceita;
- CI produzir plano apenas para push/main;
- candidate iniciar somente de CI verde push/main confiavel;
- nenhum job com escrita executar antes da classificacao;
- permissoes forem minimas por job;
- actions estiverem fixadas;
- plano for ligado ao run/SHA/catalogo;
- matriz vier do resolvedor;
- primeiro candidato exigir seis;
- incremental construir apenas afetados;
- docs-only herdar seis sem bloquear montagem;
- build ocorrer uma vez antes de scan/push;
- scan bloquear high/critical;
- push usar somente GHCR canonico e tag SHA;
- digests remotos forem validados;
- attestations forem exigidas/verificadas;
- proveniencia built/inherited for coerente;
- candidato anterior for validado e selecao fail-closed;
- conjunto completo por digest tiver harness configurado;
- artifact ocorrer somente apos todas as validacoes;
- candidato permanecer `deployable: false`;
- nenhum release/tag/deploy/SSH existir;
- validadores/mutantes/parser passarem;
- documentacao distinguir configurado de executado;
- estado Git protegido permanecer;
- nenhuma publicacao real for alegada.

O aceite desta slice confirma a implementacao local do workflow, nao seu
sucesso remoto.

A proxima slice prevista sera a S13: primeiro commit/push manual, ativacao
remota acompanhada da CI e do candidato, triagem de CVEs e validacao dos
artifacts/GHCR/attestations reais. Ela dependera de uma acao explicita do
usuario no terminal e ainda nao publicara release global nem fara deploy.

## 19. Condicoes de bloqueio

Parar e documentar se:

- for preciso publicar algo para validar localmente;
- uma action exigir secret/licenca externa;
- provenance exigir permissao mais ampla que a matriz aprovada;
- o evento `workflow_run` nao permitir provar push/main/repo/SHA;
- a CI nao puder transportar o plano de forma inequivoca;
- nao houver estrategia fail-closed para candidato anterior expirado;
- matriz vazia nao puder ser tratada com seguranca;
- o build precisar ocorrer antes dos gates ou ser repetido apos scan;
- o harness exigir acesso externo real;
- for necessario alterar codigo comercial, Dockerfile, Compose de producao,
  gateway ou migration;
- surgir capacidade de release, deploy, SSH ou input arbitrario;
- o indice real deixar de estar vazio.

## 20. Emenda corretiva definitiva do orquestrador

### 20.1 Autoridade e retificacao de governanca

Esta secao substitui qualquer requisito conflitante das secoes 1 a 19 e das
revisoes anteriores. Os ciclos de revisao 1 e 2 foram **emendas de contrato
causadas por lacunas do orquestrador**, e nao rejeicoes de decisoes tomadas
incorretamente pelo executor.

Na retomada, o executor:

- implementa literalmente as decisoes abaixo;
- nao escolhe arquitetura, formato, politica de concorrencia, estrategia de
  lineage, probes, semantica de cleanup ou schema alternativo;
- nao preserva comportamento anterior quando ele conflitar com esta emenda;
- registra uma divergencia como bloqueio antes de implementar apenas se duas
  exigencias desta propria secao forem materialmente incompativeis.

Nenhum criterio arquitetural novo podera ser usado para reprovar esta
retomada. Achado posterior nao escrito aqui sera tratado como nova emenda do
orquestrador ou como slice futura.

### 20.2 Resultado final e grafo obrigatorio

Os unicos workflows ativos continuam sendo:

```text
ci.yml
publish-candidate.yml
```

O grafo de `publish-candidate.yml` deve ser exatamente:

```text
trust
  -> predecessor
       -> build[matriz, quando has_builds]
       -> assemble
            -> integrated
                 -> publish
```

Regras:

1. `trust` valida o evento e o plano original produzido pela CI.
2. `predecessor` resolve lineage e produz o plano efetivo cumulativo.
3. `build` usa somente a matriz do plano efetivo.
4. `assemble` produz um bundle interno pendente, ainda nao publicavel.
5. `integrated` testa as seis referencias e produz um recibo.
6. `publish` combina bundle e recibo, valida tudo e envia o unico artifact
   candidato final.
7. `already_published`, `no_changes` e `superseded` encerram o workflow com
   sucesso, sem build, integracao ou novo artifact candidato; produzem apenas
   o artifact de outcome definido em 20.11.
8. Nenhum job anterior a `trust` possui permissao de escrita.

Nao usar `queue: max`. Manter concorrencia por SHA e tornar a seguranca
independente da ordem de conclusao por meio das verificacoes de lineage,
HEAD e idempotencia definidas nesta secao.

O output `mode` usa somente:

```text
continue | already_published | no_changes | superseded
```

Somente `continue` habilita jobs posteriores. Falha de confianca, lineage,
shape ou vinculo nao usa um desses modos: encerra o job com exit code nao
zero.

### 20.3 Plano original da CI

`candidate_plan.py generate` deve ler `GITHUB_EVENT_PATH` e aceitar somente
evento `push` da `main`. O JSON `candidate-plan.json` possui exatamente:

```json
{
  "schemaVersion": 2,
  "repository": "greggorio/abaronesa-emporio",
  "commitSha": "<40 hex minusculos>",
  "baseCommitSha": "<40 hex minusculos ou 40 zeros>",
  "ref": "refs/heads/main",
  "workflowRunId": "<inteiro decimal positivo em string>",
  "workflowAttempt": 1,
  "catalogSha256": "sha256:<64 hex minusculos>",
  "resolution": {}
}
```

`resolution` e a saida integral do resolvedor canonico aceita na S05.
`workflowAttempt` e inteiro positivo. Nao sao permitidas propriedades extras.
O plano original e seu hash sao evidencia da CI, mas sua matriz nao e usada
diretamente para o build antes da resolucao do predecessor.

O job `plan` da CI publica artifact `candidate-plan`, retencao de 7 dias,
contendo somente `candidate-plan.json`. Ele nao e produzido em pull request.

### 20.4 Confianca, HEAD e idempotencia

`trust` exige todos os itens:

- `workflow_run.event == "push"`;
- `workflow_run.conclusion == "success"`;
- branch `main`;
- repositorio e owner canonicos;
- workflow disparador exatamente `CI`;
- artifact de plano pertencente ao run e attempt recebidos;
- plano original conforme 20.3 e coerente com o payload;
- checkout do `head_sha` recebido.

Depois do checkout, executar fetch explicito de `origin main` e exigir:

```text
workflow_run.head_sha == git rev-parse origin/main
```

Ser apenas ancestral nao basta. A mesma igualdade deve ser refeita no job
`publish`, imediatamente antes do upload final. Se `main` avancar e os SHAs
continuarem na mesma lineage, o run e classificado como `superseded` e
termina com sucesso sem publicar. Se deixarem de pertencer a mesma lineage,
falha fechado.

Antes do build e novamente antes do upload, consultar candidatos elegiveis.
Um candidato valido do mesmo SHA resulta em `already_published`; nao e criado
segundo artifact. Um candidato valido descendente do SHA atual resulta em
`superseded`. Lineage nao relacionado ou force-push falha fechado.

### 20.5 Selecao do predecessor e resolucao cumulativa

Pesquisar no maximo 10 paginas de 50 runs concluidos com sucesso do workflow
de publicacao. Cada run bem-sucedido deve possuir exatamente um
`candidate-outcome` valido. Ausencia, duplicidade ou expiracao do outcome
falha fechado. Um candidato e elegivel somente quando:

- o outcome possui `status == published`;
- seu workflow terminou com `success`;
- possui exatamente um artifact final com o nome canonico;
- artifact, manifesto, checksum e metadata passam em todas as validacoes;
- commit pertence a mesma lineage Git do SHA atual.

Classificar cada SHA com `git merge-base --is-ancestor` em:

```text
same | ancestor | descendant | unrelated
```

Selecionar o ancestral valido mais proximo pela distancia do grafo Git, nunca
por data de conclusao. Empate ou historia ambigua falha fechado.

Regras deterministicas:

- nenhum outcome `published` encontrado entre outcomes validos: `first`;
- candidato valido no mesmo SHA: `already_published`;
- descendente valido: `superseded`;
- lineage nao relacionada: falha;
- ancestral esperado mais proximo com artifact expirado, ausente ou invalido:
  falha, sem recuar para um candidato mais antigo;
- predecessor selecionado: calcular mudancas com
  `git diff --name-only -z <predecessorSha>..<currentSha>`;
- primeiro candidato: resolver como primeiro release e selecionar os seis;
- predecessor diferente com diff cumulativo vazio: `no_changes`;
- em qualquer outro caso, passar os paths NUL-separated ao resolvedor
  canonico e usar exatamente seu fechamento de dependencias.

O job grava `candidate-effective-plan.json`, sem propriedades extras:

```json
{
  "schemaVersion": 1,
  "kind": "candidate-effective-plan",
  "repository": "greggorio/abaronesa-emporio",
  "commitSha": "<sha atual>",
  "sourceCi": {
    "runId": "<id decimal>",
    "attempt": 1,
    "baseCommitSha": "<sha ou zero sha>",
    "planSha256": "sha256:<64 hex>"
  },
  "catalog": {
    "schemaVersion": 1,
    "sha256": "sha256:<64 hex>"
  },
  "predecessor": {
    "status": "first",
    "candidateId": null,
    "commitSha": null,
    "workflowRunId": null,
    "artifactId": null,
    "artifactDigest": null
  },
  "resolution": {}
}
```

Para predecessor selecionado, `status` e `selected` e os cinco campos
nullable possuem os identificadores validados; `artifactDigest` usa
`sha256:<64 hex>`. `resolution` e a saida cumulativa integral do resolvedor.
A matriz e `has_builds` sao derivados desse arquivo e escritos no
`GITHUB_OUTPUT` com LF real; nao sao duplicados no JSON.

O job `predecessor` publica:

- artifact `candidate-effective-plan`, retencao de 1 dia, contendo somente
  `candidate-effective-plan.json`;
- artifact `candidate-predecessor-context`, retencao de 1 dia, contendo
  sempre `selection.json` e, somente quando `status == selected`, o
  subdiretorio `previous/` com os tres arquivos validados de 20.6.

`selection.json` possui exatamente o objeto `predecessor` do plano efetivo.
Para `first`, nao existe subdiretorio `previous/`.

O job `build` executa somente quando `mode == continue` e
`has_builds == true`. `assemble` depende de `predecessor` e `build`, usa
`if: always()` e prossegue somente se predecessor teve sucesso e:

```text
build == success
ou
has_builds == false e build == skipped
```

Qualquer outro skip/failure bloqueia. Cada item da matriz publica artifact
`candidate-component-<component>`, retencao de 1 dia, contendo somente
`result.json`.

### 20.6 Download seguro de artifacts anteriores

Usar somente endpoint derivado de repository e artifact ID ja validados:

```text
/repos/greggorio/abaronesa-emporio/actions/artifacts/<artifact-id>/zip
```

Executar `gh api` por lista de argumentos, sem shell, gravando stdout binario
diretamente em file handle. Sao proibidos `--output`, URL fornecida pelo
payload e redirecionamento de shell.

Antes da extracao:

- exigir digest REST `sha256:<64 hex>`;
- calcular e comparar SHA-256 do ZIP;
- limitar ZIP a 2 MiB;
- rejeitar duplicidade, path absoluto, `..`, diretorio e entrada extra;
- nao usar `extractall`.

Ler e gravar explicitamente apenas:

```text
candidate.json                         maximo 1 MiB
candidate.json.sha256                  exatamente uma linha canonica
metadata.json                          maximo 16 KiB
```

### 20.7 Build, push, digest remoto e resultado por componente

Para cada componente selecionado:

1. construir uma unica vez com `--load`, tag
   `ghcr.io/greggorio/abaronesa-emporio-<component>:sha-<commit>`;
2. inspecionar os quatro labels OCI locais obrigatorios:
   `source`, `revision`, `version`, `created`;
3. executar o scan local prescrito;
4. autenticar e executar um unico `docker push`;
5. obter o digest do registry com:

   ```text
   docker buildx imagetools inspect <tag> --format "{{json .Manifest}}"
   ```

6. parsear `.digest` e exigir `sha256:<64 hex>`;
7. atestar exatamente a referencia `<repository>@<digest>`;
8. executar `gh attestation verify` dessa referencia;
9. em `always()`, logout e remocao da tag local exata, seguida de
   `docker image inspect` que obrigatoriamente deve indicar ausencia.

Nao usar `RepoDigests` local como prova remota e nao reconstruir depois do
scan.

Cada `component-result.json` e estrito e possui exatamente:

```text
schemaVersion, component, repository, tag, digest, immutableRef,
commitSha, workflowRunId, workflowAttempt, builtAt, labels, checks,
provenance
```

Contratos:

- `schemaVersion == 1`;
- componente/repository/tag/SHA/run/attempt iguais ao plano efetivo e catalogo;
- `digest` no formato `sha256:<64 hex>`;
- `immutableRef == repository + "@" + digest`;
- `builtAt` UTC canonico;
- `labels` contem exatamente os quatro labels OCI e seus valores esperados;
- `checks` contem exatamente `build`, `test` e `scan`, todos `passed`;
- `provenance` contem exatamente `attestationId`, `attestationUrl`,
  `verifiedSubject` e `verifiedAt`;
- ID, URL canonica e subject sao validados; `verifiedAt` so e gravado depois
  de `gh attestation verify` retornar sucesso.

Todos os JSONs e linhas de `GITHUB_OUTPUT`/`GITHUB_ENV` usam LF real.

### 20.8 Schema v2 e bundle pendente

Atualizar schema e exemplo do candidato para `schemaVersion: 2`.
O candidato final preserva BOM de seis componentes e passa a exigir:

- top-level `sourceCi`, igual ao vinculo do plano efetivo;
- top-level `predecessor`, igual ao predecessor do plano efetivo;
- top-level `integration`, conforme 20.10;
- `catalog.sha256` no formato `sha256:<64 hex>`;
- em cada componente, os quatro labels OCI, `checks` com apenas
  `build/test/scan` e a procedencia estrita de 20.7;
- nenhuma alegacao de `health` por componente antes do harness.

O job `assemble` valida resultados novos contra plano/catalogo/ambiente e
dados herdados contra o candidato anterior. Ele nao inventa procedencia.
Sua saida interna e:

```text
pending.json
pending.json.sha256
metadata.json
```

O metadata pendente possui exatamente:

```json
{
  "schemaVersion": 1,
  "stage": "pending",
  "repository": "greggorio/abaronesa-emporio",
  "commitSha": "<sha>",
  "workflowRunId": "<id>",
  "workflowAttempt": 1,
  "pendingSha256": "sha256:<64 hex>"
}
```

O bundle usa staging, flush, `fsync`, rename atomico, rollback e verificacao
pos-escrita. Ele e artifact interno `candidate-pending`, retencao de 1 dia,
contem exatamente os tres arquivos listados nesta secao e nunca e tratado
como candidato publicavel.

### 20.9 Harness integrado exato

O job `integrated` baixa o bundle pendente, valida-o e gera env com uma
atribuicao por linha LF. Deve:

1. validar `docker compose config` com exatamente sete services canonicos;
2. exigir somente um bind publicado, gateway em
   `127.0.0.1:<porta-efemera>:8080`;
3. usar o mesmo project name e os mesmos dois `-f` em todos os comandos;
4. executar `docker compose pull --quiet --policy always`;
5. executar
   `docker compose up -d --no-build --pull never --wait --wait-timeout 600`;
6. analisar `docker compose ps --format json`, exigindo sete containers
   `running` e `healthy`;
7. executar, por cliente HTTP da biblioteca padrao Python, os probes com
   identificadores e expectativas exatos:
   - `website_root`: host `emporio.abaronesa.net.br`, `/`, 200 e corpo nao
     vazio;
   - `erp_root`: host `erp-emporio.abaronesa.net.br`, `/`, 200 e corpo nao
     vazio;
   - `website_theme_api`: host website,
     `/api/themes?tenantId=candidate.invalid`, 200 e JSON array;
   - `erp_login`: host ERP, `POST /api/auth/login`, 200 e `accessToken`
     string nao vazia;
   - `erp_whatsapp_api`: host ERP, `GET /api/whatsapp/status` autenticado,
     200 e JSON;
   - `publisher_route_absent`: host ERP,
     `/api/release-publisher/v1/candidates`, exatamente 404;
   - `deployer_route_absent`: host ERP,
     `/api/deployment-control/v1/current`, exatamente 404;
   - `unknown_host_denied`: host `unknown.invalid`, conexao encerrada ou
     status fora de 2xx;
8. provar a topologia interna com
   `docker compose exec -T backend curl -fsS
   http://whatsapp_service:3001/status`; esse e o nono probe,
   `whatsapp_internal`;
9. nunca expor JWT, senha ou chave em argumento, output ou log.

O status HTTP do backend nao substitui o probe interno do WhatsApp.

No bloco de cleanup, sempre:

- `docker compose down -v --remove-orphans`;
- remover somente as seis referencias imutaveis exatas do pending;
- verificar por inspect que cada uma esta ausente;
- provar zero containers, volumes e networks do project;
- executar logout;
- falhar o job se qualquer limpeza dirigida falhar.

Sao proibidos prune, glob, tag livre e remocao de recurso alheio ao project.

### 20.10 Recibo de integracao e finalizacao

Somente depois de probes e cleanup verdes, criar
`integration-result.json` estrito:

```text
schemaVersion, status, repository, commitSha, workflowRunId,
workflowAttempt, pendingSha256, checkedAt, services, probes, cleanup
```

Contratos:

- `schemaVersion == 1`, `status == "passed"`;
- repository/SHA/run/attempt/pending hash iguais ao bundle;
- `checkedAt` UTC canonico;
- `services` lista exatamente os sete services, cada um running/healthy;
- `probes` lista exatamente os nove probes de 20.9, todos passed;
- `cleanup` registra zero containers, volumes, networks e seis imagens
  ausentes.

Gerar sidecar e publicar artifact interno
`candidate-integration-result`, retencao de 1 dia, contendo exatamente
`integration-result.json` e `integration-result.json.sha256`.

O job `publish` baixa pending, recibo, plano efetivo e predecessor; revalida
todos os vinculos, refaz a verificacao de HEAD/idempotencia e entao produz:

```text
candidate.json
candidate.json.sha256
metadata.json
```

`integration` no manifesto final vincula run, attempt, pending SHA, receipt
SHA, checkedAt, status, services, probes e cleanup do recibo.

O metadata final possui exatamente:

```json
{
  "schemaVersion": 1,
  "stage": "final",
  "candidateId": "<id do manifesto>",
  "repository": "greggorio/abaronesa-emporio",
  "commitSha": "<sha>",
  "workflowRunId": "<id>",
  "workflowAttempt": 1,
  "manifestSha256": "sha256:<64 hex>"
}
```

Finalizador e validador devem exigir igualdade exata entre manifesto,
sidecar, metadata, plano, run, attempt, SHA, candidate ID, predecessor e
recibo antes do upload. A escrita usa o protocolo atomico de 20.8.

### 20.11 Artifact final e semantica de falha

O artifact final:

- contem exatamente os tres arquivos de 20.10;
- usa o nome `candidate-manifest`;
- usa retencao de 30 dias;
- permanece `deployable: false`;
- e elegivel somente se o workflow inteiro terminar `success`.

O output `artifact-digest` de `actions/upload-artifact` e **64 hex sem
prefixo**. O digest fornecido posteriormente pela API REST usa
`sha256:<64 hex>`. Registrar no summary ID, URL e digest nos respectivos
formatos.

Uma falha posterior ao step de upload pode deixar artifact fisico em run
vermelho. Ele nunca e elegivel porque a busca aceita somente runs
`success`; a documentacao nao deve alegar que toda falha impede a criacao
fisica do artifact.

Todo caminho que termina com sucesso publica tambem artifact
`candidate-outcome`, retencao de 30 dias, contendo exatamente
`outcome.json` e `outcome.json.sha256`. O JSON possui exatamente:

```text
schemaVersion, status, repository, commitSha, workflowRunId,
workflowAttempt, candidateId, candidateArtifactId, candidateArtifactDigest,
predecessorCandidateId
```

Regras:

- `schemaVersion == 1`;
- `status` e um de `published`, `already_published`, `no_changes`,
  `superseded`;
- identidade do run e SHA sao estritas;
- em `published`, os tres campos do candidato referenciam o artifact final
  criado no mesmo run; o digest usa os 64 hex crus retornados pela action;
- em `already_published`, referenciam o candidato valido ja existente;
- em `no_changes` e `superseded`, campos do candidato sao `null`;
- `predecessorCandidateId` e o ID selecionado ou `null` em first;
- sidecar, shape e vinculos sao validados antes do upload.

O job `publish` usa `if: always()` e depende de `predecessor` e `integrated`.
Com `mode == continue`, ele exige `integrated == success`, publica candidato
e outcome somente se a guarda final continuar `continue`; se a guarda final
detectar que `main` avancou na mesma lineage, converte para `superseded` e
publica somente outcome. Nos modos terminais decididos antes da integracao,
exige `integrated == skipped` e publica somente outcome. Qualquer outra
combinacao falha.

### 20.12 Permissoes exatas

No nivel global:

```yaml
permissions:
  contents: read
  actions: read
```

`build` declara exatamente:

```yaml
contents: read
actions: read
packages: write
id-token: write
attestations: write
```

`integrated` declara exatamente:

```yaml
contents: read
actions: read
packages: read
```

`trust`, `predecessor`, `assemble` e `publish` herdam exatamente as duas
permissoes globais e nao recebem packages write, id-token write ou
attestations write. Nenhum `assert` Python pode proteger validacao de
seguranca; usar condicao explicita e erro fail-closed.

### 20.13 Correcoes operacionais obrigatorias

Tambem corrigir os cinco defeitos ja comprovados:

- LF real em todos os protocolos e JSONs;
- `gh api` binario sem `--output`;
- digest do output da action sem prefixo;
- metadata pendente e final validados antes dos uploads;
- cleanup de imagens verificavel e fail-closed.

### 20.14 Testes obrigatorios e doubles

Os testes devem chamar helpers/CLIs reais com arquivos temporarios e doubles
de subprocesso; inspecao textual isolada nao satisfaz o criterio.

Cobertura causal minima:

- plano v2, chaves extras, evento/ref/SHA/base/run/attempt invalidos;
- LF real em `GITHUB_OUTPUT`, `GITHUB_ENV`, resultados e metadata;
- selecao por distancia Git em repositorios temporarios;
- first, same-SHA, ancestor, descendant, unrelated, empate, expirado,
  artifact ausente/invalido e historia reescrita;
- outcome published/no-op, ausente, duplicado, expirado e com vinculo
  adulterado;
- diff cumulativo NUL, fechamento transitivo e `no_changes`;
- comando real `gh api`, endpoint por ID, stdout binario, limite e extracao
  segura;
- digest raw da action versus digest REST prefixado;
- inspecao remota via imagetools e rejeicao de `RepoDigests`;
- labels, component result, attestation e remocao local estritos;
- schema v2 e pipeline pending -> receipt -> final;
- adulteracao de metadata, sidecar, plano, predecessor, pending e receipt;
- config com sete services, bind unico, pull/up exatos, nove probes;
- cleanup positivo e residuos de container/volume/network/imagem;
- HEAD avancado antes do build e antes do upload;
- idempotencia do mesmo SHA e descendant superseded;
- permissions e grafo de jobs exatos;
- mutantes causais para cada requisito acima.

Executar ao final:

```bash
python3 tools/ci/validate_ci.py
python3 -m unittest discover -s tools/ci/tests -p 'test_*.py'
python3 tools/releases/validate_candidate_manifest.py
python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'
python3 tools/candidates/validate_candidate_workflow.py
python3 -m unittest discover -s tools/candidates/tests -p 'test_*.py'
actionlint .github/workflows/ci.yml .github/workflows/publish-candidate.yml
docker compose \
  -f ops/compose/docker-compose.emporio.yml \
  -f ops/compose/testing/compose.candidate.yml \
  config
```

Continuam proibidos nesta retomada: Maven, NPM, Docker build, Compose `up`,
Trivy real, workflow remoto, login ou push GHCR, attestation remota, commit,
tag, push e acesso a producao.

### 20.15 Matriz fechada de aceite

A retomada e aceita se, e somente se:

1. todos os requisitos 20.2 a 20.13 estao implementados literalmente;
2. todos os testes 20.14 existem e passam;
3. `actionlint` e `docker compose config` passam;
4. workflows ativos continuam exatamente os dois autorizados;
5. task e tracker nao foram alterados pelo executor;
6. nao existem caches/residuos efemeros;
7. indice Git permanece vazio, sem HEAD, tag, reflog, commit ou push;
8. relatorio registra arquivo, comando, exit code, resultado e interpretacao;
9. nenhuma execucao externa proibida e alegada.

Uma implementacao divergente de preferencia nao escrita nesta secao nao pode
ser rejeitada. Eventual melhoria nao exigida sera planejada em slice futura.

Estado autorizado:

```text
S12 IN_PROGRESS — contrato corretivo definitivo pronto para delegacao
S13 nao autorizada
```

## 21. Emenda terminal do orquestrador

### 21.1 Motivo e autoridade

Esta emenda corrige uma incompatibilidade interna entre 20.5 e o resolvedor
S05. O contrato exige sucesso sem publicacao para `already_published`,
`superseded` e `no_changes`, mas o resolvedor canonico rejeita uma lista
vazia de paths. A implementacao estrita revelou corretamente essa lacuna.

Esta secao substitui somente o formato do effective plan e da resolution nos
modos terminais. Ela nao reabre nenhuma outra decisao da Secao 20 e nao e
classificada como erro retroativo do executor.

### 21.2 Effective plan v2 exato

`candidate-effective-plan.json` passa a possuir exatamente as mesmas chaves
de 20.5 mais:

```json
"mode": "continue"
```

O `schemaVersion` do effective plan passa de `1` para `2`. `mode` admite
somente:

```text
continue | already_published | no_changes | superseded
```

Regras:

- `continue + predecessor first`: resolution integral de first release;
- `continue + predecessor selected`: diff cumulativo obrigatoriamente nao
  vazio e resolution integral do resolvedor S05;
- `already_published`: predecessor obrigatoriamente selected;
- `no_changes`: predecessor obrigatoriamente selected;
- `superseded`: predecessor pode ser first quando `main` avancou antes da
  descoberta, ou selected quando existe candidato descendente;
- somente `continue` pode chegar a build, assemble e integrated.

### 21.3 Resolution terminal exata

Como o resolvedor S05 nao aceita zero paths, os modos terminais usam um
contrato explicito do adaptador de lineage.

Para `no_changes`:

```json
{
  "classification": "no_changes",
  "changedPaths": [],
  "directComponents": [],
  "buildComponents": [],
  "validationComponents": [],
  "inheritedComponents": [
    "backend",
    "website_back",
    "frontend",
    "website_front",
    "whatsapp_service",
    "gateway"
  ],
  "warnings": ["NO_CHANGES_SINCE_PREDECESSOR"]
}
```

Para `already_published`:

```json
{
  "classification": "terminal",
  "changedPaths": [],
  "directComponents": [],
  "buildComponents": [],
  "validationComponents": [],
  "inheritedComponents": [
    "backend",
    "website_back",
    "frontend",
    "website_front",
    "whatsapp_service",
    "gateway"
  ],
  "warnings": ["CANDIDATE_ALREADY_PUBLISHED"]
}
```

Para `superseded`:

```json
{
  "classification": "terminal",
  "changedPaths": [],
  "directComponents": [],
  "buildComponents": [],
  "validationComponents": [],
  "inheritedComponents": [
    "backend",
    "website_back",
    "frontend",
    "website_front",
    "whatsapp_service",
    "gateway"
  ],
  "warnings": ["SUPERSEDED_BY_MAIN_OR_CANDIDATE"]
}
```

Nenhuma dessas resolutions pode integrar um pending ou candidato final.
`validate_pending` exige `effective.mode == continue`.

### 21.4 Outcome `already_published`

Ao ler um outcome `already_published`, a descoberta deve consultar o endpoint
canonico do `candidateArtifactId` informado e validar:

- artifact existente, nao expirado e com nome `candidate-manifest`;
- digest REST igual ao digest cru do outcome depois de remover `sha256:`;
- workflow run associado canonico e verde;
- manifesto, sidecar e metadata validos;
- candidate ID, commit SHA, artifact ID e digest iguais ao outcome;
- `predecessorCandidateId == candidateId`.

Para outcome `published`, exigir tambem:

```text
predecessorCandidateId == manifest.predecessor.candidateId
```

considerando `null` no primeiro candidato.

### 21.5 Provas obrigatorias

Adicionar testes causais que chamem helpers/CLIs reais e comprovem:

1. `lineage.effective()` e `validate_effective()` verdes para os quatro
   modos;
2. `no_changes`, `already_published` e `superseded` geram effective plan v2,
   matriz vazia, `has_builds=false` e exit 0;
3. os tres modos terminais nao habilitam build, assemble ou integrated e
   produzem outcome;
4. resolution ou warning terminal trocado falha;
5. pending ligado a effective mode terminal falha antes de Docker;
6. outcome `already_published` com artifact inexistente, expirado, nome,
   digest, run, candidate ID ou metadata divergente falha;
7. `predecessorCandidateId` divergente falha em outcomes `published` e
   `already_published`;
8. a matriz 20.14 integral continua verde.

Nao alterar `tools/releases/catalog.py`; o adaptador terminal pertence a
`tools/candidates/lineage.py`. Nao executar build, Compose up, publicacao,
commit ou push.

Estado:

```text
S12 IN_PROGRESS — emenda terminal do orquestrador
S13 nao autorizada
```
