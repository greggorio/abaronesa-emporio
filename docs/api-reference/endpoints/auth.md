# Authentication Endpoints

## Base Path

```http
/api/auth
```

## Papel

Esses endpoints cobrem autenticacao basica, renovacao de token, logout e consulta do usuario atual.

## Evidencia Material

- [AuthController.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/controller/AuthController.java)

## Endpoints Principais

### Login

```http
POST /api/auth/login
```

Autentica usuario com email e senha.

Resposta esperada pelo controller:
- `200 OK`
- corpo do tipo `TokenResponse`

### Registro

```http
POST /api/auth/register
```

Cria novo usuario.

Resposta esperada pelo controller:
- `201 Created`
- corpo do tipo `AuthApiResponse`

### Refresh Token

```http
POST /api/auth/refresh-token
```

Renova o token de acesso.

Resposta esperada pelo controller:
- `200 OK`
- corpo do tipo `TokenResponse`

### Logout

```http
POST /api/auth/logout
```

No estado atual o controller responde:

- `204 No Content`

O proprio codigo documenta que, em fluxo JWT stateless, o logout e essencialmente tratado pelo cliente.

### Usuario Atual

```http
GET /api/auth/me
```

Retorna informacoes do usuario autenticado.

Resposta esperada pelo controller:
- `200 OK`
- corpo do tipo `AuthUserResponse`

## Observacoes

- `SecurityConfig` libera `/api/auth/**` sem autenticacao previa
- a documentacao detalhada do contrato JWT esta em [../authentication/jwt.md](../authentication/jwt.md)
