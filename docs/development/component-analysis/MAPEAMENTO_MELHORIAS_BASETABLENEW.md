# Mapeamento de Melhorias - BaseTableNew.vue

**Data:** 28/10/2025
**Componente:** `frontend/src/components/utils/BaseTableNew.vue`
**Status:** Análise Completa - Aguardando Implementação

---

## 📋 Sumário Executivo

Este documento mapeia todos os problemas identificados no componente `BaseTableNew.vue` relacionados à formatação e exibição de dados, mantendo a integridade da funcionalidade de filtros. O componente é crítico pois é utilizado por **todas as entidades CRUD do sistema**.

---

## 🔴 Problemas Críticos Identificados

### 1. **INCONSISTÊNCIA DE TIPOS - Renderização de Colunas**

**Localização:** Template, linhas 172-217

**Problema:**
As condicionais de renderização no template usam tipos **internos/mapeados** (`date`, `boolean`, `decimal`, `moeda`), mas o backend envia tipos **em maiúsculas** (`DATETIME`, `DATE`, `BOOLEAN`, `NUMBER`, `CURRENCY`).

**Código Atual (Problemático):**
```vue
<template v-else-if="col.type === 'date'">  <!-- NUNCA SERÁ TRUE -->
  {{ formatDate(props.row[col.dataField || col.field]) }}
</template>

<template v-else-if="col.type === 'boolean'">  <!-- NUNCA SERÁ TRUE -->
  {{ props.row[col.dataField || col.field] ? "Sim" : "Não" }}
</template>

<template v-else-if="col.type === 'decimal'">  <!-- NUNCA SERÁ TRUE -->
  ...
</template>
```

**Tipos Enviados pelo Backend:**
- `TEXT` → deveria ser mapeado para `string`
- `NUMBER` → deveria ser mapeado para `number`
- `CURRENCY` → deveria ser mapeado para `number` (moeda)
- `DATE` → deveria ser mapeado para `date`
- `DATETIME` → deveria ser mapeado para `date`
- `BOOLEAN` → deveria ser mapeado para `boolean`

**Impacto:**
- ❌ Datas exibidas no formato ISO bruto: `2025-10-18T10:32:07.529187`
- ❌ Booleanos exibidos como: `true` / `false` (literal)
- ❌ Números decimais sem formatação adequada
- ❌ Valores monetários sem símbolo R$ e formatação

**Evidência:**
![Imagem 1 - Datas em formato ISO incorreto]
![Imagem 2 - Booleanos como true/false]

---

### 2. **FUNÇÃO getDateLabel NÃO DEFINIDA NO ESCOPO GLOBAL**

**Localização:** Linha 359

**Problema:**
A função `getDateLabel()` é chamada no template, mas está definida **apenas dentro** do componente de renderização `DateFilter` (linhas 603-610), não no escopo global do componente.

**Erro Gerado:**
```
Uncaught (in promise) TypeError: _ctx.getDateLabel is not a function
    at BaseTableNew.vue:359:35
```

**Código Problemático:**
```vue
<!-- Linha 359 -->
<q-input
  v-model="getFilter(col.field).value"
  dense
  outlined
  :label="getDateLabel(getFilter(col.field).operator)"  <!-- ❌ ERRO -->
  type="date"
  clearable
  color="primary"
/>
```

**Impacto:**
- ❌ **QUEBRA o diálogo de filtros** ao abrir filtros avançados em telas com colunas de data
- ❌ Impede usuário de filtrar por datas
- ❌ Sistema fica inutilizável para filtragem de registros por período

---

### 3. **FUNÇÃO setDateToday NÃO DEFINIDA NO ESCOPO GLOBAL**

**Localização:** Linhas 366, 380, 388

**Problema:**
Similar ao problema anterior, a função `setDateToday()` existe apenas dentro do componente `DateFilter` (linhas 594-601), mas é chamada no template principal.

**Código Problemático:**
```vue
<!-- Linha 366 -->
<q-btn dense outline color="primary" icon="today"
  @click="setDateToday(col.field, 'value')"  <!-- ❌ ERRO -->
  size="sm">
```

