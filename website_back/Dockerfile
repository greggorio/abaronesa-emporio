# syntax=docker.io/docker/dockerfile@sha256:b5f3b260a9678e1d83d2fce86eeddf79420b79147eaba2a25986f47133d73720
FROM maven:3.9.16-eclipse-temurin-21-alpine@sha256:d88e5b38297858f65f97bc7e7964c760ab988fd18ace41589176f1468c49a489 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B package -DskipTests \
    && cp "$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" /tmp/app.jar

FROM eclipse-temurin:21.0.11_10-jre-alpine-3.23@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c AS runtime
ARG VCS_REF=unknown
ARG IMAGE_VERSION=local
LABEL org.opencontainers.image.source="https://github.com/greggorio/abaronesa-emporio" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.version="${IMAGE_VERSION}"

RUN apk add --no-cache curl ca-certificates tzdata \
    && apk upgrade --no-cache libexpat p11-kit p11-kit-trust \
    && addgroup -S -g 10001 app \
    && adduser -S -D -H -u 10001 -G app app \
    && mkdir -p /app/uploads/galeria /app/uploads/theme-assets \
       /app/uploads/android-assets /app/uploads/android-private \
    && chown -R 10001:10001 /app/uploads \
    && chmod -R 0750 /app/uploads

WORKDIR /app
COPY --from=build --chown=10001:10001 /tmp/app.jar /app/app.jar
COPY --chown=0:0 src/main/docker/migrate /app/bin/migrate
RUN chmod 0555 /app/bin/migrate

ENV JAVA_TOOL_OPTIONS="" \
    SPRING_PROFILES_ACTIVE=prod
EXPOSE 8085
USER 10001:10001
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl --fail --silent --show-error http://127.0.0.1:8085/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
