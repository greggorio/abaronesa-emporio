# S46 — correction-07: root compatível, diagnóstico por estágio e GHCR explícito

> **Data:** 05/08/2026
> **Estado:** `AUTHORIZED`
> **Natureza:** retomada incremental no rehearsal; checkpoints anteriores preservados
> **Contrato principal:** `S46-primeiro-deploy-acompanhado-v0.1.1.task.md`
> **Checkpoint técnico:** `d43d0d12338fc324c2caa054b211c2bc0f6bb006`
> **Relatório contínuo:** `S46-primeiro-deploy-acompanhado-v0.1.1.report.md`

## 1. Aceite do checkpoint, não da S46

O relatório com SHA-256
`d46e4944d9f25608b0cd2e15086160cf7e3f14cec8603d0e857516a100e72f1f`
é aceito como checkpoint factual da correction-06. O run `31015462556`
preservou trust e cleanup, falhou antes do CLI e não tocou SSH, VPS ou produção.
**S46 continua não aceita.**

Não repetir:

```text
planner first-install                 corrigido em d43d0d1…
prova com release.json real           verde
commit/push d43d0d1…                  aceitos
CI 31013912579                        success, 13/13
Publish Candidate 31014987937         success, 11/11
control root d43d0d1…                 instalado e íntegro
rehearsals 31011886682/31015462556    históricos falhos preservados
implementação SSH compartilhada       aceita
deploy/rollback comercial             3 falhos históricos / 0
credencial e probes da correction-05  ainda não executados
```

## 2. Causa exata reproduzida pelo orquestrador

Não é SSH, GHCR, Docker, release ou planner de lineage.

O rehearsal cria seu deploy root com:

```text
tempfile.mkdtemp(..., dir=RUNNER_TEMP)
```

No GitHub-hosted runner, `RUNNER_TEMP` é irmão do checkout. O planner S18 aceita
output somente sob o próprio workspace resolvido ou sob `/tmp`. Assim
`deployment_plan._validate_output_path()` executa `_is_relative_to()`, obtém
falso para workspace e `/tmp` e lança `DeploymentPlanError(UNSAFE_PATH)`.

A reprodução local instrumentada, sem Docker, SSH ou novo dispatch, observou a
cadeia literal:

```text
deployment_plan._validate_output_path -> DeploymentPlanError
deployment_plan.generate_bundle       -> DeploymentPlanError
rehearsal                              -> REHEARSAL_FAILED
```

O mesmo bundle foi gerado e validado quando o root ficou sob o checkout. Esse
root também passou `deployment_cli._validate_root()`. Portanto o reparo não
altera os guards: escolhe a interseção já permitida por ambos.

O código ainda reduz `DeploymentPlanError` inesperado a `REHEARSAL_FAILED`,
apagando estágio e causa. Essa perda de observabilidade também é causal e será
corrigida antes de outro run.

## 3. Reparo mínimo do rehearsal

Alterar somente o necessário para:

1. criar o root efêmero diretamente sob o checkout confiável `ROOT`, com prefixo
   fechado, modo `0700`, owner corrente e ausência prévia;
2. provar que `root.parent.resolve() == ROOT.resolve()` e que nenhum componente
   é symlink ou gravável por grupo/outros;
3. continuar usando nomes por run, projeto Docker dirigido e cleanup pelo path
   exato; nunca incluir o root no Git;
4. não relaxar `_validate_output_path()` nem `_validate_root()`;
5. preservar artifact/output fora desse root quando necessário;
6. converter falhas conhecidas em códigos sanitizados, no mínimo:

```text
PREPARE_ROOT_FAILED
BUNDLE_GENERATION_FAILED
DEPLOYMENT_CLI_FAILED
TRANSACTION_EVIDENCE_FAILED
CLEANUP_INCOMPLETE
```

7. registrar no receipt apenas `failedStage` de enum fechado e `errorCode`, sem
   traceback, path absoluto, stdout/stderr bruto ou segredo;
8. manter o outcome coerente com o receipt e os bindings existentes.

Testes causais devem chamar os dois guards reais sobre o mesmo root efêmero no
workspace e provar mutantes para `RUNNER_TEMP`, `/tmp`, symlink, mode inseguro,
root fora do checkout, exceção de planner, CLI terminal e cleanup.

## 4. Autenticação GHCR explícita antes do próximo run

Embora não tenha causado `31015462556`, há uma incompatibilidade material já
observável que não deve ser descoberta no run seguinte.

O workflow faz login em `/home/runner/.docker/config.json`, mas
`production_adapter.SubprocessRunner` executa Docker com `MINIMUM_ENV` sem
`HOME` ou `DOCKER_CONFIG`. Na VPS, a credencial preexistente está sob `/root`,
enquanto o CLI roda como `deploy-emporio`. O contrato S21 já atribui a
autenticação GHCR à VPS e o Gate B da S37 já previa esse preparo, que não foi
materializado para o usuário dedicado.

Tornar o caminho determinístico:

1. resolver o home pelo usuário efetivo do processo, usando a identidade do SO,
   nunca uma variável recebida;
