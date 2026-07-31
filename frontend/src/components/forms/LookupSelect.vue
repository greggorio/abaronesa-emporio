<template>
  <div>
    <!-- readonly input that triggers the lookup dialog -->
    <q-input
      dense
      outlined
      stack-label
      :label="field?.label || 'Selecionar'"
      v-model="displayLabel"
      :disable="props.disabled || field?.readOnly"
      :error="!!error"
      :error-message="error"
      :placeholder="field?.placeholder"
      readonly
      @click="openDialog"
    >
      <template #append>
        <q-icon name="search" class="cursor-pointer" @click.stop="openDialog" v-if="!props.disabled && !field?.readOnly" />
        <q-icon name="close" class="cursor-pointer" @click.stop="clearSelection" v-if="modelValue && !props.disabled && !field?.readOnly" />
      </template>
    </q-input>

    <!-- dialog with remote-searchable QTable -->
    <q-dialog v-model="dialog" maximized persistent>
      <q-card class="flex flex-col h-full">
        <!-- header -->
        <q-bar class="bg-primary text-white">
          <div>{{ field?.label || "Selecionar" }}</div>
          <q-space />
          <q-btn dense flat icon="close" @click="dialog = false" />
        </q-bar>

        <!-- search -->
        <q-card-section class="q-pb-none bg-gray-50">
          <q-input
            v-model="search"
            dense
            debounce="300"
            :placeholder="field?.placeholder || 'Pesquisar...'"
            hide-bottom-space
            @update:model-value="fetchOptions"
            autofocus
            clearable
          >
            <template #append>
              <q-icon name="search" />
            </template>
          </q-input>
        </q-card-section>

        <!-- table -->
        <q-card-section class="q-pt-none scroll" style="height: calc(100% - 130px)">
          <q-table
            :rows="options"
            :columns="tableColumns"
            :row-key="optionValueKey"
            :loading="loading"
            flat
            dense
            :rows-per-page-options="[10, 20, 50]"
            :pagination="{ rowsPerPage: 10 }"
            @row-click="handleRowClick"
            @row-dblclick="handleRowDblClick"
            :selected-rows-label="getSelectedString"
            selection="single"
            v-model:selected="selected"
          >
            <template v-slot:body-cell="props">
              <q-td :props="props">
                {{ getCellValue(props.row, props.col.field) }}
              </q-td>
            </template>

            <template v-slot:no-data>
              <div class="full-width text-center q-pa-md text-grey-6">
                <q-icon name="search_off" size="48px" class="q-mb-sm" />
                <div>Nenhum resultado encontrado</div>
              </div>
            </template>
          </q-table>
        </q-card-section>

        <!-- footer -->
        <q-card-actions align="right" class="bg-gray-50">
          <q-btn flat label="Cancelar" color="secondary" @click="dialog = false" />
          <q-btn v-if="allowCreate && !loading" flat label="Criar Novo" icon="add" color="primary" @click="openCreateDialog" />
          <q-btn flat label="Selecionar" color="primary" @click="confirmSelection" :disable="!selected || selected.length === 0" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<script setup>
import { ref, watch, computed, onMounted } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";
import eventBus from "@/eventBus";

const props = defineProps({
  modelValue: { default: null },
  field: { type: Object, required: true },
  disabled: { type: Boolean, default: false },
  error: { type: String, default: null },
});

const emit = defineEmits(["update:model-value"]);

const $q = useQuasar();
const { apiRequest } = useApiRequest();

const dialog = ref(false);
const search = ref("");
const options = ref([]);
const loading = ref(false);
const selected = ref([]);
const displayLabel = ref("");

// Configurações do campo
const optionLabelKey = computed(() => props.field?.props?.["option-label"] || "label");
const optionValueKey = computed(() => props.field?.props?.["option-value"] || "value");
const allowCreate = computed(() => props.field?.allowCreate || props.field?.props?.allowCreate || false);

// Colunas da tabela baseadas em displayColumns ou padrão
const tableColumns = computed(() => {
  const cols = props.field?.displayColumns || [optionLabelKey.value];
  return cols.map((col) => {
    // Se for string simples, criar objeto de coluna
    if (typeof col === "string") {
      return {
        name: col,
        label:
          col.charAt(0).toUpperCase() +
          col
            .slice(1)
            .replace(/([A-Z])/g, " $1")
            .trim(),
        field: col,
        align: "left",
        sortable: true,
      };
    }
    // Se já for objeto, usar como está
    return col;
  });
});