**Impacto:**
- ❌ Botão "Definir como hoje" não funciona
- ❌ Usuário não consegue usar atalho para filtrar data atual
- ❌ Piora UX de filtros de data

---

### 4. **FUNÇÃO getColumnType NÃO É USADA NA RENDERIZAÇÃO**

**Localização:** Função linha 917, usada apenas em filtros

**Problema:**
Existe uma função `getColumnType()` que **mapeia corretamente** os tipos do backend para tipos internos:

```javascript
function getColumnType(field) {
  const column = props.columns.find((col) => col.field === field || col.name === field);
  if (column?.type) {
    const typeMap = {
      TEXT: "string",
      NUMBER: "number",
      CURRENCY: "number",
      DATE: "date",
      DATETIME: "date",
      BOOLEAN: "boolean",
      decimal: "number",
      moeda: "number",
    };
    const mappedType = typeMap[column.type] || column.type;
    return mappedType;
  }
  // ... fallback
}
```

Mas essa função **só é usada para filtros**, não para renderização no template!

**Impacto:**
- ❌ Duplicação de lógica de detecção de tipos
- ❌ Renderização não aproveita mapeamento correto
- ❌ Manutenção difícil (dois lugares para atualizar tipos)

---

## 🟡 Problemas de Formatação

### 5. **DATAS EM FORMATO ISO - Não Formatadas**

**Problema:**
Valores de data chegam do backend no formato ISO: `2025-10-18T10:32:07.529187`

Como `col.type === 'date'` nunca é verdadeiro (backend envia `DATETIME`), a formatação nunca é aplicada.

**Formato Atual (Incorreto):**
```
2025-10-18T10:32:07.529187
2025-10-19T10:15:25.343834
```

**Formato Desejado:**
```
18/10/2025 10:32:07
19/10/2025 10:15:25
```

Ou apenas data se não houver hora relevante:
```
18/10/2025
19/10/2025
```

**Solução Existente (Não Aplicada):**
Já existe `formatDate()` importada de `useTableUtils` (linha 469), mas não está sendo usada porque a condição `col.type === 'date'` nunca é verdadeira.

---

### 6. **BOOLEANOS EXIBEM true/false LITERAL**

**Problema:**
Valores booleanos são exibidos como texto literal `true` ou `false`.

Como `col.type === 'boolean'` nunca é verdadeiro (backend envia `BOOLEAN`), cai no template padrão que apenas exibe o valor bruto.

**Formato Atual (Incorreto):**
```
true
false
```

**Formatos Desejados (Opções):**

**Opção A - Texto Sim/Não:**
```
Sim
Não
```

**Opção B - Ícones:**
```
✓ (check verde)
✗ (x vermelho)
```

**Opção C - Checkbox Visual (Somente Leitura):**
```
☑ (checkbox marcado)
☐ (checkbox vazio)
```

**Código Existente (Não Aplicado):**
```vue
<template v-else-if="col.type === 'boolean'">  <!-- Nunca executado -->
  {{ props.row[col.dataField || col.field] ? "Sim" : "Não" }}
</template>
```

---

### 7. **VALORES MONETÁRIOS SEM FORMATAÇÃO**

**Problema:**
Valores do tipo `CURRENCY` não estão sendo formatados com símbolo R$ e separadores de milhares/decimais.

**Código Atual:**
```vue
<template v-else-if="col.type === 'moeda' || col.format === 'currency' || isCurrencyColumn(col)">
  <span v-else>
    {{ formatarMoeda(props.row[col.dataField || col.field]) }}
  </span>
</template>
```

**Problema:**
- `col.type === 'moeda'` nunca é verdadeiro (backend envia `CURRENCY`)
- Depende de heurística `isCurrencyColumn()` que pode falhar
- Não trata `col.type === 'CURRENCY'` diretamente

**Formato Atual (Possível):**
```
150.50
1250.00
```

**Formato Desejado:**
```
R$ 150,50
R$ 1.250,00
```

---

## 🟢 Melhorias de UX e Código

### 8. **MAPEAMENTO DE TIPOS DEVE SER CENTRALIZADO**

