<template>
  <div class="table-column-editor">
    <!-- Lista de Colunas -->
    <draggable v-model="localColumns" handle=".drag-handle" item-key="name" @change="updateColumns">
      <template #item="{ element: column, index }">
        <q-card flat bordered class="column-item q-mb-sm">
          <q-card-section class="q-pa-sm">
            <div class="row items-center q-gutter-sm">
              <!-- Drag Handle -->
              <q-icon name="drag_indicator" class="drag-handle cursor-move text-grey-6" />

              <!-- Column Config -->
              <div class="col">
                <div class="row q-gutter-sm">
                  <!-- Nome -->
                  <q-input v-model="column.name" label="Nome do Campo" dense filled class="col-4" @update:model-value="updateColumns" />

                  <!-- Label -->
                  <q-input v-model="column.label" label="Label" dense filled class="col-4" @update:model-value="updateColumns" />

                  <!-- Tipo -->
                  <q-select
                    v-model="column.type"
                    :options="columnTypes"
                    label="Tipo"
                    dense
                    filled
                    emit-value
                    map-options
                    class="col-3"
                    @update:model-value="updateColumns"
                  />
                </div>

                <!-- Configurações específicas por tipo -->
                <div v-if="column.type === 'LOOKUP'" class="row q-gutter-sm q-mt-sm">
                  <q-input
                    v-model="column.lookupEndpoint"
                    label="Endpoint de busca"
                    dense
                    filled
                    class="col-6"
                    placeholder="/api/produtos/lookup/search"
                    @update:model-value="updateColumns"
                  />

                  <q-select
                    v-model="column.displayColumns"
                    label="Colunas para exibir"
                    dense
                    filled
                    multiple
                    use-chips
                    use-input
                    class="col-5"
                    @new-value="(val) => addDisplayColumn(val, column)"
                    @update:model-value="updateColumns"
                  />
                </div>

                <div v-else-if="column.type === 'COMPUTED'" class="row q-gutter-sm q-mt-sm">
                  <q-input
                    v-model="column.formula"
                    label="Fórmula"
                    dense
                    filled
                    class="col-11"
                    placeholder="quantidade * valorUnitario"
                    @update:model-value="updateColumns"
                  />
                </div>

                <div v-else-if="['NUMBER', 'CURRENCY'].includes(column.type)" class="row q-gutter-sm q-mt-sm">
                  <q-input
                    v-model.number="column.props.min"
                    label="Valor mínimo"
                    type="number"
                    dense
                    filled
                    class="col-3"
                    @update:model-value="updateColumns"
                  />

                  <q-input
                    v-model.number="column.props.step"
                    label="Incremento"
                    type="number"
                    dense
                    filled
                    class="col-3"
                    @update:model-value="updateColumns"
                  />

                  <q-input
                    v-if="column.type === 'CURRENCY'"
                    v-model="column.props.prefix"
                    label="Prefixo"
                    dense
                    filled
                    class="col-2"
                    @update:model-value="updateColumns"
                  />
                </div>
              </div>

              <!-- Actions -->
              <q-btn icon="delete" flat round dense color="negative" size="sm" @click="removeColumn(index)" />
            </div>
          </q-card-section>
        </q-card>
      </template>
    </draggable>

    <!-- Botão Adicionar -->
    <q-btn label="Adicionar Coluna" icon="add" flat color="primary" class="full-width q-mt-md" @click="addColumn" />

    <!-- Templates Rápidos -->
    <div class="q-mt-md">
      <div class="text-caption text-grey-6 q-mb-sm">Templates rápidos:</div>
      <div class="row q-gutter-sm">
        <q-btn
          v-for="template in templates"
          :key="template.name"
          :label="template.label"
          size="sm"
          flat
          dense
          color="primary"
          @click="applyTemplate(template)"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from "vue";
import draggable from "vuedraggable";

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => [],
  },
});

const emit = defineEmits(["update:modelValue"]);

// Estado local
const localColumns = ref([]);

// Opções
const columnTypes = [
  { label: "Texto", value: "TEXT" },
  { label: "Número", value: "NUMBER" },
  { label: "Moeda", value: "CURRENCY" },
  { label: "Data", value: "DATE" },
  { label: "Lookup", value: "LOOKUP" },
  { label: "Calculado", value: "COMPUTED" },
  { label: "Checkbox", value: "CHECKBOX" },
];

// Templates
const templates = [
  {
    name: "itens-pedido",
    label: "Itens de Pedido",
    columns: [
      {
        name: "produtoId",
        label: "Produto",
        type: "LOOKUP",
        lookupEndpoint: "/api/produtos/lookup/search",
        displayColumns: ["codigo", "descricao"],
        props: { dense: true },
      },
      {
        name: "quantidade",
        label: "Quantidade",
        type: "NUMBER",
        props: { min: 1, step: 1, dense: true },
      },
      {
        name: "valorUnitario",
        label: "Valor Unit.",
        type: "CURRENCY",
        props: { prefix: "R$", dense: true },
      },
      {
        name: "valorTotal",
        label: "Total",
        type: "COMPUTED",
        formula: "quantidade * valorUnitario",
        props: { prefix: "R$", dense: true, readonly: true },
      },
    ],
  },
  {
    name: "itens-estoque",
    label: "Movimento Estoque",
    columns: [
      {
        name: "produtoId",
        label: "Produto",
        type: "LOOKUP",
        lookupEndpoint: "/api/produtos/lookup/search",
        displayColumns: ["codigo", "descricao"],
        props: { dense: true },
      },
      {
        name: "quantidade",
        label: "Quantidade",
        type: "NUMBER",
        props: { dense: true },
      },
      {
        name: "lote",
        label: "Lote",
        type: "TEXT",
        props: { dense: true },
      },
      {
        name: "dataValidade",
        label: "Validade",
        type: "DATE",
        props: { dense: true },
      },
    ],
  },
];

// Watch
watch(
  () => props.modelValue,
  (newVal) => {
    localColumns.value = JSON.parse(JSON.stringify(newVal || []));
  },
  { immediate: true, deep: true }
);

// Methods
function updateColumns() {
  emit("update:modelValue", localColumns.value);
}

function addColumn() {
  const newColumn = {
    name: `coluna${localColumns.value.length + 1}`,
    label: `Coluna ${localColumns.value.length + 1}`,
    type: "TEXT",
    align: "left",
    props: {},
  };

  localColumns.value.push(newColumn);
  updateColumns();
}

function removeColumn(index) {
  localColumns.value.splice(index, 1);
  updateColumns();
}

function addDisplayColumn(val, column) {
  if (!column.displayColumns) {
    column.displayColumns = [];
  }
  column.displayColumns.push(val);
  updateColumns();
}

function applyTemplate(template) {
  localColumns.value = JSON.parse(JSON.stringify(template.columns));
  updateColumns();
}
</script>

<style scoped>
.table-column-editor {
  max-height: 400px;
  overflow-y: auto;
}

.column-item {
  background-color: #f8f9fa;
}

.column-item:hover {
  background-color: #f0f0f0;
}

.drag-handle {
  cursor: move;
}
</style>
