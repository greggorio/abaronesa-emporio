# S07 — Saneamento do bootstrap sensivel antes dos Dockerfiles

> **Estado:** `ACCEPTED` — `2026-07-28`  
> **Tipo:** remediacao de seguranca, contrato de bootstrap e documentacao  
> **Executor previsto:** CLI  
> **Diretorio de trabalho obrigatorio:** `/home/gregorio/git/baronesa/emporio`  
> **Dependencias:** S01 a S06 `ACCEPTED`  
> **Contrato arquitetural:** [proposta-docker-ci-cd-producao-emporio.md](../../proposta-docker-ci-cd-producao-emporio.md)  
> **Relatorio de saida:** `S07-saneamento-bootstrap-sensivel-pre-docker.report.md`

## Instrucao para delegacao

Execute integralmente esta slice no diretorio indicado. Leia primeiro:

1. esta task;
2. a revisao final da S06;
3. as revisoes finais da S02 e da S03;
4. `backend/src/main/java/com/baronesa/emporio/ConfigSeeder.java`;
5. `backend/src/main/java/com/baronesa/emporio/config/RootUserInitializer.java`;
6. os arquivos `application*.properties` de `backend/src/main/resources` e
   `backend/src/test/resources`;
7. `.env.example`, `backend/.env.example` e a documentacao local de segredos.

Nao altere esta task nem o tracker
`docs/infrastructure/deployment/implementation/README.md`.

Valores existentes com aparencia de credencial ou dados pessoais nao devem
ser copiados para o relatorio, para testes, para exemplos ou para mensagens de
commit. Registre somente path, chave/categoria, classificacao e resultado
sanitizado.

## Motivo da insercao desta slice

Durante a preparacao dos Dockerfiles Java foram encontrados riscos anteriores
ao primeiro commit:

- `ConfigSeeder.java` ainda possui defaults nao vazios em configuracoes
  sensiveis, identificadores de conta e identidade fiscal especifica de
  estabelecimento legado;
- `seedConfig` registra valores em log;
- `RootUserInitializer.java` possui identidade e senha default em codigo e
  registra a senha;
- paths fiscais ainda dependem de diretorio pessoal do host.

A S02 removeu os literais dos arquivos de propriedades que estavam no seu
escopo. Esta S07 trata o residuo agora comprovado nos inicializadores Java e
adiciona um gate local para impedir regressao.

Nao revogar ou reescrever o historico das slices aceitas. Registrar este
achado como remediacao incremental.

## Objetivo observavel

Ao final:

- nenhum usuario administrativo nasce com credencial literal;
- bootstrap de root e opt-in, configurado somente por ambiente;
- bootstrap habilitado sem dados validos falha fechado e sem expor valores;
- nenhum segredo, token, senha, chave, identificador de conta ou dado fiscal
  especifico de tenant possui default funcional no seeder;
- nenhum valor de configuracao e escrito em log pelo seeder;
- paths fiscais default sao portaveis para o futuro container;
- configuracoes existentes no banco nao sao apagadas, sobrescritas ou
  migradas;
- existe validador local fail-closed e suite mutante;
- exemplos e documentacao ensinam o bootstrap sem conter segredo;
- os dois backends continuam compilando/testando conforme o escopo prescrito;
- indice Git, `HEAD`, tags, reflog e ausencia de workflows permanecem
  preservados.

## 1. Contrato do bootstrap de root

O futuro runtime deve consumir exatamente estas variaveis:

```text
ROOT_BOOTSTRAP_ENABLED
ROOT_BOOTSTRAP_NAME
ROOT_BOOTSTRAP_EMAIL
ROOT_BOOTSTRAP_PASSWORD
```

Mapear para propriedades Spring equivalentes:

```properties
app.bootstrap.root.enabled=${ROOT_BOOTSTRAP_ENABLED:false}
app.bootstrap.root.name=${ROOT_BOOTSTRAP_NAME:Root}
app.bootstrap.root.email=${ROOT_BOOTSTRAP_EMAIL:}
app.bootstrap.root.password=${ROOT_BOOTSTRAP_PASSWORD:}
```

Regras obrigatorias:

- `enabled` possui default `false`;
- email e password nao possuem default nao vazio;
- nenhum alias legado com credencial fixa permanece;
- quando desabilitado, o inicializador nao cria nem altera usuario;
- quando habilitado, nome, email valido e password com pelo menos 16
  caracteres sao obrigatorios;
