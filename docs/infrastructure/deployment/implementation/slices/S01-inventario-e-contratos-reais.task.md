# S01 — Inventario e contratos reais do monorepo

> **Estado:** `ACCEPTED` — revisão concluída em 28/07/2026  
> **Tipo:** descoberta documental, sem implementacao funcional  
> **Executor previsto:** CLI  
> **Diretorio de trabalho obrigatorio:** `/home/gregorio/git/baronesa/emporio`  
> **Contrato arquitetural:** [proposta-docker-ci-cd-producao-emporio.md](../../proposta-docker-ci-cd-producao-emporio.md)  
> **Relatorio de saida:** `S01-inventario-e-contratos-reais.report.md`

## Instrucao para delegacao

Execute integralmente esta slice no diretorio indicado. Leia primeiro este contrato e o contrato arquitetural vinculado. Nao implemente Docker, CI/CD ou `release_control` nesta slice.

Seu produto e um inventario verificavel do codigo e dos artefatos atuais. Escreva toda a evidencia no relatorio de saida definido acima. Nao altere este arquivo de task.

## Objetivo observavel

Produzir a fotografia tecnica que permita ao orquestrador decompor as proximas slices sem assumir incorretamente:

- fronteiras dos componentes;
- comandos de build e teste;
- contratos de configuracao;
- portas e health checks;
- dependencias de build e runtime;
- persistencia e migrations;
- workflows e artefatos de deploy existentes;
- pontos reais de integracao do futuro `release_control`;
- estado Git da raiz e de repositorios aninhados;
- divergencias entre o codigo atual e a arquitetura aprovada.

A slice termina com documentacao e evidencia. Ela nao corrige os problemas encontrados.

## Contexto aprovado que nao deve ser rediscutido

- O remoto canonico e `git@github.com:greggorio/abaronesa-emporio.git`.
- A raiz `emporio/` sera o monorepo, mas ainda precisa ser inicializada e vinculada durante uma slice futura.
- Commits e pushes continuarao sendo feitos no terminal.
- A UI de desenvolvimento publicara uma release elegivel.
- A UI de producao implantara ou revertera uma release global.
- Nao havera selecao manual de versoes por componente.
- Dependencias serao resolvidas antes da publicacao e congeladas em manifesto.
- O plano de controle tera papeis `publisher` e `deployer` mutuamente exclusivos.
- O plano de controle nao acessara Docker diretamente.
- Produção nao sera alterada automaticamente por push em `main`.
- A stack comercial e o plano de controle terao ciclos operacionais separados.

Se o codigo atual contradisser algum desses pontos, registre a divergencia. Nao modifique silenciosamente o contrato aprovado.

## Escopo de leitura

O executor pode ler todo o conteudo necessario sob:

```text
/home/gregorio/git/baronesa/emporio
```

Priorize:

```text
.github/
backend/
frontend/
website_back/
website_front/
whatsapp_service/
deploy/
ops/
docs/
quality/
tools/
```

Diretorios gerados, caches, dependencias instaladas, binarios e uploads devem ser apenas classificados, nao varridos integralmente sem necessidade.

Exemplos:

```text
target/
node_modules/
.quasar/
.gradle/
uploads/
outputs/
.ai-workflow/evidence/
```

## Escopo de escrita

O unico arquivo que o executor pode criar ou alterar e:

```text
docs/infrastructure/deployment/implementation/slices/S01-inventario-e-contratos-reais.report.md
```

Nenhum arquivo de codigo, configuracao, ambiente, workflow ou documentacao canonica pode ser modificado.

## Fora de escopo

- executar `git init`;
- configurar `origin`;
- executar `git add`, `commit`, `tag` ou `push`;
- criar ou alterar `.gitignore`;
- mover, apagar ou rotacionar segredos;
- corrigir Dockerfiles, Compose, workflows ou variaveis;
- criar o modulo `release_control`;
- criar `components.yml` ou `manifest.schema.json`;
- instalar dependencias;
- executar builds ou suites de teste;
- construir ou publicar imagens;
- acessar GHCR ou disparar GitHub Actions;
- conectar, consultar ou alterar a VPS;
- consultar ou alterar DNS;
- iniciar ou parar containers e processos;
- alterar banco de dados;
- reformatar arquivos existentes;
- editar o contrato arquitetural.

## Preservacao e seguranca

- Presuma que arquivos e alteracoes existentes pertencem ao usuario.
- Nao use comandos destrutivos.
- Nao revele valores de `.env`, tokens, senhas, chaves, certificados ou secrets.
- Ao encontrar material sensivel, registre somente caminho, nome da variavel, permissao observada e classificacao de risco.
- Mascare qualquer valor sensivel que apareca acidentalmente na saida.
- Nao copie conteudo sensivel para o relatorio.
- Nao dependa de acesso de rede para concluir a slice.

## Levantamento obrigatorio

### 1. Topologia do workspace e Git

Documente:

