# S39 — correction-01: base Alpine sem vulnerabilidades HIGH/CRITICAL

> **Data:** 03/08/2026
> **Task corrigida:** `S39-workflow-imagem-release-control.task.md`
> **Relatório contínuo:** `S39-workflow-imagem-release-control.report.md`
> **Tipo:** correção causal local, commit/push e validação remota sem execução do novo workflow
> **Produção/VPS:** proibidas

## 1. Motivo e decisão

A primeira execução da S39 parou corretamente antes de stage, commit e push. A
imagem construída sobre `python:3.13-slim` herdou integralmente da base Debian
13.6 23 vulnerabilidades (`19 HIGH`, `4 CRITICAL`), todas sem `FixedVersion`.
O mesmo scan executado diretamente na base reproduziu as 23 ocorrências.

Não haverá waiver, `ignore-unfixed=true`, redução de severidade nem espera
indeterminada por correção do Debian. Esta correção autoriza especificamente a
troca da família da base para Alpine, preservando o gate estrito da S39.

A referência aprovada para os dois estágios é:

```text
python:3.13-alpine3.23@sha256:9fdbf2e3e82628351513560b121e2ee6ce31cac212be9e070c5a5e2769fb5e76
```

O digest é o índice oficial multi-arquitetura; a prova e a publicação continuam
obrigatoriamente em `linux/amd64`. Em 03/08/2026, a base pinada foi verificada
com Trivy `v0.70.0`, `HIGH,CRITICAL`, `ignore-unfixed=false`: `0` achados.

## 2. Relação com o contrato original

A task S39 permanece imutável. Esta correção substitui apenas:

- a limitação da §2.4 que restringia a mudança a um pin de Debian slim;
- a fronteira da §7 quanto aos três espelhos documentais/validadores listados
  na §4 abaixo.

Todos os demais objetivos, invariantes, gates, negativos, proibições, regra de
um único commit/push e critérios de aceite da S39 continuam vigentes.

S39 **não está aceita** e esta correção não cria S40.

## 3. Alteração autorizada no Dockerfile

1. Usar a referência Alpine pinada acima em ambas as instruções `FROM`.
2. Preservar Python `3.13`, `uv==0.9.17`, `pyproject.toml`, `uv.lock`, código do
   runtime, dependências Python e contrato de execução.
3. Adaptar somente a criação de grupo/usuário para comandos nativos do Alpine,
   mantendo o runtime final em `10001:10001`.
4. Não executar `apk upgrade`.
5. Não instalar shell, compilador ou toolchain no estágio final.
6. Pacotes `apk` adicionais só podem ser incluídos se a construção pelo lock ou
   o probe demonstrar necessidade concreta. Cada pacote deve ser justificado no
   relatório, instalado no menor estágio possível e permanecer compatível com
   scan final em zero `HIGH/CRITICAL`.
7. Se wheel musllinux, build pelo lock, testes, healthcheck ou runtime não forem
   compatíveis, interromper fail-closed; não alterar dependência ou código para
   contornar o problema nesta correção.

## 4. Ratificação da fronteira transitiva

Ficam ratificadas como consequência mecânica necessária do sexto workflow as
alterações já feitas em:

```text
.github/workflows/README.md
tools/releases/validate_release_workflow.py
tools/releases/tests/test_release_publication.py
```

Elas podem somente registrar/validar `publish-release-control.yml` no inventário
canônico. Não podem alterar semântica de candidato, release global, deploy,
rollback, state machine ou produção.

Não há autorização para qualquer outro arquivo fora da fronteira original e
dos três caminhos acima. O executor deve registrar que a divergência de
fronteira foi ratificada, não ocultá-la.

## 5. Retomada obrigatória

Partir do patch local já existente. Não descartar nem reimplementar o trabalho
verde. Antes de editar:

```bash
cd /home/gregorio/git/baronesa/emporio
git status --short --branch
git rev-parse HEAD
git rev-parse origin/main
git rev-list --left-right --count origin/main...HEAD
git ls-remote origin refs/heads/main
sha256sum docs/infrastructure/deployment/implementation/slices/S39-workflow-imagem-release-control.task.md
sha256sum docs/infrastructure/deployment/implementation/slices/S39-workflow-imagem-release-control.correction-01.md
git diff --check
```

