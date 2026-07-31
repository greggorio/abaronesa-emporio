<template>
  <div class="q-page-header">
    <!-- Botões de ação do topo (dinâmicos) - agora ocupam o espaço do nome do programa -->
    <ActionButtons v-if="topActions.length > 0" context="top" :actions="topActions" :loading="loadingAction" @action="handleAction" />
  </div>

  <div class="q-pa-md" style="margin-top: -8px; margin-bottom: 0">
    <!-- Componente customizado se existir -->
    <component v-if="!!isCustomForm && CustomFormComponent" :is="CustomFormComponent" :form_data="formData" @reloadListagem="loadData" />

    <!-- Tabela padrão -->
    <BaseTableNew
      v-if="!isCustomForm"
      :rows="sortedTableRows"
      :columns="columns"
      row-key="id"
      :loading="loading"
      :filter="filter"
      :filter-fields="filterFields"
      :sort-method="customSort"
      :selected="selected"
      :pagination="pagination"
      @update:pagination="updatePagination"
      @page-changed="handlePageChange"
      @generate-full-report="handleGenerateFullReport"
      @row-click="onRowClick"
      @row-dblclick="onRowDblClick"
      @filter-changed="onFilterChanged"
      @advanced-filters-changed="onAdvancedFiltersChanged"
    >
      <!-- Quick search à esquerda do botão de filtros -->
      <template #toolbar-left>
        <q-input
          v-model="quickSearch"
          borderless
          clearable
          debounce="500"
          placeholder="Busca rápida..."
          @update:model-value="handleQuickSearchInput"
          class="quick-search-input"
        >
          <template #prepend>
            <q-icon name="search" size="20px" color="primary" />
          </template>
        </q-input>
      </template>
      <!-- Slot para coluna de imagem -->
      <template v-if="showImageColumn" v-slot:body-cell-cover="{ props }">
        <q-img
          v-if="props.row.cover"
          :src="getImageUrl(props.row.cover)"
          :alt="props.row.descricao || props.row.nome"
          style="width: 60px; height: 60px; object-fit: cover"
          @error="handleImageError"
        />
        <div v-else style="width: 60px; height: 60px; background: #f5f5f5"></div>
      </template>

      <!-- Slot para coluna ações -->
      <template v-if="rowActions.length > 0" v-slot:body-cell-acoes="{ props }">
        <ActionButtons context="row" :actions="rowActions" :row="props.row" :loading="loadingAction" @action="handleAction" />
      </template>

      <!-- Slots customizados adicionais -->
      <template v-for="slot in customSlots" :key="slot.name" v-slot:[slot.name]="slotProps">
        <component :is="slot.component" v-bind="slotProps" />
      </template>
    </BaseTableNew>

    <!-- FAB para ações em lote -->
    <ActionButtons
      v-if="selected && Array.isArray(selected) && selectionActions.length > 0"
      context="selection"
      :actions="selectionActions"
      :selected="selected"
      :loading="loadingAction"
      @action="handleAction"
    />

    <!-- Dialog genérico ou customizado -->
    <component
      v-if="dialogComponent"
      :is="dialogComponent"
      v-model="dialogVisible"
      :registro="selectedRecord"
      :form_definitions="formDefinitions"
      v-bind="dialogProps"
      :dialog-style="dialogConfig"
      :fullscreen-mobile="dialogFullscreenMobile"
      @salvar-registro="handleSaveRecord"
      @fechar-dialogo="closeDialog"
      @reloadListagem="loadData"
    />

    <!-- Dialogs customizados adicionais -->
    <component
      v-if="currentCustomDialog && showCustomDialog"
      :is="currentCustomDialog"
      v-model="showCustomDialog"
      v-bind="customDialogProps"
      @dialogoConcluido="loadData"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch, onBeforeUnmount } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useQuasar } from "quasar";
import { useActions } from "@/composables/useActions";
import { useDynamicTable } from "@/composables/useDynamicTable";
import { useTableColumns } from "@/composables/useTableColumns";
import { useCrudOperations } from "@/composables/useCrudOperations";
import { useDynamicComponents } from "@/composables/useDynamicComponents";
import { baseApiUrl } from "@/global";
import BaseTableNew from "@/components/utils/BaseTableNew.vue";
import ActionButtons from "@/components/actions/ActionButtons.vue";
import GenericFormDialog from "@/components/forms/GenericFormDialog.vue";
import { useProgramStore } from "src/stores/programStore";
import eventBus from "@/eventBus";

