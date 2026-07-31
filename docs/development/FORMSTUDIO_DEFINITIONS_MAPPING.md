# FormStudio - Motor de Geração Dinâmica de Formulários

## Definições

- **FormStudio**: Nome da solução completa de geração dinâmica de formulários e listagens
- **FormBuilder**: Ferramenta de design (UI) dentro do FormStudio onde o usuário configura as definições de formulários

## Visão Geral do Sistema

O **FormStudio** é um motor de geração dinâmica de formulários e listagens que opera em três camadas principais:

1. **Definição** (FormBuilder): Interface visual onde o usuário configura como cada entidade deve ser exibida
2. **Armazenamento**: Persistência em banco de dados (JSONB) das configurações
3. **Renderização**: Backend converte o JSON em formato entendível pelo Quasar, frontend desenha a tela

---

## Arquitetura de Dados

### Tabela `dynamic_form_definitions`

**Localização**: `backend/src/main/resources/db/migration/V2__dynamic_form_definitions.sql`

```
Campos principais:
- id: UUID (PK)
- entity_type: VARCHAR(100) UNIQUE - identificador único da entidade (ex: "produtos", "clientes")
- program_name: VARCHAR(200) - nome exibido na UI
- program_icon: VARCHAR(50) - ícone do menu
- table_order: VARCHAR(100) - ordenação da listagem
- complexity: VARCHAR(20) - complexidade do formulário
- form_structure: JSONB - tabs e actions
- table_columns: JSONB - colunas da listagem
- dialog_config: JSONB - configurações do formulário
- custom_slots: JSONB - slots customizados
- active: BOOLEAN - ativar/desativar
```

### Estrutura JSON do form_structure

O JSON armazena a estrutura visual do formulário:
- **tabs**: abas do formulário (cada aba contém campos)
- **actions**: ações disponíveis (criar, editar, excluir, customizadas)
- **field_layout**: posição e organização dos campos

---

## RegistryEntry - Mapeamento EntityType ↔ DTO

**Arquivo**: `backend/src/main/java/com/smartdata/bares/dynamicform/registry/EntityRegistryService.java`

O `EntityRegistryService` faz a ligação entre o identificador textual e a classe concreta:

```java
entityMap.put("produtos", ProdutoFormFields.class);
entityMap.put("clientes", ClienteFormFields.class);
entityMap.put("usuarios-admin", FuncionarioFormFields.class);
entityMap.put("contas-pagar", ContaPagarFormBuilderDTO.class);
entityMap.put("vendas", VendaFormRequest.class);
```

### DTOs do FormBuilder disponíveis

| Entity Type | Classe DTO |
|-------------|------------|
| produtos | ProdutoFormFields.java |
| clientes | ClienteFormFields.java |
| usuarios-admin | FuncionarioFormFields.java |
| contas-pagar | ContaPagarFormBuilderDTO.java |
| vendas | VendaFormRequest.java |
| usuarios | UsuarioAdminFormRequest.java |

Cada DTO contém anotações ou estrutura que define:
- Quais campos estão disponíveis
- Tipos de dados de cada campo
- Validações
- Labels

---

## Fluxo de Execução

### 1. Carregamento da Definição (Backend)

```
Request: GET /api/{entity}/form-config
         ↓
DynamicFormDefinition.findByEntityType(entity)
         ↓
DynamicFormConfig.convert(formStructure)
         ↓
FieldTypeMapper.mapField(cada campo)
         ↓
Response: { "form_definitions_new": { tabs, actions, tableColumns } }
```

**DynamicFormConfig**: Classe que converte a estrutura do banco para formato frontend. Aplica o `FieldTypeMapper` em cada campo.

**FieldTypeMapper**: Mapeia tipos internos para componentes Quasar:
- TEXT → QInput
- NUMBER → QInput type="number"
- DATE → QInput com máscara de data
- SELECT → QSelect
- CHECKBOX → QToggle
- etc.

