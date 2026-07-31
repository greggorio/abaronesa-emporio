const handleSort = (columnName) => { if (isColumnSortable(columnName)) { // Encontrar a coluna para pegar o campo correto const column =
allColumns.value.find(col => col.name === columnName); const sortField = column?.dataField || column?.field || columnName; if (sortColumn.value ===
sortField) { sortDirection.value = sortDirection.value === "asc" ? "desc" : "asc"; } else { sortColumn.value = sortField; sortDirection.value = "asc";
} handlePaginationUpdate(sortField, sortDirection.value); } };
<template>
  <q-table
    :rows="filteredRows"
    :columns="visibleColumns"
    :row-key="rowKey"
    :loading="loading"
    :filter="localFilter"
    :sort-method="sortMethod"
    :pagination="localPagination"
    :rows-per-page-options="[pagination.rowsPerPage]"
    style="background-color: rgba(255, 255, 255, 0.9); height: calc(100vh - 135px)"
    table-class="text-info"
    table-header-class="text-primary"
    card-class="text-primary"
    dense
    color="secondary"
    flat
  >
    <!-- Slot para no-data -->
    <template v-slot:no-data>
      <slot name="no-data">
        <div class="full-height column flex-center" style="min-height: 300px; width: 100%">
          <div class="row justify-center items-center" style="opacity: 0.7">
            <q-icon name="info_outline" size="4rem" color="grey-7" class="q-mr-md" />
            <div class="column">
              <span class="text-h5 text-grey-8"><strong>Sem dados para exibir!</strong></span>
              <span class="text-grey-6">Não foram encontrados registros para os filtros atuais</span>
            </div>
          </div>
        </div>
      </slot>
    </template>

    <!-- Header customizável -->
    <template v-slot:header="props">
      <q-tr :props="props">
        <q-th
          v-for="col in props.cols"
          :key="col.name"
          :props="props"
          @click="handleSort(col.name)"
          :class="{ 'cursor-pointer': isColumnSortable(col.name) }"
        >
          <div class="row items-center no-wrap justify-between">
            <div>
              {{ col.label }}
              <span v-if="isColumnSortable(col.name)" class="q-ml-xs">
                <q-icon :name="getSortIcon(col.name)" size="12px" :color="sortColumn === col.name ? 'secondary' : 'grey-7'" />
              </span>
            </div>
          </div>
        </q-th>
      </q-tr>
    </template>

    <!-- Paginação -->
    <template v-slot:pagination>
      <div class="row">
        <div class="row items-center justify-start q-mr-md">
          <span class="q-mr-sm">
            Página {{ localPagination.page }} de {{ totalPages || 1 }}
            <span v-if="pagination.totalItems">({{ pagination.totalItems }} itens)</span>
          </span>
        </div>
        <q-pagination
          :model-value="localPagination.page"
          :max="totalPages"
          :direction-links="true"
          :boundary-links="true"
          :max-pages="7"
          :boundary-numbers="true"
          @update:model-value="handlePageChange"
          size="sm"
        />
        <q-btn flat round color="primary" icon="o_cloud_download" @click="exportTable" title="Exportar dados" />
        <q-btn-dropdown flat round color="primary" icon="o_description" title="Gerar Relatório">
          <q-list>
            <q-item clickable v-close-popup @click="generateReportHandler()">
              <q-item-section>
                <q-item-label>Gerar relatório (Auto)</q-item-label>
                <q-item-label caption>Detectar orientação automaticamente</q-item-label>
              </q-item-section>
            </q-item>
            <q-item clickable v-close-popup @click="generateReportHandler('portrait')">
              <q-item-section>
                <q-item-label>Formato Retrato</q-item-label>
                <q-item-label caption>Melhor para poucas colunas</q-item-label>
              </q-item-section>
              <q-item-section side>
                <q-icon name="crop_portrait" />
              </q-item-section>
            </q-item>
            <q-item clickable v-close-popup @click="generateReportHandler('landscape')">
              <q-item-section>
                <q-item-label>Formato Paisagem</q-item-label>
                <q-item-label caption>Melhor para muitas colunas</q-item-label>
              </q-item-section>
              <q-item-section side>
                <q-icon name="crop_landscape" />
              </q-item-section>
            </q-item>
          </q-list>
        </q-btn-dropdown>
      </div>
    </template>

    <!-- Top toolbar -->
    <template v-slot:top>
      <q-space />
      <div class="row q-gutter-sm items-center">
        <!-- Slot para conteúdo à esquerda dos botões da toolbar (ex.: busca rápida) -->
        <slot name="toolbar-left" />
        <q-btn
          dense
          color="primary"
          icon="filter_alt"
          @click="openFilterDialog"
          :class="{ 'text-orange': hasActiveFilters }"
          :label="hasActiveFilters ? `Filtros (${activeFiltersCount})` : ''"
        />
        <q-btn
          dense
          color="secondary"
          icon="view_column"
          @click="openColumnsDialog"
          :class="{ 'text-yellow': hasHiddenColumns }"
          title="Selecionar colunas visíveis"
        >
          <q-tooltip>Selecionar colunas</q-tooltip>
        </q-btn>
      </div>
    </template>

    <!-- Body com slots dinâmicos -->
    <template v-slot:body="props">
      <q-tr
        :props="props"
        @dblclick="onRowDblClick(props.row, props.pageIndex, $event)"
        :class="{
          'custom-hover': true,
          selected: isSelected(props.row),
          'striped-row': props.pageIndex % 2 === 0,
        }"
        @click="onRowClickHandler(props.row)"
      >
        <q-td
          v-for="col in props.cols"
          :key="col.name"
          :props="{ ...props, col }"
          :class="{ 'text-right': getMappedColumnType(col) === 'number' || getMappedColumnType(col) === 'currency' || col.format === 'currency' || isCurrencyColumn(col) }"
        >
          <!-- Slot dinâmico para células customizadas -->
          <slot :name="`body-cell-${col.name}`" :props="{ ...props, col, row: props.row, value: props.row[col.dataField || col.field] }">
            <!-- Renderização padrão baseada no tipo -->
            <template v-if="col.field === 'pago'">
              <q-icon
                :name="props.row[col.dataField || col.field] ? 'check_circle' : 'cancel'"
                :color="props.row[col.dataField || col.field] ? 'green' : 'red'"
              />
            </template>

            <template v-else-if="col.name === 'id'">
              <q-chip dense square color="secondary" text-color="white" size="sm">
                {{ String(props.row[col.dataField || col.field]).padStart(6, "0") }}
              </q-chip>
            </template>

            <template v-else-if="getMappedColumnType(col) === 'datetime'">
              {{ formatDateTime(props.row[col.dataField || col.field]) }}
            </template>

            <template v-else-if="getMappedColumnType(col) === 'date'">
              {{ formatDateOnly(props.row[col.dataField || col.field]) }}
            </template>

            <template v-else-if="col.name === 'status'">
              <q-chip
                dense
                square
                :color="props.row._statusColor || 'grey'"
                text-color="white"
                size="sm"
                :icon="props.row._statusIcon || 'o_hourglass_empty'"
              >
                {{ props.row._statusLabel || props.row[col.dataField || col.field] }}
              </q-chip>
            </template>

            <template v-else-if="getMappedColumnType(col) === 'boolean'">
              <q-checkbox
                :model-value="props.row[col.dataField || col.field]"
                disable
                dense
                color="primary"
              />
            </template>

            <template
              v-else-if="
                ['precoVenda', 'precoCusto'].includes(col.dataField || col.field) ||
                getMappedColumnType(col) === 'currency' ||
                col.format === 'currency' ||
                isCurrencyColumn(col)
              "
            >
              {{ formatarMoeda(props.row[col.dataField || col.field]) }}
            </template>

            <template v-else-if="getMappedColumnType(col) === 'number'">
              {{
                props.row[col.dataField || col.field] !== null && props.row[col.dataField || col.field] !== undefined
                  ? typeof props.row[col.dataField || col.field] === "number"
                    ? Number.isInteger(props.row[col.dataField || col.field])
                      ? props.row[col.dataField || col.field]
                      : props.row[col.dataField || col.field].toFixed(2).replace(".", ",")
                    : props.row[col.dataField || col.field]
                  : ""
              }}
            </template>

            <template v-else>
              <!-- NOVA IMPLEMENTAÇÃO: Texto com truncamento e tooltip -->
              <div class="text-truncate-cell">
                <q-icon v-if="col.icon" :name="col.icon" class="q-mr-xs" />
                <span v-if="shouldTruncate(props.row[col.dataField || col.field], col)">
                  {{ truncateText(props.row[col.dataField || col.field], col) }}
                  <q-tooltip anchor="center middle" self="center middle" :offset="[10, 10]" class="bg-grey-8 text-white" max-width="350px">
                    {{ props.row[col.dataField || col.field] }}
                  </q-tooltip>
                </span>
                <span v-else>
                  {{ props.row[col.dataField || col.field] }}
                </span>
              </div>
            </template>
          </slot>
        </q-td>
      </q-tr>
    </template>
  </q-table>

  <!-- Dialog de Filtro Avançado -->
  <q-dialog v-model="showFilterDialog" persistent :maximized="$q.screen.lt.md">
    <q-card style="width: 800px; max-width: 95vw; max-height: 85vh" class="filter-dialog">
      <q-card-section class="row items-center q-pb-sm filter-header">
        <q-icon name="filter_alt" color="primary" size="24px" class="q-mr-sm" />
        <div class="text-h6 text-primary">Filtros Avançados</div>
        <q-space />
        <q-badge v-if="hasActiveFilters" color="orange" :label="activeFiltersCount" />
        <q-btn icon="close" flat round dense v-close-popup color="grey-7" />
      </q-card-section>

      <q-separator />

      <q-card-section class="q-pa-md" style="max-height: 60vh; overflow-y: auto">
        <div class="row q-col-gutter-md">
          <div v-for="col in filterableColumns" :key="col.name" class="col-12 col-md-6 col-lg-4">
            <q-card flat bordered class="filter-card">
              <q-card-section class="q-pa-md">
                <div class="row items-center q-mb-sm">
                  <q-icon :name="getColumnIcon(col.field)" :color="isFilterActive(col.field) ? 'primary' : 'grey-6'" size="16px" class="q-mr-xs" />
                  <div class="text-subtitle2 text-weight-medium" :class="isFilterActive(col.field) ? 'text-primary' : 'text-grey-8'">
                    {{ col.label }}
                  </div>
                  <q-space />
                  <q-btn
                    v-if="isFilterActive(col.field)"
                    flat
                    round
                    dense
                    size="sm"
                    color="negative"
                    icon="clear"
                    @click="resetFilter(col.field)"
                    class="filter-clear-btn"
                  >
                    <q-tooltip>Limpar filtro</q-tooltip>
                  </q-btn>
                </div>

                <!-- Filtros para Texto -->
                <div v-if="getColumnType(col.field) === 'string'" class="column q-gutter-sm">
                  <q-select
                    v-model="getFilter(col.field).operator"
                    :options="stringOperators"
                    dense
                    outlined
                    option-label="label"
                    option-value="value"
                    emit-value
                    map-options
                    label="Operador"
                    color="primary"
                    class="filter-select"
                  />
                  <q-input v-model="getFilter(col.field).value" dense outlined label="Valor" clearable color="primary" />
                </div>

                <!-- Filtros para Números -->
                <div v-else-if="getColumnType(col.field) === 'number'" class="column q-gutter-sm">
                  <q-select
                    v-model="getFilter(col.field).operator"
                    :options="numberOperators"
                    dense
                    outlined
                    option-label="label"
                    option-value="value"
                    emit-value
                    map-options
                    label="Operador"
                    color="primary"
                    class="filter-select"
                  />
                  <q-input v-model="getFilter(col.field).value" dense outlined label="Valor" type="number" clearable color="primary" />
                  <q-input
                    v-if="getFilter(col.field).operator === 'between'"
                    v-model="getFilter(col.field).value2"
                    dense
                    outlined
                    label="Valor Final"
                    type="number"
                    clearable
                    color="primary"
                  />
                </div>

                <!-- Filtros para Data -->
                <div v-else-if="getColumnType(col.field) === 'date'" class="column q-gutter-sm">
                  <q-select
                    v-model="getFilter(col.field).operator"
                    :options="dateOperators"
                    dense
                    outlined
                    option-label="label"
                    option-value="value"
                    emit-value
                    map-options
                    label="Operador"
                    color="primary"
                    class="filter-select"
                  />

                  <!-- Interface para Data Atual -->
                  <div v-if="getFilter(col.field).operator === 'today'">
                    <q-banner dense class="bg-primary text-white rounded-borders date-today-banner">
                      <template v-slot:avatar>
                        <q-icon name="today" />
                      </template>
                      Data atual: {{ formatCurrentDate() }}
                    </q-banner>
                  </div>

                  <!-- Interface para datas simples -->
                  <div v-else-if="['equals', 'after', 'before'].includes(getFilter(col.field).operator)">
                    <div class="row q-gutter-sm">
                      <div class="col">
                        <q-input
                          v-model="getFilter(col.field).value"
                          dense
                          outlined
                          :label="getDateLabel(getFilter(col.field).operator)"
                          type="date"
                          clearable
                          color="primary"
                        />
                      </div>
                      <div class="col-auto">
                        <q-btn dense outline color="primary" icon="today" @click="setDateToday(col.field, 'value')" size="sm">
                          <q-tooltip>Definir como hoje</q-tooltip>
                        </q-btn>
                      </div>
                    </div>
                  </div>

                  <!-- Interface para Entre Datas -->
                  <div v-else-if="getFilter(col.field).operator === 'between'" class="column q-gutter-sm">
                    <div class="row q-gutter-sm">
                      <div class="col">
                        <q-input v-model="getFilter(col.field).value" dense outlined label="Data inicial" type="date" clearable color="primary" />
                      </div>
                      <div class="col-auto">
                        <q-btn dense outline color="primary" icon="today" @click="setDateToday(col.field, 'value')" size="sm" />
                      </div>
                    </div>
                    <div class="row q-gutter-sm">
                      <div class="col">
                        <q-input v-model="getFilter(col.field).value2" dense outlined label="Data final" type="date" clearable color="primary" />
                      </div>
                      <div class="col-auto">
                        <q-btn dense outline color="primary" icon="today" @click="setDateToday(col.field, 'value2')" size="sm" />
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Filtros para Boolean -->
                <div v-else-if="getColumnType(col.field) === 'boolean'" class="column q-gutter-sm">
                  <q-select
                    v-model="getFilter(col.field).value"
                    :options="booleanOptions"
                    dense
                    outlined
                    option-label="label"
                    option-value="value"
                    emit-value
                    map-options
                    label="Valor"
                    clearable
                    color="primary"
                    class="filter-select"
                  />
                </div>
              </q-card-section>
            </q-card>
          </div>
        </div>
      </q-card-section>

      <q-separator />

      <q-card-actions class="q-pa-md filter-actions">
        <div class="row full-width items-center">
          <div class="col-auto">
            <q-chip v-if="hasActiveFilters" color="orange" text-color="white" icon="filter_alt" :label="`${activeFiltersCount} filtro(s) ativo(s)`" />
          </div>
          <q-space />
          <div class="row q-gutter-sm">
            <q-btn flat label="Limpar Tudo" @click="resetAllFilters" :disable="!hasActiveFilters" color="negative" icon="clear_all" />
            <q-btn unelevated label="Aplicar Filtros" @click="applyFilters" color="primary" icon="check" />
          </div>
        </div>
      </q-card-actions>
    </q-card>
  </q-dialog>

  <!-- Dialog de Seleção de Colunas -->
  <q-dialog v-model="showColumnsDialog" persistent :maximized="$q.screen.lt.sm">
    <q-card style="width: 500px; max-width: 90vw">
      <q-card-section class="row items-center">
        <div class="text-h6">Selecionar Colunas</div>
        <q-space />
        <q-btn icon="close" flat round dense v-close-popup />
      </q-card-section>

      <q-card-section style="max-height: 70vh; overflow: auto">
        <q-list separator>
          <q-item v-for="col in allColumns" :key="col.name" tag="label" v-ripple>
            <q-item-section side>
              <q-checkbox :disable="isRequiredColumn(col.field)" v-model="col.visible" />
            </q-item-section>
            <q-item-section>
              <q-item-label>{{ col.label }}</q-item-label>
            </q-item-section>
          </q-item>
        </q-list>
      </q-card-section>

      <q-card-actions align="right" class="bg-white text-primary">
        <q-btn flat label="Restaurar padrão" @click="resetColumnVisibility" />
        <q-btn flat label="Aplicar" v-close-popup />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { ref, watch, toRefs, computed, reactive, onMounted, onBeforeUnmount, h } from "vue";
