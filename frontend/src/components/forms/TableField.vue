<template>
  <div :class="field?.cols || 'col-12'">
    <div class="row items-center q-mb-sm">
      <div class="text-subtitle2">
        {{ field?.label || "Itens" }}
        <span v-if="field.required" class="text-negative">*</span>
      </div>
      <q-space />
      <q-btn v-if="rowAddable && internalRows.length > 0" dense flat icon="add" @click="addRow" label="Adicionar" />
    </div>

    <!-- Mensagem de erro -->
    <div v-if="hasError" class="text-negative text-caption q-mb-sm">
      {{ errorMessage }}
    </div>

    <!-- Mensagem quando não há itens -->
    <div v-if="internalRows.length === 0" class="q-pa-md text-center text-grey-6">
      <q-icon name="inventory_2" size="48px" class="q-mb-sm" />
      <div>Nenhum item adicionado</div>
      <q-btn v-if="rowAddable" label="Adicionar primeiro item" icon="add" color="primary" outline size="sm" class="q-mt-sm" @click="addRow" />
    </div>

    <q-table
      v-else
      dense
      flat
      bordered
      :rows="internalRows"
      :columns="qColumns"
      row-key="__rowid"
      :pagination="pagination"
      @update:pagination="(p) => (pagination = p)"
      class="table-field-table"
    >
      <template v-slot:body-cell="{ col, key, rowIndex }">
        <q-td :key="key" class="table-field-cell">
          <!-- Campo Computado - apenas exibição -->
          <div v-if="col.type === 'COMPUTED'" class="computed-field-value">
            {{ formatComputedValue(rowIndex, col) }}
          </div>

          <!-- Campo LOOKUP - com wrapper especial -->
          <div v-else-if="col.type === 'LOOKUP'" class="lookup-field-wrapper">
            <!-- Se produto selecionado E não está em modo edição, mostrar nome + botão editar -->
            <div v-if="internalRows[rowIndex][col.name] && !isLookupEditing(rowIndex, col)" class="selected-product-display">
              <span class="product-name text-subtitle2">{{ getProductDisplayName(rowIndex, col) }}</span>
              <q-btn 
                icon="edit" 
                size="xs" 
                flat 
                dense 
                color="primary"
                @click="enableLookupEdit(rowIndex, col)"
                :disable="disabled"
                class="q-ml-xs"
              >
                <q-tooltip>Alterar produto</q-tooltip>
              </q-btn>
              <q-btn 
                icon="clear" 
                size="xs" 
                flat 
                dense 
                color="negative"
                @click="clearProduct(rowIndex, col)"
                :disable="disabled"
                class="q-ml-xs"
              >
                <q-tooltip>Remover produto</q-tooltip>
              </q-btn>
            </div>

            <!-- Se não selecionado OU em modo edição, mostrar lookup -->
            <LookupSelect
              v-else
              v-model="internalRows[rowIndex][col.name]"
              @update:model-value="(value) => handleLookupSelection(rowIndex, col, value)"
              :field="col"
              :disabled="disabled"
            />
          </div>

          <!-- Campo de Data - com componente especial -->
          <DateInput
            v-else-if="col.type === 'DATE'"
            v-model="internalRows[rowIndex][col.name]"
            :label="col.placeholder || ''"
            :disable="disabled"
            @update:model-value="() => handleCellUpdate(rowIndex, col)"
            :rules="[]"
            class="table-field-input"
          />

          <!-- Outros campos - editáveis -->
          <component
            v-else
            :is="getCellComponent(col)"
            v-model="internalRows[rowIndex][col.name]"
            dense
            outlined
            @update:model-value="() => handleCellUpdate(rowIndex, col)"
            v-bind="getCellProps(col)"
            :field="col"
            :disabled="disabled"
            class="table-field-input"
          />
        </q-td>
      </template>

      <!-- Remover linha -->
      <template v-if="rowRemovable" v-slot:body-cell-actions="{ rowIndex }">
        <q-td class="table-field-actions">
          <q-btn icon="delete" size="sm" flat dense color="negative" @click="removeRow(rowIndex)" :disabled="disabled" />
        </q-td>
      </template>
    </q-table>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from "vue";
