# S05 — Contrato canonico de componentes e fechamento de dependencias

> **Estado:** `ACCEPTED` — revisão concluída em 28/07/2026  
> **Tipo:** implementacao de contrato, resolvedor e documentacao  
> **Executor previsto:** CLI  
> **Diretorio de trabalho obrigatorio:** `/home/gregorio/git/baronesa/emporio`  
> **Dependencias:** S01 a S04 `ACCEPTED`  
> **Contrato arquitetural:** [proposta-docker-ci-cd-producao-emporio.md](../../proposta-docker-ci-cd-producao-emporio.md)  
> **Evidencia tecnica:** [S01-inventario-e-contratos-reais.report.md](./S01-inventario-e-contratos-reais.report.md)  
> **Evidencia Git:** [S03-fundacao-git-local-e-auditoria-primeiro-indice.report.md](./S03-fundacao-git-local-e-auditoria-primeiro-indice.report.md)  
> **Relatorio de saida:** `S05-contrato-componentes-e-fechamento-dependencias.report.md`

## Instrucao para delegacao

Execute integralmente esta slice no diretório indicado. Leia primeiro este
contrato, a arquitetura aprovada e a revisão final da S01.

Esta slice implementa o contrato que permitirá ao CI decidir quais imagens
precisam ser reconstruídas e quais consumidores precisam ser revalidados. Ela
não cria imagens, candidatos ou releases e não implanta nada.

Não altere esta task nem o tracker
`docs/infrastructure/deployment/implementation/README.md`.

## Problema que esta slice resolve

Uma release sempre representará o sistema completo. O usuário nunca escolherá
versões isoladas de frontend, website ou backend.

Para viabilizar isso, o repositório precisa distinguir:

- componente diretamente alterado;
- componentes consumidores que precisam ser revalidados;
- componentes não alterados que podem herdar digest de um conjunto anterior;
- mudança global que exige validação ampla;
- path desconhecido que deve falhar fechado;
- gate técnico que ainda impede a publicação de uma release.

O resultado desta slice será a fonte canônica dessas decisões. Produção não
usará esse catálogo para inventar versões: receberá futuramente um manifesto
global já resolvido e imutável.

## Objetivo observavel

Ao final existirão:

- `ops/releases/components.yml`, com os seis componentes da release comercial;
- schema local que valide a estrutura do catálogo;
- resolvedor determinístico de paths alterados e fechamento transitivo;
- modo de validação estrutural;
- modo `require-release-ready`, que falhará enquanto houver gates técnicos;
- testes automatizados positivos e negativos;
- documentação de manutenção e uso;
- estado Git ainda sem índice, commit, tag ou push;
- nenhum workflow GitHub Actions ativo.

## Conjunto canonico da release comercial

O catálogo deve conter exatamente:

```text
backend
website_back
frontend
website_front
whatsapp_service
gateway
```

O `release_control`:

- não pertence ao BOM comercial;
- possui ciclo de build, publicação e atualização independente;
- deve aparecer na política como componente operacional excluído;
- não pode ser inserido no conjunto comercial pelo resolvedor.

PostgreSQL, Google, Firebase e demais serviços externos são dependências de
runtime, não imagens do BOM comercial.

## Grafo direto aprovado

As dependências entre componentes comerciais devem representar consumidor
para provedor:

```text
frontend         -> backend
website_back     -> backend
website_front    -> website_back
backend          -> whatsapp_service
gateway          -> backend
gateway          -> website_back
gateway          -> frontend
gateway          -> website_front
gateway          -> whatsapp_service
```

Interpretação:

- mudança no provedor inclui seus consumidores no fechamento de validação;
- o fechamento é transitivo;
- a dependência `backend -> whatsapp_service` é opcional em runtime, mas uma
  mudança do contrato do serviço ainda exige revalidação do consumidor;
- `website_front -> backend` não deve ser declarado: a chamada atual ao nome
  `backend:8085` foi classificada pela S01 como bug e o alvo correto é
  `website_back`;
- o grafo deve permanecer acíclico.

## Fechamentos minimos esperados

Para mudanças diretas de um único componente:

| Mudanca direta | Reconstruir | Revalidar, incluindo fechamento transitivo |
|---|---|---|
| `backend` | `backend` | `backend`, `frontend`, `website_back`, `website_front`, `gateway` |
| `website_back` | `website_back` | `website_back`, `website_front`, `gateway` |
| `frontend` | `frontend` | `frontend`, `gateway` |
| `website_front` | `website_front` | `website_front`, `gateway` |
| `whatsapp_service` | `whatsapp_service` | os seis componentes |
| `gateway` | `gateway` | `gateway` |