import { QSelect, QInput, QBanner, QIcon, QBtn } from "quasar";
import eventBus from "@/eventBus";
import { useQuasar } from "quasar";
import { formatDate, formatCurrentDate, isDateField, isDouble } from "@/composables/useTableUtils";
import { useTableExport } from "@/composables/useTableExport";
import { useReportGenerator } from "@/composables/useReportGenerator";
import { stringOperators, numberOperators, dateOperators, booleanOptions, getDefaultOperatorByType } from "@/config/tableFilterConfig";
import { useFormatarMoeda } from "@/composables/formatarMoeda";

// Componentes de filtro
const StringFilter = {
  props: ["filter", "columnType"],
  emits: ["update:filter"],
  setup(props, { emit }) {
    const updateOperator = (val) => emit("update:filter", { ...props.filter, operator: val });
    const updateValue = (val) => emit("update:filter", { ...props.filter, value: val });

    return () =>
      h("div", { class: "column q-gutter-sm" }, [
        h("q-select", {
          modelValue: props.filter.operator,
          options: stringOperators,
          dense: true,
          outlined: true,
          "option-label": "label",
          "option-value": "value",
          "emit-value": true,
          "map-options": true,
          label: "Operador",
          color: "primary",
          class: "filter-select",
          "onUpdate:modelValue": updateOperator,
        }),
        h("q-input", {
          modelValue: props.filter.value,
          dense: true,
          outlined: true,
          label: "Valor",
          clearable: true,
          color: "primary",
          "onUpdate:modelValue": updateValue,
        }),
      ]);
  },
};

