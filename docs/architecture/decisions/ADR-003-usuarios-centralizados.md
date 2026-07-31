# ADR-003: Usuários Centralizados no ERP

**Status**: ✅ Aceito (vigente)  
**Data**: Março 2026  
**Revisão**: Quando mudar a estratégia de autenticação

---

## Contexto

Temos 2 backends e precisamos decidir onde gerenciar usuários:

- **ERP Backend** — Tem tabela `usuarios`, `clientes`, `roles`
- **Espresso Backend** — **Não** tem tabela de usuários própria

## Decisão

**Centralizar usuários no ERP**. Espresso **não** replica tabela de usuários.

### Implementação

**ERP**:
- Tabela `usuarios` (login, senha, roles)
- Tabela `clientes` (dados do cliente)
- Autenticação: `/api/auth/login`

**Espresso**:
- **Sem** tabela de usuários
- Recebe `X-User-ID` no header (extraído do JWT)
- Consulta dados do cliente via API do ERP (se necessário)

## Consequências

### Positivas ✅

- Fonte única da verdade para usuários
- Sem sincronização de dados
- Senha em apenas 1 lugar (mais seguro)
- Roles/permissions centralizadas
- Logout em 1 lugar afeta ambos sistemas

### Negativas 🔴

- Espresso depende do ERP para validar usuário
- Latência adicional se precisar consultar ERP
- ERP é ponto único de falha para autenticação

### Neutras ⚪

- JWT carrega `user_id`, então Espresso não precisa validar usuário
- Cliente pode ser replicado se necessário (leitura)

---

## Quando Revisar

Esta decisão deve ser revisada quando:

- [ ] **Mudança de topologia** (ERP e Espresso deixam de depender do mesmo fluxo de autenticação)
- [ ] **OAuth2 centralizado** (Keycloak, Auth0)
- [ ] **Requisito de offline** (Espresso precisa funcionar sem ERP)

---

## Alternativas Consideradas

### Alternativa 1: Usuários Replicados

**Prós**:
- Espresso funciona independente do ERP
- Menos latência

**Contras**:
- Sincronização complexa
- Dados desincronizados
- Senha em 2 lugares (risco de segurança)

**Decisão**: Não vale a complexidade

### Alternativa 2: OAuth2 Centralizado (Keycloak)

**Prós**:
- Gestão centralizada
- SSO, MFA, etc.
- Backends não gerenciam usuários

**Contras**:
- Infraestrutura adicional
- Complexidade alta

**Decisão**: Overkill para 2 clientes

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
| [`decisions/ADR-002-jwt-compartilhado.md`](./ADR-002-jwt-compartilhado.md) | JWT compartilhado |
| [`VISAO_GERAL_INTEGRACOES.md`](./VISAO_GERAL_INTEGRACOES.md) | Integrações entre sistemas |

---

**Decisão tomada por**: Tech Lead + Equipe  
**Data da decisão**: Março 2026  
**Próxima revisão**: Quando mudar a estratégia de autenticação