**Problema:**
O mapeamento de tipos está espalhado:
- Função `getColumnType()` para filtros
- Condicionais diretas no template para renderização
- Heurísticas como `isCurrencyColumn()` para detectar moeda

**Proposta:**
Criar uma **computed property** que retorna o tipo mapeado da coluna:

```javascript
function getMappedColumnType(col) {
  const typeMap = {
    TEXT: "string",
    NUMBER: "number",
    CURRENCY: "currency",
    DATE: "date",
    DATETIME: "datetime",
    BOOLEAN: "boolean",
  };
  return typeMap[col.type] || col.type.toLowerCase();
}
```

Usar no template:
```vue
<template v-else-if="getMappedColumnType(col) === 'date' || getMappedColumnType(col) === 'datetime'">
  {{ formatDate(props.row[col.dataField || col.field]) }}
</template>
```

---

### 9. **FORMATAÇÃO DE DATETIME vs DATE DEVE SER DIFERENCIADA**

**Problema:**
`DATE` e `DATETIME` são mapeados para o mesmo tipo `date`, mas deveriam ter formatações diferentes.

**Proposta:**
- `DATE` → `18/10/2025`
- `DATETIME` → `18/10/2025 10:32:07`

**Código Proposto:**
```javascript
function formatColumnValue(value, col) {
  const type = getMappedColumnType(col);

  if (type === 'date') {
    return formatDate(value); // Só data
  }

  if (type === 'datetime') {
    return formatDateTime(value); // Data + hora
  }

  // ...
}
```

---

### 10. **FUNÇÕES DE FILTRO DE DATA DEVEM ESTAR NO ESCOPO GLOBAL**

**Problema:**
As funções auxiliares para filtros de data estão apenas no componente `DateFilter`, causando erros quando usadas no template principal.

**Solução:**
Mover para o escopo global do script:

```javascript
// Adicionar no script setup
function getDateLabel(operator) {
  const labelMap = {
    equals: "Data",
    after: "A partir de",
    before: "Até",
  };
  return labelMap[operator] || "Data";
}

function setDateToday(field, valueField) {
  const today = formatCurrentDate();
  const filter = getFilter(field);
  if (valueField === 'value') {
    filter.value = today;
  } else {
    filter.value2 = today;
  }
}
```

---

### 11. **CONSISTÊNCIA DE ALINHAMENTO PARA VALORES NUMÉRICOS**

**Localização:** Linha 154

**Código Atual:**
```vue
:class="{
  'text-right': isDouble(props.row[col.dataField || col.field])
    || col.type === 'decimal'
    || col.format === 'currency'
    || isCurrencyColumn(col)
}"
```

**Problema:**
- Usa `col.type === 'decimal'` (nunca verdadeiro)
- Não trata `col.type === 'NUMBER'` ou `col.type === 'CURRENCY'`

**Solução:**
```vue
:class="{
  'text-right': isNumericColumn(col) || isCurrencyColumn(col)
}"
```

Com função auxiliar:
```javascript
function isNumericColumn(col) {
  return ['NUMBER', 'CURRENCY', 'decimal', 'moeda'].includes(col.type);
}
```

---

### 12. **MELHORAR DETECÇÃO DE VALORES DOUBLE/DECIMAL**

**Localização:** Função `isDouble()` importada de `useTableUtils`

**Verificar se a função:**
- Trata corretamente valores nulos/undefined
- Diferencia números inteiros de decimais
- Funciona com números formatados do backend

---

## 📦 Estrutura de Implementação Proposta

### Fase 1: Correções Críticas (Bloqueantes)
1. ✅ Mover `getDateLabel()` para escopo global
2. ✅ Mover `setDateToday()` para escopo global
3. ✅ Criar função `getMappedColumnType(col)` centralizada
4. ✅ Atualizar todas as condicionais de renderização para usar tipos mapeados

### Fase 2: Melhorias de Formatação
5. ✅ Garantir formatação de DATETIME no formato brasileiro
6. ✅ Diferenciar formatação de DATE vs DATETIME
7. ✅ Padronizar exibição de BOOLEAN (definir padrão: Sim/Não, ícones ou checkbox)
8. ✅ Garantir formatação de CURRENCY com R$ e separadores