const NumberFilter = {
  props: ["filter", "columnType"],
  emits: ["update:filter"],
  setup(props, { emit }) {
    const updateOperator = (val) => emit("update:filter", { ...props.filter, operator: val });
    const updateValue = (val) => emit("update:filter", { ...props.filter, value: val });
    const updateValue2 = (val) => emit("update:filter", { ...props.filter, value2: val });

    return () =>
      h("div", { class: "column q-gutter-sm" }, [
        h("q-select", {
          modelValue: props.filter.operator,
          options: numberOperators,
          dense: true,
          outlined: true,
          "option-label": "label",
          "option-value": "value",
          "emit-value": true,
          "map-options": true,
          label: "Operador",
          color: "primary",
          class: "filter-select",
          "onUpdate:modelValue": updateOperator,
        }),
        h("q-input", {
          modelValue: props.filter.value,
          dense: true,
          outlined: true,
          label: "Valor",
          type: "number",
          clearable: true,
          color: "primary",
          "onUpdate:modelValue": updateValue,
        }),
        props.filter.operator === "between"
          ? h("q-input", {
              modelValue: props.filter.value2,
              dense: true,
              outlined: true,
              label: "Valor Final",
              type: "number",
              clearable: true,
              color: "primary",
              "onUpdate:modelValue": updateValue2,
            })
          : null,
      ]);
  },
};