- configuracao ausente ou invalida com bootstrap habilitado interrompe a
  inicializacao com erro sanitizado;
- o erro nao inclui email, password nem valor recebido;
- se ja existir usuario `SYSTEM`, o bootstrap nao troca email, senha ou roles;
- a criacao permanece transacional;
- nenhuma excecao de validacao e engolida para permitir startup inseguro;
- logs podem informar apenas a acao e o resultado, nunca email ou password;
- a senha nao e retornada, persistida em texto puro nem exibida depois da
  criacao;
- nao implementar endpoint para criar root;
- nao usar `change-me`, UUID fixo, senha de teste ou qualquer fallback
  rastreavel.

Pode refatorar o binding para uma classe pequena de propriedades caso isso
melhore testabilidade. Nao introduzir framework novo.

## 2. Contrato do `ConfigSeeder`

### 2.1 Valores sensiveis

As seguintes categorias devem ter default vazio ou ser omitidas do seed:

- senha de certificado;
- token CSC/NFC-e;
- access tokens;
- client secrets;
- API keys;
- webhook secrets;
- tokens PagSeguro/Mercado Pago;
- credenciais Uber;
- chaves de sincronizacao;
- qualquer outra chave cujo nome contenha semantica de `password`, `senha`,
  `secret`, `token`, `api-key`, `api_key`, `private-key` ou equivalente.

Public keys, client IDs, customer IDs e identificadores de conta, embora nem
sempre secretos, tambem nao podem carregar valores de outro estabelecimento.

Variavel de ambiente opcional pode fornecer valor no runtime somente quando
o comportamento existente realmente o exigir. Nao criar default funcional e
nao registrar o valor. Nao ampliar esta slice para redesenhar o armazenamento
de configuracoes no banco.

### 2.2 Identidade fiscal e dados do estabelecimento

Devem iniciar vazios, sem copiar dados de tenant legado:

- CNPJ, inscricoes e razao social;
- nome fantasia;
- endereco, CEP, municipio e telefone;
- emails fiscais/contabeis;
- CPF de consumidor default;
- certificado e logo;
- identificadores CSC;
- dados de pickup ligados a conta/estabelecimento;
- URLs de callback ou notificacao especificas de instalacao.

Defaults tecnicos sem identidade de tenant podem permanecer, por exemplo:

- enums;
- timeouts;
- flags conservadoras;
- limites;
- URLs oficiais publicas de provedores;
- ambiente fiscal de homologacao;
- idioma.

Nao substituir dados legados por dados reais da A Baronesa nesta slice. Esses
dados serao configurados fora do codigo.

### 2.3 Paths portaveis

Congelar:

```text
nfe_schema_path -> /app/nfe/schemas
nfe_xml_path    -> /app/nfe/xmls
uploads         -> /app/uploads
```

O path do certificado deve permanecer vazio/configuravel; nao inventar nome
de PFX nem montar certificado dentro da imagem.

Nenhum path em codigo, propriedades ou exemplos pode depender de
`/home/gregorio`.

Esta mudanca prepara, mas nao conclui, o gate
`BACKEND_FISCAL_SCHEMA_PATH`. O gate so podera ser removido quando o futuro
Dockerfile copiar os schemas e a imagem for validada.

### 2.4 Logs

`seedConfig` e qualquer helper relacionado:

- pode registrar somente chave e tipo de acao;
- nao registra `valorPadrao`, valor anterior, valor novo ou segredo;
- nao registra mapas/objetos que possam carregar configuracao;
- nao inclui segredo em excecao produzida por esta slice.

A proibicao vale para todas as configuracoes, nao somente para as
classificadas como secretas.

### 2.5 Banco existente

Preservar a semantica nao destrutiva:

- nao apagar linha existente;
- nao sobrescrever valor existente nao vazio;
- nao limpar banco local ou de producao;
- nao criar migration para remover valores;
- nao tentar rotacionar ou validar credenciais;
- nao acessar banco externo;
- documentar que valores ja persistidos exigem revisao/rotacao operacional
  separada.

## 3. Validador local

Criar:

```text
tools/security/bootstrap_contract.py
tools/security/tests/test_bootstrap_contract.py
```

Usar somente Python 3 e biblioteca padrao. Nao instalar dependencia.

Comando:

```bash
python3 tools/security/bootstrap_contract.py validate
```

Exit codes:

- `0`: contrato valido;
- `2`: contrato invalido ou input invalido.

O output deve listar somente codigos/paths/chaves sanitizados. Nunca imprimir
o valor encontrado.

