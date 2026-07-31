# S26 — correção consolidada 01 da revisão terminal

> Estado: IN_PROGRESS — aguardando correção e nova revisão
> Slice: S26 — executor, workflow e runtime de rollback comercial
> Data: 31/07/2026

## 1. Achados objetivos

A revisão confirmou que os validadores e os testes offline passam, mas a S26 não pode ser aceita:

- a matriz canônica `PYTHONDONTWRITEBYTECODE=1 python3 -m pytest -q release_control/tests` terminou em exit 4 por `ModuleNotFoundError`; a coleta com `PYTHONPATH=release_control/src` encontrou 268 testes, sem executar a suíte;
- `release_control/tests/test_deployer_api.py` ainda espera as capabilities antigas, não inclui a rota GET de rollback no conjunto esperado e espera que o rollback continue rejeitado, sem operação e sem dispatch;
- os 9 testes S26 próprios não cobrem a API/persistência oficial e não compensam a contradição dos testes forward.

Isso viola os critérios da S26 de contrato ativo, não regressão e testes causais na superfície oficial.

## 2. Correção autorizada

Alterar somente os testes e o suporte de teste diretamente necessários:

~~~text
release_control/tests/conftest.py
release_control/tests/test_deployer_api.py
release_control/tests/test_deployer_persistence.py
release_control/tests/test_deployer_reconciliation.py
release_control/tests/test_deployer_remote_contract.py
release_control/tests/test_deployer_rollback.py (novo, se necessário)
docs/infrastructure/deployment/implementation/slices/S26-executor-workflow-runtime-rollback.report.md
~~~

É permitido corrigir código S26 somente se um teste causal novo demonstrar defeito real em API, serviço, persistência, reconciliação, schemas, GitHub dispatch ou outcome. Registrar cada correção no relatório.

## 3. Cobertura obrigatória

Atualizar ou criar testes para provar:

- capability exatamente `deployment:read`, `deployment:execute`, `deployment:rollback` e ordem da resposta;
- presença das rotas POST e GET de rollback;
- `deployment:rollback` obrigatório no POST e `deployment:read` no GET;
- request fechado, Idempotency-Key UUID v4 com prefixo, replay idêntico e conflito;
- elegibilidade de predecessor/current, migration e backup;
- criação persistida de `operationType=rollback`, dispatch versionado e resposta GET;
- lock global, operação ativa, estados, `UNCERTAIN`, restore e recuperação;
- não regressão dos deployments forward, publisher e isolamento de modos.

Não remover testes antigos sem substituí-los por testes que expressem o contrato S26.

## 4. Testes e restrição de containers

Corrigir o caminho de import para que a suíte seja reproduzível a partir do CWD da task, ou registrar explicitamente o comando equivalente com `PYTHONPATH=release_control/src`.

Não iniciar PostgresContainer, Docker, rede, volume ou qualquer serviço externo: essa proibição da S26 permanece vigente. Executar a coleta oficial e todos os testes offline. Se a execução integral de `release_control/tests` continuar exigindo PostgresContainer, registrar a falha literal e não declarar a suíte verde; isso permanece divergência para revisão do orquestrador.

~~~bash
cd /home/gregorio/git/baronesa/emporio
PYTHONDONTWRITEBYTECODE=1 python3 -m pytest --collect-only -q release_control/tests
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -p 'test_*.py'
python3 tools/deploy/validate_rollback_runtime.py
python3 tools/deploy/validate_deployer_runtime.py
python3 tools/releases/release_control_contract.py validate
git diff --check
git rev-parse --verify HEAD
find . -path './.git' -prune -o \( -name '.venv' -o -name '.coverage' -o -name '.pytest_cache' -o -name '.ruff_cache' -o -name '.mypy_cache' -o -name '__pycache__' -o -name '*.pyc' \) -print
~~~

Registrar arquivos, contagens, exits, saída literal, interpretação e a divergência de PostgresContainer sem mascará-la.

Não alterar a task, o tracker, S01–S25, S27 ou criar S27. O relatório deve terminar com:

~~~text
IN_PROGRESS — aguardando revisão do orquestrador
~~~