const DEFAULT_MAX_LENGTH = 50; // Caracteres padrão
const COLUMN_MAX_LENGTHS = {
  observacao: 30,
  descricao: 40,
  nome: 50,
  // Adicione outras colunas conforme necessário
};

function shouldTruncate(value, column) {
  if (!value || typeof value !== "string") return false;

  const maxLength = COLUMN_MAX_LENGTHS[column.field] || DEFAULT_MAX_LENGTH;
  return value.length > maxLength;
}

function truncateText(value, column) {
  if (!value || typeof value !== "string") return value;

  const maxLength = COLUMN_MAX_LENGTHS[column.field] || DEFAULT_MAX_LENGTH;
  if (value.length <= maxLength) return value;

  return value.substring(0, maxLength - 3) + "...";
}

// Funções auxiliares para filtros de data
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

// Função centralizada para mapear tipos do backend para tipos internos
function getMappedColumnType(col) {
  if (!col || !col.type) return 'string';

  const typeMap = {
    TEXT: 'string',
    TEXTAREA: 'string',
    NUMBER: 'number',
    CURRENCY: 'currency',
    DATE: 'date',
    DATETIME: 'datetime',
    BOOLEAN: 'boolean',
    // Tipos legados (manter compatibilidade)
    decimal: 'number',
    moeda: 'currency',
    date: 'date',
    boolean: 'boolean',
  };

  return typeMap[col.type] || col.type.toLowerCase();
}

// Formatar DATETIME sem segundos: dd/MM/yyyy HH:mm
function formatDateTime(dateString) {
  if (!dateString) return '';

  try {
    let date;
    if (typeof dateString === 'string') {
      // Formato ISO: 2025-10-18T10:32:07.529187
      if (dateString.includes('T')) {
        date = new Date(dateString);
      }
      // Formato brasileiro: dd/MM/yyyy HH:mm:ss
      else if (dateString.includes('/')) {
        const [datePart, timePart] = dateString.split(' ');
        const [day, month, year] = datePart.split('/');
        date = new Date(year, month - 1, day);
        if (timePart) {
          const [hours, minutes] = timePart.split(':');
          date.setHours(hours, minutes, 0);
        }
      }
      // Formato yyyy-mm-dd
      else {
        date = new Date(dateString);
      }
    } else {
      date = new Date(dateString);
    }

    if (isNaN(date.getTime())) return dateString;

    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');

    return `${day}/${month}/${year} ${hours}:${minutes}`;
  } catch (error) {
    console.error('Erro ao formatar datetime:', error);
    return dateString;
  }
}

// Formatar DATE (apenas data): dd/MM/yyyy
function formatDateOnly(dateString) {
  if (!dateString) return '';

  try {
    let date;
    if (typeof dateString === 'string') {
      // Formato ISO: 2025-10-18T10:32:07.529187 ou 2025-10-18
      if (dateString.includes('T') || dateString.match(/^\d{4}-\d{2}-\d{2}$/)) {
        date = new Date(dateString);
      }
      // Formato brasileiro: dd/MM/yyyy
      else if (dateString.includes('/')) {
        const [day, month, year] = dateString.split('/')[0].split('/');
        date = new Date(year, month - 1, day);
      }
      else {
        date = new Date(dateString);
      }
    } else {
      date = new Date(dateString);
    }

    if (isNaN(date.getTime())) return dateString;

    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();

    return `${day}/${month}/${year}`;
  } catch (error) {
    console.error('Erro ao formatar date:', error);
    return dateString;
  }
}

