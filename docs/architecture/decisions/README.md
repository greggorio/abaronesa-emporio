# Architecture Decision Records (ADRs)

Esta pasta contém os registros de decisões de arquitetura do projeto.

## O que é um ADR?

Um ADR (Architecture Decision Record) é um documento que captura uma decisão arquitetural importante, junto com seu contexto e consequências.

## ADRs Existentes

| ADR | Título | Status | Data |
|-----|--------|--------|------|
| [ADR-001](./ADR-001-frontend-duo.md) | Frontend Duo (Quasar + React) | ✅ Aceito | Mar 2026 |
| [ADR-002](./ADR-002-jwt-compartilhado.md) | JWT Compartilhado | ✅ Aceito | Mar 2026 |
| [ADR-003](./ADR-003-usuarios-centralizados.md) | Usuários Centralizados no ERP | ✅ Aceito | Mar 2026 |
| [ADR-004](./ADR-004-i18n-strategy.md) | i18n via Job Agendado + OpenAI | ✅ Aceito | Mar 2026 |

## Status dos ADRs

| Status | Significado |
|--------|-------------|
| ✅ **Aceito** | Decisão vigente, implementada |
| ⏳ **Proposto** | Em discussão, não implementado |
| 🔴 **Rejeitado** | Discutido, mas não adotado |
| ⚰️ **Substituído** | Substituído por novo ADR |

## Estrutura do ADR

Cada ADR deve conter:
- **Título**: Nome descritivo da decisão
- **Status**: Proposto, Aceito, Rejeitado, Substituído
- **Contexto**: Situação e forças envolvidas
- **Decisão**: O que foi decidido
- **Consequências**: Resultados e trade-offs
- **Quando Revisar**: Gatilhos para reavaliação

## Template

```markdown
# ADR-XXX: [Título da Decisão]

**Status**: [Proposto | Aceito | Rejeitado | Substituído]  
**Data**: [Data da decisão]  
**Revisão**: [Quando revisar]

## Contexto

[Descreva o contexto e o problema being addressed]

## Decisão

[Descreva a decisão tomada]

## Consequências

### Positivas ✅
### Negativas 🔴
### Neutras ⚪

## Quando Revisar

[Condições para reavaliar esta decisão]

## Alternativas Consideradas

[Descreva alternativas e por que foram rejeitadas]

## Status Atual

[Status atual e próxima revisão]

## Documentos Relacionados

[Links para documentos relacionados]
```

## Como Criar Novo ADR

1. Copie o template acima
2. Nomeie como `ADR-XXX-[titulo-curto].md`
3. Preencha todas as seções
4. Submeta para revisão da equipe
5. Após aprovação, mova para status "Aceito"

---

**Última atualização**: Março 2026  
**Próximo ADR**: A ser criado quando nova decisão arquitetural for tomada