### 2. Frontend - Listagem

```
Response do backend
         ↓
useDynamicTable extrai form_definitions_new
         ↓
PrgContainerNew → BaseTableNew
         ↓
Renderiza tabela com columns e dados
```

**BaseTableNew**: Componente genérico que recebe:
- `rows`: dados da API
- `columns`: estrutura das colunas (do form_definitions)
- `pagination`: config de paginação
- `filter`: configuração de filtros

### 3. Frontend - Formulário

```
Ao clicar em "novo" ou "editar"
         ↓
GenericFormDialog
         ↓
FormField.vue (para cada campo)
         ↓
Renderiza baseado em tabs e field_config
```

---

## Ciclo de Vida do FormBuilder (Fluxo Cronológico)

### 1. CADASTRO DA ENTIDADE (Desenvolvedor)
O desenvolvedor registra a entidade no **EntityRegistryService** mapeando `entityType` → classe DTO
```
produtos → ProdutoFormFields
clientes → ClienteFormFields
contas-pagar → ContaPagarFormBuilderDTO
```

### 2. DESCOBERTA DE CAMPOS (Frontend)
O **EntityDiscoveryService** varre os campos do DTO e apresenta ao usuário quais campos estão disponíveis para uso

### 3. DESIGN DO FORMULÁRIO (Usuário)
Usuário acessa o **FormBuilder** e customiza:
- Quais campos exibir
- Organização em abas (tabs)
- Labels e validações
- Layout visual

### 4. CONFIGURAÇÃO DA LISTAGEM (Usuário)
No mesmo FormBuilder, usuário define:
- Colunas da **BaseTableNew**
- Ordenação default
- Filtros disponíveis
- Ações da toolbar

### 5. SALVAMENTO
O **FormBuilderService** envia o JSON para o backend, que é persistido na tabela `dynamic_form_definitions`:
- `form_structure` → tabs e fields
- `table_columns` → colunas da listagem
- `dialog_config` → configurações do formulário

### 6. CARREGAMENTO (Runtime)
Quando o usuário acessa a listagem:
1. Request → `/api/{entity}/form-config`
2. Backend busca JSON no banco
3. **DynamicFormConfig** converte para formato Quasar
4. **FieldTypeMapper** mapeia tipos para componentes
5. Retorna `form_definitions_new`

### 7. RENDERIZAÇÃO
- **PrgContainerNew** recebe a config
- **BaseTableNew** desenha a listagem
- **GenericFormDialog** desenha o formulário

### 8. EXECUÇÃO (CRUD)
- Listagem com paginação e filtros
- Create/Update/Delete através do formulário dinâmico
- Validações conforme configurado

---

## Componentes Principais

### Backend (Java/Spring)

| Componente | Arquivo | Função |
|------------|---------|--------|
| DynamicFormDefinition | `dynamicform/entity/DynamicFormDefinition.java` | Entity JPA da tabela |
| DynamicFormService | `dynamicform/service/DynamicFormService.java` | CRUD das definições |
| FormDefinitionLoaderService | `dynamicform/service/FormDefinitionLoaderService.java` | Carrega/salva definições |
| DynamicFormConfig | `dynamicform/config/DynamicFormConfig.java` | Converte JSON → formato frontend |
| FieldTypeMapper | `dynamicform/config/FieldTypeMapper.java` | Mapeia tipos para componentes Quasar |
| HybridFormConfigRegistry | `dynamicform/config/HybridFormConfigRegistry.java` | Decisão dinâmica vs estática |
| EntityRegistryService | `dynamicform/registry/EntityRegistryService.java` | Mapeamento entity → classe |
| FormBuilderController | `dynamicform/controller/FormBuilderController.java` | Endpoints REST |

### Frontend (Vue/Quasar)

