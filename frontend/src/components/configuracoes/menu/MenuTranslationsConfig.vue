<template>
  <div class="translations-configuracoes">
    <q-card class="config-card q-mb-lg" flat bordered>
      <q-card-section class="card-header">
        <div class="header-content">
          <div class="header-left">
            <div class="text-h5 text-weight-medium text-grey-9">Traduções do Cardápio</div>
            <div class="text-body2 text-grey-7 q-mt-xs">
              Revise e edite traduções (entity_translation) de categorias, produtos, SKUs e harmonizações. Ao confirmar a edição, a tradução é salva automaticamente como MANUAL.
            </div>
          </div>
          <div class="header-icon">
            <q-icon name="g_translate" size="42px" color="grey-6" />
          </div>
          <div class="search-container">
            <q-input
              v-model="filters.search"
              outlined
              dense
              clearable
              placeholder="Buscar texto fonte ou tradução"
              class="search-input"
              @keyup.enter="fetchTranslations"
              @clear="fetchTranslations"
            >
              <template #prepend>
                <q-icon name="search" color="grey-6" />
              </template>
              <template #append>
                <q-btn flat dense icon="search" @click="fetchTranslations" :loading="loading" />
              </template>
            </q-input>
          </div>
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section class="filters-section">
        <div class="row q-col-gutter-sm filters-grid">
          <div class="col-md-2 col-sm-6 col-xs-12">
            <q-select
              v-model="filters.locale"
              :options="localeOptions"
              label="Locale"
              dense
              clearable
              outlined
              emit-value
              map-options
              class="compact-select"
              popup-content-class="compact-select-menu"
            />
          </div>
          <div class="col-md-2 col-sm-6 col-xs-12">
            <q-select
              v-model="filters.status"
              :options="statusOptions"
              label="Status"
              dense
              clearable
              outlined
              emit-value
              map-options
              class="compact-select"
              popup-content-class="compact-select-menu"
            />
          </div>
          <div class="col-md-2 col-sm-6 col-xs-12">
            <q-select
              v-model="filters.entityType"
              :options="entityOptions"
              label="Entidade"
              dense
              clearable
              outlined
              emit-value
              map-options
              class="compact-select"
              popup-content-class="compact-select-menu"
            />
          </div>
          <div class="col-md-2 col-sm-6 col-xs-12">
            <q-input
              v-model.number="filters.entityId"
              type="number"
              label="ID da entidade"
              dense
              outlined
              clearable
              @keyup.enter="fetchTranslations"
            />
          </div>
          <div class="col-md-4 col-sm-12 col-xs-12">
            <q-input
              v-model="filters.field"
              label="Campo (ex.: nome, descricao, variacao)"
              dense
              outlined
              clearable
              @keyup.enter="fetchTranslations"
            />
          </div>
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section class="q-pa-none">
        <q-table
          :rows="rows"
          :columns="columns"
          row-key="id"
          :loading="loading"
          v-model:pagination="pagination"
          @request="onRequest"
          flat
          class="translations-table"
          :rows-per-page-options="[10, 20, 50]"
          binary-state-sort
          separator="horizontal"
          :wrap-cells="true"
        >
          <template #body-cell-entityType="props">
            <q-td :props="props" class="text-grey-9 text-weight-medium">
              {{ props.value || "—" }}
            </q-td>
          </template>

          <template #body-cell-field="props">
            <q-td :props="props" class="text-grey-8">
              {{ props.value || "—" }}
            </q-td>
          </template>

          <template #body-cell-entityId="props">
            <q-td :props="props">
              <span class="text-grey-8">#{{ props.value || "—" }}</span>
            </q-td>
          </template>

          <template #body-cell-locale="props">
            <q-td :props="props" class="text-grey-9 text-weight-medium">
              {{ props.value || "—" }}
            </q-td>
          </template>

          <template #body-cell-sourceText="props">
            <q-td :props="props" class="text-cell">
              <div class="text-body2 text-grey-9">{{ props.row.sourceText || "Sem texto original" }}</div>
            </q-td>
          </template>

          <template #body-cell-translated="props">
            <q-td :props="props" class="translation-cell">
              <template v-if="editingTranslationId === props.row.id">
                <q-input
                  v-model="editingTranslationValue"
                  type="textarea"
                  autogrow
                  dense
                  outlined
                  autofocus
                  class="translation-input edit-input"
                  :placeholder="props.row.sourceText || 'Sem texto original'"
                  @keyup.esc="cancelTranslationEdit"
                  :loading="savingId === props.row.id"
                >
                  <template #append>
                    <q-btn 
                      flat 
                      round 
                      dense 
                      icon="check" 
                      color="positive" 
                      size="sm" 
                      @click="saveTranslation(props.row)"
                      :loading="savingId === props.row.id"
                    />
                    <q-btn 
                      flat 
                      round 
                      dense 
                      icon="close" 
                      color="negative" 
                      size="sm" 
                      @click="cancelTranslationEdit"
                      :disable="savingId === props.row.id"
                    />
                  </template>
                </q-input>
              </template>
              <template v-else>
                <div class="translation-display" @click="startEditingTranslation(props.row)">
                  <span class="translation-value">
                    {{ truncatedTranslation(translationValue(props.row)) || "Sem tradução" }}
                    <q-tooltip v-if="isTranslationTruncated(translationValue(props.row))">
                      {{ translationDisplayText(props.row) }}
                    </q-tooltip>
                  </span>
                  <q-icon name="edit" size="16px" color="grey-5" class="edit-icon" />
                </div>
              </template>
            </q-td>
          </template>

          <template #body-cell-provider="props">
            <q-td :props="props" class="text-grey-8">
              {{ props.value || "—" }}
            </q-td>
          </template>

          <template #body-cell-status="props">
            <q-td :props="props">
              <q-badge 
                :color="getStatusColor(props.value)" 
                :label="props.value || '—'"
                class="status-badge"
              />
            </q-td>
          </template>

          <template #body-cell-actions="props">
            <q-td :props="props">
              <div class="row q-gutter-xs">
                <q-btn
                  flat
                  dense
                  size="sm"
                  icon="refresh"
                  color="primary"
                  @click="regenerateTranslation(props.row)"
                  :loading="savingId === props.row.id"
                  :disable="savingId === props.row.id"
                >
                  <q-tooltip>Marcar como PENDING para retraduzir</q-tooltip>
                </q-btn>
              </div>
            </q-td>
          </template>

          <template #body-cell-updatedAt="props">
            <q-td :props="props">
              <div class="text-body2 text-grey-8">{{ formatTimestamp(props.row.updatedAt || props.row.createdAt) }}</div>
            </q-td>
          </template>

          <template #no-data>
            <div class="full-width row flex-center text-grey-6 q-gutter-sm q-pa-lg">
              <q-icon name="search_off" size="24px" />
              <span class="text-body1">Nenhuma tradução encontrada com os filtros atuais</span>
            </div>
          </template>
        </q-table>
      </q-card-section>
    </q-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";

