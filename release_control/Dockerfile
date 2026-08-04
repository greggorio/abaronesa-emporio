# syntax=docker/dockerfile:1.7

FROM python:3.13-alpine3.23@sha256:9fdbf2e3e82628351513560b121e2ee6ce31cac212be9e070c5a5e2769fb5e76 AS builder

ARG UV_VERSION=0.9.17
ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_NO_CACHE=1

WORKDIR /build

RUN pip install --no-cache-dir "uv==${UV_VERSION}"

COPY release_control/pyproject.toml release_control/uv.lock ./
RUN uv sync --locked --no-dev --no-install-project

COPY release_control/src ./src
COPY release_control/migrations ./migrations
COPY release_control/alembic.ini ./
COPY release_control/README.md ./
COPY ops/releases/candidate-manifest.schema.json \
     ops/releases/global-release.schema.json \
     ops/releases/release-publication-outcome.schema.json \
     ./ops/releases/
COPY ops/deploy/schemas/deployment-workflow-outcome.schema.json \
     ops/deploy/schemas/rollback-workflow-outcome.schema.json \
     ./ops/deploy/schemas/
RUN uv sync --locked --no-dev

FROM python:3.13-alpine3.23@sha256:9fdbf2e3e82628351513560b121e2ee6ce31cac212be9e070c5a5e2769fb5e76 AS runtime

ARG VCS_REF=unknown
ARG IMAGE_VERSION=local
LABEL org.opencontainers.image.source="https://github.com/greggorio/abaronesa-emporio" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.version="${IMAGE_VERSION}"

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/app/.venv/bin:${PATH}" \
    PYTHONPATH="/app/src"

WORKDIR /app

RUN addgroup --gid 10001 release-control \
    && adduser --uid 10001 --ingroup release-control --disabled-password \
        --home /home/release-control --shell /sbin/nologin release-control

COPY --from=builder --chown=10001:10001 /build/.venv /app/.venv
COPY --from=builder --chown=10001:10001 /build/src ./src
COPY --from=builder --chown=10001:10001 /build/migrations ./migrations
COPY --from=builder --chown=10001:10001 /build/alembic.ini ./alembic.ini
COPY --from=builder --chown=10001:10001 /build/pyproject.toml /build/uv.lock /build/README.md ./
COPY --from=builder --chown=10001:10001 /build/ops /ops

USER 10001:10001

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8080/health/live', timeout=2).read()"

# The venv is built at /build/.venv and copied to /app/.venv, so the console
# scripts keep a shebang pointing at an interpreter that does not exist in the
# runtime stage. Invoking through the interpreter is immune to that.
CMD ["sh", "-c", "python -m alembic upgrade head && exec python -m uvicorn emporio_release_control.main:app --host 0.0.0.0 --port 8080"]
