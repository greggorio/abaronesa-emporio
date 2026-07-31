# syntax=docker/dockerfile:1.7
FROM nginxinc/nginx-unprivileged:1.29.5-alpine3.23@sha256:42a7d7f2ee23e9f5a1dcdf3647ba5c585bbd18f79e79cd817e70e8cd61c55779
ARG VCS_REF=unknown
ARG IMAGE_VERSION=local
LABEL org.opencontainers.image.source="https://github.com/greggorio/abaronesa-emporio" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.version="${IMAGE_VERSION}"
USER 0:0
COPY --chown=0:0 nginx.conf /etc/nginx/nginx.conf
COPY --chown=0:0 conf.d/emporio.conf /etc/nginx/conf.d/emporio.conf
COPY --chown=0:0 proxy-common.conf proxy-websocket.conf /etc/nginx/
RUN rm -f /etc/nginx/conf.d/default.conf \
    && mkdir -p /tmp/nginx/client_temp /tmp/nginx/proxy_temp /tmp/nginx/fastcgi_temp \
    && chown -R 101:101 /tmp/nginx \
    && chmod -R a-w /etc/nginx
EXPOSE 8080
USER 101:101
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD wget -q -O - http://127.0.0.1:8080/healthz >/dev/null || exit 1
ENTRYPOINT ["nginx", "-g", "daemon off;"]
