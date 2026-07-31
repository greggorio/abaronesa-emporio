# syntax=docker/dockerfile:1.7
FROM maven:3.9.11-eclipse-temurin-21-alpine@sha256:922927df2c662cdd47ddb116443d6bec4696cfae3de1a0ddac8fcc7b87ce61ae AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B package -DskipTests \
    && cp "$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" /tmp/app.jar

FROM eclipse-temurin:21.0.8_9-jre-alpine-3.22@sha256:990397e0495ac088ab6ee3d949a2e97b715a134d8b96c561c5d130b3786a489d AS runtime
ARG VCS_REF=unknown
ARG IMAGE_VERSION=local
LABEL org.opencontainers.image.source="https://github.com/greggorio/abaronesa-emporio" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.version="${IMAGE_VERSION}"

RUN apk add --no-cache curl ca-certificates tzdata \
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