O estado esperado é:

- `HEAD` contém a task e esta correção documental, ainda à frente do remoto;
- o remoto permanece no snapshot registrado pela S39;
- implementação S39 local modificada/não rastreada e nada staged;
- relatório S39 local, não staged e não commitado.

Qualquer alteração remota ou resíduo alheio interrompe a retomada.

## 6. Provas causais adicionais

Além dos testes já verdes e de toda a matriz da S39, provar:

1. os dois `FROM` são exatamente a referência Alpine aprovada e idêntica;
2. referência sem digest, digest divergente, Debian slim ou outro tag/digest são
   rejeitados pelo validador;
3. o build `linux/amd64` usa somente o lock existente;
4. usuário final é exatamente `10001:10001`;
5. healthcheck e as três labels OCI permanecem válidos;
6. teste funcional mínimo do processo real do container fica verde;
7. Trivy `v0.70.0` da **imagem final construída** retorna exit `0`, com zero
   vulnerabilidades `HIGH` e zero `CRITICAL`, mantendo
   `ignore-unfixed=false`;
8. não existe segundo build entre scan e futuro push;
9. limpeza remove somente tag/imagem/container temporários da prova, sem volume
   ou rede residual e sem login no GHCR.

Registrar separadamente os resultados da base pinada e da imagem final. O scan
limpo da base não substitui o scan obrigatório da imagem final.

## 7. Gates, stage, commit e remoto

Reexecutar integralmente a §8 da S39: testes direcionados, `release_control/tests`,
17 validadores, oito suítes canônicas, secret scan e prova Docker. Todos os exits
devem ser capturados individualmente e iguais a zero; scanner de segredo deve
terminar `clean`, `unsupported=0`.

Antes do secret scan final, stagear somente o patch pretendido para que os novos
arquivos da S39 estejam incluídos na superfície rastreada. Se o scan falhar,
retirar o stage e parar. O relatório continua sempre fora do stage.

Somente com todos os gates verdes:

1. reconfirmar que `origin/main` e `git ls-remote` não se moveram;
2. confirmar o diff exato e a lista de arquivos staged;
3. criar um único commit causal da implementação S39;
4. executar um único push normal fast-forward de `main`;
5. observar CI e Publish Candidate do mesmo SHA até terminais verdes;
6. validar seus jobs e artifacts;
7. provar que `Publish Release Control Image` existe no default branch, mas tem
   zero runs;
8. provar variável `RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS` ausente, pacote
   GHCR do release control ausente, `v0.1.0` intacta e zero deploy/rollback;
9. não acessar nem mutar a VPS.

Se o remoto mover, qualquer gate falhar ou o push não for fast-forward, parar
sem rebase, amend, force, retry ou segundo push.

## 8. Relatório e terminal

Continuar o relatório existente, sem criar relatório paralelo. Acrescentar:

- SHA-256 desta correção;
- decisão Alpine e digest exato;
- diff final e ratificação dos três arquivos transitivos;
- testes causais, matriz completa e exits;
- scans separados da base e da imagem final;
- prova Docker e limpeza dirigida;
- commit/push e runs remotos, se alcançados;
- todos os negativos da S39.

O executor não aceita S39 nem cria a próxima slice. Em sucesso, substituir o
terminal anterior e terminar exatamente com:

```text
IN_PROGRESS — workflow da imagem do release control pronto; aguardando aceite e autorização de publicação
```

Na primeira causa não resolvida, terminar exatamente com:

```text
BLOCKED — correction-01 interrompida fail-closed na primeira causa técnica
```

## 9. Critério de conclusão desta correção

A correction-01 termina somente quando a S39 original estiver integralmente
cumprida sobre a base Alpine aprovada, com scan estrito da imagem final em zero,
um commit/push fast-forward, CI e candidato verdes, workflow nunca executado,
imagem nunca publicada, relatório contínuo completo e nenhuma mutação de
release, deploy, rollback ou VPS.
