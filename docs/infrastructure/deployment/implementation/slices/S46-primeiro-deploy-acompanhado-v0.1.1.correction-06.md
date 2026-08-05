# S46 — correction-06: primeira instalação direta e retomada do checkpoint

> **Data:** 05/08/2026
> **Estado:** `AUTHORIZED`
> **Natureza:** reparo causal incremental; não reinicia a S46 nem a correction-05
> **Contrato principal:** `S46-primeiro-deploy-acompanhado-v0.1.1.task.md`
> **Checkpoint técnico:** `8be7c1ff58b640964bea836d5f886b15c2cb8ee3`
> **Relatório contínuo:** `S46-primeiro-deploy-acompanhado-v0.1.1.report.md`
> **Release comercial:** `v0.1.1`, imutável

## 1. Veredito e alcance

O relatório com SHA-256
`2cf2205f6faf4aa520145281c1947b4f563f62eb08d116dbe38e93a0802aa89e`
é aceito como checkpoint factual da correction-05. O run isolado
`31011886682` parou antes de pull, bundle instalado, backup, migration,
container ou qualquer mutação comercial. **S46 continua não aceita.**

Esta correction não manda repetir o trabalho já concluído. Ficam preservados:

```text
implementação SSH compartilhada          aceita em 8be7c1f…
testes SSH e workflow                     aceitos
workflow verify-deployment-engine.yml     ativo e provado até o planner
commit/push 8be7c1f…                      aceitos
CI 31009688779                            success, 13/13
Publish Candidate 31010684163             success, 11/11
control root 8be7c1f…                     instalado e íntegro
release v0.1.1                            válida e inalterada
run 31011886682                           histórico falho preservado
deploy/rollback                           3 falhos históricos / 0
control plane                             live/ready 200/200
current comercial                         ausente, 404
duas chaves SSH                           preservadas
variável de fingerprint                   ainda ausente
operação comercial nesta retomada         zero
```

Somente a compatibilidade do planner com a primeira instalação direta e as
etapas posteriores ainda pendentes serão executadas.

## 2. Causa causal confrontada

O erro não está no manifesto `v0.1.1` e não deve ser contornado instalando
`v0.1.0` no ensaio.

Os dois manifests remotos foram revalidados em leitura:

```text
v0.1.0  release id 364130074  previousRelease=null
v0.1.1  release id 365219520  previousRelease=v0.1.0
ambas    três assets, sidecar válido, seis componentes
```

O runtime já implementa o contrato correto: sem instalação corrente, a release
SemVer mais recente pode ser a primeira instalação quando sua cadeia histórica
até `previousRelease=null` é completa, crescente, sem ciclo e com migrations
prefixais. Por isso o plano HTTP retorna para `v0.1.1`:

```text
firstInstallation=true
sourceRelease=null
seis UPDATE com currentDigest=null
backupRequired=true
migrationRequired=true
```

O planner operacional ainda carrega a regra anterior em dois lugares:

1. `_validate_chain()` rejeita qualquer primeira instalação cujo manifesto
   tenha `previousRelease` não nulo;
2. `_validate_plan_bundle_coherence()` exige que `sourceRelease` seja igual ao
   `previousRelease` histórico, inclusive quando `sourceRelease` corretamente
   é nulo na primeira instalação.

Isso bloquearia tanto o ensaio quanto o deploy comercial real. Semear v0.1.0
criaria um upgrade artificial, deixaria de provar a primeira instalação da VPS
vazia e não é uma correção aceitável.

## 3. Reparo permitido

### 3.1 Semântica do planner

Alterar `tools/deploy/deployment_plan.py` para separar explicitamente:

- `manifest.previousRelease`: lineage histórica da release publicada;
- `plan.sourceRelease`: release efetivamente instalada no ambiente.

Regras obrigatórias:

1. com `current=None`, aceitar uma release global válida mesmo quando seu
   `previousRelease` histórico não é nulo;
2. nesse caso produzir `firstInstallation=true` e `sourceRelease=null`;
3. manter seis componentes `UPDATE`, os dois inventários completos como
   migrations pendentes e `backupRequired=true`;
4. na validação do bundle, não comparar `sourceRelease=null` com o predecessor
   histórico da primeira instalação;
5. com current existente, preservar sem relaxamento a exigência de SemVer
   crescente e `target.previousRelease == current.release`;
6. preservar schema, sidecars, validação target/current, migrations prefixais,
   atomicidade e todos os demais códigos fail-closed.

Não alterar o manifesto, a release, o runtime de elegibilidade ou o schema para
mascarar o defeito.

### 3.2 Testes causais antes de commit

Adicionar ou ajustar testes para provar, no mínimo:

- primeira instalação com target válido e predecessor histórico não nulo;
- `firstInstallation=true`, `sourceRelease=null`, seis updates, dois bancos,
  migration e backup requeridos;
- `generate_bundle()` e `validate_bundle()` verdes nesse caso;
- mutação do bundle que invente `sourceRelease` na primeira instalação falha;
- update continua recusando salto, predecessor divergente, igualdade e
  downgrade;
- current e current-manifest continuam obrigatoriamente pareados;
- o ensaio continua chamando o planner com `current=None`; não baixar, instalar
  ou sintetizar v0.1.0.

Antes do push, executar uma reprodução dirigida usando o `release.json` real de
`v0.1.1` baixado em `mktemp -d`: gerar e validar o bundle com current ausente,
confirmar o plano acima e remover o temporário nominalmente. Essa prova não usa
Docker, SSH, VPS, secret ou POST.

## 4. Matriz incremental e publicação técnica