### Fase 3: Refatoração e Otimização
9. ✅ Centralizar lógica de detecção de tipos
10. ✅ Criar funções auxiliares reutilizáveis
11. ✅ Remover código duplicado
12. ✅ Melhorar alinhamento de colunas numéricas

### Fase 4: Testes e Validação
13. ✅ Testar em todas as entidades CRUD
14. ✅ Validar filtros funcionando corretamente
15. ✅ Verificar compatibilidade com slots customizados
16. ✅ Testar exportação CSV e geração de relatórios PDF

---

## 🎯 Critérios de Aceite

### Para Datas
- [ ] Colunas `DATE` exibem formato: `dd/MM/yyyy`
- [ ] Colunas `DATETIME` exibem formato: `dd/MM/yyyy HH:mm:ss`
- [ ] Filtros de data funcionam corretamente
- [ ] Botão "Definir como hoje" funciona
- [ ] Exportação CSV mantém formato legível
- [ ] Relatório PDF exibe datas formatadas

### Para Booleanos
- [ ] Valores `true` exibem: **Sim** (ou ícone ✓)
- [ ] Valores `false` exibem: **Não** (ou ícone ✗)
- [ ] Filtros de boolean funcionam
- [ ] Exportação CSV usa Sim/Não
- [ ] Relatório PDF exibe Sim/Não

### Para Valores Monetários
- [ ] Valores exibem: `R$ 1.250,50`
- [ ] Alinhamento à direita
- [ ] Filtros numéricos funcionam
- [ ] Exportação CSV mantém formato
- [ ] Relatório PDF formata moeda

### Para Compatibilidade
- [ ] Todas as entidades CRUD funcionam
- [ ] Nenhuma tela quebrou
- [ ] Slots customizados não afetados
- [ ] Performance mantida
- [ ] Sem erros no console

---

## 🔧 Arquivos Relacionados

### Componentes
- `frontend/src/components/utils/BaseTableNew.vue` (principal)
- `frontend/src/components/PrgContainerNew.vue` (container)

### Composables
- `frontend/src/composables/useTableUtils.js` (formatDate, isDouble, etc.)
- `frontend/src/composables/useTableExport.js` (exportação CSV)
- `frontend/src/composables/useReportGenerator.js` (geração PDF)
- `frontend/src/composables/formatarMoeda.js` (formatação moeda)

### Configuração
- `frontend/src/config/tableFilterConfig.js` (operadores de filtro)

### Backend
- Tabela: `dynamic_form_definitions`
- API: `/api/{entity}/form-config`

---

## 📝 Observações Importantes

### Compatibilidade Retroativa
Este componente é o **coração do sistema**, usado por todas as entidades CRUD. Qualquer mudança deve:
- ✅ Manter compatibilidade com código existente
- ✅ Não quebrar slots customizados
- ✅ Preservar funcionalidade de filtros
- ✅ Manter exportação e relatórios funcionando

### Testes Sugeridos
Após implementação, testar em pelo menos:
1. Produtos (tem DATETIME, BOOLEAN, CURRENCY)
2. Mesas (tem DATE, BOOLEAN)
3. Vendas (tem DATETIME, CURRENCY, BOOLEAN)
4. Movimentos de Estoque (tem DATETIME, NUMBER)

### Decisões Pendentes
- [ ] Formato de BOOLEAN: Sim/Não, ícones ou checkbox?
- [ ] Mostrar ou ocultar segundos em DATETIME?
- [ ] Formato de número: sempre 2 casas decimais ou dinâmico?

---

## ✅ Próximos Passos

1. **Revisar este documento** com a equipe
2. **Decidir formato de booleanos** (Sim/Não, ícones ou checkbox)
3. **Priorizar implementação** (começar por problemas críticos)
4. **Implementar correções** fase por fase
5. **Testar extensivamente** em ambiente de desenvolvimento
6. **Validar em produção** com testes controlados

---

**Documento criado em:** 28/10/2025
**Última atualização:** 28/10/2025
**Status:** Aguardando Aprovação para Implementação
