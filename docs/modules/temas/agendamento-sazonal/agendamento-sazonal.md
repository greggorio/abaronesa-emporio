# Agendamento Sazonal

**Status**: EM_DESENVOLVIMENTO

## Quem sou

Sub-domínio de Agendamento Sazonal — programação de temas por período com controle de prioridade. Permite que temas especiais (Natal, Páscoa, Halloween, etc.) sejam ativados automaticamente em datas específicas.

## Para que existo

Automatizar a troca de identidade visual do estabelecimento conforme o calendário sazonal, sem necessidade de intervenção manual. No Natal o app aparece temático; na Páscoa, outro tema entra no lugar automaticamente.

## A quem pertenço

Módulo de **Temas** — o agendamento controla *quando* cada tema fica ativo, enquanto o white-label define *como* cada tema se parece.

## Domínio imediato

- Criação de assignments (tema + período + prioridade)
- Ativação automática: busca assignments com validade atual, ordenados por prioridade
- Substituição: ao agendar um novo tema, assignments anteriores do tenant são desativados
- Prioridade entre temas concorrentes (maior número = maior prioridade)
- Fallback para tema PUBLISHED quando nenhum agendamento está ativo

## Especificação

### ThemeAssignment

Tabela `theme_assignment`. Ver `temas.md` para campos completos.

### Ativação automática

A lógica de ativação está em `ThemeService.getActiveTheme(tenantId)`:

```
1. Busca assignments do tenant onde:
     isActive = true
     AND (validFrom IS NULL OR validFrom <= now)
     AND (validTo IS NULL OR validTo >= now)
   Ordenados por priority DESC

2. Se encontrou assignment:
     retorna theme do assignment de maior prioridade

3. Se não:
     busca último theme com status = PUBLISHED do tenant

4. Se não:
     busca default_tenant_id em tenant_config
     repete passo 3 para o tenant padrão
```

### Fluxo de agendamento

1. Staff acessa `TemasPage.tsx`
2. Seleciona um tema e clica em "Agendar"
3. Define datas de início e fim (validFrom, validTo) e prioridade
4. `POST /api/themes/{id}/schedule`:
   - Desativa todos assignments existentes do tenant (`isActive = false`)
   - Remove assignments antigos deste tema
   - Cria novo `ThemeAssignment`
   - Salva `default_tenant_id` em `tenant_config`
   - Broadcast WebSocket para todos clientes
5. Clientes conectados recebem notificação e recarregam o tema

### Temas sazonais pré-definidos

O sistema não armazena temas sazonais pré-cadastrados — eles são criados manualmente via CRUD e agendados para o período desejado. Exemplos de uso:
- **Natal**: tema com cores vermelho/verde, ativo de 01/12 a 26/12
- **Páscoa**: tema com tons pastel, ativo de 01/03 a 15/04
- **Halloween**: tema laranja/roxo, ativo de 20/10 a 01/11
- **Aniversário do estabelecimento**: tema especial por uma semana

### DTO

`ThemeScheduleDTO` (record): `themeId`, `validFrom`, `validTo`, `priority`.

## Regras

1. **Prioridade**: assignments com maior `priority` prevalecem quando múltiplos temas têm período válido. Útil sobrepor um tema promocional sobre um sazonal
2. **Data nula**: se `validFrom` é nulo, o tema é ativado imediatamente. Se `validTo` é nulo, o tema não expira
3. **Substituição**: ao agendar um novo tema, todos os assignments do tenant são desativados (`isActive = false`). Isso evita conflitos de períodos sobrepostos
4. **Fallback**: quando nenhum agendamento está ativo, o sistema volta para o tema PUBLISHED padrão do tenant (ou do `default_tenant_id`)
5. **Tema arquivado**: um tema com status ARCHIVED não pode ser agendado. Temas DRAFT podem ser agendados mas não devem ser usados em produção (o `getActiveTheme` filtra por PUBLISHED como fallback, mas o assignment pode apontar para qualquer tema)
