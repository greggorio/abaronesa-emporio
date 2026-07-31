# Authentication

Documentacao dos contratos de autenticacao usados entre ERP, backend e superficies digitais.

## Cobertura Atual

| Documento | Papel | Status |
|-----------|-------|--------|
| [jwt.md](./jwt.md) | JWT compartilhado, headers e fronteiras de acesso | `parcial` |

## Escopo da Pasta

Esta pasta deve documentar:

- login e autenticacao compartilhada
- JWT bearer token
- cabecalhos relevantes
- rotas publicas e protegidas em alto nivel
- contratos de autenticacao consumidos por app, site e integracoes

Ela nao deve virar dump completo de `SecurityConfig`. O objetivo aqui e documentar o contrato que um consumidor da API precisa entender.

## Proximos Passos

- expandir este diretorio se a autenticacao social, refresh ou API keys precisarem de documentacao separada
- manter alinhamento com [../../architecture/decisions/ADR-002-jwt-compartilhado.md](../../architecture/decisions/ADR-002-jwt-compartilhado.md)
