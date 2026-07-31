<template>
  <div class="table-config">
    <!-- Header -->
    <div class="config-header">
      <h6 class="text-subtitle1 q-ma-none">Configuração de Colunas</h6>
      <div class="text-caption text-grey-6">Selecione e configure as colunas visíveis na tabela</div>
    </div>

    <!-- Toolbar -->
    <div class="config-toolbar q-mt-md q-mb-sm">
      <q-btn-group flat>
        <q-btn flat size="sm" label="Selecionar Todas" @click="selectAllColumns" />
        <q-btn flat size="sm" label="Limpar Seleção" @click="clearSelection" />
      </q-btn-group>

      <q-space />

      <q-btn flat dense icon="add" label="Adicionar Campo" color="primary" size="sm" @click="showFieldSelector" />
    </div>

    <!-- Lista de Colunas -->
    <div class="columns-list">
      <draggable v-model="localColumns" handle=".drag-handle" item-key="name" :animation="200" @change="updateColumns">
        <template #item="{ element: column, index }">
          <q-expansion-item :key="column.name" group="columns" class="column-item" :class="{ 'column-disabled': !column.visible }">
            <template v-slot:header>
              <q-item-section avatar>
                <div class="row no-wrap q-gutter-xs">
                  <q-icon name="drag_indicator" class="drag-handle" size="20px" />
                  <q-checkbox v-model="column.visible" @update:model-value="updateColumns" @click.stop />
                </div>
              </q-item-section>

              <q-item-section>
                <q-item-label>{{ column.label }}</q-item-label>
                <q-item-label caption>
                  <span class="text-mono">{{ column.name }}</span>
                  <q-chip size="xs" color="grey-3" class="q-ml-xs">
                    {{ column.type }}
                  </q-chip>
                </q-item-label>
              </q-item-section>

              <q-item-section side>
                <div class="row items-center q-gutter-xs">
                  <q-chip v-if="column.width" size="sm" color="blue-1" text-color="blue-9" :label="column.width" />
                  <q-btn flat dense round size="sm" icon="delete" color="negative" @click.stop="removeColumn(index)" />
                </div>
              </q-item-section>
            </template>

            <!-- Configurações Expandidas -->
            <q-card flat class="column-config">
              <q-card-section>
                <div class="row q-col-gutter-md">
                  <!-- Campo de Dados -->
                  <div class="col-12">
                    <q-input
                      v-model="column.dataField"
                      label="Campo de Dados (Entidade)"
                      filled
                      dense
                      placeholder="Ex: taxaDebito"
                      @update:model-value="updateColumns"
                    >
                      <template v-slot:append>
                        <q-icon name="info">
                          <q-tooltip class="bg-grey-8" max-width="300px">Nome do campo na API/Banco. Se vazio, usa o nome da coluna.</q-tooltip>
                        </q-icon>
                      </template>
                    </q-input>
                  </div>

                  <!-- Largura -->
                  <div class="col-12 col-md-6">
                    <q-select
                      v-model="column.width"
                      label="Largura (pixels)"
                      :options="widthOptions"
                      filled
                      dense
                      emit-value
                      map-options
                      clearable
                      @update:model-value="updateColumns"
                    >
                      <template v-slot:append>
                        <q-icon name="info">
                          <q-tooltip>Largura em pixels. Deixe vazio para largura automática.</q-tooltip>
                        </q-icon>
                      </template>
                    </q-select>
                  </div>

                  <!-- Alinhamento -->
                  <div class="col-12 col-md-6">
                    <div class="text-caption text-grey-7 q-mb-xs">Alinhamento</div>
                    <q-btn-toggle
                      v-model="column.align"
                      toggle-color="primary"
                      :options="[
                        { icon: 'format_align_left', value: 'left' },
                        { icon: 'format_align_center', value: 'center' },
                        { icon: 'format_align_right', value: 'right' },
                      ]"
                      dense
                      @update:model-value="updateColumns"
                    />
                  </div>

                  <!-- Formato -->
                  <div class="col-12" v-if="showFormatOptions(column.type)">
                    <q-select
                      v-model="column.format"
                      label="Formato de Exibição"
                      :options="getFormatOptions(column.type)"
                      filled
                      dense
                      emit-value
                      map-options
                      clearable
                      @update:model-value="updateColumns"
                    />
                  </div>

                  <!-- Ordenação -->
                  <div class="col-12">
                    <q-checkbox v-model="column.sortable" label="Permitir ordenação" @update:model-value="updateColumns" />
                  </div>

                  <!-- Template customizado -->
                  <div class="col-12">
                    <q-input
                      v-model="column.cellTemplate"
                      label="Template de célula (opcional)"
                      filled
                      dense
                      type="textarea"
                      rows="2"
                      placeholder="Ex: {{ value | currency }}"
                      @update:model-value="updateColumns"
                    >
                      <template v-slot:append>
                        <q-icon name="help">
                          <q-tooltip max-width="300px">Use {{ value }} para o valor do campo. Você pode usar filtros e HTML.</q-tooltip>
                        </q-icon>
                      </template>
                    </q-input>
                  </div>

                  <!-- Classes CSS -->
                  <div class="col-12">
                    <q-input
                      v-model="column.classes"
                      label="Classes CSS (opcional)"
                      filled
                      dense
                      placeholder="text-bold text-primary"
                      @update:model-value="updateColumns"
                    />
                  </div>
                </div>
              </q-card-section>
            </q-card>
          </q-expansion-item>
        </template>
      </draggable>
    </div>

    <!-- Botões de Ação -->
    <div class="action-buttons q-mt-md q-mb-md">
      <q-btn unelevated label="Aplicar Alterações" color="primary" icon="save" :disable="!hasChanges" @click="applyChanges" />
      <q-btn flat label="Resetar" color="grey" icon="restore" class="q-ml-sm" :disable="!hasChanges" @click="resetChanges" />
    </div>

    <!-- Preview da Tabela -->
    <q-expansion-item label="Preview da Tabela" icon="visibility" class="q-mt-md" default-opened>
      <q-card flat>
        <q-card-section>
          <q-table :columns="previewColumns" :rows="previewRows" row-key="id" dense flat :pagination="{ rowsPerPage: 5 }">
            <template v-slot:body-cell="props">
              <q-td :props="props" :class="props.col.classes">
                <div v-if="props.col.cellTemplate" v-html="renderTemplate(props.col.cellTemplate, props.value)" />
                <span v-else>{{ formatValue(props.value, props.col.formatType) }}</span>
              </q-td>
            </template>
          </q-table>
        </q-card-section>
      </q-card>
    </q-expansion-item>

    <!-- Dialog de Seleção de Campo -->
    <q-dialog v-model="showFieldDialog">
      <q-card style="width: 400px">
        <q-card-section>
          <div class="text-h6">Adicionar Campo</div>
        </q-card-section>

        <q-card-section>
          <q-list>
            <q-item v-for="field in availableFields" :key="field.name" clickable v-ripple @click="addFieldAsColumn(field)">
              <q-item-section avatar>
                <q-icon :name="getFieldIcon(field.type)" />
              </q-item-section>
              <q-item-section>
                <q-item-label>{{ humanizeFieldName(field.name) }}</q-item-label>
                <q-item-label caption>{{ field.name }} - {{ field.type }}</q-item-label>
              </q-item-section>
            </q-item>
          </q-list>
        </q-card-section>
      </q-card>
    </q-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch } from "vue";
