# ADR-002: JWT Compartilhado entre ERP e Espresso

**Status**: ✅ Aceito (vigente)  
**Data**: Março 2026  
**Revisão**: Quando mudar a estratégia de autenticação

---

## Contexto

Temos 2 backends separados:
- **ERP Backend** (`/backend/`) — Autenticação centralizada
- **Espresso Backend** (`/espresso_back/`) — Consome autenticação do ERP

Ambos precisam autenticar os mesmos usuários.

## Decisão

**Compartilhar JWT** entre ERP e Espresso usando a mesma `secret key`.

### Implementação

```properties
# application.properties (ambos os backends)
integration.system-token-secret=mesma-chave-secreta-aqui
```

**Fluxo**:
1. Usuário faz login no ERP (`/api/auth/login`)
2. ERP gera JWT com `secret key` compartilhada
3. Usuário usa JWT em requisições para ERP **e** Espresso
4. Ambos validam JWT com a mesma `secret key`

## Consequências

### Positivas ✅

- Single Sign-On (SSO) implícito
- Usuário loga uma vez, acessa ambos sistemas
- Sem necessidade de OAuth2 complexo
- Implementação simples

### Negativas 🔴

- `secret key` hardcoded em 2 lugares
- Rotação de chave requer deploy em ambos
- Se chave vazar, ambos sistemas comprometidos
- Não há revogação centralizada de tokens

### Neutras ⚪

- ERP é fonte da verdade para usuários
- Espresso não tem tabela de usuários própria

---

## Quando Revisar

Esta decisão deve ser revisada quando:

- [ ] **Mudança de topologia** (ERP e Espresso deixam de ser autenticados de forma independente)
- [ ] **OAuth2 centralizado** (Keycloak, Auth0)
- [ ] **Requisito de segurança** (rotação frequente de chaves)

---

## Alternativas Consideradas

### Alternativa 1: OAuth2 Centralizado (Keycloak)

**Prós**:
- Gestão centralizada de usuários
- Rotação de chaves automática
- Revogação de tokens
- MFA, SSO, etc.

**Contras**:
- Complexidade alta
- Infraestrutura adicional
- Curva de aprendizado

**Decisão**: Overkill para 2 clientes

### Alternativa 2: Token Exchange

**Prós**:
- ERP emite token próprio
- Espresso emite token próprio
- Troca de tokens via API

**Contras**:
- Latência adicional
- Complexidade de implementação
- Ponto único de falha

**Decisão**: Não vale a complexidade

### Alternativa 3: Session Sharing (Redis)

**Prós**:
- Sessão centralizada
- Revogação imediata

**Contras**:
- Acoplamento forte
- Redis como dependência crítica
- Não funciona bem com JWT

**Decisão**: JWT é mais simples para nosso caso

---

## Status Atual

**Status**: ✅ **Aceito** (vigente)

Esta decisão funciona bem no estado atual.

**Próxima revisão**: Quando mudar a estratégia de autenticação

---

## Documentos Relacionados

| Documento | Descrição |
|-----------|-----------|
| [`arquitetura_atual.md`](../arquitetura_atual.md) | Estado atual |
| [`VISAO_GERAL_INTEGRACOES.md`](./VISAO_GERAL_INTEGRACOES.md) | Integrações entre sistemas |
| [`decisions/ADR-003-usuarios-centralizados.md`](./ADR-003-usuarios-centralizados.md) | Usuários centralizados no ERP |

---

**Decisão tomada por**: Tech Lead + Equipe  
**Data da decisão**: Março 2026  
**Próxima revisão**: Quando mudar a estratégia de autenticação