const $q = useQuasar();
const { apiRequest } = useApiRequest();

const loading = ref(false);
const savingId = ref(null);
const editingTranslationId = ref(null);
const editingTranslationValue = ref("");
const TRANSLATION_VALUE_LIMIT = 60;

const columns = [
  { name: "entityType", label: "Entidade", field: "entityType", align: "left", style: "width: 130px" },
  { name: "field", label: "Campo", field: "field", align: "left", style: "width: 140px" },
  { name: "entityId", label: "ID", field: "entityId", align: "left", style: "width: 90px" },
  { name: "locale", label: "Locale", field: "locale", align: "left", style: "width: 110px" },
  { name: "sourceText", label: "Texto Original", field: "sourceText", align: "left", style: "min-width: 220px" },
  { name: "translated", label: "Tradução", field: "translatedText", align: "left", style: "min-width: 220px" },
  { name: "provider", label: "Provider", field: "provider", align: "left", style: "width: 140px" },
  { name: "status", label: "Status", field: "status", align: "left", style: "width: 120px" },
  { name: "updatedAt", label: "Atualizado", field: "updatedAt", align: "left", style: "width: 160px" },
  { name: "actions", label: "Ações", field: "id", align: "left", style: "width: 120px" },
];

const localeOptions = [
  { label: "pt-BR", value: "pt-BR" },
  { label: "en-US", value: "en-US" },
  { label: "es-ES", value: "es-ES" },
];

const statusOptions = [
  { label: "PENDING", value: "PENDING" },
  { label: "OK", value: "OK" },
  { label: "MANUAL", value: "MANUAL" },
  { label: "FAILED", value: "FAILED" },
];

const entityOptions = [
  { label: "CATEGORY", value: "CATEGORY" },
  { label: "PRODUCT", value: "PRODUCT" },
  { label: "SKU", value: "SKU" },
  { label: "HARMONIZATION", value: "HARMONIZATION" },
];

const getStatusColor = (status) => {
  const colors = {
    PENDING: "orange",
    OK: "positive",
    MANUAL: "primary",
    FAILED: "negative",
  };
  return colors[status] || "grey";
};

const dateFormatter = new Intl.DateTimeFormat("pt-BR", {
  day: "2-digit",
  month: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
});

const formatTimestamp = (value) => {
  if (!value) return "—";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "—";
  return dateFormatter.format(parsed);
};

const filters = reactive({
  locale: null,
  status: null,
  entityType: null,
  entityId: null,
  field: "",
  search: "",
});

const rows = ref([]);
const pagination = ref({
  page: 1,
  rowsPerPage: 20,
  rowsNumber: 0,
});

const buildQuery = () => {
  const params = new URLSearchParams();
  params.append("page", pagination.value.page - 1);
  params.append("size", pagination.value.rowsPerPage);
  if (filters.locale) params.append("locale", filters.locale);
  if (filters.status) params.append("status", filters.status);
  if (filters.entityType) params.append("entityType", filters.entityType);
  if (filters.entityId) params.append("entityId", filters.entityId);
  if (filters.field) params.append("field", filters.field.trim());
  if (filters.search) params.append("search", filters.search.trim());
  return params.toString();
};