// Props e Emits
const props = defineProps({
  programa: {
    type: String,
    default: null,
  },
  customSlots: {
    type: Array,
    default: () => [],
  },
  showImageColumn: {
    type: Boolean,
    default: true,
  },
  dialogComponent: {
    type: [Object, String],
    default: null,
  },
  dialogProps: {
    type: Object,
    default: () => ({}),
  },
});

const emit = defineEmits(["loaded", "error"]);

// Inicialização
const $q = useQuasar();
const route = useRoute();
const router = useRouter();
const programStore = useProgramStore();
const endpoint = ref(props.programa || route.query.programa || "");

// Composables
const { handleAction: processAction, createActionGetters, loadingAction } = useActions();

const {
  response,
  loading,
  selected,
  filter,
  pagination,
  activeFilters,
  tableData,
  tableOrder,
  programName,
  formDefinitions,
  tableDefinitions,
  actions,
  loadData,
  loadFullData,
  updatePagination,
  handlePageChange,
  selectRow,
  applyFilters,
  ProgramIcon,
  // NOVA: Importar funções de paginação adaptativa
  initializeDynamicPagination,
  cleanupDynamicPagination,
  dynamicRowsPerPage,
} = useDynamicTable(endpoint);

const { generateColumns, customSort } = useTableColumns();

const { dialogVisible, selectedRecord, isEditing, createRecord, editRecord, deleteRecord, saveRecord, closeDialog } = useCrudOperations(endpoint, {
  onDataChanged: loadData,
});

const { loadComponent } = useDynamicComponents();

// Estado adicional
const isCustomForm = ref(false);
const formData = ref({});
const showCustomDialog = ref(false);
const currentCustomDialog = ref(null);
const customDialogProps = ref({});
const customDialogs = ref({});

// Busca rápida unificada no mesmo canal dos filtros avançados
const quickSearch = ref("");

// Computed properties
const columns = computed(() => {
  const tableDefs = tableDefinitions.value?.columns || tableDefinitions.value || [];

  const visibleColumns = tableDefs.filter((col) => {
    return col.visible !== false;
  });

  return generateColumns(visibleColumns, {
    includeImage: props.showImageColumn,
    includeActions: rowActions.value.length > 0,
  });
});

const filterFields = computed(() => {
  return response.value?.filter_fields || null;
});

const sortedTableRows = computed(() => {
  return customSort(
    tableData.value,
    pagination.value.sortBy, // ← Use sortBy em vez de tableOrder
    pagination.value.descending
  );
});
// Distribui ações por contexto
const { topActions, rowActions, selectionActions, doubleClickAction } = createActionGetters(actions);

// Componentes dinâmicos
const CustomFormComponent = computed(() => {
  const componentName = response.value?.custom_form_component;
  return componentName ? loadComponent(componentName) : null;
});

const dialogComponent = computed(() => {
  if (props.dialogComponent) {
    return typeof props.dialogComponent === "string" ? loadComponent(props.dialogComponent) : props.dialogComponent;
  }

  const dialogName = response.value?.dialog_component;
  return dialogName ? loadComponent(dialogName) : GenericFormDialog;
});

// Configurações do diálogo vindas do backend
const dialogConfig = computed(() => {
  return response.value?.dialog_config || {
    width: '800px',
    maxWidth: '95vw',
    maxHeight: '90vh',
  };
});

const dialogFullscreenMobile = computed(() => {
  return response.value?.dialog_config?.fullscreenMobile ?? true;
});

// Handler principal de ações
async function handleAction(action, target = null) {
  if (action.opensDialog && action.dialogComponent) {
    await handleCustomDialogAction(
      {
        component: action.dialogComponent,
        dialogProps: action.props || action.dialogProps || {},
      },
      target
    );
    return;
  }

  // Verifica se é uma ação customizada com componente
  if (action.component) {
    await handleCustomDialogAction(action, target);
    return;
  }

  // Processa ação padrão
  await processAction(action, target, {
    onAdd: createRecord,
    onEdit: editRecord,
    onDelete: async (item) => {
      if (item?.id) {
        const success = await deleteRecord(item.id);
        if (success) await loadData();
      }
    },
    onRefresh: loadData,
    onSuccess: async (action, target) => {
      if (action.successMessage) {
        $q.notify({
          type: "positive",
          message: action.successMessage,
        });
      }
      await loadData();
    },
  });
}

