<template>
  <div class="form-definitions-config">
    <q-card class="config-card q-mb-lg" flat bordered>
      <q-card-section class="card-header">
        <div class="header-content">
          <div>
            <div class="text-h5 text-weight-medium text-primary">Definições de Formulários</div>
            <div class="text-body2 text-grey-7 q-mt-xs">Gerencie os templates dos formulários dinâmicos do sistema</div>
          </div>
          <div class="actions-container">
            <q-btn
              color="warning"
              icon="refresh"
              label="Resetar Modificados"
              @click="resetModified"
              :disable="!hasModifications"
              :loading="loading"
              v-if="modifiedCount > 0"
            />
            <q-btn
              color="negative"
              icon="restore"
              label="Resetar Todos"
              @click="confirmResetAll"
              :loading="loading"
              class="q-ml-sm"
            />
          </div>
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section class="q-pa-none">
        <!-- Tabela de definições -->
        <q-table
          :rows="definitions"
          :columns="columns"
          :loading="loading"
          flat
          class="definitions-table"
          :rows-per-page-options="[10, 25, 50]"
          :pagination="{ rowsPerPage: 25 }"
          row-key="entityType"
          binary-state-sort
        >
          <template v-slot:body-cell-programName="props">
            <q-td :props="props" class="name-cell">
              <div class="definition-name">
                <q-icon :name="props.row.programIcon || 'o_dynamic_form'" size="20px" color="primary" class="q-mr-sm" />
                <span class="text-weight-medium">{{ props.value }}</span>
              </div>
            </q-td>
          </template>

          <template v-slot:body-cell-entityType="props">
            <q-td :props="props">
              <q-chip :label="props.value" color="grey-3" text-color="grey-8" size="sm" class="entity-chip" />
            </q-td>
          </template>

          <template v-slot:body-cell-status="props">
            <q-td :props="props">
              <q-badge
                :color="props.value === 'ORIGINAL' ? 'positive' : 'warning'"
                :label="props.value === 'ORIGINAL' ? 'Original' : 'Modificado'"
                class="status-badge"
              />
            </q-td>
          </template>

          <template v-slot:body-cell-updatedAt="props">
            <q-td :props="props">
              <span class="text-caption text-grey-7">
                {{ props.value ? formatDate(props.value) : '-' }}
              </span>
            </q-td>
          </template>

          <template v-slot:body-cell-actions="props">
            <q-td :props="props">
              <div class="action-buttons">
                <q-btn
                  flat
                  dense
                  round
                  icon="compare_arrows"
                  color="primary"
                  size="sm"
                  @click="showComparison(props.row)"
                >
                  <q-tooltip>Comparar com original</q-tooltip>
                </q-btn>
                <q-btn
                  flat
                  dense
                  round
                  icon="refresh"
                  color="warning"
                  size="sm"
                  @click="resetOne(props.row.entityType)"
                  :disable="props.row.status === 'ORIGINAL'"
                >
                  <q-tooltip>Resetar para original</q-tooltip>
                </q-btn>
              </div>
            </q-td>
          </template>

          <template v-slot:no-data>
            <div class="full-width row flex-center text-grey-6 q-gutter-sm q-pa-lg">
              <q-icon name="search_off" size="24px" />
              <span class="text-body1">Nenhuma definição de formulário encontrada</span>
            </div>
          </template>
        </q-table>
      </q-card-section>
    </q-card>

    <!-- Dialog de comparação -->
    <q-dialog v-model="comparisonDialog" maximized>
      <q-card>
        <q-card-section class="row items-center q-pb-none">
          <div class="text-h6">Comparação: {{ selectedDefinition?.programName }}</div>
          <q-space />
          <q-btn icon="close" flat round dense v-close-popup />
        </q-card-section>

        <q-card-section v-if="comparison">
          <div class="comparison-content">
            <div class="text-subtitle1 q-mb-md">
              Status:
              <q-badge
                :color="comparison.status === 'ORIGINAL' ? 'positive' : 'warning'"
                :label="comparison.status === 'ORIGINAL' ? 'Original' : 'Modificado'"
              />
            </div>

            <div class="comparison-grid">
              <div class="comparison-item">
                <div class="text-caption text-grey-7">Nome do Programa</div>
                <div class="text-body1">
                  Original: {{ comparison.programName?.original }}
                </div>
                <div class="text-body1">
                  Atual: {{ comparison.programName?.current }}
                </div>
                <q-badge v-if="comparison.programName?.changed" color="warning" label="Modificado" />
              </div>

              <div class="comparison-item">
                <div class="text-caption text-grey-7">Ícone</div>
                <div class="text-body1">
                  Original: {{ comparison.programIcon?.original }}
                </div>
                <div class="text-body1">
                  Atual: {{ comparison.programIcon?.current }}
                </div>
                <q-badge v-if="comparison.programIcon?.changed" color="warning" label="Modificado" />
              </div>

              <div class="comparison-item">
                <div class="text-caption text-grey-7">Estrutura do Formulário</div>
                <q-badge
                  :color="comparison.hashes?.structureChanged ? 'warning' : 'positive'"
                  :label="comparison.hashes?.structureChanged ? 'Estrutura Modificada' : 'Estrutura Original'"
                />
              </div>

              <div class="comparison-item">
                <div class="text-caption text-grey-7">Última Atualização</div>
                <div class="text-body1">{{ formatDate(comparison.updatedAt) }}</div>
              </div>

              <div class="comparison-item">
                <div class="text-caption text-grey-7">Versão</div>
                <div class="text-body1">{{ comparison.version }}</div>
              </div>
            </div>
          </div>
        </q-card-section>

        <q-card-actions align="right">
          <q-btn flat label="Fechar" color="primary" v-close-popup />
          <q-btn
            label="Resetar para Original"
            color="warning"
            @click="resetOne(selectedDefinition.entityType); comparisonDialog = false"
            v-if="comparison?.status === 'MODIFIED'"
          />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";