const fetchTranslations = async () => {
  loading.value = true;
  try {
    const query = buildQuery();
    const data = await apiRequest(`/api/admin/translations?${query}`);
    rows.value = (data.content || []).map((item) => ({
      ...item,
      editedText: item.translatedText,
    }));
    pagination.value = {
      ...pagination.value,
      rowsNumber: data.totalElements || rows.value.length || 0,
    };
  } catch (e) {
    $q.notify({ type: "negative", message: "Erro ao carregar traduções" });
  } finally {
    loading.value = false;
  }
};

const onRequest = async (props) => {
  const { page, rowsPerPage } = props.pagination;
  pagination.value.page = page;
  pagination.value.rowsPerPage = rowsPerPage;
  await fetchTranslations();
};

watch(
  () => [filters.locale, filters.status, filters.entityType, filters.entityId, filters.field],
  () => {
    pagination.value.page = 1;
    fetchTranslations();
  }
);

const translationValue = (row) => {
  if (!row) return "";
  return row.editedText ?? row.translatedText ?? "";
};

const translationDisplayText = (row) => {
  const value = translationValue(row);
  return value || "Sem tradução";
};

const truncatedTranslation = (val) => {
  if (!val) return "";
  const str = String(val);
  return str.length > TRANSLATION_VALUE_LIMIT ? str.slice(0, TRANSLATION_VALUE_LIMIT) + "..." : str;
};

const isTranslationTruncated = (val) => {
  if (!val) return false;
  return String(val).length > TRANSLATION_VALUE_LIMIT;
};

const startEditingTranslation = (row) => {
  editingTranslationId.value = row.id;
  editingTranslationValue.value = translationValue(row);
};

const cancelTranslationEdit = () => {
  editingTranslationId.value = null;
  editingTranslationValue.value = "";
};

const saveTranslation = async (row) => {
  savingId.value = row.id;
  try {
    await apiRequest(`/api/admin/translations/${row.id}`, "PUT", {
      translatedText: editingTranslationValue.value,
      status: "MANUAL",
    });
    $q.notify({ type: "positive", message: "Tradução salva como MANUAL" });
    editingTranslationId.value = null;
    editingTranslationValue.value = "";
    fetchTranslations();
  } catch (e) {
    $q.notify({ type: "negative", message: "Erro ao salvar tradução" });
  } finally {
    savingId.value = null;
  }
};

const regenerateTranslation = async (row) => {
  if (!row?.id) return;
  savingId.value = row.id;
  try {
    await apiRequest(`/api/admin/translations/${row.id}/regenerate`, "POST");
    $q.notify({ type: "positive", message: "Tradução marcada como PENDING" });
    fetchTranslations();
  } catch (e) {
    $q.notify({ type: "negative", message: "Erro ao marcar como PENDING" });
  } finally {
    savingId.value = null;
  }
};

onMounted(() => {
  fetchTranslations();
});
</script>

<style scoped lang="scss">
.translations-configuracoes {
  margin: 0 auto;
  padding: 0 16px;
}

.config-card {
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12), 0 1px 2px rgba(0, 0, 0, 0.24);
}

.card-header {
  background: linear-gradient(135deg, #f5f5f5 0%, #fafafa 100%);
  padding: 20px 24px;

  .header-content {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;

    .header-left {
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-width: 240px;
    }

    .header-icon {
      opacity: 0.7;
    }

    .search-container {
      flex: 1;
      max-width: 420px;

      .search-input {
        background-color: #fff;
      }
    }
  }
}

.filters-section {
  background-color: #fafafa;
  padding: 12px 24px 4px;

  .filters-grid > div {
    display: flex;
  }

  .q-field {
    width: 100%;
    background: #fff;
    border-radius: 8px;
  }
}

.translations-table {
  .q-td {
    vertical-align: top;
  }

  .text-cell {
    max-width: 520px;
    white-space: normal;
    line-height: 1.4;
  }

  .translation-cell {
    min-width: 260px;
  }

  .translation-display {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 12px;
    border-radius: 6px;
    cursor: pointer;
    transition: all 0.2s ease;
    min-height: 36px;
    background-color: #fff;

    &:hover {
      background-color: #f5f5f5;

      .edit-icon {
        opacity: 1;
      }
    }

    .translation-value {
      flex: 1;
      color: #2c3e50;
      font-weight: 500;
      white-space: normal;
    }

    .edit-icon {
      opacity: 0;
      transition: opacity 0.2s ease;
      margin-left: 8px;
    }
  }

  .translation-input {
    min-height: 90px;

    .q-field__control {
      min-height: 40px;
    }
  }

  .status-badge {
    padding: 4px 10px;
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.3px;
  }
}

.compact-select {
  :deep(.q-field__control) {
    min-height: 40px;
  }

  :deep(.q-field__native),
  :deep(.q-field__marginal) {
    min-height: 40px;
  }
}

.compact-select-menu {
  .q-item {
    min-height: 32px;
    padding: 6px 12px;
  }
}

@media (max-width: 960px) {
  .card-header .header-content {
    flex-direction: column;
    align-items: flex-start;

    .search-container {
      width: 100%;
      max-width: none;
    }
  }
}
</style>
