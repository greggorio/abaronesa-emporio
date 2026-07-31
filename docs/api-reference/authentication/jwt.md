# JWT Compartilhado

## Visao Geral

O ecossistema usa autenticacao compartilhada baseada em JWT entre o ERP e as superficies digitais.

No desenho atual:

- o login ocorre no ERP
- o token JWT e reutilizado por outros consumidores
- o backend valida o token e reconstrui o contexto autenticado localmente

## Contrato Basico

### Header de autenticacao

```http
Authorization: Bearer <TOKEN>
```

### Header auxiliar de usuario

```http
X-User-ID: <ID_DO_USUARIO>
```

Esse header aparece em fluxos customer-facing e deve ser entendido como complementar ao JWT, nao como substituto da autenticacao.

## Origem do Token

O fluxo documentado na arquitetura indica:

1. o login acontece em `/api/auth/**`
2. o ERP emite o JWT
3. app e site armazenam o token
4. chamadas autenticadas reenviam esse token ao backend

### Credenciais de Desenvolvimento

| Campo | Valor |
|-------|-------|
| **Email** | `root@localhost` |
| **Senha** | `123456` |

> **Atencao:** O campo JSON no body de login e `password` (nao `senha`).

**Obter token JWT localmente:**

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"root@localhost","password":"123456"}'
```

Resultado esperado: resposta `200` com `accessToken` e `tokenType: "Bearer"`.

## Validacao no Backend

Evidencia principal:

- [SecurityConfig.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/config/SecurityConfig.java)

No backend:

- `JwtAuthenticationFilter` valida o JWT
- `SecurityContext` recebe o principal autenticado
- endpoints nao liberados explicitamente exigem autenticacao

## Rotas Publicas Relevantes

Pelo estado atual de `SecurityConfig`, estes grupos aparecem liberados sem autenticacao:

- `/api/auth/**`
- `/oauth2/**`
- `/api/public/**`
- rotas publicas de mesa e pedidos
- rotas publicas de pagamentos usados no checkout
- webhooks externos

### Fluxo Mesa Digital (guest_token)

No fluxo da mesa digital, o cliente e identificado por `guest_token` armazenado no navegador — nao e necessario login tradicional. Esse token e gerado pelo backend e trocado entre as superficies digitais.

## Webhooks e Endpoints Nao Autenticados

Alguns endpoints sao publicos por desenho operacional, mesmo sem JWT:

- `/api/webhooks/mercadopago/**`
- `/api/webhooks/pagseguro/**`
- `/api/uber/webhooks/**`

Esses casos devem ser tratados como callbacks confiados ao provedor e nao como endpoints autenticados por usuario final.

## Relacao com a Arquitetura

Documentos relacionados:

- [../../architecture/decisions/ADR-002-jwt-compartilhado.md](../../architecture/decisions/ADR-002-jwt-compartilhado.md)
- [../../architecture/VISAO_GERAL_INTEGRACOES.md](../../architecture/VISAO_GERAL_INTEGRACOES.md)

## Limites da Documentacao Atual

Este documento ainda nao detalha:

- refresh token
- expiracao e renovacao
- diferenca completa entre roles e perfis
- matriz completa de autorizacao por endpoint

Esses pontos so devem ser adicionados quando houver evidencias claras no backend e necessidade real de consumo.
