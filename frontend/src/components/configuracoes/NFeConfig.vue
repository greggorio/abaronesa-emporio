<template>
  <div class="nfe-configurations">
    <!-- Seção de Configurações -->
    <q-card class="config-card q-mb-lg" flat bordered>
      <q-card-section class="card-header">
        <div class="header-content">
          <div>
            <div class="text-h5 text-weight-medium text-primary">Configurações de Nota Fiscal Eletrônica</div>
            <div class="text-body2 text-grey-7 q-mt-xs">Configure os parâmetros para emissão de NF-e</div>
          </div>
          <div class="search-container">
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

      <q-card-section class="q-pa-none">
        <!-- Tabela de configurações -->
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
                <q-icon name="settings_applications" size="18px" color="primary" class="q-mr-sm" />
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
              <!-- Tratamento especial para booleanos (toggle) -->
              <template v-if="isBooleanValue(props.row.valor)">
                 <q-toggle
                  :model-value="props.row.valor === 'true'"
                  @update:model-value="(val) => updateBooleanConfig(props.row, val)"
                  color="primary"
                  :label="props.row.valor === 'true' ? 'Ativo' : 'Inativo'"
                  left-label
                />
              </template>

              <!-- Campo de caminho do certificado - somente leitura -->
              <template v-else-if="props.row.chave === 'nfe_certificado_path'">
                <div class="config-value readonly">
                  <span class="value-text">{{ props.value ? getFileName(props.value) : "Nenhum certificado configurado" }}</span>
                  <q-icon name="lock" size="16px" color="blue-5" class="info-icon" />
                  <q-tooltip>
                    Caminho do certificado gerenciado automaticamente.
                    Use a seção "Certificado Digital" para fazer upload.
                  </q-tooltip>
                </div>
              </template>

              <!-- Logo DANFE em Base64 -->
              <template v-else-if="props.row.chave === 'nfe_logo_base64'">
                <div class="logo-config">
                  <img
                    v-if="logoPreview(props.row.valor)"
                    :src="logoPreview(props.row.valor)"
                    class="logo-preview"
                    alt="Logo DANFE"
                  />
                  <div class="logo-actions">
                    <q-file
                      v-model="logoFile"
                      dense
                      outlined
                      clearable
                      accept="image/*"
                      label="Selecionar logo"
                      @update:model-value="(file) => handleLogoFile(props.row, file)"
                    />
                    <q-btn
                      v-if="props.row.valor"
                      flat
                      dense
                      color="negative"
                      icon="delete"
                      class="q-ml-sm"
                      @click="clearLogo(props.row)"
                    >
                      <q-tooltip>Remover logo</q-tooltip>
                    </q-btn>
                  </div>
                </div>
              </template>

              <!-- Campo de senha do certificado - tratamento especial -->
              <template v-else-if="props.row.chave === 'nfe_certificado_senha' && editingConfigId === props.row.id">
                <q-input
                  v-model="editingValue"
                  outlined
                  dense
                  autofocus
                  :type="mostrarSenha ? 'text' : 'password'"
                  class="edit-input"
                  @keyup.enter="saveEdit(props.row)"
                  @blur="saveEdit(props.row)"
                  @keyup.esc="cancelEdit"
                >
                  <template v-slot:append>
                    <q-icon
                      :name="mostrarSenha ? 'visibility' : 'visibility_off'"
                      class="cursor-pointer"
                      @click="mostrarSenha = !mostrarSenha"
                    />
                    <q-btn flat round dense icon="check" color="positive" size="sm" @click="saveEdit(props.row)" />
                    <q-btn flat round dense icon="close" color="negative" size="sm" @click="cancelEdit" />
                  </template>
                </q-input>
              </template>

              <!-- Campo de senha do certificado - visualização -->
              <template v-else-if="props.row.chave === 'nfe_certificado_senha'">
                <div class="config-value" @click="startEditing(props.row)">
                  <span class="value-text">{{ props.value ? '••••••••' : "Não definido" }}</span>
                  <q-icon name="edit" size="16px" color="grey-5" class="edit-icon" />
                  <q-tooltip>
                    Clique para editar a senha do certificado
                  </q-tooltip>
                </div>
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
                {{ searchFilter ? "Nenhuma configuração encontrada para o termo pesquisado" : "Nenhuma configuração disponível" }}
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
const nfeConfigs = ref([]);
const searchFilter = ref("");
const editingConfigId = ref(null);
const editingValue = ref("");
const loading = ref(false);
const testingConnection = ref(false);
const validatingCertificate = ref(false);
const mostrarSenha = ref(false);
const logoFile = ref(null);
const MAX_LOGO_SIZE_KB = 300;
const LOGO_WIDTH = 90;
const LOGO_HEIGHT = 60;