import { format } from "date-fns";
import { ptBR } from "date-fns/locale";

const { apiRequest } = useApiRequest();
const $q = useQuasar();

// Estado
const definitions = ref([]);
const loading = ref(false);
const comparisonDialog = ref(false);
const selectedDefinition = ref(null);
const comparison = ref(null);

// Computed
const hasModifications = computed(() => {
  return definitions.value.some(d => d.status === 'MODIFIED');
});

const modifiedCount = computed(() => {
  return definitions.value.filter(d => d.status === 'MODIFIED').length;
});

// Colunas da tabela
const columns = [
  {
    name: "programName",
    label: "Nome do Formulário",
    field: "programName",
    align: "left",
    sortable: true,
    style: "width: 300px",
  },
  {
    name: "entityType",
    label: "Entity Type",
    field: "entityType",
    align: "left",
    sortable: true,
    style: "width: 200px",
  },
  {
    name: "status",
    label: "Status",
    field: "status",
    align: "center",
    sortable: true,
    style: "width: 120px",
  },
  {
    name: "version",
    label: "Versão",
    field: "version",
    align: "center",
    sortable: true,
    style: "width: 100px",
  },
  {
    name: "updatedAt",
    label: "Última Atualização",
    field: "updatedAt",
    align: "left",
    sortable: true,
    style: "width: 180px",
  },
  {
    name: "actions",
    label: "Ações",
    field: "actions",
    align: "center",
    style: "width: 120px",
  },
];

// Funções
const loadDefinitions = async () => {
  try {
    loading.value = true;
    const response = await apiRequest("/api/admin/form-definitions/status");

    if (response) {
      definitions.value = response;
    }
  } catch (error) {
    console.error("Erro ao carregar definições:", error);
    $q.notify({
      type: "negative",
      message: "Erro ao carregar definições de formulários",
      position: "top-right",
    });
  } finally {
    loading.value = false;
  }
};

const showComparison = async (definition) => {
  try {
    selectedDefinition.value = definition;
    comparisonDialog.value = true;

    const response = await apiRequest(`/api/admin/form-definitions/compare/${definition.entityType}`);
    comparison.value = response;
  } catch (error) {
    console.error("Erro ao comparar definição:", error);
    $q.notify({
      type: "negative",
      message: "Erro ao comparar definição",
      position: "top-right",
    });
  }
};