import { useQuasar } from "quasar";
import draggable from "vuedraggable";
import { useFormBuilderStore } from "@/stores/formBuilderStore";
import FieldTypeMapper from "@/services/form-builder/FieldTypeMapper";

const $q = useQuasar();
const store = useFormBuilderStore();

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => [],
  },
});

const emit = defineEmits(["update:modelValue"]);

// Estado
const localColumns = ref([]);
const showFieldDialog = ref(false);
const originalColumns = ref([]);
const hasChanges = ref(false);

// Opções
const widthOptions = [
  { label: "Automático", value: null },
  { label: "80px", value: 80 },
  { label: "100px", value: 100 },
  { label: "120px", value: 120 },
  { label: "150px", value: 150 },
  { label: "200px", value: 200 },
  { label: "250px", value: 250 },
  { label: "300px", value: 300 },
  { label: "400px", value: 400 },
  { label: "500px", value: 500 },
];

// Computed
const availableFields = computed(() => {
  // Usar detectedFields do store e filtrar os que já estão nas colunas
  return store.detectedFields.filter((field) => !localColumns.value.find((col) => col.name === field.name));
});

const previewColumns = computed(() => {
  return localColumns.value
    .filter((col) => col.visible !== false)
    .map((col) => ({
      name: col.name,
      label: col.label,
      field: col.name,
      align: col.align || "left",
      sortable: col.sortable !== false,
      style: col.width ? `width: ${col.width}` : "",
      classes: col.classes || "",
      // Não usar 'format' diretamente - o Quasar espera uma função
      formatType: col.format,
      cellTemplate: col.cellTemplate,
    }));
});