import LookupSelect from "./LookupSelect.vue";
import DateInput from "./date/DateInput.vue";
import { useApiRequest } from "@/composables/useApiRequest";

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false },
  error: { type: String, default: null },
});

const emit = defineEmits(["update:model-value"]);

// Garantir que temos um array válido
const getValidArray = (value) => {
  if (!value) return [];
  if (!Array.isArray(value)) return [];
  return value;
};

// Local copy com IDs
const internalRows = ref([]);

// Estado para controlar quais campos LOOKUP estão em modo edição
const editingLookups = ref(new Set());

// Cache para descrições de produtos
const productCache = ref(new Map());

// Composable para requests
const { apiRequest } = useApiRequest();

// Configurações
const rowAddable = computed(() => props.field.rowAddable !== false);
const rowRemovable = computed(() => props.field.rowRemovable !== false);

const pagination = reactive({
  rowsPerPage: 0, // 0 = mostrar todas as linhas
});

// Computed para validação
const hasError = computed(() => {
  return props.field.required && internalRows.value.length === 0 && props.error;
});

const errorMessage = computed(() => {
  return props.error || "É necessário adicionar pelo menos um item";
});

// Inicializar dados
const initializeData = () => {
  const validArray = getValidArray(props.modelValue);
  internalRows.value = validArray.map((r, i) => ({ ...r, __rowid: i }));

  // Recalcular todos os campos computados
  internalRows.value.forEach((row, index) => {
    updateComputedFields(index);
  });
};

// Lifecycle
onMounted(() => {
  initializeData();
});

// Handlers
function handleCellUpdate(rowIndex, col) {
  // Recalcular campos computados após atualização
  updateComputedFields(rowIndex);
  // Emitir mudança
  emitValue();
}

function updateComputedFields(rowIndex) {
  const row = internalRows.value[rowIndex];
  if (!row) return;

  // Percorrer todas as colunas procurando campos computados
  if (props.field.columns) {
    props.field.columns.forEach((col) => {
      if (col.type === "COMPUTED" && col.formula) {
        try {
          // Avaliar a fórmula no contexto da linha
          const computed = evaluateFormula(col.formula, row);
          row[col.name] = computed;
        } catch (error) {
          console.error("Erro ao calcular campo:", col.name, error);
          row[col.name] = 0;
        }
      }
    });
  }
}

function evaluateFormula(formula, rowData) {
  try {
    // Criar um contexto seguro para avaliar a fórmula
    let processedFormula = formula;

    // Para cada propriedade da linha, substituir na fórmula
    Object.keys(rowData).forEach((key) => {
      if (key !== "__rowid") {
        const value = rowData[key] || 0;
        // Usar regex para substituir apenas palavras completas
        const regex = new RegExp(`\\b${key}\\b`, "g");
        processedFormula = processedFormula.replace(regex, value);
      }
    });

    // Avaliar a expressão de forma mais segura
    // Considere usar uma biblioteca como math.js para maior segurança
    const result = Function('"use strict"; return (' + processedFormula + ")")();

    return isNaN(result) ? 0 : result;
  } catch (error) {
    console.error("Erro na fórmula:", formula, error);
    return 0;
  }
}

function formatComputedValue(rowIndex, col) {
  const row = internalRows.value[rowIndex];
  if (!row) return "";

  const value = row[col.name] || 0;

  // Se tem prefixo (como R$), formatar como moeda
  if (col.props?.prefix === "R$") {
    return formatCurrency(value);
  }

  // Se tem sufixo, adicionar
  if (col.props?.suffix) {
    return value + col.props.suffix;
  }

  return value;
}

function formatCurrency(value) {
  if (!value && value !== 0) return "R$ 0,00";

  const num = parseFloat(value);
  return (
    "R$ " +
    num.toLocaleString("pt-BR", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })
  );
}

function addRow() {
  const idx = internalRows.value.length;
  const blank = {};

  // Inicializar campos com valores padrão apropriados
  if (props.field.columns) {
    props.field.columns.forEach((col) => {
      switch (col.type) {
        case "NUMBER":
        case "CURRENCY":
          blank[col.name] = col.defaultValue || 0;
          break;
        case "COMPUTED":
          blank[col.name] = 0; // Será recalculado
          break;
        case "DATE":
          blank[col.name] = null; // Sempre vazio para campos de data
          break;
        default:
          blank[col.name] = col.defaultValue || null;
      }
    });
  }

  const newRow = { ...blank, __rowid: idx };
  internalRows.value.push(newRow);

  // Calcular campos computados da nova linha
  updateComputedFields(internalRows.value.length - 1);

  emitValue();
}