const resetOne = async (entityType) => {
  $q.dialog({
    title: "Confirmar Reset",
    message: `Deseja resetar a definição de "${entityType}" para o padrão original? Esta ação não pode ser desfeita.`,
    cancel: true,
    persistent: true,
  }).onOk(async () => {
    try {
      loading.value = true;
      const response = await apiRequest(`/api/admin/form-definitions/reset/${entityType}`, "POST");

      if (response.success) {
        $q.notify({
          type: "positive",
          message: "Definição resetada com sucesso!",
          position: "top-right",
        });
        await loadDefinitions();
      } else {
        throw new Error(response.message);
      }
    } catch (error) {
      console.error("Erro ao resetar definição:", error);
      $q.notify({
        type: "negative",
        message: `Erro ao resetar: ${error.message}`,
        position: "top-right",
      });
    } finally {
      loading.value = false;
    }
  });
};

const resetModified = async () => {
  $q.dialog({
    title: "Confirmar Reset",
    message: `Deseja resetar todas as ${modifiedCount.value} definições modificadas para os padrões originais? Esta ação não pode ser desfeita.`,
    cancel: true,
    persistent: true,
  }).onOk(async () => {
    try {
      loading.value = true;
      const response = await apiRequest("/api/admin/form-definitions/reset-modified", "POST");

      if (response.success) {
        $q.notify({
          type: "positive",
          message: `${response.totalResetados} definições resetadas com sucesso!`,
          position: "top-right",
        });
        await loadDefinitions();
      } else {
        throw new Error("Erro ao resetar definições");
      }
    } catch (error) {
      console.error("Erro ao resetar definições modificadas:", error);
      $q.notify({
        type: "negative",
        message: `Erro ao resetar: ${error.message}`,
        position: "top-right",
      });
    } finally {
      loading.value = false;
    }
  });
};

const confirmResetAll = () => {
  $q.dialog({
    title: "⚠️ Confirmar Reset Total",
    message: "ATENÇÃO: Deseja resetar TODAS as definições de formulários para os padrões originais? Esta ação afetará todos os formulários personalizados e NÃO PODE SER DESFEITA.",
    cancel: true,
    persistent: true,
    color: "negative",
  }).onOk(async () => {
    try {
      loading.value = true;
      const response = await apiRequest("/api/admin/form-definitions/reset-all", "POST");

      if (response.success) {
        $q.notify({
          type: "positive",
          message: `${response.totalResetados} definições resetadas com sucesso!`,
          position: "top-right",
        });
        await loadDefinitions();
      } else {
        throw new Error("Erro ao resetar todas as definições");
      }
    } catch (error) {
      console.error("Erro ao resetar todas as definições:", error);
      $q.notify({
        type: "negative",
        message: `Erro ao resetar: ${error.message}`,
        position: "top-right",
      });
    } finally {
      loading.value = false;
    }
  });
};

const formatDate = (date) => {
  if (!date) return '-';
  try {
    return format(new Date(date), "dd/MM/yyyy HH:mm", { locale: ptBR });
  } catch (e) {
    return date;
  }
};

onMounted(() => {
  loadDefinitions();
});
</script>

<style lang="scss" scoped>
.form-definitions-config {
  margin: 0 auto;
  padding: 0 16px;
}

.config-card {
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12), 0 1px 2px rgba(0, 0, 0, 0.24);

  .card-header {
    background: linear-gradient(135deg, #f5f5f5 0%, #fafafa 100%);

    .header-content {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 16px;

      .actions-container {
        display: flex;
        gap: 8px;
      }
    }
  }
}

.definitions-table {
  .name-cell {
    .definition-name {
      display: flex;
      align-items: center;
    }
  }

  .entity-chip {
    font-family: "Roboto Mono", monospace;
    font-size: 11px;
    border-radius: 6px;
  }

  .status-badge {
    font-size: 11px;
    padding: 4px 8px;
  }

  .action-buttons {
    display: flex;
    gap: 4px;
    justify-content: center;
  }
}

.comparison-content {
  .comparison-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: 16px;

    .comparison-item {
      padding: 16px;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      background: #fafafa;
    }
  }
}

@media (max-width: 768px) {
  .form-definitions-config {
    padding: 0 8px;
  }

  .header-content {
    flex-direction: column;
    align-items: flex-start !important;
  }
}
</style>