O validador deve falhar fechado quando detectar, no minimo:

1. bootstrap root habilitado por default;
2. email ou password root com fallback nao vazio;
3. constante Java de password/email default no inicializador;
4. password menor que o minimo contratual na regra implementada;
5. log contendo password, email root ou variavel de credencial;
6. `seedConfig` sensivel com literal nao vazio;
7. `seedConfig` de identificador de conta com literal legado;
8. identidade fiscal/tenant com literal nao vazio;
9. log do `valorPadrao` ou valor de configuracao;
10. fallback conhecido do tipo default key/dev key;
11. path `/home/gregorio`;
12. path fiscal diferente do contrato portavel;
13. exemplo `.env` com valor nao vazio em variavel sensivel;
14. arquivo obrigatorio ausente;
15. documentacao operacional ausente.

O validador pode usar listas explicitas de chaves para reduzir falso positivo.
Nao fazer scanner generico de todo o repositorio nem ler binarios, uploads,
caches, HPROF, PFX ou `.env.production`.

## 4. Testes

### 4.1 Testes Python

Criar pelo menos 18 casos com `unittest`, incluindo:

- contrato real valido;
- um mutante para cada item minimo do validador;
- valor sensivel nunca aparece na mensagem de erro;
- path inexistente retorna codigo `2`;
- input invalido falha fechado.

Mutantes somente em memoria ou `/tmp`. Nao alterar os contratos reais durante
os testes.

Executar:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/security/tests \
  -p 'test_bootstrap_contract.py' \
  -v
