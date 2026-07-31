# S25 — contrato seguro de rollback comercial

> Estado: PLANNED
> Tipo: contrato offline, segurança e política operacional
> Executor previsto: CLI
> Diretório obrigatório: /home/gregorio/git/baronesa/emporio
> Dependências: S01 a S24 ACCEPTED
> Relatório de saída: S25-contrato-seguro-rollback-comercial.report.md

## Instrução para delegação

Execute integralmente esta task. Leia, nesta ordem:

1. esta task inteira;
2. a seção terminal do relatório S24;
3. o roadmap residual do HANDOFF_ORQUESTRADOR.md;
4. CONTRATO_API_ESTADOS_SEGURANCA.md;
5. RUNTIME_DEPLOYER.md;
6. deployer.openapi.yml, state-machines.yml e security-matrix.yml;
7. PLANO_IMPLANTACAO.md, TRANSACAO_IMPLANTACAO.md, OPERACAO_IMPLANTACAO.md;
8. WORKFLOW_IMPLANTACAO.md, RELEASES.md e schemas versionados;
9. os testes e validadores locais de deployment.

Esta slice fecha o contrato de rollback comercial e seus artefatos offline.
Não implementa rollback e não habilita sua operação.
Não altere esta task, o tracker ou crie S26.

## 1. Resultado observável

Fechar, sem decisões abertas, a elegibilidade da release anterior,
compatibilidade de migrations, restore de banco, identidade e retenção de
backups, uploads, sessão WhatsApp, diferença entre compensação forward e
rollback solicitado, API, idempotência, lock, estados e recovery.

O contrato não pode prometer downgrade seguro por mera troca das seis imagens.

## 2. Decisões fechadas

### Elegibilidade e alvo

- somente instalação atual reconciliada pode iniciar rollback;
- o alvo é release global imutável, publicada, anterior e da mesma cadeia;
- salto, predecessor divergente, candidato e release não implantável são
  inelegíveis;
- elegibilidade é calculada e anunciada pelo servidor;
- operador não escolhe componente, digest, tag, imagem ou migration;
- rollback solicitado é explícito; compensação forward nunca vira rollback
  comercial.

### Migrations, restore e backups

- qualquer migration aplicada desde o alvo exige restore, salvo prova explícita
  de reversibilidade integral;
- restore exige backup anterior à release atual, compatível e verificado;
- backup ausente, expirado, parcial ou com hash divergente bloqueia;
- imagens revertidas sem restore verificado não significam rollback concluído;
- databaseRestoreRequired permanece verdadeiro até evidência terminal.

O registro canônico do backup contém backupId, sourceRelease,
sourceStateSha256, bancos erp e website, artifactSha256, createdAt e
expiresAt. Não contém path, credencial, dump ou URL privada.

- retenção mínima: 365 dias, sem renovação silenciosa;
- uploads não são apagados nem restaurados implicitamente;
- restore de upload exige evidência e operação contratada;
- sessão WhatsApp não é restaurada automaticamente;
- incompatibilidade exige estado seguro e reemparelhamento manual.

### API futura

~~~
POST /api/deployment-control/v1/rollbacks
GET  /api/deployment-control/v1/rollbacks/{operationId}
~~~

- request exato: release e reason;
- reason entre 10 e 1000 caracteres;
- header Idempotency-Key com prefixo deployer-rollback-<UUID v4>;
- scope futuro deployment:rollback e operationType rollback;
- capability permanece ausente nesta S25;
- rota continua reservada e indisponível até S26;
- lock concorrente retorna PRODUCTION_OPERATION_ACTIVE;
- replay idêntico retorna a mesma operação;
- request divergente com a mesma chave retorna IDEMPOTENCY_CONFLICT;
- não existe retry automático após rede, resposta inválida ou conflito.

### Lock, estados e recovery

Rollback e deployment forward compartilham um lock global e uma operação
ativa máxima. O estado futuro é:

~~~
QUEUED, PRECHECKING, RESTORING, SWITCHING, VERIFYING, SUCCEEDED,
ROLLING_BACK, ROLLED_BACK, FAILED, UNCERTAIN
~~~

