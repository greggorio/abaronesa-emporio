# syntax=docker/dockerfile:1.7

FROM python:3.13-slim AS builder

ARG UV_VERSION=0.9.17
ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_NO_CACHE=1

WORKDIR /build

RUN pip install --no-cache-dir "uv==${UV_VERSION}"

COPY pyproject.toml uv.lock ./
RUN uv sync --locked --no-dev --no-install-project

COPY src ./src
COPY migrations ./migrations
COPY alembic.ini ./
COPY README.md ./
RUN uv sync --locked --no-dev

FROM python:3.13-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/app/.venv/bin:${PATH}" \
    PYTHONPATH="/app/src"

WORKDIR /app

RUN groupadd --gid 10001 release-control \
    && useradd --uid 10001 --gid 10001 --create-home \
        --home-dir /home/release-control --shell /usr/sbin/nologin release-control

COPY --from=builder --chown=10001:10001 /build/.venv /app/.venv
COPY --chown=10001:10001 src ./src
COPY --chown=10001:10001 migrations ./migrations
COPY --chown=10001:10001 alembic.ini ./alembic.ini
COPY --chown=10001:10001 pyproject.toml uv.lock README.md ./

USER 10001:10001

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8080/health/live', timeout=2).read()"

CMD ["sh", "-c", "alembic upgrade head && exec uvicorn emporio_release_control.main:app --host 0.0.0.0 --port 8080"]