// Observar mudanças no valor
watch(
  () => props.modelValue,
  async (val) => {
    if (!val) {
      displayLabel.value = "";
      selected.value = [];
      return;
    }

    // Se temos opções carregadas, procurar o label
    const opt = options.value.find((o) => o[optionValueKey.value] === val);
    if (opt) {
      displayLabel.value = opt[optionLabelKey.value] || "";
      selected.value = [opt];
    } else {
      // Se não encontrou nas opções, pode precisar buscar
      displayLabel.value = "Carregando...";
      await fetchSingleOption(val);
    }
  },
  { immediate: true }
);

// Buscar uma única opção pelo ID
async function fetchSingleOption(id) {
  if (!props.field?.lookupEndpoint || !id) return;

  try {
    const url = `${props.field.lookupEndpoint}/${id}`;
    const resp = await apiRequest(url);
    if (resp) {
      const item = resp.data || resp;
      displayLabel.value = item[optionLabelKey.value] || `ID: ${id}`;
      // Adicionar às opções se não existir
      const exists = options.value.some((o) => o[optionValueKey.value] === id);
      if (!exists) {
        options.value.unshift(item);
      }
    }
  } catch (e) {
    console.log("Não foi possível carregar o label para o ID:", id);
    displayLabel.value = `ID: ${id}`;
  }
}

// Buscar opções
async function fetchOptions() {
  if (!props.field?.lookupEndpoint) return;

  loading.value = true;
  try {
    const url = `${props.field.lookupEndpoint}?search=${encodeURIComponent(search.value || "")}`;
    const resp = await apiRequest(url);

    // Tratar diferentes formatos de resposta
    let data = [];
    if (Array.isArray(resp)) {
      data = resp;
    } else if (resp && resp.data && Array.isArray(resp.data)) {
      data = resp.data;
    } else if (resp && resp.content && Array.isArray(resp.content)) {
      data = resp.content;
    }

    options.value = data;
  } catch (e) {
    console.error("Erro ao carregar opções:", e);
    $q.notify({
      type: "negative",
      message: "Erro ao carregar opções",
      caption: e.message || "Erro desconhecido",
    });
    options.value = [];
  } finally {
    loading.value = false;
  }
}

// Abrir dialog
function openDialog() {
  if (props.disabled || props.field?.readOnly) return;

  dialog.value = true;
  search.value = "";
  // Selecionar item atual se existir
  if (props.modelValue && options.value.length > 0) {
    const currentItem = options.value.find((o) => o[optionValueKey.value] === props.modelValue);
    if (currentItem) {
      selected.value = [currentItem];
    }
  }
  // Buscar opções iniciais
  fetchOptions();
}

// Limpar seleção
function clearSelection() {
  emit("update:model-value", null);
  displayLabel.value = "";
  selected.value = [];
}

// Click na linha
function handleRowClick(evt, row) {
  selected.value = [row];
}

// Duplo click na linha
function handleRowDblClick(evt, row) {
  selected.value = [row];
  confirmSelection();
}

// Confirmar seleção
function confirmSelection() {
  if (!selected.value || selected.value.length === 0) return;

  const selectedItem = selected.value[0];
  const value = selectedItem[optionValueKey.value];
  const label = selectedItem[optionLabelKey.value];

  emit("update:model-value", value);
  displayLabel.value = label || "";
  dialog.value = false;
}

// Obter valor de célula (suporta nested properties)
function getCellValue(row, field) {
  if (!field || !row) return "";

  // Suportar campos aninhados com ponto (ex: "produto.nome")
  const keys = field.split(".");
  let value = row;

  for (const key of keys) {
    if (value && typeof value === "object") {
      value = value[key];
    } else {
      return "";
    }
  }

  return value || "";
}

// String de seleção
function getSelectedString() {
  return selected.value.length === 0 ? "" : `${selected.value.length} selecionado(s)`;
}

// Abrir dialog de criação
function openCreateDialog() {
  if (!props.field?.createDialogComponent) return;

  dialog.value = false;
  eventBus.emit("open-custom-dialog", {
    component: props.field.createDialogComponent,
    props: {
      quickCreate: true,
      onCreated: (newItem) => {
        // Adicionar novo item às opções
        options.value.unshift(newItem);
        // Selecionar o novo item
        emit("update:model-value", newItem[optionValueKey.value]);
        displayLabel.value = newItem[optionLabelKey.value] || "";
      },
    },
  });
}

// Ao montar, verificar se precisa carregar o label inicial
onMounted(() => {
  if (props.modelValue && !displayLabel.value) {
    fetchSingleOption(props.modelValue);
  }
});
</script>

<style scoped>
.scroll {
  overflow-y: auto;
}

.q-table >>> .q-table__top {
  padding: 0;
}
</style>