- PRECHECKING valida current, cadeia, backup, migrations e lock;
- RESTORING existe somente quando restore é obrigatório;
- SWITCHING ocorre somente depois dos prechecks;
- VERIFYING exige evidência do alvo, banco, links e seis componentes;
- SUCCEEDED exige estado reconciliado no alvo;
- falha antes da troca termina FAILED;
- falha após side effect só termina ROLLED_BACK com evidência completa;
- incerteza de banco, links, volume, operação ou journal termina UNCERTAIN e
  bloqueia nova operação até reconciliação humana;
- estados terminais não repetem side effects.

## 3. Artefatos autorizados

Criar:

~~~
docs/infrastructure/deployment/release-control/ROLLBACK_COMERCIAL.md
docs/infrastructure/deployment/release-control/api/rollback.openapi.yml
docs/infrastructure/deployment/release-control/contracts/rollback-state-machine.yml
docs/infrastructure/deployment/release-control/contracts/rollback-security.yml
tools/deploy/validate_rollback_contract.py
tools/deploy/tests/test_rollback_contract.py
docs/infrastructure/deployment/implementation/slices/S25-contrato-seguro-rollback-comercial.report.md
~~~

Alterar somente para referências, sem ativação:

~~~
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md
docs/infrastructure/deployment/release-control/RUNTIME_DEPLOYER.md
~~~

Não alterar deployer.openapi.yml, state-machines.yml ou security-matrix.yml;
eles continuam descrevendo a superfície ativa. Os novos artefatos devem
declarar que são contrato futuro e não são consumidos pelo runtime atual.

## 4. Fora da fronteira

Não alterar backend, release_control, frontend, publisher S17, capabilities,
workflows, Docker, Compose, Nginx, gateway, VPS, DNS, TLS, produção,
secrets, .env, chaves, tokens, schemas ativos, ops/deploy, adapters, CLI S20,
task, tracker ou S01–S24. Não acessar GitHub, GHCR, SSH, rede, containers ou
volumes reais. Não ativar deployment:rollback.

## 5. Testes causais

Matar mutantes de alvo e predecessor, current incerto, migrations não
reversíveis, backup ausente/expirado/hash divergente, body extra, reason
inválido, scope/capability indevidos, idempotência, lock, transições
impossíveis, sucesso sem restore, UNCERTAIN como sucesso, restore implícito
de upload/sessão WhatsApp e confusão entre compensação e rollback.

Validar consistência entre documento humano e os três artefatos
machine-readable.

## 6. Matriz terminal

~~~bash
cd /home/gregorio/git/baronesa/emporio
python3 tools/deploy/validate_rollback_contract.py
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools/deploy/tests/test_rollback_contract.py -v
python3 tools/releases/release_control_contract.py validate
git diff --check
git rev-parse --verify HEAD
git tag --list
git reflog show --all
find .github/workflows -maxdepth 1 -type f -printf "%f\n" | sort
find . -path "./.git" -prune -o \( -name ".venv" -o -name ".coverage" -o -name ".pytest_cache" -o -name ".ruff_cache" -o -name ".mypy_cache" -o -name "__pycache__" -o -name "*.pyc" \) -print
~~~

Todos os comandos devem terminar em exit 0, exceto git rev-parse em exit 128.
Busca de resíduos deve ser vazia; não deixar rede, segredo, container, volume
ou artefato temporário.

## 7. Aceite e bloqueio

Aceitar somente com decisões fechadas, artefatos consistentes, testes causais
verdes, ausência de ativação runtime/UI/capability e matriz terminal verde.
Parar se for necessário assumir restore por troca de imagem, decidir migration
sem evidência, alterar runtime atual, habilitar capability/UI ou misturar
compensação com rollback solicitado.

O relatório deve terminar com:

~~~
IN_PROGRESS — aguardando revisão do orquestrador
~~~

Não declarar ACCEPTED e não criar S26.

## 8. Formato da resposta

Responder somente com caminho do relatório, arquivos, decisões, artefatos,
testes causais, matriz terminal, Git/workflows/resíduos/acessos, divergências
com prova e o estado literal acima.

Não alterar esta task e não criar S26.
