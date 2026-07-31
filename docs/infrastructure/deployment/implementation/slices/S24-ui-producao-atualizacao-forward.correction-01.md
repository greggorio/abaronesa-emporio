# S24 — correção consolidada 01 da matriz terminal

> Estado: IN_PROGRESS — aguardando correção e nova revisão
> Slice: S24 — UI de produção para atualização forward
> Data: 31/07/2026

## 1. Autoridade e achado

A revisão terminal reproduziu a implementação e os testes causais da S24,
mas a matriz canônica não terminou verde:

~~~
cd /home/gregorio/git/baronesa/emporio/frontend
npm run test:unit -- --run
exit 1
npm error Missing script: "test:unit"
~~~

O frontend/package.json existente possui npm test apontando para vitest run,
mas não possui o alias test:unit. O equivalente:

~~~
npm test -- --run
exit 0
Test Files  12 passed (12)
Tests       143 passed (143)
~~~

não substitui o comando prescrito. A task exigia exit 0 para todos os
comandos, exceto somente git rev-parse --verify HEAD, e simultaneamente
deixava frontend/package.json fora da fronteira. Esta correção resolve essa
inconsistência sem mudar o contrato funcional da UI.

## 2. Correção fechada

O executor pode alterar exclusivamente:

~~~
frontend/package.json
~~~

Adicionar o script exato, preservando todos os scripts e dependências
existentes:

~~~
"test:unit": "vitest run"
~~~

Não alterar frontend/package-lock.json, dependências, publisher S17,
código da UI, contratos, task, tracker ou qualquer outro arquivo da S24.
Não criar S25.

## 3. Prova obrigatória

Executar novamente a matriz da task e registrar:

- npm run test:unit -- --run com exit 0;
- npm test -- --run com exit 0;
- lint, build, node --test runtime-entrypoint.spec.js e contrato com exit 0;
- git rev-parse --verify HEAD com exit 128;
- busca final de resíduos vazia;
- ausência de alteração do lockfile e dos arquivos fora da correção;
- estado Git, workflows e acessos externos conforme a task original.

O relatório deve registrar a saída literal do comando corrigido e permanecer:

~~~
IN_PROGRESS — aguardando revisão do orquestrador
~~~

Não declarar ACCEPTED e não criar S25.