| Componente | Arquivo | Função |
|------------|---------|--------|
| formBuilderStore | `stores/formBuilderStore.js` | Estado reativo da definição |
| FormBuilderService | `services/form-builder/FormBuilderService.js` | API para salvar/carregar |
| EntityDiscoveryService | `services/form-builder/EntityDiscoveryService.js` | Detecta campos disponíveis |
| FieldTypeMapper | `services/form-builder/FieldTypeMapper.js` | Mapeia tipos Java → Quasar |
| FormDesigner | `components/form-builder/FormDesigner.vue` | UI de design do formulário |
| FieldEditor | `components/form-builder/FieldEditor.vue` | Editor de campos |
| TabManager | `components/form-builder/TabManager.vue` | Gerenciador de abas |
| TableConfig | `components/form-builder/TableConfig.vue` | Configuração de colunas |
| BaseTableNew | `components/utils/BaseTableNew.vue` | Componente de listagem |
| GenericFormDialog | `components/forms/GenericFormDialog.vue` | Dialog de formulário |
| PrgContainerNew | `components/PrgContainerNew.vue` | Container principal |
| useDynamicTable | `composables/useDynamicTable.js` | Composables para buscar config |

---

## Filtro e Paginação

### 1. Paginação Inteligente (Frontend)

O sistema possui paginação adaptativa que detecta a altura do dispositivo e ajusta a quantidade de registros automaticamente.

**Arquivo**: `frontend/src/composables/useDynamicTable.js`

**Cálculo de rows baseado na altura da tela:**
```javascript
function calculateRowsPerPage() {
  const availableHeight = window.innerHeight * 0.8;  // 80% da altura visível
  const headerHeight = 50;
  const paginationHeight = 40;
  const extraMargin = 10;
  const rowHeight = 33; // altura padrão de cada linha

  const calculatedRows = Math.max(1, Math.floor(
    (availableHeight - headerHeight - paginationHeight - extraMargin) / rowHeight
  ));

  dynamicRowsPerPage.value = calculatedRows;
  pagination.value.rowsPerPage = calculatedRows;
}
```

**Redimensionamento automático:**
```javascript
function handleResize() {
  const oldRowsPerPage = pagination.value.rowsPerPage;
  calculateRowsPerPage();

  if (oldRowsPerPage !== pagination.value.rowsPerPage) {
    pagination.value.page = 1;
    loadData();  // novo request com nova quantidade de linhas
  }
}

window.addEventListener("resize", handleResize);
```

**Nota**: O código atual NÃO possui debounce/throttle - cada evento de resize dispara verificação imediata.

### 2. Filtros Compostos (Backend)

O backend suporta filtros complexos com operadores específicos por tipo de dado.

**Arquivo**: `backend/src/main/java/com/smartdata/bares/util/FilterSpecificationBuilder.java`

**Operadores por tipo de campo:**

| Tipo | Operadores |
|------|------------|
| String | `contains`, `equals`, `startsWith`, `endsWith`, `notContains` |
| Number | `equals`, `notEquals`, `greaterThan`, `greaterThanOrEqual`, `lessThan`, `lessThanOrEqual`, `between` |
| Date | `today`, `equals`, `before`, `after`, `between` |
| Boolean | `equals` (true/false) |
| Special | `$quick` - busca rápida em todos os campos String |

**Formato JSON dos filtros:**
```json
{
  "nome": { "operator": "contains", "value": "teste" },
  "precoVenda": { "operator": "greaterThan", "value": 10 },
  "dataCriacao": { "operator": "between", "value": "2024-01-01", "value2": "2024-12-31" },
  "$quick": { "value": "termo" }
}
```

**Query params da API:**
```
GET /api/{entity}?pagina=0&tamanho=15&ordenacao=nome&direcao=asc&filter={...}
```

### 3. Backend - Serviços de Paginação

**BaseListService.java**: Serviço base que aplica paginação e filtros
```java
Pageable pageable = PageRequest.of(pagina, tamanho, sort);
Specification<T> spec = new FilterSpecificationBuilder<>(getEntityClass(), getFieldMappings()).build(filtroJson);
Page<T> page = getRepository().findAll(spec, pageable);
```

