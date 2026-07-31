# syntax=docker/dockerfile:1.7
FROM node:24.13.0-alpine3.23@sha256:cd6fb7efa6490f039f3471a189214d5f548c11df1ff9e5b181aa49e22c14383e AS dependencies
WORKDIR /workspace
ENV PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --omit=dev

FROM node:24.13.0-alpine3.23@sha256:cd6fb7efa6490f039f3471a189214d5f548c11df1ff9e5b181aa49e22c14383e AS runtime
ARG VCS_REF=unknown
ARG IMAGE_VERSION=local
LABEL org.opencontainers.image.source="https://github.com/greggorio/abaronesa-emporio" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.version="${IMAGE_VERSION}"
RUN apk add --no-cache chromium ca-certificates font-liberation nss freetype harfbuzz \
    && addgroup -S -g 10001 app \
    && adduser -S -D -H -u 10001 -G app app \
    && mkdir -p /app /data/session \
    && chown 10001:10001 /data/session \
    && chmod 0700 /data/session
WORKDIR /app
COPY --from=dependencies /workspace/node_modules ./node_modules
COPY package.json package-lock.json index.js app.js ./
RUN chmod -R a-w /app && chmod -R a+rX /app
ENV PORT=3001 \
    SESSION_DIR=/data/session \
    BASE_COUNTRY_CODE=55 \
    PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium-browser
EXPOSE 3001
USER 10001:10001
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:3001/health/live').then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))"
CMD ["node", "index.js"]