O resolvedor deve ordenar listas de saída de forma estável, conforme a ordem
canônica do catálogo.

## Politica de paths

O catálogo deve separar:

### Paths de componente

```text
backend/**
website_back/**
frontend/**
website_front/**
whatsapp_service/**
ops/gateway/**
```

`ops/gateway/**` é o path canônico futuro do gateway, ainda ausente. Essa
ausência deve aparecer como gate de readiness, não ser substituída
silenciosamente por protótipos em `deploy/nginx/`.

### Paths globais

Mudanças nestas áreas devem exigir revalidação dos seis componentes, sem
necessariamente reconstruir todas as imagens:

```text
.github/workflows/**
ops/releases/**
ops/compose/**
ops/deploy/**
deploy/**
```

### Paths documentais

Mudanças exclusivamente em:

```text
docs/**
README.md
```

não exigem rebuild de imagem nem revalidação de componente.

### Path desconhecido

Qualquer path que não corresponda a regra declarada deve:

- retornar classificação `unknown`;
- aplicar política `fail_closed_all`;
- selecionar os seis componentes para rebuild e revalidação;
- produzir aviso explícito;
- nunca retornar conjunto vazio por conveniência.

### Primeiro release

Sem manifesto candidato anterior, o resolvedor deve selecionar os seis
componentes para build e validação, independentemente dos paths.

## Gates tecnicos iniciais

O catálogo estrutural será válido, mas ainda não estará pronto para publicar
release. Registre gates verificáveis, no mínimo:

### `backend`

- Dockerfile ainda não endurecido;
- opções de JVM ainda não consumidas pelo entrypoint;
- portabilidade do path de schemas fiscais pendente.

### `website_back`

- health check dedicado ausente;
- persistência de uploads ainda não garantida no Compose canônico;
- Dockerfile ainda não endurecido.

### `frontend`

- Dockerfile ainda não endurecido;
- compatibilidade com Node 24 LTS ainda não comprovada.

### `website_front`

- divergência `VITE_VILLA_API_URL` versus `VITE_WEBSITE_API_URL`;
- alvo interno `backend:8085` ainda precisa virar `website_back:8085`;
- Dockerfile ainda não endurecido;
- compatibilidade com Node 24 LTS ainda não comprovada.

### `whatsapp_service`

- Node 18 fora de suporte no Dockerfile atual;
- liveness separado do estado de autenticação/QR ainda não definido;
- Dockerfile ainda não endurecido.

### `gateway`

- diretório e Dockerfile canônicos ainda ausentes;
- health check ainda não definido;
- porta loopback `8120` ainda precisa ser validada operacionalmente.

O catálogo não deve afirmar que esses gates foram resolvidos. Slices futuras
os removerão somente junto com implementação e evidência.

## Escopo de escrita

Arquivos que podem ser criados:

```text
ops/releases/components.yml
ops/releases/components.schema.json
tools/releases/requirements.txt
tools/releases/catalog.py
tools/releases/tests/test_catalog.py
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/implementation/slices/S05-contrato-componentes-e-fechamento-dependencias.report.md
```

Arquivos que podem ser alterados:

```text
.gitignore
docs/infrastructure/README.md
```

Uso restrito:

- `.gitignore` somente para caches Python introduzidos pela ferramenta;
- `docs/infrastructure/README.md` somente para:
  - atualizar o estado do tracker;
  - vincular o contrato de componentes;
  - não declarar a arquitetura proposta como já implantada.

Nenhum outro arquivo pode ser alterado.

## Dependencias da ferramenta

Criar `tools/releases/requirements.txt` com versões fixadas e apenas:

```text
PyYAML
jsonschema
```

Use versões compatíveis com as já disponíveis no ambiente, sem executar
instalação nesta slice. Não adicionar framework de CLI ou testes.

O resolvedor deve usar:

- Python 3;
- biblioteca padrão;
- PyYAML;
- jsonschema;
- `unittest` para testes.

Não baixar schema remoto. Toda validação deve usar arquivos locais.

## Contrato do catalogo

`components.yml` deve conter, no mínimo:

- `schema_version`;
- ordem canônica;
- política de path desconhecido;
- paths globais e documentais;
- componente operacional excluído;
- para cada componente:
  - id;
  - estado atual;
  - repositório de imagem sem tag ou digest;
  - source paths;
  - contexto e Dockerfile;
  - comandos atuais de teste e build;
  - porta interna;
  - health check confirmado, inferido ou pendente;
  - dependências comerciais diretas;
  - dependências externas/runtime;
  - migrations;
  - persistência;
  - gates de readiness.

Regras:

- imagens devem começar por
  `ghcr.io/greggorio/abaronesa-emporio-`;
