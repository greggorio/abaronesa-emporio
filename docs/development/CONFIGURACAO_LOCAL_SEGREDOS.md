# Configuracao local de segredos

Os arquivos Spring nao mantêm mais credenciais ou tokens literais. Valores
sensíveis entram no processo por variaveis de ambiente, evitando que um futuro
primeiro commit grave esses dados no historico Git.

## Preparar o ambiente local

Na raiz do workspace, crie um arquivo local a partir do exemplo:

```bash
cp .env.example .env.local
```

Preencha `.env.local` apenas na sua maquina. Esse arquivo e
`ops/env/.env.production` sao locais e nunca devem ser commitidos.

Gere um token de integracao local sem reutilizar credenciais de outros
ambientes:

```bash
openssl rand -hex 32
```

Grave o resultado somente em `INTEGRATION_SYSTEM_TOKEN_SECRET` no seu
`.env.local`. O backend ERP e o backend do website devem receber exatamente o
mesmo valor.

Carregue as variaveis na sessao atual do shell:

```bash
set -a
source .env.local
set +a
```

Na IDE, cadastre os mesmos nomes no campo de variaveis de ambiente da
configuracao de execucao. Nao copie os valores para
`application.properties`.

## Bootstrap administrativo local

O bootstrap de root e opt-in. No `.env.local` ignorado, mantenha
`ROOT_BOOTSTRAP_ENABLED=false` normalmente. Para a criacao inicial, informe
nome, email valido e password com pelo menos 16 caracteres somente nesse
arquivo local e habilite explicitamente o bootstrap.

Depois de confirmar o acesso, volte `ROOT_BOOTSTRAP_ENABLED` para `false`.
Desabilitar o bootstrap nao remove nem modifica o usuario existente. Recuperar
ou redefinir credencial esta fora deste procedimento.

Consulte tambem
[`BOOTSTRAP_ADMIN_E_CONFIGURACOES_SENSIVEIS.md`](../infrastructure/deployment/BOOTSTRAP_ADMIN_E_CONFIGURACOES_SENSIVEIS.md).

## Executar os backends

Com PostgreSQL local disponivel e as variaveis obrigatorias carregadas:

```bash
cd backend
mvn spring-boot:run
```

Em outro terminal, carregue o mesmo `.env.local` e execute:

```bash
cd website_back
mvn spring-boot:run
```

`DB_PASSWORD` e `INTEGRATION_SYSTEM_TOKEN_SECRET` sao obrigatorias. Google,
Uber, `ESPRESSO_SYNC_API_KEY` e `WEBSITE_ERP_SYNC_KEY` sao integracoes
opcionais e podem permanecer vazias quando o fluxo correspondente nao for
usado.

### Fallback de desenvolvimento local

Para reduzir atrito no boot local sem commitar segredos reais, o
`website_back` possui um `application-dev.properties` que sobrescreve
apenas o profile `dev` com defaults operacionais:

- `integration.system-token-secret`
- `spring.datasource.password`

Esses defaults NAO sao injetados em nenhum outro profile (`prod`,
`staging`, `homolog` etc.) e existem somente para que o comando
`mvn spring-boot:run` nao exija `.env.local` carregado no shell. Em
qualquer ambiente compartilhado, configure as variaveis via ambiente
(`set -a; source .env.local; set +a` ou injecao do runner) e mantenha
os valores sincronizados entre `backend` e `website_back` - vide secao
"Preparar o ambiente local" acima.

## Limites e rotacao

Este procedimento serve somente ao desenvolvimento manual local; nao define
como segredos serao injetados em producao. Os valores anteriormente presentes
no workspace ainda exigem rotacao nos provedores e sistemas externos. Remover
o default literal do codigo nao revoga uma credencial.