Não repetir as oito suítes locais e as 30 validações já aceitas sem relação com
o patch. Executar somente:

1. testes de `deployment_plan`;
2. testes de `deployment_executor`, `deployment_transport` e do rehearsal que
   consomem o plano;
3. validadores de deployment plan, executor, transport e engine workflow;
4. reprodução com o asset imutável real;
5. `git diff --check`;
6. secret scan do patch/stage com `unsupported=0`.

Somente tudo verde:

1. criar um único commit causal normal sobre o commit documental desta
   correction, preservando `8be7c1f…` como checkpoint técnico herdado;
2. push fast-forward, sem amend, rebase ou force;
3. exigir CI verde no novo SHA;
4. o Publish Candidate automático pode concluir em paralelo, mas não é
   pré-condição do ensaio nem substitui a release `v0.1.1` imutável;
5. reconstruir duas vezes somente o control root, porque
   `deployment_plan.py` pertence ao caminho operacional;
6. exigir pacote byte-idêntico, prova Python 3.10/network-blocked e rotação
   transacional para o novo SHA;
7. provar `verify` e `capabilities` diretos como `deploy-emporio`.

Não reconstruir SSH, não publicar imagem do control plane, não criar release e
não repetir rotação de credenciais nesta seção.

## 5. Retomada exata no checkpoint falho

Depois do novo control root verde:

1. criar **um novo dispatch** de `verify-deployment-engine.yml`, attempt 1, no
   SHA corretivo; não fazer rerun do run histórico `31011886682`;
2. validar trust, rehearsal e outcome, artifacts e sidecars;
3. exigir a primeira instalação direta de `v0.1.1`, não um upgrade artificial;
4. exigir journal terminal `SUCCEEDED`, backup real dos dois bancos, migrations
   completas, sete serviços saudáveis, `current -> releases/v0.1.1`, previous
   ausente, replay sem efeito e cleanup zero;
5. provar que deploy/rollback comerciais permanecem 3/0.

Essa é a única repetição autorizada da prova que falhou. Não repetir CI antigo,
Publish Candidate antigo, implementação SSH, instalação do workflow ou etapas
34–37 do relatório.

Se o rehearsal corrigido for verde, continuar diretamente no primeiro item
ainda não executado da correction-05, seção 6:

1. criar `PRODUCTION_SSH_PUBLIC_KEY_SHA256` com a fingerprint já preservada;
2. atualizar `PRODUCTION_SSH_PRIVATE_KEY` por stdin com a mesma chave;
3. probe com as duas chaves;
4. remover somente a chave antiga;
5. probe somente com a chave nova;
6. destruir os materiais locais conforme o contrato;
7. revalidar readiness 200, current 404, elegibilidade, capacidade e ausência
   de efeitos comerciais;
8. somente então criar exatamente uma operação comercial para `v0.1.1` e
   acompanhá-la até o terminal.

Não repetir qualquer etapa que já esteja verde e não tenha sido invalidada pela
alteração do planner.

## 6. Limites

Continuam proibidos:

- instalar v0.1.0 na VPS ou no rehearsal para contornar a primeira instalação;
- nova release, alteração de manifesto ou novo candidato como alvo comercial;
- segunda operação comercial, rerun de deploy ou nova idempotency key;
- patch pós-POST, SQL manual, alteração retroativa das três operações falhas;
- rollback, restore, reboot, update ou intervenção em outro tenant;
- nova chave SSH sem prova de comprometimento da atual;
- imagem nova do release control/control plane;
- aceitar S46 ou criar S47 pelo executor.

Falha antes do POST encerra no checkpoint exato e preserva todos os anteriores.
Falha depois do POST preserva evidência e não autoriza correção ou repetição.

## 7. Autoridade cumulativa

A mensagem humana `prossiga com a correção` autoriza este reparo causal do
planner e um novo rehearsal no SHA corretivo. As autorizações de commit, push,
rotação do control root, atualização segura da credencial, dois probes e uma
única operação comercial já concedidas na correction-05 permanecem vigentes
somente para as etapas ainda não executadas.

Não pedir ao usuário chave, fingerprint, secret, ID, release, digest, janela ou
nova autorização para essas ações.

## 8. Relatório e terminal

Acrescentar `Retomada correction-06` ao mesmo relatório contínuo. Não criar
outro relatório. Mantê-lo não rastreado, fora do stage e sem segredos.

Registrar separadamente:

- checkpoints herdados e explicitamente não repetidos;
- patch causal e testes incrementais;
- reprodução do bundle real;
- commit, CI e novo control root;
- novo rehearsal e seus artifacts;
- somente depois, variável/secret, probes, rotação final da chave e deploy;
- efeitos, negativos, cleanup e preservação dos demais tenants.

Em sucesso, terminar exatamente:

```text
IN_PROGRESS — primeiro deploy de v0.1.1 concluído e reconciliado; aguardando aceite e Gate E de recuperação
```

Em qualquer falha:

```text
BLOCKED — S46 correction-06 interrompida fail-closed no checkpoint corrente
```

## 9. Critérios de aceite

A S46 somente poderá ser aceita quando houver:

- planner e bundle provando primeira instalação direta de `v0.1.1` com
  `sourceRelease=null`, sem simular v0.1.0 instalada;
- rehearsal corrigido verde e cleanup zero;
- identidade SSH final provada pelo materializador compartilhado;
- exatamente quatro operações comerciais: três falhas históricas preservadas e
  uma `SUCCEEDED`;
- `v0.1.1` corrente e reconciliada, backup, migrations, sete serviços,
  HTTPS/JWKS/UI e preservação diferencial;
- zero rollback/restore.