### 4. Componentes Relacionados

| Arquivo | Função |
|---------|--------|
| `frontend/src/composables/useDynamicTable.js` | Paginação adaptativa e resize handler |
| `frontend/src/components/utils/BaseTableNew.vue` | Componente de tabela com filtros |
| `frontend/src/components/PrgContainerNew.vue` | Container que inicializa paginação |
| `frontend/src/config/tableFilterConfig.js` | Operadores de filtro disponíveis |
| `backend/.../BaseListService.java` | Serviço base de paginação |
| `backend/.../FilterSpecificationBuilder.java` | Processamento de filtros compostos |

### 5. Distinção entre Filtro e Busca

O sistema faz uma distinção clara entre dois tipos de busca:

#### Busca Local (`localFilter`)
- **Escopo**: Dados que já estão em memória (paginação atual)
- **Request ao Backend**: Não
- **Localização**: `BaseTableNew.vue`

```javascript
const localFilter = ref(filter.value);

// Filtra linhas já carregadas na memória
if (localFilter.value && !Object.keys(row).some((key) =>
  String(row[key]).toLowerCase().includes(localFilter.value.toLowerCase()))) {
  // linha ocultada
}
```

- Campo "quick-search-input" no header (PrgContainerNew)
- Não faz nova request - filtra apenas os dados já exibidos

#### Filtro Avançado (`filter`)
- **Escopo**: Banco de dados completo
- **Request ao Backend**: Sim
- **Localização**: `useDynamicTable.js`

```javascript
if (filters && Object.keys(filters).length > 0) {
  queryParams.push(`filter=${encodeURIComponent(JSON.stringify(filters))}`);
}
```

- Enviado como query param `filter`
- Executa nova query no banco com operadores compostos

#### Busca Rápida (`$quick`)
- **Escopo**: Banco de dados completo
- **Request ao Backend**: Sim

Operador especial que faz OR em todos os campos String da entidade:
```json
{
  "$quick": { "value": "termo" }
}
```

**Resumo comparativo:**

| Tipo | Escopo | Request ao Backend |
|------|--------|-------------------|
| `localFilter` | Dados em memória (paginação atual) | Não |
| `filter` (avançado) | Banco de dados completo | Sim |
| `$quick` | Banco de dados completo | Sim |

### 6. Mecanismo de Filtros no BaseTableNew

O BaseTableNew monta as opções de filtro dinamicamente com base nas definições de coluna recebidas do backend.

#### Fluxo de Dados

1. **Backend** - Responde com o tipo de cada coluna:
```json
{
  "table_definitions": {
    "columns": [
      { "name": "nome", "type": "TEXT", "label": "Nome" },
      { "name": "preco", "type": "CURRENCY", "label": "Preço" },
      { "name": "dataCadastro", "type": "DATE", "label": "Data" },
      { "name": "ativo", "type": "BOOLEAN", "label": "Ativo" }
    ]
  }
}
```

2. **generateColumns** (`useTableColumns.js`):
```javascript
const tipo = param.type || param.tipo || "text";
return {
  name: campo,
  type: tipo,  // Propagado para a coluna
  ...
};
```

3. **getColumnType** (`BaseTableNew.vue`) - Detecção de tipo:
```javascript
function getColumnType(field) {
  const column = props.columns.find((col) => col.field === field || col.name === field);
  if (column?.type) {
    const typeMap = {
      TEXT: "string", NUMBER: "number", CURRENCY: "number",
      DATE: "date", DATETIME: "date", BOOLEAN: "boolean"
    };
    return typeMap[column.type] || column.type;
  }
}
```

#### Operadores por Tipo (`tableFilterConfig.js`)

| Tipo | Operadores |
|------|------------|
| **string** | contains, equals, startsWith, endsWith, notContains |
| **number** | equals, notEquals, greaterThan, greaterThanOrEqual, lessThan, lessThanOrEqual, between |
| **date** | today, equals, after, before, between |
| **boolean** | equals (Sim/Não) |