// Handler para ações com diálogos customizados
async function handleCustomDialogAction(action, target) {
  if (!customDialogs.value[action.component]) {
    customDialogs.value[action.component] = loadComponent(action.component);
  }

  currentCustomDialog.value = customDialogs.value[action.component];

  // Prepara props específicas para cada tipo de diálogo
  customDialogProps.value = {
    registro: target,
    ...action.dialogProps,
  };

  showCustomDialog.value = true;
}

// Handler para salvar registro e recarregar dados
async function handleSaveRecord(data, callback) {
  try {
    const result = await saveRecord(data, callback);
    await loadData(); // Recarrega os dados após salvar
    return result;
  } catch (error) {
    // Erro já foi tratado no saveRecord
    throw error;
  }
}

// Handlers de eventos
function onRowClick(row) {
  selectRow(row);
}

function onRowDblClick({ row, index, event }) {
  if (doubleClickAction.value) {
    handleAction(doubleClickAction.value, row);
  } else {
    editRecord(row);
  }
}

function onFilterChanged(newFilter) {
  filter.value = newFilter;
  loadData();
}

function onAdvancedFiltersChanged(filters) {
  console.log("[PrgContainerNew] onAdvancedFiltersChanged recebeu:", JSON.stringify(filters, null, 2));

  const merged = { ...filters };
  const term = (quickSearch.value || "").trim();

  // Adicionar ou remover $quick baseado no termo de busca
  if (term.length >= 3) {
    merged["$quick"] = { value: term };
  } else {
    // Remove $quick se existir
    delete merged["$quick"];
  }

  console.log("[PrgContainerNew] Enviando para applyFilters:", JSON.stringify(merged, null, 2));

  // SEMPRE aplicar filtros (avançados + busca rápida)
  pagination.value.page = 1;
  applyFilters(merged);
}

async function handleGenerateFullReport({ filters, localFilter }, callback) {
  loading.value = true;
  try {
    const allData = await loadFullData(filters);
    callback(allData);
  } catch (error) {
    console.error("Erro ao carregar dados completos:", error);
    $q.notify({
      type: "negative",
      message: "Erro ao obter dados completos para o relatório.",
    });
    callback([]);
  } finally {
    loading.value = false;
  }
}

// Funções auxiliares
function getImageUrl(path) {
  if (!path) return "";

  if (path.startsWith("http://") || path.startsWith("https://")) {
    return path;
  }

  if (path.startsWith("/")) {
    return `${baseApiUrl}${path}`;
  }

  return `${baseApiUrl}/media/${path}`;
}

function handleImageError(event) {
  console.error("Erro ao carregar imagem:", event.target.src);
}

// Handler da busca rápida
function handleQuickSearchInput(val) {
  const term = (val || "").trim();
  const merged = { ...activeFilters.value };
  const hasQuick = Object.prototype.hasOwnProperty.call(activeFilters.value, "$quick");

  if (term.length === 0) {
    if (hasQuick) {
      delete merged["$quick"];
      pagination.value.page = 1;
      applyFilters(merged);
    }
    return;
  }

  if (term.length < 3) {
    // Não dispara request enquanto abaixo do limiar, mas remove $quick se estava ativo
    if (hasQuick) {
      delete merged["$quick"];
      pagination.value.page = 1;
      applyFilters(merged);
    }
    return;
  }

  // 3+ caracteres: aplica/atualiza $quick
  merged["$quick"] = { value: term };
  pagination.value.page = 1;
  applyFilters(merged);
}

// Event Bus handlers (para compatibilidade)
const eventHandlers = {
  add: () => createRecord(),
  edit: () => editRecord(selected.value),
  delete: () => handleAction({ action: "delete" }, selected.value),
  refresh: () => loadData(),
  "custom-action": ({ action, row }) => handleAction(action, row),
  customFormClear: () => {
    isCustomForm.value = false;
  },
  "hide-actionbar": () => {
    /* implementar se necessário */
  },
  "show-actionbar": () => {
    /* implementar se necessário */
  },
  "reset-all-filters": () => {
    filter.value = "";
    activeFilters.value = {};
    pagination.value.page = 1;
    quickSearch.value = "";
  },
};

// Lifecycle
onMounted(async () => {
  // NOVA: Inicializar paginação adaptativa
  initializeDynamicPagination();

  // Registrar event handlers
  Object.entries(eventHandlers).forEach(([event, handler]) => {
    eventBus.on(event, handler);
  });

  eventBus.on("open-custom-dialog", ({ component, props }) => {
    handleCustomDialogAction({ component, dialogProps: props });
  });

  // Carregar dados iniciais
  try {
    await loadData();
    emit("loaded", response.value);

    // Emitir título do programa
    if (programName.value) {
      eventBus.emit("program_title", programName.value);
    }
    // Emitir ícone do programa
    if (ProgramIcon.value) {
      programStore.setProgramIcon(ProgramIcon.value);
      eventBus.emit("program_icon", ProgramIcon.value);
      console.log("Ícone do programa definido:", ProgramIcon.value);
    }
  } catch (error) {
    emit("error", error);
    $q.notify({
      type: "negative",
      message: `Erro ao carregar dados: ${error.message}`,
    });
  }
});