const previewRows = computed(() => {
  // Gerar dados de exemplo baseados nas colunas visíveis
  const rows = [];
  for (let i = 1; i <= 5; i++) {
    const row = { id: i };
    localColumns.value.forEach((col) => {
      if (col.visible !== false) {
        row[col.name] = generateSampleData(col, i);
      }
    });
    rows.push(row);
  }
  return rows;
});

// Watch
watch(
  () => props.modelValue,
  (newVal) => {
    console.log("TableConfig recebeu modelValue:", newVal);

    // Garantir que todas as colunas tenham as propriedades necessárias
    const columns = (newVal || []).map((col) => ({
      ...col,
      // Tratar null como true
      visible: col.visible !== false, // Se for null ou undefined, será true
      width: col.width || null,
      format: col.format || null,
      cellTemplate: col.cellTemplate || null,
      classes: col.classes || "",
      dataField: col.dataField || col.name, // Garantir que dataField existe
    }));

    console.log("TableConfig após processar:", columns);

    localColumns.value = JSON.parse(JSON.stringify(columns));
    originalColumns.value = JSON.parse(JSON.stringify(columns));
    hasChanges.value = false;
  },
  { immediate: true, deep: true }
);

// Methods
function updateColumns() {
  // Marcar como alterado mas NÃO emitir ainda
  hasChanges.value = true;
}

function applyChanges() {
  // Emitir as mudanças para o componente pai
  emit("update:modelValue", localColumns.value);

  // Atualizar diretamente no store, garantindo que visible seja preservado
  store.formDefinition.tableColumns = localColumns.value.map((col) => ({
    ...col,
    // Garantir que visible seja explicitamente incluído
    visible: col.visible !== undefined ? col.visible : true,
  }));

  store.isDirty = true;

  // Atualizar o estado original
  originalColumns.value = JSON.parse(JSON.stringify(localColumns.value));
  hasChanges.value = false;

  $q.notify({
    message: "Alterações aplicadas",
    color: "positive",
    icon: "check",
    position: "top",
  });
}

function resetChanges() {
  // Restaurar valores originais
  localColumns.value = JSON.parse(JSON.stringify(originalColumns.value));
  hasChanges.value = false;

  $q.notify({
    message: "Alterações descartadas",
    color: "info",
    icon: "restore",
  });
}

function selectAllColumns() {
  localColumns.value.forEach((col) => {
    col.visible = true;
  });
  updateColumns();
}

function clearSelection() {
  localColumns.value.forEach((col) => {
    col.visible = false;
  });
  updateColumns();
}

function removeColumn(index) {
  localColumns.value.splice(index, 1);
  updateColumns();
}

function showFieldSelector() {
  showFieldDialog.value = true;
}

function addFieldAsColumn(field) {
  const column = {
    name: field.name,
    label: humanizeFieldName(field.name),
    type: FieldTypeMapper.mapJavaTypeToColumnType(field.type),
    align: getDefaultAlign(field.type),
    sortable: true,
    visible: true,
    width: null,
    format: null,
    cellTemplate: null,
    classes: "",
    order: localColumns.value.length + 1,
  };

  localColumns.value.push(column);
  updateColumns();
  showFieldDialog.value = false;
}

function humanizeFieldName(fieldName) {
  return FieldTypeMapper.humanizeFieldName(fieldName);
}

function getFieldIcon(type) {
  const icons = {
    String: "text_fields",
    Integer: "numbers",
    Long: "numbers",
    Double: "numbers",
    Float: "numbers",
    BigDecimal: "attach_money",
    Boolean: "check_box",
    LocalDate: "event",
    LocalDateTime: "schedule",
    LocalTime: "access_time",
  };
  return icons[type] || "help";
}

function getDefaultAlign(type) {
  if (["Integer", "Long", "Double", "Float", "BigDecimal"].includes(type)) return "right";
  if (["LocalDate", "LocalDateTime", "LocalTime"].includes(type)) return "center";
  if (["Boolean"].includes(type)) return "center";
  return "left";
}