function removeRow(index) {
  internalRows.value.splice(index, 1);
  // Reindexar os __rowid
  internalRows.value.forEach((row, i) => {
    row.__rowid = i;
  });
  emitValue();
}

function getCellComponent(col) {
  const componentMap = {
    LOOKUP: LookupSelect,
    NUMBER: "q-input",
    CURRENCY: "q-input",
    DATE: DateInput,
    TEXT: "q-input",
    COMPUTED: "q-input",
  };

  return componentMap[col.type] || "q-input";
}

function getCellProps(col) {
  const baseProps = col.props || {};

  const propsMap = {
    NUMBER: {
      ...baseProps,
      type: "number",
      min: baseProps.min || 0,
      step: baseProps.step || 1,
    },
    CURRENCY: {
      ...baseProps,
      type: "number",
      prefix: baseProps.prefix || "R$",
      mask: baseProps.mask || "#.##0,00",
      "fill-mask": baseProps["fill-mask"] !== false,
      "reverse-fill-mask": baseProps["reverse-fill-mask"] !== false,
    },
    DATE: {
      ...baseProps,
      // Remove props específicas do HTML5, DateInput usa suas próprias props
    },
    COMPUTED: {
      ...baseProps,
      readonly: true,
      filled: true,
    },
    LOOKUP: {
      ...baseProps,
      lookupEndpoint: col.lookupEndpoint,
      displayColumns: col.displayColumns,
      allowCreate: col.allowCreate,
      createDialogComponent: col.createDialogComponent,
    },
  };

  return propsMap[col.type] || baseProps;
}

// Funções para gerenciar exibição de produtos selecionados
function getLookupKey(rowIndex, col) {
  return `${rowIndex}-${col.name}`;
}

function isLookupEditing(rowIndex, col) {
  return editingLookups.value.has(getLookupKey(rowIndex, col));
}

function enableLookupEdit(rowIndex, col) {
  editingLookups.value.add(getLookupKey(rowIndex, col));
}

function disableLookupEdit(rowIndex, col) {
  editingLookups.value.delete(getLookupKey(rowIndex, col));
}

function getProductDisplayName(rowIndex, col) {
  const row = internalRows.value[rowIndex];
  const productId = row[col.name];
  
  if (!productId) return '';
  
  // Primeira tentativa: usar produtoDescricao se disponível (dados do servidor ou cache)
  if (row['produtoDescricao']) {
    return row['produtoDescricao'];
  }
  
  // Segunda tentativa: outros campos possíveis
  const possibleDescriptionFields = [
    'produto_descricao',     // snake_case
    'descricao',             // campo simples
    'nome',                  // nome do produto
    'title',                 // título
    'label'                  // label genérico
  ];
  
  for (const field of possibleDescriptionFields) {
    if (row[field]) {
      return row[field];
    }
  }
  
  // Verificar cache também
  if (productCache.value.has(productId)) {
    const cachedProduct = productCache.value.get(productId);
    return cachedProduct.descricao;
  }
  
  // Fallback: ainda carregando ou produto sem descrição
  return `Produto #${productId}`;
}

function clearProduct(rowIndex, col) {
  internalRows.value[rowIndex][col.name] = null;
  // Limpar também campos relacionados se existirem
  const descriptionField = col.name.replace('Id', 'Descricao');
  if (internalRows.value[rowIndex][descriptionField]) {
    internalRows.value[rowIndex][descriptionField] = null;
  }
  disableLookupEdit(rowIndex, col);
  handleCellUpdate(rowIndex, col);
}

async function handleLookupSelection(rowIndex, col, selectedValue) {
  console.log('DEBUG: Produto selecionado:', selectedValue);
  
  // Se é um campo de produto, buscar a descrição
  if (col.name === 'produtoId' && selectedValue) {
    await fetchProductDescription(rowIndex, selectedValue);
  }
  
  disableLookupEdit(rowIndex, col);
  handleCellUpdate(rowIndex, col);
}