2. derivar `<home>/.docker` e validar home/diretório/config como paths reais,
   owner corrente, sem symlink e sem escrita por grupo/outros;
3. passar `DOCKER_CONFIG=<home>/.docker` somente aos subprocessos Docker;
4. manter Curl e demais comandos no ambiente mínimo atual;
5. falhar antes do primeiro pull com código sanitizado estável quando o config
   estiver ausente ou inseguro;
6. nunca ler, parsear, imprimir, hashear ou incluir o conteúdo do config em
   artifact.

No runner, o login já existente deve alimentar exatamente esse path. Na VPS,
antes de qualquer operação comercial, usar a rota bootstrap root somente para:

- confirmar opacamente a credencial preexistente funcional;
- instalar uma cópia opaca em `/home/deploy-emporio/.docker/config.json`, com
  diretório `0700`, arquivo `0600` e owner dedicado, sem exibir conteúdo;
- provar como `deploy-emporio`, por `docker manifest inspect`, os seis digests
  de `v0.1.1`, sem pull;
- remover a cópia se qualquer prova falhar, preservando o original root.

O adapter continua limitado a inspect/pull/compose e não ganha push, login ou
logout. Não modificar packages, visibilidade ou token remoto.

## 5. Matriz incremental

Escopo técnico esperado:

```text
tools/deploy/deployment_engine_rehearsal.py
tools/deploy/production_adapter.py
testes causais e validadores diretamente correspondentes
espelho documental estritamente necessário
```

Não alterar planner, executor, transport SSH, release, runtime, migrations,
Compose canônico, control plane ou workflows comerciais.

Antes do commit, exigir:

1. testes do rehearsal/engine workflow;
2. testes do production adapter e CLI real/falso relevantes;
3. validadores de engine workflow, adapter, CLI e control-root package;
4. prova local dos dois guards sobre root efêmero sob `ROOT`;
5. prova de códigos/estágios para cada mutante sem emitir detalhe interno;
6. prova de `DOCKER_CONFIG` explícito para Docker e ausente para Curl;
7. `git diff --check` e secret scan do patch/stage com `unsupported=0`.

Não repetir oito suítes locais, release validation ou candidate integration já
aceitos. A CI remota continua sendo o gate amplo após o push.

Somente tudo verde:

1. um commit técnico normal sobre o commit documental desta correction;
2. push fast-forward, sem amend/rebase/force;
3. CI verde no novo SHA;
4. Publish Candidate automático não é pré-condição;
5. como `production_adapter.py` pertence ao control root, reconstruir duas
   vezes, provar Python 3.10/network-blocked e rotacionar somente esse pacote;
6. verify/capabilities diretos verdes no SHA terminal.

## 6. Retomada no ponto exato

Depois do código e control root verdes:

1. executar a prova opaca GHCR como `deploy-emporio` descrita na seção 4;
2. criar um novo dispatch attempt 1 de `verify-deployment-engine.yml`; não fazer
   rerun dos runs históricos;
3. exigir trust/rehearse/outcome verdes, primeira instalação direta de v0.1.1,
   backup, migrations, sete serviços, replay e cleanup zero;
4. validar artifacts, sidecars, `failedStage=null` e `errorCode=null`;
5. provar deploy/rollback ainda em 3/0.

Somente então continuar nos itens ainda não executados da correction-05:

1. configurar fingerprint e private key SSH;
2. probe com duas chaves;
3. remover somente a chave antiga;
4. probe apenas com a chave nova;
5. cleanup dos materiais locais;
6. revalidar readiness/current/elegibilidade/capacidade;
7. exatamente uma operação comercial para v0.1.1.

Nenhum checkpoint verde e não afetado deve ser repetido.

## 7. Autoridade e limites

A mensagem humana `prossiga` autoriza este reparo causal, o preparo opaco e
reversível da credencial GHCR para o usuário dedicado e um novo rehearsal. As
autorizações não consumidas da S46/correction-05 continuam vigentes para
control root, configuração SSH, dois probes e uma única operação comercial.

Continuam proibidos:

- abrir ou registrar Docker config, token, PEM, chave, JWT ou env protegido;
- relaxar os guards de path ou executar root fora do checkout no rehearsal;
- instalar v0.1.0, alterar release/manifest ou publicar imagem do control plane;
- segundo deploy, rerun de deploy, rollback, restore ou SQL manual;
- intervenção em outro tenant, reboot ou update;
- aceitar S46 ou criar S47 pelo executor.

## 8. Relatório e terminais

Acrescentar `Retomada correction-07` ao relatório contínuo existente,
mantendo-o não rastreado, fora do stage e sem segredos. Separar checkpoints
herdados, causa reproduzida, patch, testes, CI/control root, GHCR opaco,
rehearsal, probes e eventual operação comercial.

Em sucesso:

```text
IN_PROGRESS — primeiro deploy de v0.1.1 concluído e reconciliado; aguardando aceite e Gate E de recuperação
```

Em falha:

```text
BLOCKED — S46 correction-07 interrompida fail-closed no checkpoint corrente
```