#### Mapeamento de Tipos Backend → Interno

| Backend Type | Tipo Interno | Componente UI |
|--------------|--------------|---------------|
| TEXT, text | string | QSelect (operador) + QInput (valor) |
| NUMBER, CURRENCY, decimal | number | QSelect (operador) + QInput (valor) + QInput (value2 para between) |
| DATE, DATETIME | date | QSelect (operador) + QInput type="date" + botão "hoje" |
| BOOLEAN | boolean | QSelect com opções Sim/Não |

#### Arquivos Envolvidos

| Arquivo | Função |
|---------|--------|
| `frontend/src/composables/useTableColumns.js` | Gera colunas propagando o tipo |
| `frontend/src/components/utils/BaseTableNew.vue` | Detecta tipo e monta UI de filtros |
| `frontend/src/config/tableFilterConfig.js` | Definição de operadores por tipo |

### 7. Geração de Relatórios

O BaseTableNew oferece funcionalidade de geração de relatórios PDF baseados nos dados visíveis e filtros ativos.

#### Fluxo de Execução

1. **Usuário solicita relatório** (menu dropdown no BaseTableNew)
2. **BaseTableNew** dispara evento com filtros ativos
3. **PrgContainerNew** carrega TODOS os dados (sem paginação)
4. **useReportGenerator** gera PDF com jsPDF

#### Detalhes do Fluxo

1. **BaseTableNew** - Botão de relatório:
```javascript
emit("generate-full-report", { filters, localFilter }, async (fullData) => {
  await generateReport({ ... });
});
```

2. **PrgContainerNew** - Carrega dados completos:
```javascript
async function handleGenerateFullReport({ filters, localFilter }, callback) {
  const allData = await loadFullData(filters);  // semPaginacao=true
  callback(allData);
}
```

3. **loadFullData** (useDynamicTable.js):
```javascript
async function loadFullData(advancedFilters = null) {
  let url = `/api/${endpoint.value}?semPaginacao=true`;
  if (filters) {
    url += `&filter=${encodeURIComponent(JSON.stringify(filters))}`;
  }
}
```

4. **generateReport** (useReportGenerator.js) - Gera PDF:
- Cria container HTML temporário invisível
- Monta tabela com colunas visíveis
- Detecta orientação automaticamente (>5 colunas = landscape)
- Exibe filtros aplicados no header do relatório
- Paginação automática (14 linhas landscape, 20 portrait)
- Exibe total de registros na última página
- Usa jsPDF para renderizar

#### Features do Relatório

| Feature | Descrição |
|---------|-----------|
| Orientação automática | >5 colunas = landscape, caso contrário portrait |
| Filtros descritos | Exibe filtros ativos no header do relatório |
| Total de registros | Exibe contagem na última página |
| Formatação de dados | Currency, date, datetime, boolean, numbers |
| Paginação | Quebra automática por página (14/20 linhas) |

#### Componentes Envolvidos

| Arquivo | Função |
|---------|--------|
| `BaseTableNew.vue` | Botões de relatório, emite evento com filtros |
| `PrgContainerNew.vue` | Captura evento, carrega dados completos |
| `useDynamicTable.js` | `loadFullData()` - busca todos os dados sem paginação |
| `useReportGenerator.js` | `generateReport()` - gera PDF com jsPDF |
| `useTableExport.js` | `exportToCSV()` - exportação CSV |

---

## Estrutura de Resposta da API

