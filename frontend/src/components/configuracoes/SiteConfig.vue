<template>
  <div class="site-config">
    <!-- Seção de Configurações do Site -->
          <q-card class="config-card q-mb-lg" flat bordered>
            <q-card-section class="card-header">
                        <div class="header-content">
                          <div>
                            <div class="text-h5 text-weight-medium text-grey-9">Configurações do Site</div>
                            <div class="text-body2 text-grey-7 q-mt-xs">Gerencie parâmetros do site e cardápio digital</div>
                          </div>
                          <q-icon name="o_web" size="48px" color="grey-6" class="header-icon" />                <div class="search-container">
                  <q-input v-model="searchFilter" outlined dense placeholder="Buscar configuração..." class="search-input">
                    <template v-slot:prepend>
                      <q-icon name="search" color="grey-6" />
                    </template>
                    <template v-slot:append v-if="searchFilter">
                      <q-icon name="clear" color="grey-6" class="cursor-pointer" @click="searchFilter = ''" />
                    </template>
                  </q-input>
                </div>
              </div>
            </q-card-section>
    
          <q-separator />
    
          <q-card-section class="q-pa-none">        <!-- Tabela de configurações -->
        <q-table
          :rows="filteredConfigs"
          :columns="columnsConfigs"
          :loading="loading"
          flat
          class="configs-table"
          :rows-per-page-options="[10, 25, 50, 100]"
          :pagination="{ rowsPerPage: 10 }"
          row-key="id"
          binary-state-sort
        >
          <template v-slot:body-cell-nome="props">
            <q-td :props="props" class="config-name-cell">
              <div class="config-name">
                <q-icon name="language" size="18px" color="grey-7" class="q-mr-sm" />
                <span class="text-weight-medium">{{ props.value }}</span>
              </div>
            </q-td>
          </template>

          <template v-slot:body-cell-chave="props">
            <q-td :props="props">
              <q-chip :label="props.value" color="grey-3" text-color="grey-8" size="sm" class="config-key-chip" />
            </q-td>
          </template>

          <template v-slot:body-cell-valor="props">
            <q-td :props="props" class="config-value-cell">
              <!-- Tratamento especial para booleanos (checkbox) -->
              <template v-if="isBooleanValue(props.row.valor)">
                 <q-toggle
                  :model-value="props.row.valor === 'true'"
                  @update:model-value="(val) => updateBooleanConfig(props.row, val)"
                  color="primary"
                  :label="props.row.valor === 'true' ? 'Ativo' : 'Inativo'"
                  left-label
                />
              </template>
              
              <!-- Select dedicado para site_service_mode -->
              <template v-else-if="isServiceModeConfig(props.row)">
                <q-select
                  outlined
                  dense
                  :options="[
                    { label: 'Entregar na mesa (garçom)', value: 'waiter_delivery' },
                    { label: 'Retirar no balcão (cliente)', value: 'customer_pickup' }
                  ]"
                  :model-value="props.row.valor || 'waiter_delivery'"
                  @update:model-value="(val) => updateServiceMode(props.row, val)"
                  options-dense
                  emit-value
                  map-options
                  class="full-width"
                />
              </template>
              
              <!-- Edição padrão para outros tipos -->
              <template v-else-if="editingConfigId === props.row.id">
                <q-input
                  v-model="editingValue"
                  outlined
                  dense
                  autofocus
                  class="edit-input"
                  @keyup.enter="saveEdit(props.row)"
                  @blur="saveEdit(props.row)"
                  @keyup.esc="cancelEdit"
                >
                  <template v-slot:append>
                    <q-btn flat round dense icon="check" color="positive" size="sm" @click="saveEdit(props.row)" />
                    <q-btn flat round dense icon="close" color="negative" size="sm" @click="cancelEdit" />
                  </template>
                </q-input>
              </template>
              <template v-else>
                <div class="config-value" @click="startEditing(props.row)">
                  <span class="value-text">{{ props.value || "Não definido" }}</span>
                  <q-icon name="edit" size="16px" color="grey-5" class="edit-icon" />
                </div>
              </template>
            </q-td>
          </template>

          <template v-slot:body-cell-descricao="props">
            <q-td :props="props" class="description-cell">
              <div class="description-text">
                {{ props.value }}
              </div>
            </q-td>
          </template>

          <template v-slot:no-data>
            <div class="full-width row flex-center text-grey-6 q-gutter-sm q-pa-lg">
              <q-icon name="search_off" size="24px" />
              <span class="text-body1">
                {{ searchFilter ? "Nenhuma configuração encontrada" : "Nenhuma configuração de site disponível" }}
              </span>
            </div>
          </template>
        </q-table>
      </q-card-section>
    </q-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";

const { apiRequest } = useApiRequest();
const $q = useQuasar();

// Estado
const siteConfigs = ref([]);
const searchFilter = ref("");
const editingConfigId = ref(null);
const editingValue = ref("");
const loading = ref(false);