// Colunas da tabela
const columnsConfigs = [
  {
    name: "nome",
    label: "Nome da Configuração",
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

const sortConfigsByName = (configs) =>
  [...configs].sort((a, b) =>
    (a.nome || "").localeCompare(b.nome || "", "pt-BR", { sensitivity: "base" })
  );

// Configurações filtradas
const filteredConfigs = computed(() => {
  const filter = searchFilter.value.toLowerCase();
  const listToFilter = filter
    ? nfeConfigs.value.filter(
        (config) =>
          config.nome.toLowerCase().includes(filter) ||
          config.chave.toLowerCase().includes(filter) ||
          config.valor.toLowerCase().includes(filter) ||
          config.descricao.toLowerCase().includes(filter)
      )
    : nfeConfigs.value;

  return sortConfigsByName(listToFilter);
});

// Utilitários
const isBooleanValue = (val) => {
  return val === 'true' || val === 'false';
};

const getFileName = (path) => {
  if (!path) return 'Nenhum arquivo';
  return path.split('/').pop();
};

const logoPreview = (value) => {
  if (!value) return "";
  const trimmed = value.trim();
  if (!trimmed) return "";
  if (trimmed.startsWith("data:")) return trimmed;
  return `data:image/png;base64,${trimmed.replace(/\s+/g, "")}`;
};

const resizeImageToDataUrl = (file) =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        const canvas = document.createElement("canvas");
        canvas.width = LOGO_WIDTH;
        canvas.height = LOGO_HEIGHT;
        const ctx = canvas.getContext("2d");
        if (!ctx) {
          reject(new Error("Falha ao preparar o canvas"));
          return;
        }

        ctx.clearRect(0, 0, LOGO_WIDTH, LOGO_HEIGHT);

        const ratio = Math.min(LOGO_WIDTH / img.width, LOGO_HEIGHT / img.height);
        const drawWidth = Math.round(img.width * ratio);
        const drawHeight = Math.round(img.height * ratio);
        const dx = Math.round((LOGO_WIDTH - drawWidth) / 2);
        const dy = Math.round((LOGO_HEIGHT - drawHeight) / 2);

        ctx.drawImage(img, dx, dy, drawWidth, drawHeight);
        resolve(canvas.toDataURL("image/png"));
      };
      img.onerror = () => reject(new Error("Imagem inválida"));
      img.src = reader.result;
    };
    reader.onerror = () => reject(reader.error || new Error("Falha ao ler arquivo"));
    reader.readAsDataURL(file);
  });

const handleLogoFile = async (row, file) => {
  if (!file) return;
  if (file.size > MAX_LOGO_SIZE_KB * 1024) {
    $q.notify({
      type: "negative",
      message: `Logo muito grande. Máximo ${MAX_LOGO_SIZE_KB}KB.`,
      position: "top-right",
    });
    logoFile.value = null;
    return;
  }

  try {
    const dataUrl = await resizeImageToDataUrl(file);
    await updateConfig({ ...row, valor: dataUrl });
    $q.notify({
      type: "positive",
      message: "Logo atualizado com sucesso! (90x60)",
      position: "top-right",
      timeout: 3000,
    });
  } catch (error) {
    $q.notify({
      type: "negative",
      message: `Erro ao carregar logo: ${error.message}`,
      position: "top-right",
    });
  } finally {
    logoFile.value = null;
  }
};

const clearLogo = async (row) => {
  try {
    await updateConfig({ ...row, valor: "" });
    $q.notify({
      type: "positive",
      message: "Logo removido com sucesso!",
      position: "top-right",
      timeout: 3000,
    });
  } catch (error) {
    $q.notify({
      type: "negative",
      message: `Erro ao remover logo: ${error.message}`,
      position: "top-right",
    });
  }
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
  if (row.chave === "nfe_logo_base64") return;
  editingConfigId.value = row.id;
  editingValue.value = row.valor;
};

const saveEdit = async (row) => {
  if (editingConfigId.value !== null && editingValue.value !== row.valor) {
    const updatedConfig = {
      ...row,
      valor: editingValue.value,
    };

    try {
      await updateConfig(updatedConfig);
      $q.notify({
        type: "positive",
        message: "Configuração atualizada com sucesso!",
        position: "top-right",
        timeout: 3000,
      });
    } catch (error) {
      $q.notify({
        type: "negative",
        message: `Erro ao atualizar configuração: ${error.message}`,
        position: "top-right",
      });
    }
  }

  editingConfigId.value = null;
};

