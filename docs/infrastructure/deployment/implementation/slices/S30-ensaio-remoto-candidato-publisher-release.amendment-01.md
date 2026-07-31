# Emenda 01 — autorização do primeiro commit e push pelo executor

> **Estado:** `APPROVED` pelo orquestrador em 31/07/2026  
> **Contrato-base:** `S30-ensaio-remoto-candidato-publisher-release.task.md`  
> **Escopo:** substituir a regra de push manual do usuário pela autorização
> controlada abaixo

## 1. Decisão

Fica autorizado o executor da S30 a criar o primeiro commit e fazer o primeiro
push do workspace para o remote exato:

```text
git@github.com:greggorio/abaronesa-emporio.git
```

O destino obrigatório é a branch `main`, usando somente:

```bash
git add -A
git commit -m "chore: establish initial emporio baseline"
git push --set-upstream origin main
```

Esta emenda prevalece sobre as instruções anteriores da S29 e da S30 que
reservavam o primeiro push ao usuário. Ela não autoriza push forçado, tag,
release, deploy, rollback ou alteração de produção.

## 2. Gates antes do commit

O executor só pode executar os comandos acima depois de:

1. confirmar CWD `/home/gregorio/git/baronesa/emporio`;
2. confirmar `git remote get-url origin` exatamente igual ao remote acima;
3. confirmar branch local `main` e repositório remoto vazio ou sem divergência;
4. executar os validadores S30 e o scanner canônico com exit 0,
   `secret-scan:clean` e `unsupported=0`;
5. revisar a lista candidata e a lista staged, rejeitando `.env`, chaves,
   certificados, caches, `.venv`, coverage, bytecode, uploads, `node_modules`
   e qualquer resíduo ignorado;
6. executar `git diff --cached --check` e verificar que não há valor secreto,
   credencial ou arquivo fora da fronteira prevista;
7. confirmar que o nome/email Git local já estão configurados; não inventar
   identidade nem alterar configuração global.

Se qualquer gate falhar, não criar commit nem fazer push; registrar o bloqueio
no relatório.

## 3. Gates do push e pós-push

- Não usar `--force`, `--force-with-lease`, `--no-verify`, `--tags`, outro
  remote ou outra branch.
- Depois do commit, registrar SHA, lista de arquivos commitados e saída
  sanitizada; não registrar tokens ou credenciais.
- Depois do push, observar CI e `publish-candidate.yml` pelo GitHub, cruzando
  commit, run, candidato, manifesto, digest, provenance e attestation.
- Se a sessão não tiver `read:packages`, parar a verificação GHCR e registrar
  o bloqueio; não reautenticar nem criar credenciais sem autorização separada.
- Não publicar a release global nesta execução: `candidate_id`,
  `version_bump`, descrição e changelog ainda precisam de aprovação explícita.
- Não executar `publish-release.yml` diretamente, deploy, rollback, SSH, VPS,
  Docker, produção ou cleanup destrutivo.

## 4. Prompt vigente

```text
Execute a S30 com a emenda amendment-01 em /home/gregorio/git/baronesa/emporio.
Leia o HANDOFF_ORQUESTRADOR, tracker, task S30, esta emenda, arquitetura,
relatórios S11–S30, RUNTIME_PUBLISHER, UI_PUBLISHER, RELEASES e os cinco
workflows.

A autorização vigente inclui criar o primeiro commit e fazer o primeiro push.
Antes disso, execute todos os validadores S30, o scanner canônico e a revisão
da lista candidata. Confirme que o remote origin é exatamente
git@github.com:greggorio/abaronesa-emporio.git, que a branch é main, que o
workspace não contém segredos/resíduos proibidos e que git diff --cached --check
passa após git add -A.

Se todos os gates passarem, execute exatamente:
git add -A
git commit -m "chore: establish initial emporio baseline"
git push --set-upstream origin main

Não use force, tags, outra branch/remote, no-verify, git init, alteração de
remote ou configuração global de identidade. Pare antes do commit/push se
qualquer gate falhar ou se o remote não estiver vazio/compatível.

Depois do push, observe CI e publish-candidate.yml no GitHub, cruze o SHA,
candidate, manifesto, digests, provenance e attestation e registre tudo sem
tokens. Se faltar read:packages, registre o bloqueio e não crie credenciais.

Não publique release global: aguarde aprovação separada de candidate_id,
version_bump, descrição e changelog. Não execute publish-release.yml direto,
deploy, rollback, SSH, VPS, Docker, produção, cleanup destrutivo ou S31.
Altere somente o relatório S30 e termine com:
IN_PROGRESS — aguardando revisão do orquestrador
```