```json
{
  "form_definitions_new": {
    "tabs": [
      {
        "id": "tab-principal",
        "label": "Principal",
        "fields": [
          {
            "name": "nome",
            "type": "TEXT",
            "label": "Nome",
            "required": true,
            "component": "QInput",
            "props": { "outlined": true }
          }
        ]
      }
    ],
    "actions": [
      { "id": "create", "label": "Criar", "icon": "add" },
      { "id": "edit", "label": "Editar", "icon": "edit" },
      { "id": "delete", "label": "Excluir", "icon": "delete" }
    ],
    "tableColumns": [
      { "name": "id", "label": "ID", "field": "id", "align": "left" },
      { "name": "nome", "label": "Nome", "field": "nome", "align": "left", "sortable": true }
    ]
  }
}
```

### 8. Componentes Customizados em Abas

O sistema permite adicionar abas com componentes Vue customizados que recebem o `recordId` do registro para exibir informações específicas.

#### 8.1 Configuração no FormBuilder

No TabManager.vue, o usuário pode definir um componente customizado para a aba:

```javascript
// TabManager.vue - Formulário de edição de aba
tabForm: {
  name: "",
  label: "",
  icon: "folder",
  component: ""  // Nome do componente customizado (ex: "PermissoesTab")
}
```

UI: Campo "Componente Customizado (opcional)" onde o usuário informa o nome do componente Vue.

#### 8.2 Renderização no GenericFormDialog

O GenericFormDialog renderiza o componente customizado passando o `recordId`:

```vue
<q-tab-panel v-for="tab in visibleTabs" :key="tab.name" :name="tab.name">
  <!-- Componente customizado -->
  <component
    v-if="tab.component"
    :is="resolveComponent(tab.component)"
    v-model="formData"
    :record-id="formData.id"
  />
  <!-- Campos normais da aba -->
  <div v-else>...</div>
</q-tab-panel>
```

#### 8.3 Estrutura do Componente Customizado

Os componentes customizados devem seguir o padrão de props:

```javascript
const props = defineProps({
  modelValue: {
    type: Object,
    required: true,
  },
  recordId: {
    type: Number,
    default: null,
  },
});

const emit = defineEmits(["update:modelValue"]);

// Modo edição é detectado pela presença do recordId
const isEditMode = computed(() => !!props.recordId);

// Watch no recordId para carregar dados quando mudar
watch(
  () => props.recordId,
  async (newId) => {
    if (newId) {
      await loadData();  // Carrega dados usando o recordId
    }
  },
  { immediate: true }
);
```

#### 8.4 Exemplo - Carregamento de Dados (PermissoesTab)

```javascript
const loadPermissoes = async () => {
  if (!props.recordId) return;
  
  const url = `/api/permissoes/permissaoporgrupo/${props.recordId}`;
  const response = await apiRequest(url);
  permissoes.value = response;
};
```

#### 8.5 Registro de Componentes

Os componentes são registrados em um `componentRegistry` no GenericFormDialog:

```javascript
const componentRegistry = new Map([
  ["VencimentosTab", VencimentosTab],
  ["RecebimentosTab", RecebimentosTab],
  ["PermissoesTab", PermissoesTab],
  // ...outros componentes
]);
```

#### Fluxo Completo

| Etapa | Descrição |
|-------|-----------|
| 1. Configuração | Usuário define nome do componente na aba (TabManager) |
| 2. Salvamento | JSON da tab é salvo no banco (`tab.component = "PermissoesTab"`) |
| 3. Carregamento | GenericFormDialog recupera a config com component name |
| 4. Renderização | Renderiza o componente passando `record-id={formData.id}` |
| 5. Dados | Componente usa watch no recordId para carregar dados específicos |

#### Props Recebidas pelo Componente Customizado

| Prop | Descrição |
|------|-----------|
| `modelValue` | Objeto com os dados do formulário (formData) |
| `recordId` | ID do registro sendo editado (null para novo registro) |

#### Componentes Customizados Existentes

| Componente | Descrição |
|------------|-----------|
| VencimentosTab | Gerenciamento de parcelas e vencimentos |
| RecebimentosTab | Controle de recebimentos |
| PermissoesTab | Permissões de grupo de usuários |
| ProdutoPromocoesTab | Promoções do produto |
| ProdutoVariacoesTab | Variações do produto |
| ProdutoMidiaTab | Mídia do produto |
| EmbalagensTab | Gerenciamento de embalagens |