const cancelEdit = () => {
  editingConfigId.value = null;
  editingValue.value = "";
};

// API Functions
const updateConfig = async (config) => {
  const apiURL = `/api/configs/${config.id}`;
  await apiRequest(apiURL, "PUT", config);
  await loadNfeConfigs();
};

const loadNfeConfigs = async () => {
  try {
    loading.value = true;
    const apiURL = `/api/configs`;
    const response = await apiRequest(apiURL);

    if (response) {
      nfeConfigs.value = response.filter((config) => config.chave.startsWith("nfe_"));
    }
  } catch (error) {
    console.error("Erro ao carregar configurações de NFe:", error);
    $q.notify({
      type: "negative",
      message: "Erro ao carregar configurações de NFe",
      position: "top-right",
    });
  } finally {
    loading.value = false;
  }
};

const testarConexao = async () => {
  try {
    testingConnection.value = true;

    const apiURL = `/api/nfe/teste-conexao`;
    const response = await apiRequest(apiURL);

    if (response && response.success) {
      $q.notify({
        type: "positive",
        message: "Conexão com o WebService da SEFAZ estabelecida com sucesso!",
        position: "top-right",
        timeout: 5000,
      });
    } else {
      $q.notify({
        type: "negative",
        message: `Falha na conexão: ${response?.message || "Erro desconhecido"}`,
        position: "top-right",
      });
    }
  } catch (error) {
    $q.notify({
      type: "negative",
      message: `Erro ao testar conexão: ${error.message}`,
      position: "top-right",
    });
  } finally {
    testingConnection.value = false;
  }
};

const validarCertificado = async () => {
  try {
    validatingCertificate.value = true;

    const apiURL = `/api/nfe/valida-certificado`;
    const response = await apiRequest(apiURL);

    if (response && response.valid) {
      $q.notify({
        type: "positive",
        message: `Certificado válido! Validade: ${response.validUntil}`,
        position: "top-right",
        timeout: 5000,
      });
    } else {
      $q.notify({
        type: "negative",
        message: `Certificado inválido: ${response?.error || "Erro não especificado"}`,
        position: "top-right",
      });
    }
  } catch (error) {
    $q.notify({
      type: "negative",
      message: `Erro ao validar certificado: ${error.message}`,
      position: "top-right",
    });
  } finally {
    validatingCertificate.value = false;
  }
};

onMounted(() => {
  loadNfeConfigs();
});
</script>

<style lang="scss" scoped>
.nfe-configurations {
  margin: 0 auto;
  padding: 0 16px;
}

// Cards principais
.config-card,
.operations-card {
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
    }
  }
}

// Barra de ferramentas
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background-color: #fafafa;
  border-bottom: 1px solid #e0e0e0;

  .search-container {
    flex: 1;
    max-width: 400px;

    .search-input {
      background-color: white;
    }
  }

  .toolbar-actions {
    margin-left: 16px;
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

    .logo-config {
      display: flex;
      align-items: center;
      gap: 12px;
      min-height: 56px;
    }

    .logo-preview {
      width: 80px;
      height: 40px;
      object-fit: contain;
      border: 1px solid #e0e0e0;
      border-radius: 6px;
      background: #fafafa;
      padding: 4px;
    }

    .logo-actions {
      display: flex;
      align-items: center;
      gap: 8px;
      flex: 1;
    }
  }

  .description-cell {
    .description-text {
      color: #5f6368;
      line-height: 1.4;
    }
  }
}

// Cards de operações
.operation-card {
  border-radius: 8px;
  transition: all 0.2s ease;
  border: 1px solid #e0e0e0;

  &:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
    transform: translateY(-2px);
  }

  .operation-header {
    display: flex;
    align-items: flex-start;
    gap: 16px;

    .operation-icon-container {
      padding: 12px;
      border-radius: 8px;
      background-color: #f8f9fa;
    }

    .operation-content {
      flex: 1;
    }
  }
}

// Responsividade
@media (max-width: 768px) {
  .nfe-configurations {
    padding: 0 8px;
  }

  .toolbar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;

    .search-container {
      max-width: none;
    }
  }

  .header-content {
    flex-direction: column;
    text-align: center;
    gap: 16px;

    .header-icon {
      order: -1;
    }
  }

  .operation-header {
    flex-direction: column;
    text-align: center;

    .operation-icon-container {
      align-self: center;
    }
  }
}

// Estados de loading
.q-table--loading {
  .q-table__middle {
    opacity: 0.6;
  }
}

// Animações
.config-value,
.operation-card {
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

// Cores personalizadas baseadas na paleta
</style>