- catálogo não usa `latest`, tag mutável ou digest;
- digests pertencem ao manifesto futuro, não ao catálogo;
- estado e gate devem ser consistentes;
- campo pendente deve ser explícito, nunca string ambígua
  `NAO DETERMINADO`;
- não incorporar segredo ou valor de ambiente.

## Schema

`components.schema.json` deve:

- usar JSON Schema local;
- rejeitar componente extra ou ausente;
- rejeitar campo obrigatório ausente;
- restringir enums e tipos;
- rejeitar propriedades desconhecidas nos objetos de contrato;
- validar formato dos IDs e repositórios de imagem;
- validar listas não vazias onde aplicável;
- permitir gates pendentes de forma explícita;
- não usar `$ref` remoto.

Validações semânticas que JSON Schema não expressar devem ficar em
`catalog.py`.

## CLI do resolvedor

`tools/releases/catalog.py` deve oferecer:

### Validacao estrutural

```bash
python3 tools/releases/catalog.py validate
```

Exit code:

- `0`: schema e invariantes semânticas válidos;
- diferente de zero: contrato inválido.

Validar semanticamente:

- conjunto exato dos seis componentes;
- ordem canônica sem duplicidade;
- referências de dependência existentes;
- ausência de ciclo;
- imagens únicas e no namespace aprovado;
- `release_control` excluído;
- coerência entre readiness e gates;
- source paths sem colisão indevida;
- política `fail_closed_all`;
- grafo e fechamentos mínimos aprovados.

### Readiness

```bash
python3 tools/releases/catalog.py validate --require-release-ready
```

Enquanto os gates iniciais existirem:

- deve falhar com código não zero estável;
- deve listar somente IDs e códigos de gates;
- não deve emitir segredo;
- a falha é esperada e prova que uma release prematura permanece bloqueada.

### Resolucao de paths

```bash
python3 tools/releases/catalog.py resolve --changed <path> [--changed <path> ...]
```

Saída JSON determinística contendo:

```text
classification
changedPaths
directComponents
buildComponents
validationComponents
inheritedComponents
warnings
```

Adicionar opção explícita para primeiro release:

```bash
python3 tools/releases/catalog.py resolve --first-release
```

Regras:

- paths devem ser relativos à raiz;
- rejeitar path absoluto, vazio ou com `..`;
- normalizar separador sem permitir escape da raiz;
- não depender da ordem dos argumentos;
- docs-only retorna listas de build/validation vazias;
- path global revalida os seis;
- path desconhecido aplica fail-closed aos seis;
- primeiro release constrói e valida os seis;
- `inheritedComponents` é o complemento de `buildComponents`;
- o resolver não lê Git nem consulta GitHub.

## Testes obrigatorios

Criar testes `unittest` cobrindo, no mínimo:

1. catálogo real passa schema e invariantes;
2. modo readiness falha enquanto gates existirem;
3. conjunto de componente ausente falha;
4. componente extra falha;
5. dependência desconhecida falha;
6. ciclo no grafo falha;
7. imagem duplicada falha;
8. namespace de imagem divergente falha;
9. `latest`, tag ou digest no repositório do catálogo falha;
10. readiness sem gate e estado bloqueado falha;
11. gate existente com estado pronto falha;
12. fechamento de `backend`;
13. fechamento de `website_back`;
14. fechamento de `frontend`;
15. fechamento de `website_front`;
16. fechamento de `whatsapp_service`;
17. fechamento de `gateway`;
18. múltiplos paths produzem união determinística;
19. path global revalida os seis;
20. docs-only não seleciona componente;
21. path desconhecido seleciona os seis em fail-closed;
22. primeiro release seleciona os seis;
23. path absoluto é rejeitado;
24. path com `..` é rejeitado;
25. `release_control` nunca entra no BOM.

Os testes podem criar catálogos mutantes somente em diretório temporário.
Não alterar o catálogo real para produzir casos negativos.

Executar sem criar bytecode ou cache no workspace:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests \
  -p 'test_*.py' \
  -v