const DateFilter = {
  props: ["filter", "columnType"],
  emits: ["update:filter"],
  setup(props, { emit }) {
    const updateOperator = (val) => emit("update:filter", { ...props.filter, operator: val });
    const updateValue = (val) => emit("update:filter", { ...props.filter, value: val });
    const updateValue2 = (val) => emit("update:filter", { ...props.filter, value2: val });

    const setDateToday = (field) => {
      const today = formatCurrentDate();
      if (field === "value") {
        updateValue(today);
      } else {
        updateValue2(today);
      }
    };

    const getDateLabel = (operator) => {
      const labelMap = {
        equals: "Data",
        after: "A partir de",
        before: "Até",
      };
      return labelMap[operator] || "Data";
    };

    return () => {
      const operator = props.filter.operator;

      return h("div", { class: "column q-gutter-sm" }, [
        h("q-select", {
          modelValue: operator,
          options: dateOperators,
          dense: true,
          outlined: true,
          optionLabel: "label",
          optionValue: "value",
          emitValue: true,
          mapOptions: true,
          label: "Operador",
          color: "primary",
          class: "filter-select",
          "onUpdate:modelValue": updateOperator,
        }),

        operator === "today"
          ? h(
              "q-banner",
              {
                dense: true,
                class: "bg-primary text-white rounded-borders date-today-banner",
              },
              {
                default: () => `Data atual: ${formatCurrentDate()}`,
                avatar: () => h("q-icon", { name: "today" }),
              }
            )
          : ["equals", "after", "before"].includes(operator)
          ? h("div", { class: "row q-gutter-sm" }, [
              h("div", { class: "col" }, [
                h("q-input", {
                  modelValue: props.filter.value,
                  dense: true,
                  outlined: true,
                  label: getDateLabel(operator),
                  type: "date",
                  clearable: true,
                  color: "primary",
                  "onUpdate:modelValue": updateValue,
                }),
              ]),
              h("div", { class: "col-auto" }, [
                h("q-btn", {
                  dense: true,
                  outline: true,
                  color: "primary",
                  icon: "today",
                  size: "sm",
                  onClick: () => setDateToday("value"),
                }),
              ]),
            ])
          : operator === "between"
          ? h("div", { class: "column q-gutter-sm" }, [
              h("div", { class: "row q-gutter-sm" }, [
                h("div", { class: "col" }, [
                  h("q-input", {
                    modelValue: props.filter.value,
                    dense: true,
                    outlined: true,
                    label: "Data inicial",
                    type: "date",
                    clearable: true,
                    color: "primary",
                    "onUpdate:modelValue": updateValue,
                  }),
                ]),
                h("div", { class: "col-auto" }, [
                  h("q-btn", {
                    dense: true,
                    outline: true,
                    color: "primary",
                    icon: "today",
                    size: "sm",
                    onClick: () => setDateToday("value"),
                  }),
                ]),
              ]),
              h("div", { class: "row q-gutter-sm" }, [
                h("div", { class: "col" }, [
                  h("q-input", {
                    modelValue: props.filter.value2,
                    dense: true,
                    outlined: true,
                    label: "Data final",
                    type: "date",
                    clearable: true,
                    color: "primary",
                    "onUpdate:modelValue": updateValue2,
                  }),
                ]),
                h("div", { class: "col-auto" }, [
                  h("q-btn", {
                    dense: true,
                    outline: true,
                    color: "primary",
                    icon: "today",
                    size: "sm",
                    onClick: () => setDateToday("value2"),
                  }),
                ]),
              ]),
            ])
          : null,
      ]);
    };
  },
};

const BooleanFilter = {
  props: ["filter", "columnType"],
  emits: ["update:filter"],
  setup(props, { emit }) {
    const updateValue = (val) => emit("update:filter", { ...props.filter, value: val });

    return () =>
      h("div", { class: "column q-gutter-sm" }, [
        h("q-select", {
          modelValue: props.filter.value,
          options: booleanOptions,
          dense: true,
          outlined: true,
          "option-label": "label",
          "option-value": "value",
          "emit-value": true,
          "map-options": true,
          label: "Valor",
          clearable: true,
          color: "primary",
          class: "filter-select",
          "onUpdate:modelValue": updateValue,
        }),
      ]);
  },
};

const { exportToCSV } = useTableExport();
const { generateReport } = useReportGenerator();
const $q = useQuasar();
const { formatarMoeda } = useFormatarMoeda();

// Heurística para identificar colunas monetárias quando o backend não envia format
function isCurrencyColumn(col) {
  if (!col) return false;
  const name = (col.name || col.field || '').toString().toLowerCase();
  const label = (col.label || '').toString().toLowerCase();
  // Removido 'total' para evitar falso positivo em 'totalSkus'
  const hints = ['preco', 'preço', 'valor', 'faturamento', 'receb', 'pago', 'saldo'];
  return hints.some(h => name.includes(h) || label.includes(h));
}

const props = defineProps({
  rows: Array,
  columns: Array,
  rowKey: { type: String, default: "id" },
  loading: Boolean,
  filter: { type: String, default: "" },
  sortMethod: Function,
  selected: Object,
  requiredColumns: {
    type: Array,
    default: () => [],
  },
  hideFilters: {
    type: Boolean,
    default: false,
  },
  filterFields: {
    type: Array,
    default: null,
  },
  pagination: {
    type: Object,
    default: () => ({
      page: 1,
      rowsPerPage: 15,
      sortBy: null,
      descending: false,
    }),
  },
});

const emit = defineEmits([
  "update:pagination",
  "page-changed",
  "generate-full-report",
  "row-click",
  "row-dblclick",
  "filter-changed",
  "advanced-filters-changed",
]);

const { filter, rows } = toRefs(props);
const localFilter = ref(filter.value);
const showFilterDialog = ref(false);
const sortColumn = ref(null);
const sortDirection = ref("asc");
const showColumnsDialog = ref(false);
const allColumns = ref([]);
const tituloRelatorio = ref("Sistema");

// Filtros
const filters = reactive({});
const allowedFilterFields = computed(() => {
  if (!props.filterFields || props.filterFields.length === 0) {
    return null;
  }
  return new Set(props.filterFields);
});

// Computed para colunas filtráveis
const filterableColumns = computed(() => {
  const allowed = allowedFilterFields.value;
  return visibleColumns.value.filter((col) => {
    if (["acoes", "actions"].includes(col.name)) {
      return false;
    }
    if (!allowed) {
      return true;
    }
    return allowed.has(col.field) || allowed.has(col.name);
  });
});

// Lifecycle hooks
onMounted(() => {
  eventBus.on("program_title", (title) => {
    tituloRelatorio.value = title;
  });

  eventBus.on("reset-all-filters", handleResetAllFilters);
  initializeColumns();

  if (props.columns) {
    props.columns.forEach((col) => {
      getFilter(col.field);
    });
  }

  // Inicializar ordenação se houver
  if (props.pagination.sortBy) {
    sortColumn.value = props.pagination.sortBy;
    sortDirection.value = props.pagination.descending ? "desc" : "asc";
  }
});