async function fetchProductDescription(rowIndex, productId) {
  try {
    // Verificar cache primeiro
    if (productCache.value.has(productId)) {
      const cachedProduct = productCache.value.get(productId);
      internalRows.value[rowIndex]['produtoDescricao'] = cachedProduct.descricao;
      console.log('DEBUG: Usando descrição do cache:', cachedProduct.descricao);
      return;
    }

    // Buscar na API usando o endpoint de lookup
    const endpoint = '/api/produtos/lookup/search';
    const response = await apiRequest(endpoint, 'GET');
    
    if (response && Array.isArray(response)) {
      // Encontrar o produto específico
      const product = response.find(p => p.id === productId);
      
      if (product) {
        // Armazenar no cache
        productCache.value.set(productId, {
          id: product.id,
          descricao: product.descricao || product.label,
          label: product.label
        });
        
        // Atualizar a linha atual
        internalRows.value[rowIndex]['produtoDescricao'] = product.descricao || product.label;
        console.log('DEBUG: Descrição obtida da API:', product.descricao || product.label);
      }
    }
  } catch (error) {
    console.warn('Erro ao buscar descrição do produto:', error);
    // Em caso de erro, continuar sem a descrição
  }
}

const qColumns = computed(() => {
  if (!props.field.columns) return [];

  const base = props.field.columns.map((col) => ({
    name: col.name,
    field: col.name,
    label: col.label || col.name,
    align: col.align || "left",
    sortable: false,
    type: col.type,
    props: col.props || {},
    formula: col.formula,
    // Passar todas as propriedades da coluna
    ...col,
  }));

  if (rowRemovable.value) {
    base.push({
      name: "actions",
      label: "",
      field: "actions",
      align: "center",
      sortable: false,
    });
  }

  return base;
});

function emitValue() {
  const cleanRows = internalRows.value.map(({ __rowid, ...row }) => row);
  emit("update:model-value", cleanRows);
}

// Watch para mudanças no modelValue
watch(
  () => props.modelValue,
  (newValue) => {
    initializeData();
  },
  { deep: true }
);
</script>

<style scoped>
/* Reset de padding das células para controle fino */
.table-field-table :deep(.q-table th) {
  font-weight: bold;
  padding: 8px;
}

.table-field-table :deep(.q-table td) {
  padding: 2px 4px;
  vertical-align: middle;
}

/* Estilo para a célula */
.table-field-cell {
  padding: 2px 4px !important;
}

/* Wrapper para lookup field */
.lookup-field-wrapper {
  width: 100%;
  min-width: 0;
}

/* Forçar altura consistente para o LookupSelect */
.lookup-field-wrapper :deep(.q-field--dense .q-field__control) {
  height: 40px !important;
}

.lookup-field-wrapper :deep(.q-field__control-container) {
  padding-top: 0 !important;
}

/* Input padrão na tabela */
.table-field-input {
  width: 100%;
}

/* Campo computado */
.computed-field-value {
  padding: 8px 12px;
  background-color: #f5f5f5;
  border-radius: 4px;
  text-align: right;
  min-height: 40px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

/* Ações */
.table-field-actions {
  text-align: center !important;
  padding: 2px 4px !important;
}

/* Garantir que o q-input dentro da célula ocupe toda largura */
.table-field-table :deep(.q-input) {
  width: 100%;
}

/* Ajustar densidade dos inputs na tabela */
.table-field-table :deep(.q-field--dense .q-field__control) {
  height: 40px;
}

/* Garantir altura consistente para todos os tipos de campo */
.table-field-table :deep(.q-field__control) {
  min-height: 40px;
  align-items: center;
}

/* Remover margens extras */
.table-field-table :deep(.q-field) {
  margin: 0;
}

.table-field-table :deep(.q-field__bottom) {
  display: none;
}

/* Estilos para produto selecionado */
.selected-product-display {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  background: #f8f9fa;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  min-height: 40px;
  width: 100%;
}

.product-name {
  flex: 1;
  color: #2c3e50;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-right: 8px;
}

.selected-product-display .q-btn {
  opacity: 0.7;
  transition: opacity 0.2s;
}

.selected-product-display:hover .q-btn {
  opacity: 1;
}
</style>