```

Não instalar dependências. Se PyYAML ou jsonschema não estiverem disponíveis,
registre bloqueio em vez de usar parser improvisado.

## Documentacao

Criar `docs/infrastructure/deployment/release-control/README.md` explicando:

- diferença entre catálogo, candidato e manifesto global;
- conjunto comercial completo;
- exclusão operacional do `release_control`;
- dependências diretas e fechamento transitivo;
- build direto versus revalidação de consumidor;
- herança de digest para componentes não reconstruídos;
- comportamento de primeiro release;
- paths globais, documentais e desconhecidos;
- gates iniciais e modo `require-release-ready`;
- comandos de validação e resolução;
- procedimento para adicionar componente ou dependência;
- regra de atualizar contrato, schema, testes e documentação no mesmo change.

Não afirmar que candidato, release, UI ou produção já existem.

Atualizar o índice de infraestrutura apenas dentro do escopo autorizado.

## Protecao Git e CI

Durante toda a slice:

- índice Git real deve permanecer vazio;
- `HEAD`, tags e reflog devem permanecer vazios;
- nenhum commit ou push;
- `.github/workflows/` continua sem YAML ativo;
- não usar índice temporário;
- não acessar GitHub ou produção.

Se testes criarem cache por engano:

- não apagar de forma ampla;
- identificar somente o cache criado pela execução;
- adicionar regra estreita ao `.gitignore`;
- remover apenas o cache criado e registrar recuperabilidade;
- repetir a verificação Git.

## Validacoes obrigatorias

Executar e registrar:

```bash
python3 tools/releases/catalog.py validate
python3 tools/releases/catalog.py validate --require-release-ready
python3 tools/releases/catalog.py resolve --changed backend/src/main/App.java
python3 tools/releases/catalog.py resolve --changed website_back/src/main/App.java
python3 tools/releases/catalog.py resolve --changed whatsapp_service/index.js
python3 tools/releases/catalog.py resolve --changed docs/README.md
python3 tools/releases/catalog.py resolve --changed caminho/desconhecido.txt
python3 tools/releases/catalog.py resolve --first-release
```

Resultados:

- validação estrutural: código `0`;
- readiness: código não zero esperado;
- fechamentos iguais aos definidos no contrato;
- docs-only sem build/revalidação;
- desconhecido e primeiro release com os seis.

Também registrar:

```bash
git ls-files --stage
git rev-parse --verify HEAD
git tag --list
git reflog
find .github/workflows -type f \( -name '*.yml' -o -name '*.yaml' \)
```

## Evidencia obrigatoria

Criar:

```text
docs/infrastructure/deployment/implementation/slices/S05-contrato-componentes-e-fechamento-dependencias.report.md
```

O relatório deve conter:

1. metadados, data e CWD;
2. arquivos criados e alterados;
3. decisões do catálogo;
4. matriz dos seis componentes;
5. grafo direto e fechamentos calculados;
6. política de paths;
7. gates iniciais;
8. schema e invariantes semânticas;
9. interface do CLI e códigos de saída;
10. testes com comando exato, código, quantidade e resultado;
11. validações manuais prescritas;
12. estado da documentação;
13. estado Git e ausência de workflows;
14. comandos relevantes com CWD, código, resultado e interpretação;
15. desvios, itens não determinados e bloqueios;
16. declaração de que não houve commit, push, CI, imagem, release, Docker ou
    acesso externo;
17. resposta final solicitada ao CLI.

Não incluir valores sensíveis ou conteúdo de ambientes locais.

## Criterios de aceite

- Catálogo contém exatamente os seis componentes comerciais.
- `release_control` está explicitamente fora do BOM.
- Schema local valida a estrutura e rejeita deriva.
- Validações semânticas cobrem grafo, imagens, gates e paths.
- Grafo é acíclico e corresponde ao contrato aprovado.
- Fechamentos são determinísticos e conservadores.
- Unknown path falha fechado para os seis.
- Docs-only não provoca build.
- Primeiro release seleciona os seis.
- Catálogo estrutural passa.
- Readiness falha enquanto gates reais permanecerem.
- Todos os testes passam.
- Documentação corresponde ao comportamento entregue.
- Índice, `HEAD`, tags e reflog permanecem vazios.
- Nenhum workflow YAML reaparece.
- Nenhum arquivo fora do escopo é alterado.
- Não ocorre operação externa.

## Condicoes de bloqueio

Interrompa e registre `BLOCKED` se:

- evidência do código contradisser o grafo fixado;
- for necessário inventar compatibilidade não sustentada;
- PyYAML ou jsonschema não estiverem disponíveis;
- o schema exigir referência remota;
- algum teste só passar ocultando path desconhecido;
- o resolvedor produzir ciclo ou resultado não determinístico;
- for necessário alterar aplicação, Docker ou workflow;
- o estado Git divergir da S04;
- surgir necessidade de acesso externo.

## Resposta final esperada do CLI

Responder de forma concisa com:

- caminho absoluto do relatório;
- arquivos criados/alterados;
- conjunto canônico;
- resultado da validação estrutural;
- resultado esperado do modo readiness;
- quantidade e resultado dos testes;
- resumo dos fechamentos;
- gates ainda abertos;
- estado Git e workflows;
- bloqueios e itens não determinados;
- estado `IN_PROGRESS`, aguardando revisão do orquestrador.

Não declarar `ACCEPTED`.
