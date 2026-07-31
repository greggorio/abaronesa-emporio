# ADR-004: i18n via Job Agendado + OpenAI

**Status**: ✅ Aceito (vigente)  
**Data**: Março 2026  
**Revisão**: Quando padronizar i18n (Fase 1)

---

## Contexto

Precisamos de internacionalização (i18n) para:
- Cardápio (PT → EN, ES)
- Interface do app (PT → EN, ES)
- Conteúdo gerado pelo cliente (produtos, categorias)

## Decisão

**i18n via job agendado + OpenAI** no ERP, consumido pelo Espresso via API.

### Implementação

**ERP**:
- Tabela `entity_translation` (entidades + traduções)
- `TranslationService` (traduz entidades)
- `TranslationJobService` (job agendado que chama OpenAI)
- Job: `TRANSLATION_SYNC` (roda a cada hora)

**Espresso**:
- `I18nService` (consome traduções do ERP)
- Header `Accept-Language` define idioma da resposta

**Fluxo**:
1. Cliente cadastra produto em português
2. `TranslationService.markSourceChanged()` detecta mudança
3. Cria registros `PENDING` em `entity_translation`
4. Job agendado chama OpenAI para traduzir
5. Traduções salvas com status `OK`
6. Espresso consulta com `Accept-Language: en-US`
7. API retorna texto traduzido

## Consequências

### Positivas ✅

- Tradução automática (sem trabalho manual)
- Job agendado não bloqueia operações
- OpenAI gera traduções de qualidade
- ERP é fonte da verdade para traduções

### Negativas 🔴

- Custo do OpenAI (por token)
- Tradução pode demorar (job não é instantâneo)
- Dependência de API externa (OpenAI)
- Espresso e ERP têm implementações diferentes

### Neutras ⚪

- Traduções manuais podem sobrescrever automáticas
- Status `MANUAL` vs. `OK` para diferenciar origem

---

## Quando Revisar

Esta decisão deve ser revisada quando:

- [ ] **Padronizar i18n** (Fase 1: mesma estratégia para ERP e Espresso)
- [ ] **Custo OpenAI > $50/mês** (buscar alternativas)
- [ ] **Requisito de tradução instantânea** (job não atende)

---

## Alternativas Consideradas

### Alternativa 1: Tradução Manual (Admin)

**Prós**:
- Sem custo de API
- Tradução humana (qualidade)

**Contras**:
- Trabalho manual
- Escala ruim (1000+ produtos)
- Erros humanos

**Decisão**: Não escala

### Alternativa 2: Google Translate API

**Prós**:
- Mais barato que OpenAI
- Tradução instantânea

**Contras**:
- Qualidade inferior para contexto gastronômico
- Não gera conteúdo, só traduz

**Decisão**: OpenAI tem melhor custo-benefício

### Alternativa 3: DeepL API

**Prós**:
- Qualidade superior para PT/EN/ES
- Mais barato que OpenAI

**Contras**:
- Menos idiomas
- API menos madura

**Decisão**: OpenAI é mais flexível

---

## Status Atual

**Status**: ✅ **Aceito** (vigente)

**Gap relacionado**: [`gaps.md`](../gaps.md) — B-003 (i18n diferente entre ERP e Espresso)

**Próxima revisão**: Fase 1 (padronização i18n)

---

## Documentos Relacionados

| Documento | Descrição |
|-----------|-----------|
| [`arquitetura_atual.md`](../arquitetura_atual.md) | Estado atual |
| [`gaps.md`](../gaps.md) | Gap B-003: i18n diferente |
| [`roadmap.md`](../roadmap.md) | Fase 1: Padronização |
| [`development/I18N_ENTITY_TRANSLATIONS.md`](../development/I18N_ENTITY_TRANSLATIONS.md) | Documentação de i18n |

---

**Decisão tomada por**: Tech Lead + Equipe  
**Data da decisão**: Março 2026  
**Próxima revisão**: Fase 1 (Q2 2026)