### 9. Mecanismo de Ações Customizadas

O sistema permite adicionar ações (botões) que executam operações customizadas vinculadas a endpoints do backend, sem necessidade de código frontend.

#### 9.1 Tipos de Ações

```javascript
const actionTypes = [
  { label: "Adicionar", value: "ADD" },
  { label: "Editar", value: "EDIT" },
  { label: "Excluir", value: "DELETE" },
  { label: "Visualizar", value: "VIEW" },
  { label: "Duplicar", value: "DUPLICATE" },
  { label: "Exportar", value: "EXPORT" },
  { label: "Importar", value: "IMPORT" },
  { label: "Customizada", value: "CUSTOM" },
];
```

#### 9.2 Configuração de Ação Customizada (tipo = CUSTOM)

No ActionEditor.vue, o usuário pode configurar:

```javascript
{
  type: "CUSTOM",
  name: "nome-da-acao",           // Identificador único
  label: "Ação Personalizada",    // Label do botão
  icon: "icon-name",              // Ícone do botão
  color: "primary",               // Cor do botão
  endpoint: "/api/produtos/{id}/ativar",  // Endpoint da API
  method: "POST",                 // Método HTTP (GET, POST, PUT, DELETE)
  opensDialog: true,              // Abre diálogo
  dialogComponent: "MeuDialog",   // Nome do componente do diálogo
  successMessage: "Operação realizada com sucesso",
  condition: "selectedItems.length > 0",  // Condição de visibilidade
  requiresSelection: false,       // Requer item selecionado
  onDoubleClick: true,            // Ativar no duplo clique na linha
  inlineOnly: false,              // Apenas inline (não aparece no topo)
}
```

#### 9.3 Execução no Frontend (useActions.js)

```javascript
async function executeBackendHandler(action, target) {
  let endpoint = action.endpoint;
  let data = null;

  if (Array.isArray(target)) {
    // Múltipla seleção - envia array de IDs
    data = { ids: target.map((item) => item.id) };
  } else if (target?.id) {
    // Seleção única - substitui {id} no endpoint
    endpoint = endpoint.replace("{id}", target.id);
    data = action.sendData ? target : null;
  }

  // Executa a requisição
  const response = await apiRequest(endpoint, action.method || "POST", data);
  return response;
}
```

#### 9.4 Fluxo de Execução

```
1. Usuário clica no botão de ação
      ↓
2. handleAction(action, target) é chamado
      ↓
3. Verifica o tipo de ação:
   - ADD/EDIT/DELETE → operações CRUD padrão
   - CUSTOM com opensDialog → abre diálogo customizado
   - CUSTOM com endpoint → executa no backend
      ↓
4. Para ações com endpoint:
   - Substitui {id} pelo ID do registro
   - Envia dados (se sendData=true)
   - Exibe mensagem de sucesso
   - Recarrega dados da tabela
```

#### 9.5 Utilidade das Ações Customizadas

| Utilidade | Exemplo |
|-----------|---------|
| **Operações específicas** | Ativar/inativar produto, Gerar NF-e, Enviar email |
| **Integração com serviços** | Consulta API externa, Sincronização |
| **Ações em lote** | Enviar mensagem para múltiplos itens selecionados |
| **Fluxos de trabalho** | Aprovar/rejeitar, Finalizar processo |
| **Diálogos customizados** | Abrir tela de configuração específica |
| **Condicional** | Botão só aparece quando há seleção |

#### 9.6 Contextos de Execução

As ações podem ser configuradas para diferentes contextos:

| Contexto | Descrição | Exemplo |
|----------|-----------|---------|
| **topActions** | Botões no header da página | "Adicionar", "Exportar" |
| **rowActions** | Ações por linha da tabela | "Editar", "Visualizar" |
| **selectionActions** | Ações para seleção múltipla | "Enviar email", "Excluir em lote" |
| **onDoubleClick** | Ação ao dar duplo clique na linha | "Editar" |