- se a raiz e ou nao um repositorio Git;
- todos os diretorios `.git` encontrados sob a raiz, incluindo arquivos `.git` que apontem para worktrees;
- remotes, branch e estado de trabalho de cada repositorio encontrado;
- workflows localizados na raiz ou em componentes;
- artefatos gerados e dados locais que exigirao regra de exclusao antes do primeiro commit;
- confirmacao de que o remoto planejado ainda nao foi configurado localmente, se esse continuar sendo o estado real.

Nao inicialize nem consolide repositorios.

### 2. Matriz dos componentes

Crie uma tabela com uma linha para cada componente atual:

```text
backend
website_back
frontend
website_front
whatsapp_service
gateway/deploy
release_control
```

Para cada linha, informe:

- estado: existente, parcial, prototipo ou ausente;
- linguagem e framework;
- versoes declaradas de Java, Node e ferramentas;
- gerenciador de dependencias;
- arquivo de build;
- comando de desenvolvimento;
- comando de build de producao;
- comando de teste conhecido;
- artefato produzido;
- Dockerfile e contexto de build;
- porta interna;
- health check existente;
- evidencia `arquivo:linha`.

Nao presuma comandos com base apenas no framework. Diferencie comando comprovado de comando recomendado.

### 3. Contratos de configuracao

Inventarie, sem registrar valores:

- nomes de variaveis de ambiente;
- propriedades Spring e profiles;
- variaveis Vite/Quasar e momento de aplicacao: build ou runtime;
- URLs entre servicos;
- portas;
- paths de uploads, certificados, sessoes e arquivos gerados;
- defaults;
- obrigatoriedade;
- classificacao: publica, interna ou sensivel;
- divergencias de nomes entre Dockerfile, entrypoint, Compose e aplicacao.

O relatorio deve conter uma matriz:

```text
variavel | consumidor | origem atual | ambiente | sensibilidade | divergencia | evidencia
```

### 4. Dependencias de build e runtime

Mapeie somente relacoes comprovadas no codigo ou configuracao:

- frontend ERP para backend ERP;
- website frontend para website backend;
- website backend para backend ERP;
- backend ERP para WhatsApp;
- bancos consumidos;
- WebSocket, SSE ou callbacks;
- arquivos compartilhados entre componentes;
- contextos Docker que ultrapassam a pasta de um componente;
- contratos que obriguem dois ou mais componentes a serem testados ou promovidos juntos.

Para cada relacao, informe:

```text
origem | destino | tipo | contrato | obrigatoria | evidencia
```

Apresente depois um grafo textual de dependencias e a lista de dependentes reversos.

### 5. Rascunho fundamentado do resolvedor

Sem criar `ops/releases/components.yml`, proponha no relatorio o conteudo minimo que ele precisara representar:

- identificador do componente;
- caminhos que o afetam;
- dependencias;
- dependentes que precisam de revalidacao;
- imagem prevista;
- comando de teste;
- health check;
- migration;
- persistencia;
- estrategia segura quando um caminho nao estiver mapeado.

Marque cada item como `CONFIRMADO`, `INFERIDO` ou `NAO DETERMINADO`, sempre com evidencia para os confirmados.

### 6. Banco, migrations e persistencia

Documente separadamente para os dois backends:

- banco e schema esperados;
- biblioteca ou mecanismo de migration;
- local dos scripts;
- comportamento no startup;
- seeds;
- compatibilidade conhecida de rollback;
- uploads e outros dados persistentes;
- riscos de perda ao recriar containers.

Identifique o que exige investigacao posterior sem executar banco ou migration.

### 7. Docker e deploy existentes

Inventarie todos os:

- Dockerfiles;
- Compose;
- scripts de deploy;
- configuracoes Nginx;
- entrypoints;
- arquivos `.env.example` e `.env.production`;
- volumes;
- networks;
- mapeamentos de porta;
- nomes de imagem e tags.

Classifique cada artefato como:

```text
CANONICO_ATUAL
PROTOTIPO
LEGADO
CONFLITANTE
NAO_DETERMINADO
```

Essa classificacao descreve o estado encontrado. Ela nao transforma um prototipo em arquivo aprovado.

### 8. CI/CD existente

Para cada workflow encontrado, registre:

- caminho;
- gatilhos;
- componentes cobertos;
- testes;
- build e push de imagem;
- registry e tags;
- secrets referenciados, somente pelos nomes;
- acesso SSH;
- diretorio remoto presumido;
- mecanismos de concorrencia;
- environment GitHub;
- rollback;
- erros sintaticos ou referencias aparentemente invalidas;
- sobreposicao com outros workflows.

Nao execute nem corrija workflows.

### 9. Pontos de integracao do `release_control`

Localize no frontend administrativo:

- definicao de rotas;
- menu e controle de visibilidade;
- cliente HTTP;
- autenticacao e autorizacao;
- tratamento de operacoes assincronas;
- componentes reutilizaveis para status e progresso;
- existencia de tela anterior de gerenciamento de versoes, se houver neste projeto.

Localize nos backends ou infraestrutura:

