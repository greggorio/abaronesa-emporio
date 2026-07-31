# Gaps Arquiteturais

> **Status**: 📋 Inventário do que falta implementar  
> **Última atualização**: Março 2026

---

## Visão Geral

Este documento lista os **gaps** entre a arquitetura atual e a arquitetura alvo. Cada gap representa trabalho a ser feito.

**Princípio**: **Manter separação entre ERP e Digital Experience** — gaps são melhorias dentro de cada sistema, não unificação.

**Arquitetura atual**: [`arquitetura_atual.md`](./arquitetura_atual.md)  
**Arquitetura alvo**: [`arquitetura_alvo.md`](./arquitetura_alvo.md)

---

## Gaps por Categoria

### 1. Frontend

| ID | Gap | Impacto | Esforço | Prioridade | Sistema |
|----|-----|---------|---------|------------|---------|
| **F-001** | Componentes de UI não compartilhados | Médio | Médio | 🟡 Baixa | Ambos |
| **F-002** | Design system não documentado | Baixo | Baixo | 🟢 Ignorar | Ambos |
| **F-003** | Build e deploy manuais | Baixo | Baixo | 🟢 Ignorar | Ambos |

#### Detalhe: F-001 (Componentes não compartilhados)

**Problema**: Componentes básicos (forms, tabelas, modais) podem ser duplicados.

**Consequências**:
- Pequena duplicação de esforço
- Inconsistência visual possível

**Solução Alvo**: **NÃO unificar** — cada frontend tem propósito diferente.

**Critérios para Resolver**:
- [ ] Documentar componentes de cada frontend
- [ ] Identificar componentes verdadeiramente compartilháveis
- [ ] Criar biblioteca compartilhada (opcional, só se necessário)

**Decisão**: Manter separação. Duplicação é aceitável para independência.

---

### 2. Backend

| ID | Gap | Impacto | Esforço | Prioridade | Sistema |
|----|-----|---------|---------|------------|---------|
| **B-001** | APIs não documentadas (OpenAPI) | Alto | Médio | 🔴 Alta | Ambos |
| **B-002** | i18n implementado de forma diferente | Médio | Médio | 🟡 Alta | Ambos |
| **B-003** | Health checks não padronizados | Médio | Baixo | 🟡 Alta | Ambos |
| **B-004** | Testes de integração inexistentes | Alto | Alto | 🔴 Alta | Ambos |

#### Detalhe: B-001 (APIs não documentadas)

**Problema**: APIs não têm contrato formal (OpenAPI/Swagger).

**Consequências**:
- Frontend depende de "tentativa e erro"
- Mudanças quebram clientes sem aviso
- Dificuldade de onboarding

**Solução Alvo**: OpenAPI 3.0 para todas as APIs.

**Critérios para Resolver**:
- [ ] Adicionar `springdoc-openapi` (backend Java)
- [ ] Documentar endpoints existentes
- [ ] Versionar APIs (`/api/v1/...`)
- [ ] Publicar Swagger UI

#### Detalhe: B-002 (i18n Diferente)

**Problema**: 2 implementações de i18n:

| Dimensão | ERP | Espresso |
|----------|-----|----------|
| **Serviço** | `TranslationService` | `I18nService` |
| **Job** | `TranslationJobService` (agendado) | ❌ Não tem |
| **Tabela** | `entity_translation` | ❌ Consome via API |
| **OpenAI** | ✅ Integrado | ❌ Não tem |

**Solução Alvo**: i18n padronizado, com ERP como fonte da verdade e Espresso consumindo por contrato.

**Critérios para Resolver**:
- [ ] Padronizar `TranslationService` + `I18nService` (mesma estratégia)
- [ ] Mesma tabela `entity_translation`
- [ ] Mesmo job agendado
- [ ] Mesma API de consulta

#### Detalhe: B-003 (Health Checks não Padronizados)

**Problema**: Cada backend tem health check diferente (ou não tem).

**Solução Alvo**: Endpoint `/health` padronizado em todos os serviços.