```

### 4.2 Testes Java

Adicionar testes focados para o bootstrap root, cobrindo no minimo:

- desabilitado nao cria usuario;
- habilitado sem email falha;
- habilitado sem password falha;
- email invalido falha;
- password curta falha;
- usuario `SYSTEM` existente nao e alterado;
- configuracao valida cria exatamente um usuario `SYSTEM`;
- password e entregue ao encoder e somente o hash e persistido.

Nao testar valor real nem registrar password de fixture.

Para `ConfigSeeder`, provar por teste ou pelo validador estrutural:

- nenhuma categoria sensivel recebe literal;
- nenhuma identidade de tenant recebe literal;
- logs nao recebem valores;
- paths portaveis estao congelados.

## 5. Propriedades e exemplos

Atualizar somente o necessario em:

```text
.env.example
backend/.env.example
backend/src/main/resources/application.properties
backend/src/main/resources/application-dev.properties
backend/src/main/resources/application-prod.properties
backend/src/test/resources/application-test.properties
```

Regras:

- exemplos declaram as quatro variaveis de bootstrap vazias, exceto
  `ROOT_BOOTSTRAP_ENABLED=false` e nome nao sensivel;
- nenhuma senha ficticia e fornecida;
- prod nao habilita bootstrap implicitamente;
- teste usa apenas fixture efemera no proprio teste;
- propriedades existentes de segredo continuam sem default literal;
- nao duplicar fontes conflitantes de configuracao.

## 6. Documentacao

Atualizar:

```text
docs/development/CONFIGURACAO_LOCAL_SEGREDOS.md
docs/development/README.md
```

Criar:

```text
docs/infrastructure/deployment/BOOTSTRAP_ADMIN_E_CONFIGURACOES_SENSIVEIS.md
```

Documentar:

- como habilitar bootstrap local usando `.env.local` ignorado;
- que nenhuma credencial deve ser colocada em arquivo rastreavel;
- procedimento futuro de producao: habilitar explicitamente, criar root uma
  vez, verificar acesso e desabilitar no deploy seguinte;
- desabilitar bootstrap nao remove usuario existente;
- recuperar/resetar credencial nao faz parte desta slice;
- seeds nao sobrescrevem valores existentes;
- valores historicamente persistidos exigem auditoria/rotacao operacional;
- paths portaveis preparados para o futuro Dockerfile;
- esta slice nao cria imagem, CI, release ou deploy.

Nao reproduzir nenhum valor removido.

## 7. Escopo de escrita

Pode criar ou alterar somente:

```text
.env.example
backend/.env.example
backend/src/main/java/com/baronesa/emporio/ConfigSeeder.java
backend/src/main/java/com/baronesa/emporio/config/RootUserInitializer.java
backend/src/main/java/com/baronesa/emporio/config/*RootBootstrap*.java
backend/src/main/resources/application.properties
backend/src/main/resources/application-dev.properties
backend/src/main/resources/application-prod.properties
backend/src/test/resources/application-test.properties
backend/src/test/java/com/baronesa/emporio/config/*RootUserInitializer*Test.java
tools/security/bootstrap_contract.py
tools/security/tests/test_bootstrap_contract.py
docs/development/CONFIGURACAO_LOCAL_SEGREDOS.md
docs/development/README.md
docs/infrastructure/deployment/BOOTSTRAP_ADMIN_E_CONFIGURACOES_SENSIVEIS.md
docs/infrastructure/deployment/implementation/slices/S07-saneamento-bootstrap-sensivel-pre-docker.report.md
```

Se uma correcao exigir outro arquivo, nao o altere. Registre bloqueio com
path, motivo e impacto.

## 8. Fora de escopo

Nao:

- alterar Dockerfile, Compose, Nginx ou gateway;
- alterar catalogo de componentes ou remover readiness gate;
- criar workflow GitHub Actions;
- criar `release_control`;
- implementar UI;
- configurar dados reais da A Baronesa;
- migrar, limpar ou acessar banco;
- rotacionar ou validar credencial externa;
- abrir PFX, `.env.production`, HPROF, upload ou arquivo ignorado sensivel;
- instalar dependencia;
- acessar GitHub, GHCR, DNS ou VPS;
- executar aplicacao contra servico externo;
- criar commit, tag ou push;
- usar `git add`;
- alterar esta task ou o tracker.

## 9. Validacoes obrigatorias

Executar e registrar CWD, comando, codigo e interpretacao.

### Contrato local

```bash
python3 tools/security/bootstrap_contract.py validate

PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/security/tests \
  -p 'test_bootstrap_contract.py' \
  -v
```

### Backend ERP

Executar a partir de `backend/`, com valores sinteticos efemeros passados
somente ao processo:

```bash
DB_PASSWORD=s07-ephemeral-db-password \
INTEGRATION_SYSTEM_TOKEN_SECRET=s07-ephemeral-integration-token-for-tests \
mvn -B verify
```

Se o ambiente Maven exigir outra variavel obrigatoria, usar fixture efemera,
registrar apenas o nome e nao persistir o valor.

Nao repetir testes do `website_back`: esta slice nao altera esse componente.

### Estado protegido

```bash
git ls-files --stage
git rev-parse --verify HEAD
git tag --list
git reflog show --all
find .github/workflows -maxdepth 1 -type f \
  \( -name '*.yml' -o -name '*.yaml' \) -print
find tools/security \
  \( -type d -name __pycache__ -o -type f -name '*.pyc' \) -print
```

Esperado:

- indice real vazio;
- `HEAD` inexistente;
- zero tags e reflog;
- zero workflow YAML ativo;
- zero cache Python em `tools/security`.

## 10. Relatorio obrigatorio

Criar:

```text
docs/infrastructure/deployment/implementation/slices/S07-saneamento-bootstrap-sensivel-pre-docker.report.md
```

O relatorio deve incluir:

1. resumo do risco sem transcrever valores;
2. arquivos alterados;
3. contrato final do bootstrap root;
4. categorias de seeds saneadas;
5. tratamento de logs;
6. paths portaveis;
7. comportamento para banco existente;
8. testes/mutantes e resultados;
9. comandos exatos, CWD e codigos;
10. itens nao determinados;
11. confirmacao de escopo negativo;
12. estado Git/workflows/caches;
13. bloqueios, se houver.

Nao declarar `ACCEPTED`. Estado final:

```text
IN_PROGRESS — aguardando revisao do orquestrador
```

## 11. Criterios de aceite do orquestrador

A S07 somente podera ser aceita se:

- bootstrap root for opt-in e fail-closed;
- nenhum default de credencial root permanecer;
- logs nao expuserem email/password/config values;
- seeds sensiveis e de tenant estiverem vazios ou omitidos;
- nenhum valor removido aparecer em exemplo, teste, doc ou relatorio;
- paths fiscais forem portaveis sem fechar prematuramente o gate Docker;
- banco existente permanecer intocado;
- validador e mutantes passarem;
- testes Java focados e `mvn verify` passarem;
- documentacao corresponder ao comportamento;
- nenhum arquivo fora do escopo tiver sido alterado;
- indice, `HEAD`, tags, workflows e caches permanecerem protegidos;
- nao houver commit, push, instalacao ou acesso externo.

A proxima slice prevista, apos aceite, sera a S08: endurecimento e validacao
dos Dockerfiles Java de `backend` e `website_back`.
