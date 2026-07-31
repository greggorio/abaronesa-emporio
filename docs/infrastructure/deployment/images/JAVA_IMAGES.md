# Imagens Java

## Bases e plataforma

As imagens `backend` e `website_back` usam `linux/amd64` como target inicial.
O build usa Maven 3.9.11 com Eclipse Temurin JDK 21 Alpine. O runtime
compartilhado usa Eclipse Temurin JRE 21.0.8+9 sobre Alpine 3.22.

Tags e digests estão fixados diretamente em cada `FROM`. A atualização exige
resolver uma nova tag completa e digest no Docker Hub, executar ambos os
`mvn -B verify`, reconstruir sem push e repetir todas as inspeções.

## Estrutura e execução

Os Dockerfiles possuem stages `build` e `runtime`. O POM é copiado antes do
source e o cache BuildKit de `/root/.m2` é reutilizado. O package da imagem usa
`-DskipTests` somente porque o mesmo workspace passa por `mvn -B verify` antes
dos builds.

O runtime contém apenas JRE e packages operacionais. Um usuário estável
`10001:10001` executa:

```text
java -jar /app/app.jar
```

`JAVA_TOOL_OPTIONS` fica vazio na imagem e é consumido nativamente pela JVM
quando injetado em runtime. O profile default da imagem é `prod`, ainda
sobrescritível por ambiente.

## Backend ERP

O runtime instala `curl`, certificados CA, timezone, `ffmpeg`, `ffprobe`,
`fontconfig` e uma família livre de fontes. Schemas fiscais são copiados para
`/app/nfe/schemas` como somente leitura. `/app/nfe/xmls` e `/app/uploads`
pertencem ao usuário da aplicação.

Health interno:

```text
http://127.0.0.1:8080/actuator/health
```

## Website backend

O runtime instala `curl`, certificados CA e timezone. Os diretórios
`/app/uploads`, `galeria`, `theme-assets`, `android-assets` e
`android-private` pertencem ao usuário final.

O Actuator expõe somente health no profile de produção, sem detalhes ou
componentes na resposta não autenticada:

```text
http://127.0.0.1:8085/actuator/health
```

## Build e inspeção local

```bash
docker build --platform linux/amd64 \
  --build-arg VCS_REF=s08-local-validation \
  --build-arg IMAGE_VERSION=s08-local \
  -t abaronesa-emporio-backend:s08 backend

docker build --platform linux/amd64 \
  --build-arg VCS_REF=s08-local-validation \
  --build-arg IMAGE_VERSION=s08-local \
  -t abaronesa-emporio-website-backend:s08 website_back
```

Use `docker image inspect`, `docker history --no-trunc` e containers
descartáveis com entrypoint sobrescrito para validar metadados, packages,
permissões e Java. O histórico integral não deve ser persistido em relatório.

## Limites

As tags `:s08` são artefatos locais de validação, não imagens de release.
Nenhum push é realizado. Persistência de uploads do website continua pendente
do Compose canônico. Runtime integrado com PostgreSQL e health completo será
revalidado nessa etapa. A arquitetura real da VPS também permanece pendente.