function showFormatOptions(type) {
  return ["NUMBER", "CURRENCY", "DATE", "DATETIME", "BOOLEAN"].includes(type);
}

function getFormatOptions(type) {
  switch (type) {
    case "NUMBER":
      return [
        { label: "Número simples", value: "number" },
        { label: "Separador de milhares", value: "number:thousand" },
        { label: "Porcentagem", value: "percent" },
        { label: "2 casas decimais", value: "number:2" },
      ];
    case "CURRENCY":
      return [
        { label: "R$ 1.234,56", value: "currency:BRL" },
        { label: "$ 1,234.56", value: "currency:USD" },
        { label: "€ 1.234,56", value: "currency:EUR" },
      ];
    case "DATE":
      return [
        { label: "DD/MM/AAAA", value: "date:DD/MM/YYYY" },
        { label: "DD de MMM", value: "date:DD MMM" },
        { label: "DD/MM", value: "date:DD/MM" },
      ];
    case "DATETIME":
      return [
        { label: "DD/MM/AAAA HH:mm", value: "datetime:DD/MM/YYYY HH:mm" },
        { label: "DD/MM HH:mm", value: "datetime:DD/MM HH:mm" },
        { label: "Há X tempo", value: "datetime:relative" },
      ];
    case "BOOLEAN":
      return [
        { label: "Sim/Não", value: "boolean:sim-nao" },
        { label: "Ativo/Inativo", value: "boolean:ativo-inativo" },
        { label: "Habilitado/Desabilitado", value: "boolean:habilitado" },
      ];
    default:
      return [];
  }
}

function generateSampleData(column, index) {
  switch (column.type) {
    case "NUMBER":
      return Math.floor(Math.random() * 1000) + index;
    case "CURRENCY":
      return (Math.random() * 1000 * index).toFixed(2);
    case "DATE":
      const date = new Date();
      date.setDate(date.getDate() - index);
      return date.toLocaleDateString("pt-BR");
    case "DATETIME":
      const datetime = new Date();
      datetime.setHours(datetime.getHours() - index);
      return datetime.toLocaleString("pt-BR");
    case "BOOLEAN":
      return index % 2 === 0;
    default:
      return `${column.label} ${index}`;
  }
}

function formatValue(value, formatType) {
  if (!formatType || value === null || value === undefined) return value;

  // Implementar formatação baseada no formato selecionado
  const [type, param] = formatType.split(":");

  switch (type) {
    case "currency":
      return new Intl.NumberFormat("pt-BR", {
        style: "currency",
        currency: param || "BRL",
      }).format(value);
    case "number":
      if (param === "thousand") {
        return new Intl.NumberFormat("pt-BR").format(value);
      } else if (param) {
        return parseFloat(value).toFixed(parseInt(param));
      }
      return value;
    case "percent":
      return `${value}%`;
    case "boolean":
      if (param === "sim-nao") return value ? "Sim" : "Não";
      if (param === "ativo-inativo") return value ? "Ativo" : "Inativo";
      if (param === "habilitado") return value ? "Habilitado" : "Desabilitado";
      return value ? "✓" : "✗";
    case "date":
    case "datetime":
      // Implementar formatação de data
      return value;
    default:
      return value;
  }
}

function renderTemplate(template, value) {
  // Renderizar template simples
  return template.replace(/\{\{\s*value\s*\}\}/g, value);
}
</script>

<style lang="scss" scoped>
.table-config {
  .config-header {
    margin-bottom: 16px;
  }

  .config-toolbar {
    display: flex;
    align-items: center;
  }

  .columns-list {
    max-height: 500px;
    overflow-y: auto;
    margin-bottom: 16px;
  }

  .column-item {
    margin-bottom: 8px;
    border: 1px solid $grey-4;
    border-radius: 4px;

    &.column-disabled {
      opacity: 0.6;

      .q-item__label {
        text-decoration: line-through;
      }
    }

    .drag-handle {
      cursor: move;
      color: $grey-6;
    }
  }

  .column-config {
    background-color: $grey-1;
  }

  .action-buttons {
    text-align: right;
    padding: 16px 0;
    border-top: 1px solid $grey-3;
  }

  .text-mono {
    font-family: monospace;
    font-size: 12px;
  }
}

:deep(.q-expansion-item__content) {
  padding: 0;
}
</style>