**Critérios para Resolver**:
- [ ] Definir estrutura de resposta padrão
- [ ] Implementar em todos os backends
- [ ] Integrar com monitoramento

#### Detalhe: B-004 (Testes de Integração)

**Problema**: Testes de integração inexistentes.

**Consequências**:
- Regressões frequentes
- Medo de refatorar
- Deploy arriscado

**Solução Alvo**: Suite de testes de integração para ambos sistemas.

**Critérios para Resolver**:
- [ ] Definir framework de testes (JUnit 5 + Testcontainers)
- [ ] Criar testes para fluxos críticos
- [ ] Integrar com CI/CD
- [ ] Code coverage medido

---

### 3. Banco de Dados

| ID | Gap | Impacto | Esforço | Prioridade | Sistema |
|----|-----|---------|---------|------------|---------|
| **D-001** | Migrações manuais de banco | Médio | Baixo | 🟡 Média | Ambos |
| **D-002** | Backup não automatizado | Alto | Baixo | 🔴 Alta | Infra |

#### Detalhe: D-001 (Migrações Manuais)

**Problema**: Migrações de banco são executadas manualmente.

**Solução Alvo**: Flyway automatizado em ambos sistemas.

**Critérios para Resolver**:
- [ ] Configurar Flyway em ambos backends
- [ ] Scripts de migração versionados
- [ ] Validação pré-deploy

---

### 4. Integrações

| ID | Gap | Impacto | Esforço | Prioridade | Sistema |
|----|-----|---------|---------|------------|---------|
| **I-001** | API Gateway não implementado | Médio | Baixo | 🟡 Alta | Infra |
| **I-002** | WebSocket não padronizado | Médio | Baixo | 🟡 Média | Espresso |
| **I-003** | Eventos não têm schema definido | Baixo | Baixo | 🟢 Baixa | Ambos |

#### Detalhe: I-001 (API Gateway)

**Problema**: Não há reverse proxy único.

**Solução Alvo**: Nginx como API Gateway simples.

**Critérios para Resolver**:
- [ ] Configurar Nginx como reverse proxy
- [ ] Rotas: `/api/erp/*` → ERP, `/api/app/*` → Espresso
- [ ] SSL centralizado
- [ ] Rate limiting básico

---

### 5. DevOps / Infra

| ID | Gap | Impacto | Esforço | Prioridade | Sistema |
|----|-----|---------|---------|------------|---------|
| **O-001** | 2 deploys separados | Baixo | Baixo | 🟢 Ignorar | Infra |
| **O-002** | Rollback manual | Alto | Baixo | 🔴 Alta | Infra |
| **O-003** | Scripts de deploy não documentados | Médio | Baixo | 🟡 Média | Infra |

#### Detalhe: O-001 (2 Deploys Separados)

**Problema**: ERP e Espresso têm deploys separados.

**Decisão**: **NÃO unificar** — independência de deploy é vantagem.

**Justificativa**:
- ERP pode deployar sem afetar Espresso
- Equipes trabalham em paralelo
- Rollback independente

#### Detalhe: O-002 (Rollback Manual)

**Problema**: Rollback é manual e propenso a erro.

**Solução Alvo**: Script de rollback automatizado.

**Critérios para Resolver**:
- [ ] Script `rollback.sh` para ERP
- [ ] Script `rollback.sh` para Espresso
- [ ] Teste de rollback trimestral

---

### 6. Qualidade / Testes

| ID | Gap | Impacto | Esforço | Prioridade | Sistema |
|----|-----|---------|---------|------------|---------|
| **Q-001** | Testes de contrato de API inexistentes | Alto | Médio | 🔴 Alta | Ambos |
| **Q-002** | Code coverage não medido | Médio | Baixo | 🟡 Média | Ambos |
| **Q-003** | Testes de carga inexistentes | Baixo | Médio | 🟢 Baixa | Ambos |

---

## Resumo por Prioridade

### 🔴 Alta Prioridade (Resolver em Q2-Q3 2026)