onBeforeUnmount(() => {
  // REMOVIDO: window.removeEventListener("resize", calculateRowsPerPage);
  eventBus.off("reset-all-filters", handleResetAllFilters);
  eventBus.off("program_title");
});

// REMOVIDA: função calculateRowsPerPage()

const totalPages = computed(() => {
  // Se o backend forneceu totalPages, use esse valor
  if (props.pagination.totalPages) {
    return props.pagination.totalPages;
  }

  // Caso contrário, calcule baseado nos dados filtrados
  const totalItems = props.pagination.totalItems || filteredRows.value.length;
  const rowsPerPage = props.pagination.rowsPerPage || 15;

  if (rowsPerPage === 0) return 0;

  return Math.ceil(totalItems / rowsPerPage);
});

function getFilter(field) {
  if (!filters[field]) {
    const type = getColumnType(field);
    filters[field] = {
      operator: getDefaultOperatorByType(type),
      value: "",
      value2: "",
      active: false,
    };
  }
  return filters[field];
}

function updateFilter(field, newFilter) {
  filters[field] = newFilter;
}

function openFilterDialog() {
  if (props.columns) {
    props.columns.forEach((col) => {
      getFilter(col.field);
    });
  }

  showFilterDialog.value = true;
}

function handlePageChange(newPage) {
  emit("update:pagination", { ...props.pagination, page: newPage });
  emit("page-changed", newPage);
}

function handlePaginationUpdate(column, direction) {
  emit("update:pagination", {
    ...props.pagination,
    sortBy: column,
    descending: direction === "desc" ? true : false,
  });
}

function getColumnIcon(field) {
  const type = getColumnType(field);
  const iconMap = {
    string: "text_fields",
    number: "numbers",
    date: "event",
    boolean: "toggle_on",
  };
  return iconMap[type] || "filter_alt";
}

function getColumnType(field) {
  // Primeiro verificar o tipo definido na coluna
  const column = props.columns.find((col) => col.field === field || col.name === field);
  if (column?.type) {
    // Mapear tipos do backend para tipos internos
    const typeMap = {
      // Tipos em MAIÚSCULO (padrão novo)
      TEXT: "string",
      NUMBER: "number",
      CURRENCY: "number",
      DATE: "date",
      DATETIME: "date",
      BOOLEAN: "boolean",
      // Tipos em minúsculo (legado/compatibilidade)
      text: "string",
      number: "number",
      currency: "number",
      date: "date",
      datetime: "date",
      boolean: "boolean",
      decimal: "number",
      moeda: "number",
      // Tipos especiais
      badge: "string",  // badges são filtráveis como texto
    };

    const mappedType = typeMap[column.type] || column.type;
    return mappedType;
  }

  // Fallback para detecção automática baseada nos dados
  if (field === "pago") return "boolean";

  if (!props.rows || props.rows.length === 0) return "string";

  const sampleRow = props.rows.find((row) => row[field] !== undefined && row[field] !== null);
  if (!sampleRow) return "string";

  const value = sampleRow[field];

  // Verificar se é uma data no formato brasileiro dd/mm/yyyy
  if (typeof value === "string") {
    // Formato brasileiro com ou sem hora
    if (value.match(/^\d{2}\/\d{2}\/\d{4}(\s\d{2}:\d{2}:\d{2})?$/)) {
      return "date";
    }

    // Outros formatos de data
    if (isDateField(value)) {
      return "date";
    }

    // Formato ISO
    if (value.match(/^\d{4}-\d{2}-\d{2}$/) || value.match(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/)) {
      return "date";
    }
  }

  if (typeof value === "number" || (typeof value === "string" && !isNaN(value) && value.trim() !== "")) {
    return "number";
  }

  if (typeof value === "boolean" || value === "true" || value === "false") {
    return "boolean";
  }
  return "string";
}

// Função auxiliar para verificar se coluna é ordenável
function isColumnSortable(colName) {
  const column = allColumns.value.find((col) => col.name === colName || col.field === colName);
  return column && column.customSortable === true;
}

function isFilterActive(field) {
  const filter = getFilter(field);
  return filter.operator === "today" || (filter.value !== "" && filter.value !== null);
}

function resetFilter(field) {
  const filter = getFilter(field);
  filter.value = "";
  filter.value2 = "";
  filter.active = false;
}

function resetAllFilters() {
  Object.keys(filters).forEach((field) => {
    const filter = filters[field];
    filter.value = "";
    filter.value2 = "";
    filter.active = false;
  });
  applyFilters();
}

function applyFilters() {
  Object.keys(filters).forEach((field) => {
    const filter = filters[field];
    filter.active = isFilterActive(field);
  });

  showFilterDialog.value = false;
  // Emitir o evento corretamente
  emit("advanced-filters-changed", { ...filters });
}

const filteredRows = computed(() => {
  // Quando há paginação server-side (totalItems fornecido),
  // os dados já vêm filtrados do backend, então apenas retornamos as rows
  if (!props.rows) return [];

  // Se há totalItems, significa que o backend está fazendo a paginação e filtragem
  if (props.pagination?.totalItems !== undefined) {
    return props.rows;
  }

  // Caso contrário, aplicar filtros locais (paginação client-side)
  return props.rows.filter((row) => {
    if (localFilter.value && !Object.keys(row).some((key) => String(row[key]).toLowerCase().includes(localFilter.value.toLowerCase()))) {
      return false;
    }

    for (const field in filters) {
      const filter = filters[field];
      if (!filter.active) continue;

      const value = row[field];
      const type = getColumnType(field);

      if (value === undefined || value === null) {
        return false;
      }

      switch (type) {
        case "string":
          if (!applyStringFilter(value, filter)) return false;
          break;
        case "number":
          if (!applyNumberFilter(value, filter)) return false;
          break;
        case "date":
          if (!applyDateFilter(value, filter)) return false;
          break;
        case "boolean":
          if (!applyBooleanFilter(value, filter)) return false;
          break;
      }
    }

    return true;
  });
});