#### 9.7 Placeholders no Endpoint

O endpoint pode usar placeholders que são substituídos dinamicamente:

```
/api/produtos/{id}/ativar     → Substitui {id} pelo ID do registro
/api/clientes/{id}/faturar    → Substitui {id} pelo ID do registro
/api/vendas/{id}/imprimir     → Substitui {id} pelo ID do registro
```

### 10. Características Adicionais do Sistema

#### 10.1 Sistema de Validações

O sistema possui validações nativas que podem ser configuradas por campo:

```javascript
{
  name: "email",
  validations: ["required", "email"]
}
```

Validações disponíveis:
- **required** - Campo obrigatório
- **email** - Validação de e-mail (regex: `/^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$/`)
- **phone** - Validação de telefone (regex: `/^\(\d{2}\) \d{5}-\d{4}$/`)
- **Funções customizadas** - Regras personalizadas via função

#### 10.2 Options Dinâmicas (optionsEndpoint)

Campos SELECT podem carregar opções de endpoints externos:

```javascript
{
  name: "categoriaId",
  component: "q-select",
  optionsEndpoint: "/api/categorias/list"  // Carrega opções do backend
}
```

Características:
- Carregamento automático na inicialização do formulário
- Suporte a diferentes formatos de resposta:
  - `response.data` (array)
  - `response` (array direto)
  - `response.success` com `response.data`

#### 10.3 Campos Condicionais (visibilityCondition)

Sistema de visibilidade condicional para mostrar/esconder campos:

```javascript
{
  name: "campoB",
  visibilityCondition: "formData.campoA === 'valorX'"  // Só aparece se campoA = valorX
}
```

Características:
- Usa `new Function()` para avaliar expressões JavaScript
- Sanitização contra padrões perigosos (eval, Function, etc.)
- Compatibilidade com `showIf` (função para backward compatibility)

#### 10.4 Dependência entre Campos (dependsOn)

Campos que dependem de outro para carregar suas opções:

```javascript
{
  name: "subcategoriaId",
  optionsEndpoint: "/api/subcategorias/{value}",  // {value} é substituído pelo valor do campo dependente
  dependsOn: "categoriaId"  // Carrega quando categoriaId mudar
}
```

Fluxo:
1. Usuário muda o valor de `categoriaId`
2. Sistema substitui `{value}` no endpoint
3. Carrega as opções para `subcategoriaId`

#### 10.5 Eventos de Campo (field-event)

Sistema de comunicação entre campos para ações específicas:

```javascript
// No campo - emitir evento
emit("field-event", { type: "file-selected", field: "arquivo", data: file });

// Handler no GenericFormDialog
function handleFieldEvent(event) {
  const { type, field, data, url, action } = event;
  // type pode ser: file-selected, dependent-change, custom
}
```

---

## Pontos-Chave para Reproduzir em Python

### 1. Modelagem de Dados
- Tabela para armazenar definições em JSON
- Campos: entity_type, form_structure, table_columns, dialog_config

### 2. Registry de Entidades
- Mapping nome → classe/campos disponíveis
- Cada entidade tem uma "classe" que define seus campos (usar Pydantic)

### 3. Conversor de Tipos
- Função que mapeia tipo Python → componente frontend
- Props adicionais (máscaras, formatos)

### 4. API Endpoints
- `GET /api/{entity}/form-config` → retorna definição
- `POST /api/form-builder/save` → salva definição
- `GET /api/{entity}` → dados com paginação/filtro

### 5. Stack Sugerido (Python)
- **Framework**: FastAPI ou Flask
- **ORM**: SQLAlchemy com JSONB (PostgreSQL)
- **Serialização**: Pydantic para DTOs
- **Frontend**: Same (Quasar/Vue)