| ID | Gap | Sistema | Fase |
|----|-----|---------|------|
| **B-001** | APIs não documentadas (OpenAPI) | Ambos | Fase 1 |
| **B-002** | i18n diferente | Ambos | Fase 1 |
| **B-004** | Testes de integração inexistentes | Ambos | Fase 2-3 |
| **O-002** | Rollback manual | Infra | Infra Fase 2 |
| **Q-001** | Testes de contrato inexistentes | Ambos | Fase 1 |
| **D-002** | Backup não automatizado | Infra | Infra Fase 1 |

### 🟡 Média Prioridade (Resolver em Q3-Q4 2026)

| ID | Gap | Sistema | Fase |
|----|-----|---------|------|
| **B-003** | Health checks não padronizados | Ambos | Fase 1 |
| **I-001** | API Gateway não implementado | Infra | Fase 1 |
| **I-002** | WebSocket não padronizado | Espresso | Fase 3 |
| **D-001** | Migrações manuais de banco | Ambos | Fase 2-3 |
| **O-003** | Scripts de deploy não documentados | Infra | Infra Fase 1 |
| **Q-002** | Code coverage não medido | Ambos | Fase 2-3 |

### 🟢 Baixa Prioridade (Resolver em 2027+)

| ID | Gap | Sistema | Fase |
|----|-----|---------|------|
| **F-001** | Componentes de UI não compartilhados | Ambos | Opcional |
| **I-003** | Eventos sem schema | Ambos | Fase 4 |
| **Q-003** | Testes de carga | Ambos | Fase 3 |

### 🟢 Ignorar (Não é problema real)

| ID | Gap | Justificativa |
|----|-----|---------------|
| **F-002** | Design system não documentado | Cada frontend tem propósito diferente |
| **F-003** | Build e deploy manuais | Funciona bem hoje |
| **O-001** | 2 deploys separados | **Vantagem**, não problema — independência |

---

## Plano de Ação

### Q2 2026 (Fase 1: Padronização)

1. **Padronizar i18n** (B-002)
   - Unificar `TranslationService` + `I18nService`
   - Mesma tabela, mesmo job

2. **Documentar APIs** (B-001)
   - OpenAPI/Swagger em ambos
   - Versionamento (`/api/v1/...`)

3. **Criar API Gateway** (I-001)
   - Nginx como reverse proxy único
   - Rotas: `/api/erp/*`, `/api/app/*`

4. **Health checks padronizados** (B-003)
   - Endpoint `/health` em todos os serviços
   - Mesma estrutura de resposta

5. **Testes de contrato** (Q-001)
   - Suite de testes para APIs

### Q3-Q4 2026 (Fases 2-3: Melhorar Cada Sistema)

**ERP**:
- Módulo de Pedidos de Compra
- Módulo de Signage Digital
- Dashboard de Gamificação
- Testes de integração

**Espresso**:
- Mesa Digital (Self-Checkout)
- Delivery (Uber Direct)
- Quiz Ao Vivo (WebSocket)
- KDS / Waiter
- Testes de integração

---

## Métricas de Progresso

| Métrica | Atual | Meta Q2 2026 | Meta Q4 2026 | Meta Q2 2027 |
|---------|-------|--------------|--------------|--------------|
| **Gaps resolvidos** | 0/18 | 6/18 | 14/18 | 18/18 |
| **Gaps críticos** | 6 | 2 | 0 | 0 |
| **APIs documentadas** | 0% | 100% | 100% | 100% |
| **Testes de integração** | 0% | 0% | 80% | 100% |

---

## Documentos Relacionados

| Documento | Descrição |
|-----------|-----------|
| [`arquitetura_atual.md`](./arquitetura_atual.md) | Estado atual |
| [`arquitetura_alvo.md`](./arquitetura_alvo.md) | Arquitetura alvo (manter separação) |
| [`roadmap.md`](./roadmap.md) | Roadmap de evolução |
| [`decisions/`](./decisions/) | Architecture Decision Records (5 ADRs) |
| [`../infrastructure/roadmap.md`](../infrastructure/roadmap.md) | Roadmap de infraestrutura |

---

**Última atualização**: Março 2026  
**Próxima revisão**: Abril 2026 (após padronização i18n)