function applyStringFilter(value, filter) {
  const strValue = String(value).toLowerCase();
  const filterValue = String(filter.value).toLowerCase();

  switch (filter.operator) {
    case "contains":
      return strValue.includes(filterValue);
    case "equals":
      return strValue === filterValue;
    case "startsWith":
      return strValue.startsWith(filterValue);
    case "endsWith":
      return strValue.endsWith(filterValue);
    case "notContains":
      return !strValue.includes(filterValue);
    default:
      return true;
  }
}

function applyNumberFilter(value, filter) {
  const numValue = typeof value === "number" ? value : Number(value);
  const filterValue = Number(filter.value);
  const filterValue2 = Number(filter.value2);

  switch (filter.operator) {
    case "equals":
      return numValue === filterValue;
    case "notEquals":
      return numValue !== filterValue;
    case "greaterThan":
      return numValue > filterValue;
    case "greaterThanOrEqual":
      return numValue >= filterValue;
    case "lessThan":
      return numValue < filterValue;
    case "lessThanOrEqual":
      return numValue <= filterValue;
    case "between":
      return numValue >= filterValue && numValue <= filterValue2;
    default:
      return true;
  }
}

function applyDateFilter(value, filter) {
  if (filter.operator !== "today" && (!filter.value || filter.value === "")) {
    return true;
  }

  let date;
  try {
    // Converter data do formato brasileiro (dd/mm/yyyy) ou ISO para objeto Date
    if (typeof value === "string") {
      if (value.includes("/")) {
        // Formato brasileiro: dd/mm/yyyy ou dd/mm/yyyy hh:mm:ss
        const [datePart] = value.split(" ");
        const [day, month, year] = datePart.split("/");
        date = new Date(year, month - 1, day);
      } else {
        // Formato ISO ou outros
        date = new Date(value);
      }
    } else {
      date = new Date(value);
    }

    if (isNaN(date.getTime())) return false;
  } catch (e) {
    return false;
  }

  if (filter.operator === "today") {
    const today = new Date();
    return date.getDate() === today.getDate() && date.getMonth() === today.getMonth() && date.getFullYear() === today.getFullYear();
  }

  let filterDate;
  try {
    filterDate = new Date(filter.value);
    if (isNaN(filterDate.getTime())) return true;
  } catch (e) {
    return true;
  }

  date.setHours(0, 0, 0, 0);
  filterDate.setHours(0, 0, 0, 0);

  switch (filter.operator) {
    case "equals":
      return date.getTime() === filterDate.getTime();
    case "before":
      return date <= filterDate;
    case "after":
      return date >= filterDate;
    case "between":
      if (!filter.value2) return date >= filterDate;
      let filterDate2;
      try {
        filterDate2 = new Date(filter.value2);
        if (isNaN(filterDate2.getTime())) return date >= filterDate;
      } catch (e) {
        return date >= filterDate;
      }
      filterDate2.setHours(0, 0, 0, 0);
      return date >= filterDate && date <= filterDate2;
    default:
      return true;
  }
}

function applyBooleanFilter(value, filter) {
  const boolValue = typeof value === "boolean" ? value : value === "true" || value === true || value === 1;
  const filterValue = filter.value === "true" || filter.value === true || filter.value === 1;

  return boolValue === filterValue;
}

function exportTable() {
  exportToCSV(filteredRows.value, visibleColumns.value);
}

const activeFiltersCount = computed(() => {
  return Object.values(filters).filter((f) => f.active).length;
});

const hasActiveFilters = computed(() => activeFiltersCount.value > 0);

const localPagination = computed(() => ({
  ...props.pagination,
  rowsPerPage: props.pagination.rowsPerPage || 15,
  rowsNumber: props.pagination.totalItems || filteredRows.value.length,
}));

function initializeColumns() {
  if (!props.columns || props.columns.length === 0) return;

  const savedConfig = localStorage.getItem(`table-columns-${location.pathname}`);
  const savedVisibility = savedConfig ? JSON.parse(savedConfig) : {};

  allColumns.value = props.columns.map((col) => ({
    ...col,
    visible: savedVisibility[col.field] !== undefined ? savedVisibility[col.field] : true,
  }));

}

function isRequiredColumn(field) {
  return props.requiredColumns.includes(field);
}

function openColumnsDialog() {
  showColumnsDialog.value = true;
}

function resetColumnVisibility() {
  allColumns.value.forEach((col) => {
    col.visible = true;
  });
  saveColumnVisibility();
}

function saveColumnVisibility() {
  const visibilityConfig = {};
  allColumns.value.forEach((col) => {
    visibilityConfig[col.field] = col.visible;
  });
  localStorage.setItem(`table-columns-${location.pathname}`, JSON.stringify(visibilityConfig));
}

const hasHiddenColumns = computed(() => {
  return allColumns.value.some((col) => !col.visible);
});

const visibleColumns = computed(() => {
  return allColumns.value.filter((col) => col.visible);
});

watch(
  () => props.pagination,
  (newPagination) => {
    // Removido log de debug
  },
  { deep: true }
);

watch(
  allColumns,
  () => {
    saveColumnVisibility();
  },
  { deep: true }
);

watch(
  () => props.columns,
  () => {
    initializeColumns();
  },
  { deep: true }
);

watch(localFilter, (newFilter) => {
  emit("filter-changed", newFilter);
});

watch(
  () => props.columns,
  (newColumns) => {
    if (newColumns) {
      newColumns.forEach((col) => {
        getFilter(col.field);
      });
    }
  },
  { deep: true }
);

function onRowClickHandler(row) {
  emit("row-click", row);
}

function onRowDblClick(row, index, event) {
  emit("row-dblclick", { row, index, event });
}

function isSelected(row) {
  return props.selected && row[props.rowKey] === props.selected[props.rowKey];
}

const handleSort = (columnName) => {
  const column = visibleColumns.value.find((col) => col.name === columnName);
  if (column && (column.sortable === true || column.customSortable === true)) {
    if (sortColumn.value === columnName) {
      sortDirection.value = sortDirection.value === "asc" ? "desc" : "asc";
    } else {
      sortColumn.value = columnName;
      sortDirection.value = "asc";
    }

    handlePaginationUpdate(columnName, sortDirection.value);
  }
};