onBeforeUnmount(() => {
  // NOVA: Limpar listener de resize
  cleanupDynamicPagination();

  eventBus.off("open-custom-dialog");

  // Remover event handlers
  Object.entries(eventHandlers).forEach(([event, handler]) => {
    eventBus.off(event, handler);
  });
});

// Watchers
watch(
  () => route.query.programa,
  (newProgram) => {
    if (newProgram && newProgram !== endpoint.value) {
      endpoint.value = newProgram;
      pagination.value.page = 1;
      isCustomForm.value = false;
      eventBus.emit("reset-all-filters");
      loadData();
    }
  }
);

watch(ProgramIcon, (novo) => programStore.setProgramIcon(novo || ""));

watch(
  () => props.programa,
  (newProgram) => {
    if (newProgram && newProgram !== endpoint.value) {
      endpoint.value = newProgram;
      loadData();
    }
  }
);

// Expor métodos se necessário
defineExpose({
  loadData,
  createRecord,
  editRecord,
  selected,
});
</script>

<style scoped>
.q-page-header {
  display: flex;
  justify-content: flex-start; /* Alinha à esquerda, já que agora são só botões */
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

/* h4 removido, então o seletor abaixo pode ser excluído ou deixado vazio */
.q-page-header h4 {
  margin: 0;
}

/* Responsividade para mobile */
@media (max-width: 1023px) {
  .q-page-header {
    padding: 8px;
  }

  .q-pa-md {
    padding: 8px !important;
  }
}

@media (max-width: 599px) {
  .q-page-header {
    padding: 4px;
  }
}

/* Estilo moderno para o campo de busca rápida */
.quick-search-input {
  width: 220px;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 8px 8px 0 0;
  padding: 4px 12px;
  position: relative;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  backdrop-filter: blur(10px);
}

/* Expande no focus para melhor usabilidade */
.quick-search-input:focus-within {
  width: 280px;
}

/* Responsividade para mobile */
@media (max-width: 768px) {
  .quick-search-input {
    width: 160px;
  }

  .quick-search-input:focus-within {
    width: 200px;
  }
}

@media (max-width: 599px) {
  .quick-search-input {
    width: 120px;
    padding: 2px 8px;
  }

  .quick-search-input:focus-within {
    width: 160px;
  }

  .quick-search-input :deep(.q-field__control) {
    height: 36px;
  }

  .quick-search-input :deep(.q-field__native) {
    font-size: 0.85rem;
  }
}

.quick-search-input::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, #6B3E26 0%, #C67C48 100%);
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.quick-search-input:hover::after {
  transform: scaleX(0.3);
  opacity: 0.5;
}

.quick-search-input:focus-within::after {
  transform: scaleX(1);
  opacity: 1;
}

.quick-search-input :deep(.q-field__control) {
  height: 40px;
}

.quick-search-input :deep(.q-field__control::before),
.quick-search-input :deep(.q-field__control::after) {
  border: none !important;
}

.quick-search-input:hover {
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 2px 12px rgba(107, 62, 38, 0.12);
}

.quick-search-input :deep(.q-field__native) {
  font-size: 14px;
  font-weight: 500;
  color: #2A1F1B;
}

.quick-search-input :deep(.q-field__native::placeholder) {
  color: #8B7355;
  font-weight: 400;
  opacity: 0.7;
}

.quick-search-input :deep(.q-icon) {
  transition: all 0.3s ease;
}

.quick-search-input:hover :deep(.q-icon) {
  transform: scale(1.1);
  color: #C67C48 !important;
}

/* Dark mode support */
.body--dark .quick-search-input {
  background: rgba(30, 26, 24, 0.8);
}

.body--dark .quick-search-input:hover {
  background: rgba(42, 31, 27, 0.95);
  box-shadow: 0 2px 12px rgba(198, 124, 72, 0.15);
}

.body--dark .quick-search-input :deep(.q-field__native) {
  color: #D7B899;
}

.body--dark .quick-search-input :deep(.q-field__native::placeholder) {
  color: #8B7355;
  opacity: 0.6;
}
</style>
