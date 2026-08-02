# S32 — correction-01: restauração do BOM OkHttp

> **Estado:** `AUTHORIZED`
> **Autoridade:** complementa a S32 somente para fechar o achado novo de OkHttp
> **Checkpoint:** commit do orquestrador com mensagem exata `docs: authorize OkHttp BOM restoration for S32`
> **Commit técnico final:** permanece `fix: upgrade Spring baseline to 3.5.16`

## 1. Diagnóstico aceito

A parada do executor foi correta. O grupo A fechou e o `website_back` ficou em
zero, mas o `backend` ganhou `CVE-2021-0341` porque a atualização retirou um
gerenciamento transitivo:

```text
service:0.18.2 -> retrofit:2.9.0 -> okhttp:3.14.9
```

O Spring Boot 3.3.13 importava `okhttp-bom` e fixava `okhttp.version=4.12.0`.
O Boot 3.5.16 não gerencia OkHttp. Por isso a transitiva antiga passou a vencer.
As medições da S30a não tinham achado OkHttp.

Uma propriedade isolada não corrige o problema: a resolução experimental com
`-Dokhttp.version=4.12.0` continuou em OkHttp 3.14.9, pois não existe mais um BOM
consumindo a propriedade. Em cópia isolada, restaurar a importação do BOM
resolveu a árvore para:

```text
service:0.18.2 -> retrofit:2.9.0 -> okhttp:4.12.0
                                      -> okio:3.6.0 -> okio-jvm:3.6.0
```

Essa é a mesma linha OkHttp efetivamente fornecida antes da S32, não uma versão
nova escolhida sem baseline.

## 2. Correção fechada

No `backend/pom.xml`, adicionar:

```xml
<okhttp.version>4.12.0</okhttp.version>
```

e importar no `dependencyManagement`:

```xml
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp-bom</artifactId>
    <version>${okhttp.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

Não declarar versão diretamente em `okhttp`, Retrofit ou no cliente OpenAI. Não
alterar `website_back`: ele não possui essa cadeia nem o achado.

Em `tools/docker/java_images_contract.py`, fechar duas condições apenas para o
`backend`:

- `okhttp.version` deve ser exatamente `4.12.0`, usando o erro existente
  `PROTECTIVE_OVERRIDE_MISSING:backend:okhttp.version`;
- o BOM deve estar importado com `${okhttp.version}`, `type=pom` e
  `scope=import`; ausência, versão literal/diferente ou import incompleto produz
  `OKHTTP_BOM_IMPORT_REQUIRED:backend`.

Em `test_java_images_contract.py`, adicionar mutantes que provem separadamente
o valor da propriedade e a importação completa do BOM. O contrato real continua
retornando lista vazia. Nenhuma regra anterior será relaxada.

## 3. Retomada focal

Os 12 arquivos da S32 foram preservados no worktree. Para isolar este checkpoint
documental, o orquestrador apenas os removeu do stage; não houve descarte nem
reescrita da implementação.

Antes da alteração, confirmar:

```bash
test "$(pwd)" = "/home/gregorio/git/baronesa/emporio"
test "$(git branch --show-current)" = "main"
test "$(git rev-parse origin/main)" = "0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06"
test "$(git log -1 --format=%s)" = "docs: authorize OkHttp BOM restoration for S32"
test "$(git rev-list --count origin/main..HEAD)" = "11"
test -z "$(git diff --cached --name-only)"
git rev-parse HEAD
git status --short
```

O status deve conter os dez arquivos técnicos modificados e os dois arquivos de
evidência não rastreados já enumerados no relatório, sem outro caminho.

Depois da correção, executar:

1. `python3 tools/docker/java_images_contract.py validate`;
2. a suíte `tools/docker/tests` completa;
3. `mvn -B verify` do `backend`, com PostgreSQL efêmero pinado como na primeira
   passagem;
4. `dependency:tree` focal, provando OkHttp `4.12.0`, Okio `3.6.0` e ausência de
   OkHttp `3.14.9`;
5. rebuild somente do `backend`, com os mesmos frontend, BuildKit,
   `network=host`, `linux/amd64`, argumentos e SHA-base `5b859b0`;
6. novo scan Trivy somente do `backend`, com a política original.

Não repetir o build/scan do `website_back`, as seis suítes Python não Docker,
seu `mvn verify` ou os probes Flyway: os respectivos arquivos e resultados não
foram afetados por esta correção. Preservar essas evidências no relatório.

O novo scan do `backend` deve conter exatamente os dois achados JasperReports.
Qualquer OkHttp, Spring ou achado diferente exige parada sem commit.

## 4. Evidência, cleanup e commit

Atualizar o JSON existente com a medição real corrigida:

- `sourceSha` permanece `5b859b039d064c536b9eaba1348babc6890e2e7f`;
- recalcular `sourceDiffSha256` e `sourceTreeSha` sobre os mesmos dez arquivos
  técnicos, agora incluindo a correção OkHttp;
- atualizar identidade e achados do `backend`;
- preservar a identidade/medição anterior do `website_back`, cuja árvore não
  mudou;
- contagens finais: duas ocorrências, dois CVEs, ambos JasperReports no
  `backend`; grupo A e OkHttp em zero.

Acrescentar ao relatório uma seção de retomada. Preservar o histórico da parada,
registrar comandos, exits, árvore resolvida, scan, linhagem, cleanup e estado
Git. A linha terminal continua exatamente:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```

Remover nominalmente apenas recursos criados na retomada e comprovar Build
Cache `0B`. O volume anônimo `358ec441441567726a50d4ea73ac6f4ab5501a30d0f8aed48beb0346848662b8`
permanece preservado: está sem mount ativo, mas sua origem não foi atribuída à
S32.

Ao final, stage somente dos 12 caminhos originais da S32, executar
`git diff --cached --check`, o secret scan rastreado sobre o conteúdo final e
`git diff --check origin/main..HEAD`. Se todos os critérios estiverem atendidos,
criar exatamente um commit técnico:

```text
fix: upgrade Spring baseline to 3.5.16
```

Não fazer push, não alterar a task, esta correction, o README ou qualquer
arquivo fora dos 12 caminhos.