const getSortIcon = (columnName) => {
  if (columnName !== sortColumn.value) {
    return "unfold_more";
  }
  return sortDirection.value === "asc" ? "arrow_upward" : "arrow_downward";
};

const handleResetAllFilters = () => {
  resetAllFilters();
  localFilter.value = "";
  sortColumn.value = null;
};

async function generateReportHandler(forcedOrientation = null) {
  emit("generate-full-report", { filters, localFilter: localFilter.value }, async (fullData) => {
    try {
      if (!fullData || fullData.length === 0) {
        $q.notify({
          color: "warning",
          message: "Não há dados para gerar o relatório",
          icon: "warning",
        });
        return;
      }

      // Filtrar colunas de ação antes de gerar o relatório
      const reportColumns = visibleColumns.value.filter(
        (col) => col.name !== "acoes" && col.field !== "acoes" && col.label !== "Ações"
      );

      await generateReport({
        rows: fullData,
        visibleColumns: reportColumns,
        title: tituloRelatorio.value,
        forcedOrientation,
        localFilter: localFilter.value,
        filters,
        getColumnType,
        stringOperators,
        numberOperators,
        dateOperators,
      });
    } finally {
    }
  });
}
</script>

<style scoped>
.text-right {
  text-align: right;
}
.custom-hover:hover {
  background-color: rgba(198, 124, 72, 0.15);
  color: #2A1F1B;
}

.selected {
  background-color: rgba(107, 62, 38, 0.2);
  color: #6B3E26;
}

.striped-row {
  background-color: rgba(191, 191, 193, 0.08);
}

.filter-dialog {
  background: linear-gradient(135deg, #f8f9ff 0%, #ffffff 100%);
}

.filter-header {
  background: linear-gradient(90deg, rgba(25, 118, 210, 0.05) 0%, rgba(25, 118, 210, 0.02) 100%);
  border-bottom: 1px solid rgba(25, 118, 210, 0.1);
}

.filter-card {
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(25, 118, 210, 0.1);
  border-radius: 8px;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.filter-card:hover {
  border-color: rgba(25, 118, 210, 0.3);
  box-shadow: 0 2px 8px rgba(25, 118, 210, 0.15);
  transform: translateY(-1px);
}

.filter-card:has(.filter-clear-btn) {
  border-color: rgba(25, 118, 210, 0.4);
  background: rgba(25, 118, 210, 0.02);
}

.filter-card:has(.filter-clear-btn)::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  width: 3px;
  height: 100%;
  background: linear-gradient(180deg, #1976d2 0%, #42a5f5 100%);
}

.filter-select .q-field__control {
  background: rgba(25, 118, 210, 0.02);
}

.date-today-banner {
  background: linear-gradient(90deg, #1976d2 0%, #42a5f5 100%) !important;
  font-size: 12px;
}

.filter-actions {
  background: linear-gradient(90deg, rgba(25, 118, 210, 0.02) 0%, rgba(255, 255, 255, 0.8) 100%);
  border-top: 1px solid rgba(25, 118, 210, 0.1);
}

.filter-clear-btn {
  opacity: 0.7;
  transition: opacity 0.2s ease;
}

.filter-clear-btn:hover {
  opacity: 1;
}

.body--dark .filter-dialog {
  background: linear-gradient(135deg, #1a1a1a 0%, #2a2a2a 100%);
}

.body--dark .filter-card {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.1);
}

.body--dark .filter-card:hover {
  border-color: rgba(25, 118, 210, 0.5);
  background: rgba(25, 118, 210, 0.05);
}

.text-truncate-cell {
  display: inline-block;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Ajuste para células específicas se necessário */
.q-table td {
  max-width: 250px; /* Ajuste conforme necessário */
}

/* Estilo específico para colunas que precisam de largura fixa */
.q-table td[data-col="observacao"] {
  max-width: 200px;
}

.q-table td[data-col="descricao"] {
  max-width: 300px;
}

@media (max-width: 1023px) {
  /* Toolbar mais compacto em tablets */
  .q-table__top {
    padding: 8px;
  }

  /* Botões menores */
  .q-table__top .q-btn {
    padding: 6px 10px;
  }
}

@media (max-width: 768px) {
  .filter-dialog {
    margin: 0;
    max-height: 100vh !important;
  }

  .filter-dialog .q-card-section {
    padding: 12px;
  }

  .filter-card {
    margin-bottom: 8px;
  }

  .filter-header {
    position: sticky;
    top: 0;
    z-index: 10;
    background: white;
  }

  .body--dark .filter-header {
    background: #1a1a1a;
  }

  .filter-actions {
    position: sticky;
    bottom: 0;
    z-index: 10;
  }

  .body--dark .filter-actions {
    background: #1a1a1a;
  }

  /* Filtros em coluna única no mobile */
  .filter-dialog .col-12.col-md-6.col-lg-4 {
    flex: 0 0 100%;
    max-width: 100%;
  }

  /* Tabela responsiva */
  .q-table {
    font-size: 0.9rem;
  }

  /* Paginação mais compacta */
  .q-pagination {
    font-size: 0.85rem;
  }

  .q-table__top {
    padding: 4px 8px;
  }

  .q-table__top .q-btn {
    padding: 4px 8px;
    font-size: 0.85rem;
  }
}

@media (max-width: 599px) {
  /* Mobile muito pequeno */
  .q-table {
    font-size: 0.85rem;
  }

  .q-table td, .q-table th {
    padding: 8px 4px;
  }

  /* Chips menores */
  .q-chip {
    font-size: 0.75rem;
    padding: 2px 8px;
  }

  /* Toolbar mais compacto */
  .q-table__top {
    padding: 4px;
  }

  /* Botões ainda menores em mobile muito pequeno */
  .q-table__top .q-btn {
    min-width: 32px;
    padding: 6px 8px;
  }

  /* Paginação simplificada */
  .q-pagination {
    font-size: 0.75rem;
  }

  .q-pagination .q-btn {
    min-width: 32px;
    padding: 4px;
  }
}

@media print {
  .report-page {
    padding: 10mm;
    margin: 0;
    box-shadow: none;
  }
}

.full-width-div {
  width: 100%;
}
</style>
