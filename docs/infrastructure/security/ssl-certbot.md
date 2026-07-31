# SSL/TLS com Certbot

> **Status**: ✅ Implementado | **Renovação**: Automática (cron)

---

## Visão Geral

SSL/TLS é gerenciado via **Certbot** no Nginx (edge proxy).

---

## Domínios Ativos

| Domínio | Redireciona Para | Status |
|---------|------------------|--------|
| `erp.smartdataerp.com.br` | `:8094` (backend ERP) | ✅ |
| `app.smartdataerp.com.br` | `:8093` (backend Espresso) | ✅ |
| `:8098` | Frontend ERP | ✅ |
| `:7089` | Frontend Espresso | ✅ |

---

## Comandos Úteis

### Listar Certificados

```bash
sudo certbot certificates
```

**Saída esperada**:
```
Saving debug log to /var/log/letsencrypt/letsencrypt.log

- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
Found the following certs:
  Certificate Name: erp.smartdataerp.com.br
    Domains: erp.smartdataerp.com.br
    Expiry Date: 2026-06-15
    Certificate Path: /etc/letsencrypt/live/erp.smartdataerp.com.br/fullchain.pem
    Private Key Path: /etc/letsencrypt/live/erp.smartdataerp.com.br/privkey.pem
  Certificate Name: app.smartdataerp.com.br
    Domains: app.smartdataerp.com.br
    Expiry Date: 2026-06-15
    Certificate Path: /etc/letsencrypt/live/app.smartdataerp.com.br/fullchain.pem
    Private Key Path: /etc/letsencrypt/live/app.smartdataerp.com.br/privkey.pem
- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
```

### Renovar Certificados

```bash
# Dry run (teste)
sudo certbot renew --dry-run

# Renovação real
sudo certbot renew
```

### Adicionar Novo Domínio

```bash
# Para novo cliente
sudo certbot --nginx -d novo-cliente.smartdataerp.com.br
```

---

## Configuração do Nginx

### Template por Cliente

Arquivo: `/etc/nginx/sites-available/erp-cliente.conf`

```nginx
server {
    listen 443 ssl http2;
    server_name erp.smartdataerp.com.br;

    # Certificados SSL
    ssl_certificate /etc/letsencrypt/live/erp.smartdataerp.com.br/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/erp.smartdataerp.com.br/privkey.pem;

    # Otimização de Buffers
    client_header_buffer_size 64k;
    large_client_header_buffers 4 64k;

    location / {
        proxy_pass http://127.0.0.1:8094;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Resiliência
        proxy_buffering off;
        proxy_request_buffering off;
        proxy_connect_timeout 300s;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }
}

# Redirecionar HTTP → HTTPS
server {
    listen 80;
    server_name erp.smartdataerp.com.br;
    return 301 https://$server_name$request_uri;
}
```

### Testar Configuração

```bash
# Validar configuração
sudo nginx -t

# Recarregar Nginx
sudo systemctl reload nginx
```

---

## Renovação Automática

### Cron Job

Certbot instala automaticamente um cron job em `/etc/cron.d/certbot`:

```bash
# Cat /etc/cron.d/certbot
0 */12 * * * root test -x /usr/bin/certbot && /usr/bin/certbot -q renew
```

**O que faz**: Tenta renovar a cada 12 horas (certbot só renova se < 30 dias do expiry).

### Verificar Logs

```bash
# Logs de renovação
sudo tail -f /var/log/letsencrypt/letsencrypt.log

# Logs de erro
sudo grep -i error /var/log/letsencrypt/letsencrypt.log
```

---

## Troubleshooting

### Problema: Certificado Expirou

```bash
# 1. Forçar renovação
sudo certbot renew --force-renewal

# 2. Recarregar Nginx
sudo systemctl reload nginx

# 3. Verificar nova data de expiração
sudo certbot certificates
```

### Problema: Renovação Falha

```bash
# 1. Verificar logs
sudo tail -100 /var/log/letsencrypt/letsencrypt.log

# 2. Verificar se porta 80 está livre
sudo lsof -i:80

# 3. Parar Nginx temporariamente
sudo systemctl stop nginx

# 4. Tentar renovação standalone
sudo certbot renew --standalone

# 5. Reiniciar Nginx
sudo systemctl start nginx
```

### Problema: Nginx Não Recarrega

```bash
# 1. Testar configuração
sudo nginx -t

# 2. Verificar erro específico
sudo systemctl status nginx
sudo journalctl -u nginx -n 50

# 3. Verificar permissões dos certificados
ls -la /etc/letsencrypt/live/*/
```

---

## Segurança Adicional (Opcional)

### Headers de Segurança

Adicionar ao Nginx:

```nginx
# Security headers
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
add_header X-Frame-Options "SAMEORIGIN" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-XSS-Protection "1; mode=block" always;
```

### SSL Labs Test

Verificar qualidade do SSL:

- [SSL Labs Test](https://www.ssllabs.com/ssltest/)
- Target: `erp.smartdataerp.com.br`
- Meta: **A+**

---

## Monitoramento de Expiração

### Script de Verificação

```bash
#!/bin/bash
# /opt/sistemas/check-ssl-expiry.sh

CERT_DIR="/etc/letsencrypt/live"
DAYS_WARNING=30

for cert in ${CERT_DIR}/*/cert.pem; do
    domain=$(basename $(dirname $cert))
    expiry=$(openssl x509 -enddate -noout -in ${CERT_DIR}/${domain}/cert.pem | cut -d= -f2)
    expiry_epoch=$(date -d "$expiry" +%s)
    now_epoch=$(date +%s)
    days_left=$(( ($expiry_epoch - $now_epoch) / 86400 ))
    
    echo "${domain}: expira em ${days_left} dias (${expiry})"
    
    if [ $days_left -lt $DAYS_WARNING ]; then
        echo "⚠️  ALERTA: ${domain} expira em menos de ${DAYS_WARNING} dias!"
        # Enviar alerta (implementar)
        # curl -X POST https://api.telegram.org/...
    fi
done
```

### Adicionar ao Cron

```bash
# Rodar diariamente
0 9 * * * /opt/sistemas/check-ssl-expiry.sh >> /var/log/ssl-expiry.log 2>&1
```

---

## Documentos Relacionados

| Documento | Descrição |
|-----------|-----------|
| [`../arquitetura_atual.md`](../arquitetura_atual.md) | Arquitetura de produção |
| [`../monitoring/README.md`](../monitoring/README.md) | Monitoramento |
| [`../deployment/deploy-bakery.md`](../deployment/deploy-bakery.md) | Deploy do ERP |

---

**Última atualização**: Março 2026