// Colunas da tabela
const columnsConfigs = [
  {
    name: "nome",
    label: "Nome",
    field: "nome",
    align: "left",
    sortable: true,
    style: "width: 250px",
  },
  {
    name: "chave",
    label: "Chave",
    field: "chave",
    align: "left",
    sortable: true,
    style: "width: 200px",
  },
  {
    name: "valor",
    label: "Valor",
    field: "valor",
    align: "left",
    sortable: true,
    style: "min-width: 200px",
  },
  {
    name: "descricao",
    label: "Descrição",
    field: "descricao",
    align: "left",
    sortable: true,
  },
];

// Configuração auxiliar para ordenação alfabeticamente
const sortConfigsByName = (configs) =>
  [...configs].sort((a, b) =>
    (a.nome || "").localeCompare(b.nome || "", "pt-BR", { sensitivity: "base" })
  );

// Configurações filtradas
const filteredConfigs = computed(() => {
  const filter = searchFilter.value.toLowerCase();
  const listToFilter = filter
    ? siteConfigs.value.filter(
        (config) =>
          config.nome.toLowerCase().includes(filter) ||
          config.chave.toLowerCase().includes(filter) ||
          config.valor.toLowerCase().includes(filter) ||
          config.descricao.toLowerCase().includes(filter)
      )
    : siteConfigs.value;

  return sortConfigsByName(listToFilter);
});

// Utilitários
const isBooleanValue = (val) => {
  return val === 'true' || val === 'false';
};

const isServiceModeConfig = (row) => row.chave === "site_service_mode";

const updateServiceMode = async (row, newVal) => {
  const updatedConfig = {
    ...row,
    valor: newVal,
  };
  await updateConfig(updatedConfig);
};

const updateBooleanConfig = async (row, newVal) => {
  const updatedConfig = {
    ...row,
    valor: String(newVal),
  };
  await updateConfig(updatedConfig);
};

// Funções de edição
const startEditing = (row) => {
  if (isBooleanValue(row.valor)) return; // Booleanos usam toggle direto
  if (isServiceModeConfig(row)) return; // select dedicado
  editingConfigId.value = row.id;
  editingValue.value = row.valor;
};

const saveEdit = async (row) => {
  if (editingConfigId.value !== null && editingValue.value !== row.valor) {
    const updatedConfig = {
      ...row,
      valor: editingValue.value,
    };
    await updateConfig(updatedConfig);
  }
  editingConfigId.value = null;
};

const cancelEdit = () => {
  editingConfigId.value = null;
  editingValue.value = "";
};

// API Functions
const updateConfig = async (config) => {
  try {
    const apiURL = `/api/configs/${config.id}`;
    await apiRequest(apiURL, "PUT", config);
    $q.notify({
      type: "positive",
      message: "Configuração atualizada com sucesso!",
      position: "top-right",
      timeout: 1000,
    });
    await loadSiteConfigs();
  } catch (error) {
    $q.notify({
      type: "negative",
      message: `Erro ao atualizar: ${error.message}`,
      position: "top-right",
    });
  }
};

const loadSiteConfigs = async () => {
  try {
    loading.value = true;
    const apiURL = `/api/configs`;
    const response = await apiRequest(apiURL);

    if (response) {
      // Filtra apenas configurações que começam com "site_"
      siteConfigs.value = response.filter((config) => 
        config.chave.startsWith("site_")
      );
    }
  } catch (error) {
    console.error("Erro ao carregar configurações do site:", error);
    $q.notify({
      type: "negative",
      message: "Erro ao carregar configurações",
      position: "top-right",
    });
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadSiteConfigs();
});
</script>

<style lang="scss" scoped>
.site-config {
  margin: 0 auto;
  padding: 0 16px;
}

// Cards principais
.config-card {
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12), 0 1px 2px rgba(0, 0, 0, 0.24);

    .card-header {
      background: linear-gradient(135deg, #f5f5f5 0%, #fafafa 100%); 
  
      .header-content {
        display: flex;
        justify-content: space-between;
        align-items: center;
  
        .header-icon {
          opacity: 0.7;
        }
  
      .search-container {
        flex: 1;
        max-width: 400px;
        margin-left: 16px;

        .search-input {
          background-color: white;
        }
      }
    }
  }
}

// Tabela de configurações
.configs-table {
  .config-name-cell {
    .config-name {
      display: flex;
      align-items: center;
    }
  }

  .config-key-chip {
    font-family: "Roboto Mono", monospace;
    font-size: 11px;
    border-radius: 6px;
  }

  .config-value-cell {
    .config-value {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 8px 12px;
      border-radius: 6px;
      cursor: pointer;
      transition: all 0.2s ease;
      min-height: 36px;

      &:hover {
        background-color: #f5f5f5;

        .edit-icon {
          opacity: 1;
        }
      }

      .value-text {
        flex: 1;
        color: #2c3e50;
        font-weight: 500;
      }

      .edit-icon {
        opacity: 0;
        transition: opacity 0.2s ease;
        margin-left: 8px;
      }
    }

    .edit-input {
      .q-field__control {
        min-height: 40px;
      }
    }
  }

  .description-cell {
    .description-text {
      color: #5f6368;
      line-height: 1.4;
    }
  }
}
</style>
