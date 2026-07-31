# Operação do pacote isolado release-control

Este documento descreve o pacote offline entregue pela S28. Ele é uma
declaração operacional para revisão e preparação; não afirma que o serviço foi
instalado, publicado, iniciado ou validado em produção.

## Limites e topologia

O arquivo `ops/compose/release-control.yml` possui somente os serviços
`release_control` e `release_control_postgresql`. Eles usam a rede própria
`emporio_release_control_internal` e o volume
`emporio_release_control_postgresql_data`, sem compartilhar a topologia do
Compose comercial. A rede não publica portas e conserva apenas a conectividade
de saída necessária às dependências HTTPS já exigidas pelo runtime, como JWKS e
GitHub API.

O serviço HTTP é publicado no host apenas como
`127.0.0.1:${RELEASE_CONTROL_LOOPBACK_PORT:-8180}:8080`. Não há porta pública,
`network_mode: host`, socket do Docker, privilégio ou capacidade adicional.
O contêiner da aplicação é não-root, tem filesystem somente leitura e usa um
`tmpfs` mínimo para temporários. O PostgreSQL mantém somente o volume próprio
que precisa para persistência.

O exemplo de ambiente em `ops/env/release-control.env.example` contém
placeholders. Os valores reais devem ser injetados por um arquivo protegido
fora do repositório. A chave privada do GitHub App deve existir como segredo
externo do Compose, conforme o nome configurado em
`RELEASE_CONTROL_GITHUB_SECRET_NAME`, e é montada como
`/run/secrets/github-app-private-key` em modo somente leitura. Nenhuma chave,
senha, pepper ou token é fornecido por este pacote.

## Preparação e inicialização declarativa

Antes de qualquer operação autorizada, o operador deve revisar o arquivo de
ambiente, confirmar a imagem por referência imutável e provisionar o segredo
externo sem registrar o conteúdo em shell history ou logs. O pacote não cria
credenciais e não acessa rede por conta própria.

O `Dockerfile` copia o `pyproject.toml` e o `uv.lock`, resolve somente o grupo
de produção durante a construção futura, e copia apenas o código, migrations,
`alembic.ini` e metadados necessários. O comando de inicialização é ordenado:

1. `alembic upgrade head`;
2. `uvicorn emporio_release_control.main:app --host 0.0.0.0 --port 8080`.

O endereço `0.0.0.0` é interno ao namespace de rede do contêiner; a única
publicação no host permanece limitada ao loopback conforme a seção anterior.
Se a migration falhar, o processo não inicia o Uvicorn.

`/health/live` verifica somente que o processo HTTP responde. O
`/health/ready` é a referência de prontidão do runtime e depende do banco e
da migration corrente. Um healthcheck verde não substitui a verificação de
logs, estado das migrations ou revisão do ambiente.

## Backup e restauração do PostgreSQL

Backup e restauração são operações independentes da atualização da imagem. A
política real de retenção, criptografia e armazenamento deve ser fornecida
pelo operador responsável; não há backup embutido nem destino remoto neste
pacote.

Exemplo de preparação de um dump lógico, a ser revisado e executado somente
em ambiente autorizado:

```sh
docker compose --env-file /etc/emporio/release-control.env \
  -f /opt/emporio-release-control/ops/compose/release-control.yml \
  exec -T release_control_postgresql \
  pg_dump --format=custom --file=/tmp/release-control.dump "$POSTGRES_DB"
```

O dump deve ser retirado por um procedimento protegido antes de remover o
arquivo temporário. Para uma restauração previamente autorizada, pare a
aplicação, preserve o volume original, restaure em uma instância de banco
isolada e valide migrations e `/health/ready` antes de recolocar o serviço em
execução. Não sobrescreva o volume original sem uma cópia verificável.

## Atualização independente e parada segura

Uma atualização deve trocar somente a referência da imagem do serviço
`release_control`, revisar migrations pendentes e observar `/health/ready`.
O PostgreSQL não deve ser recriado como parte de uma atualização da aplicação.
O exemplo systemd usa `up --detach --no-build --wait` para iniciar o pacote e
`stop --timeout 30` para uma parada ordenada; não usa `down`, remoção de volume
ou comandos de publicação.

O unit file exige usuário e grupo dedicados, `EnvironmentFile` protegido,
`WorkingDirectory` explícito e dependência do Docker após a rede estar
disponível. Ele é um exemplo para adaptação controlada, não uma instalação
pronta.

## Verificação local permitida pela S28

As verificações offline do pacote são feitas pelo validador
`tools/deploy/validate_release_control_package.py` e seus testes causais. A
S28 não executa Docker build, pull, up, run, containers, PostgreSQL, rede,
`uv sync`, SSH, GitHub, GHCR, VPS, DNS ou produção.