- modelo de usuario e permissoes;
- padrao de endpoints administrativos;
- persistencia de jobs ou auditoria;
- polling, SSE ou WebSocket;
- configuracao por ambiente;
- health e observabilidade.

O objetivo e indicar pontos reais de extensao. Nao escolha framework nem implemente o modulo.

### 10. Seguranca e segredos

Sem exibir valores, registre:

- arquivos com aparencia de segredo;
- nomes de variaveis sensiveis;
- permissoes de filesystem quando relevantes;
- cobertura atual de ignore;
- certificados, chaves e tokens presentes no workspace;
- secrets exigidos pelos workflows;
- risco antes da inicializacao/publicacao do repositorio.

Se encontrar um segredo possivelmente real, use o formato:

```text
RISCO: caminho do arquivo
CAMPO: nome da variavel ou tipo de credencial
VALOR: REDACTED
ACAO FUTURA: remover do historico inicial e rotacionar
```

### 11. Confronto com a arquitetura aprovada

Crie tres listas:

```text
CONFIRMADO PELO CODIGO
CONTRADITO PELO CODIGO
AINDA NAO VERIFICAVEL
```

Compare pelo menos:

- monorepo;
- cinco componentes comerciais;
- gateway;
- plano de controle separado;
- portas internas;
- health checks;
- volumes;
- Node e Java;
- GHCR;
- workflows;
- manifestos;
- migrations;
- rotas propostas;
- capacidade de separar `publisher` e `deployer`.

Nao altere a arquitetura para resolver divergencias. Entregue-as ao orquestrador.

## Evidencia obrigatoria

Toda afirmacao sobre o projeto deve apontar para:

- `arquivo:linha`; ou
- comando exato, codigo de saida e trecho relevante.

O relatorio deve conter, no minimo:

1. metadados da execucao;
2. resumo executivo;
3. topologia Git;
4. matriz de componentes;
5. matriz de configuracao;
6. grafo de dependencias;
7. rascunho fundamentado do resolvedor;
8. banco, migrations e persistencia;
9. inventario Docker/deploy;
10. inventario CI/CD;
11. pontos de integracao do `release_control`;
12. riscos de seguranca sem valores;
13. confronto com a arquitetura;
14. decisoes ainda necessarias;
15. comandos executados e codigos de saida;
16. arquivos alterados;
17. declaracao do que nao foi executado.

## Comandos e disciplina de execucao

Use preferencialmente `rg`, `rg --files`, `find`, `sed`, `git` em modo de leitura e ferramentas equivalentes.

Registre:

```text
comando
diretorio
codigo de saida
resultado resumido
interpretacao
```

Um comando que falhe por ausencia de repositorio, arquivo ou ferramenta tambem e evidencia e deve ser registrado. Nao transforme essa falha em autorizacao para instalar ou inicializar algo.

Nao e necessario reproduzir arquivos inteiros no relatorio. Use referencias precisas e pequenos trechos redigidos quando indispensaveis.

## Estrutura minima do relatorio

O arquivo de saida deve iniciar com:

```markdown
# S01 — Relatorio de inventario e contratos reais

> **Estado informado pelo executor:** `IN_PROGRESS`
> **Revisao do orquestrador:** pendente
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Data:** `<data da execucao>`
```

O executor nao pode declarar `ACCEPTED`. Ao terminar, deve manter `IN_PROGRESS` e informar que o material esta pronto para revisao do orquestrador.

## Criterios de aceite

O orquestrador somente podera aceitar a S01 quando:

- todas as secoes obrigatorias estiverem presentes;
- cada componente tiver evidencia suficiente;
- configuracoes forem inventariadas sem vazamento de valores;
- dependencias diretas e reversas estiverem fundamentadas;
- workflows duplicados ou conflitantes estiverem mapeados;
- persistencia e migrations estiverem classificadas;
- pontos reais de extensao do `release_control` estiverem identificados;
- estado Git local estiver comprovado;
- divergencias com a arquitetura estiverem explicitas;
- comandos e codigos de saida estiverem registrados;
- o executor tiver alterado somente o relatorio permitido;
- nenhuma operacao externa ou destrutiva tiver sido executada.

## Condicoes de bloqueio

Nao bloqueie a slice inteira porque um item nao pode ser comprovado. Marque-o como `NAO DETERMINADO`, explique a evidencia ausente e continue os demais levantamentos.

Considere bloqueio real somente se:

- o workspace nao puder ser lido;
- o contrato arquitetural nao estiver acessivel;
- o arquivo de relatorio nao puder ser criado;
- a seguranca exigir interromper a leitura para evitar exposicao inevitavel de segredo.

Em caso de bloqueio, nao improvise mudancas. Registre o ponto exato, os comandos tentados e a autoridade adicional necessaria.

## Resposta final esperada do CLI

Ao concluir, responda apenas com:

- caminho absoluto do relatorio;
- resumo de ate cinco achados principais;
- confirmacao de que somente o relatorio foi alterado;
- itens `NAO DETERMINADO`;
- estado `pronto para revisao do orquestrador`.
