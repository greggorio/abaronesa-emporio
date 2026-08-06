# Bootstrap administrativo e configuracoes sensiveis

## Contrato operacional

O bootstrap administrativo e desabilitado por default e consome somente:

```text
ROOT_BOOTSTRAP_ENABLED
ROOT_BOOTSTRAP_NAME
ROOT_BOOTSTRAP_EMAIL
ROOT_BOOTSTRAP_PASSWORD
```

Quando habilitado, nome, email valido e password com pelo menos 6 caracteres
sao obrigatorios. O minimo e um piso contra configuracao vazia ou trivial por
descuido, nao a politica de senha do sistema: a credencial vem de variavel de
ambiente do operador, e a forca real dela e responsabilidade de quem provisiona
o ambiente. Configuracao invalida interrompe a inicializacao com erro
sanitizado. Nenhum valor recebido aparece em logs.

Se um usuario `SYSTEM` ja existir, o inicializador preserva integralmente seus
dados e roles. Quando nao existir, a password e entregue ao encoder e somente
o hash integra a entidade persistida. O gatilho implementa
`ApplicationRunner`; o Spring invoca seu metodo publico `run` depois da
criacao do proxy, e `@Transactional` envolve consulta, encode e persistencia.

## Uso local

Copie `.env.example` para `.env.local`, que e ignorado, e preencha as quatro
variaveis somente nesse arquivo. Habilite o bootstrap apenas para a criacao
inicial, verifique o acesso e desabilite-o na execucao seguinte.

Nenhuma credencial deve ser colocada em arquivo rastreavel. Desabilitar o
bootstrap nao remove usuario existente. Recuperacao e reset de credencial nao
fazem parte desta slice.

## Producao futura

O procedimento previsto e:

1. injetar as variaveis pelo mecanismo de secrets ainda a definir;
2. habilitar explicitamente o bootstrap;
3. criar e verificar o acesso inicial;
4. desabilitar o bootstrap no deploy seguinte.

Esta documentacao nao escolhe secret store nem implementa deploy.

## Seeds e banco existente

Seeds sensiveis, identificadores de conta e identidade de tenant nascem
vazios. Defaults tecnicos conservadores podem permanecer. O seeder cria apenas
configuracoes ausentes: linhas existentes, inclusive vazias, nao sao apagadas
nem sobrescritas.

Valores historicamente persistidos exigem auditoria e rotacao operacional
separadas. Esta slice nao acessa, limpa, migra, valida ou rotaciona banco ou
credenciais externas.

## Paths portaveis

Base e desenvolvimento usam paths relativos, configuraveis por ambiente:

```text
nfe_schema_path=nfe/schemas
nfe_xml_path=nfe/xmls
uploads=uploads
```

Testes usam exclusivamente o diretorio temporario da JVM. Producao/container
usa defaults sob `/app`, sempre sobrescritiveis pelas mesmas variaveis de
ambiente. O `ConfigSeeder` recebe os paths fiscais por propriedades Spring e
nao fixa path local ou de container no Java.

O path de certificado permanece vazio e configuravel. A separacao prepara o
futuro Dockerfile, mas nao encerra o gate `BACKEND_FISCAL_SCHEMA_PATH`: a
imagem ainda precisara copiar e validar os schemas.

## Limites

Esta slice nao cria imagem, Dockerfile, Compose, workflow, CI, release ou
deploy. Tambem nao implementa endpoint administrativo ou integracao externa.
