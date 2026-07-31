<template>
  <div class="table-preview">
    <!-- Table Header -->
    <div class="table-header">
      <div class="row items-center">
        <div class="col">
          <h5 class="q-ma-none">{{ store.formDefinition.programName }}</h5>
          <div class="text-caption text-grey-6">Lista de registros</div>
        </div>
        <div class="col-auto q-gutter-sm">
          <q-btn
            v-for="(action, index) in tableActions"
            :key="index"
            :flat="action.type !== 'ADD'"
            :unelevated="action.type === 'ADD'"
            :label="action.label"
            :icon="action.icon"
            :color="action.color || 'grey-7'"
          />
        </div>
      </div>
    </div>

    <!-- Table -->
    <q-card flat class="q-mt-lg">
      <q-table
        :columns="tableColumns"
        :rows="sampleRows"
        row-key="id"
        flat
        :pagination="pagination"
        :rows-per-page-options="[5, 10, 20, 50]"
        class="preview-table"
      >
        <template v-slot:body-cell-actions="props">
          <q-td :props="props">
            <q-btn
              v-for="(action, index) in inlineActions"
              :key="index"
              :icon="action.icon"
              size="sm"
              flat
              dense
              round
              :color="action.color || 'grey-7'"
              class="q-mr-xs"
            >
              <q-tooltip>{{ action.label }}</q-tooltip>
            </q-btn>
          </q-td>
        </template>

        <!-- Custom cell rendering for formatted columns -->
        <template
          v-for="col in tableColumns.filter((c) => c.name !== 'actions')"
          :key="`body-cell-${col.name}`"
          v-slot:[`body-cell-${col.name}`]="props"
        >
          <q-td :props="props" :class="props.col.classes">
            {{ formatCellValue(props.value, props.col) }}
          </q-td>
        </template>
      </q-table>
    </q-card>

    <!-- Table Configuration -->
    <div class="table-config q-mt-lg">
      <q-card flat>
        <q-card-section>
          <TableConfig v-model="store.formDefinition.tableColumns" />
        </q-card-section>
      </q-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from "vue";
import { useQuasar } from "quasar";
import { useFormBuilderStore } from "@/stores/formBuilderStore";
import TableConfig from "./TableConfig.vue";

const $q = useQuasar();
const store = useFormBuilderStore();

// Estado
const pagination = ref({
  sortBy: "id",
  descending: false,
  page: 1,
  rowsPerPage: 5,
});

// Computed
const tableColumns = computed(() => {
  // Filtrar apenas colunas visíveis
  const cols = store.formDefinition.tableColumns
    .filter((col) => col.visible !== false) // 👈 Filtra colunas não visíveis
    .map((col) => ({
      name: col.name,
      label: col.label,
      field: col.name,
      align: col.align || "left",
      sortable: col.sortable !== false,
      // NÃO usar 'format' - o Quasar espera uma função
      style: col.width ? `width: ${col.width}` : "",
      classes: col.classes || "",
      // Armazenar formato e tipo em propriedades customizadas
      customFormat: col.format || null,
      columnType: col.type,
    }));

  // Add actions column
  cols.push({
    name: "actions",
    label: "Ações",
    field: "actions",
    align: "right",
    sortable: false,
  });

  return cols;
});

const tableActions = computed(() => {
  return store.formDefinition.actions.filter((action) => !action.inlineOnly && !action.onDoubleClick);
});

const inlineActions = computed(() => {
  return store.formDefinition.actions.filter((action) => action.type === "EDIT" || action.type === "DELETE" || action.type === "VIEW");
});

const sampleRows = computed(() => {
  const rows = [];
  // Gerar apenas dados para colunas visíveis
  const visibleColumns = store.formDefinition.tableColumns.filter((col) => col.visible !== false);

  for (let i = 1; i <= 20; i++) {
    const row = { id: i };
    visibleColumns.forEach((col) => {
      row[col.name] = generateSampleData(col, i);
    });
    rows.push(row);
  }
  return rows;
});

// Methods
function generateSampleData(col, index) {
  switch (col.type) {
    case "NUMBER":
      return Math.floor(Math.random() * 100) + index;
    case "CURRENCY":
      return (Math.random() * 1000 * index).toFixed(2);
    case "DATE":
      const date = new Date();
      date.setDate(date.getDate() - index);
      return date.toLocaleDateString("pt-BR");
    case "BOOLEAN":
      return index % 2 === 0;
    default:
      return `${col.label} ${index}`;
  }
}

function formatCellValue(value, column) {
  // Usar customFormat ao invés de format
  if (!column.customFormat || value === null || value === undefined) return value;

  const [type, param] = column.customFormat.split(":");

  switch (type) {
    case "currency":
      const currency = param || "BRL";
      return new Intl.NumberFormat("pt-BR", {
        style: "currency",
        currency: currency,
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
      // Implementar formatação de data conforme parâmetro
      return value;

    case "datetime":
      // Implementar formatação de datetime
      return value;

    default:
      return value;
  }
}

function getColumnClasses(column) {
  return column.classes || "";
}
</script>

<style lang="scss" scoped>
.table-preview {
  max-width: 1400px;
  margin: 0 auto;
}

.table-header {
  background-color: $grey-1;
  padding: 20px;
  border-radius: 8px;

  h5 {
    font-weight: 500;
    color: $grey-9;
  }
}

.preview-table {
  :deep(thead) {
    background-color: $grey-2;

    th {
      font-weight: 600;
      font-size: 13px;
      color: $grey-8;
    }
  }

  :deep(tbody tr) {
    &:hover {
      background-color: $blue-1;
    }
  }
}

.table-config {
  h6 {
    font-weight: 500;
    color: $grey-8;
  }
}
</style>
