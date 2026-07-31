<template>
  <q-card class="certificado-upload-card" flat bordered>
    <q-card-section class="card-header">
      <div class="header-content">
        <div class="icon-container">
          <q-icon name="lock" size="32px" color="primary" />
        </div>
        <div class="content">
          <div class="text-h6 text-weight-medium">Upload de Certificado Digital</div>
          <div class="text-body2 text-grey-7 q-mt-xs">
            Faça upload do seu certificado digital (.pfx) para emissão de NF-e/NFC-e
          </div>
        </div>
      </div>
    </q-card-section>

    <q-separator />

    <q-card-section>
      <div class="row q-col-gutter-lg">
        <div class="col-12">
          <q-file
            v-model="selectedFile"
            outlined
            bottom-slots
            :label="currentCertInfo.caminho_atual ? 'Certificado atual selecionado' : 'Selecione o arquivo .pfx'"
            accept=".pfx"
            @update:model-value="onFileSelected"
          >
            <template v-slot:prepend>
              <q-icon name="attachment" />
            </template>
            <template v-slot:append>
              <q-icon
                v-if="selectedFile"
                name="close"
                class="cursor-pointer"
                @click="selectedFile = null"
              />
            </template>
            <template v-slot:hint>
              Formato aceito: .pfx | Tamanho máximo: 10MB
            </template>
          </q-file>
        </div>

        <div class="col-12" v-if="currentCertInfo.caminho_atual">
          <q-banner inline-actions rounded class="bg-info text-white">
            <template v-slot:avatar>
              <q-icon name="info" color="white" />
            </template>
            <strong>Certificado atual:</strong> {{ getFileName(currentCertInfo.caminho_atual) }}
            <template v-slot:action>
              <q-btn flat label="Detalhes" @click="showDetails = !showDetails" />
            </template>
          </q-banner>
          
          <q-expansion-item
            v-model="showDetails"
            header-style="background-color: #1976d2; color: white;"
            class="q-mt-sm"
          >
            <q-card>
              <q-card-section>
                <div class="row">
                  <div class="col-12">
                    <div><strong>Caminho:</strong> {{ currentCertInfo.caminho_atual }}</div>
                  </div>
                  <div class="col-12" v-if="currentCertInfo.tamanho">
                    <div><strong>Tamanho:</strong> {{ formatFileSize(currentCertInfo.tamanho) }}</div>
                  </div>
                  <div class="col-12" v-if="currentCertInfo.ultima_modificacao">
                    <div><strong>Última modificação:</strong> {{ formatDate(currentCertInfo.ultima_modificacao) }}</div>
                  </div>
                  <div class="col-12">
                    <div><strong>Status:</strong> 
                      <q-chip 
                        :color="currentCertInfo.existe_arquivo ? 'positive' : 'negative'" 
                        text-color="white"
                        size="sm"
                      >
                        {{ currentCertInfo.existe_arquivo ? 'Arquivo encontrado' : 'Arquivo não encontrado' }}
                      </q-chip>
                    </div>
                  </div>
                </div>
              </q-card-section>
            </q-card>
          </q-expansion-item>
        </div>

        <div class="col-12">
          <q-btn
            :disable="!selectedFile || uploading"
            :loading="uploading"
            unelevated
            color="primary"
            icon="cloud_upload"
            label="Enviar Certificado"
            class="full-width q-mb-md"
            @click="uploadCertificado"
          >
            <template v-slot:loading>
              <q-spinner-facebook />
              Enviando...
            </template>
          </q-btn>

          <q-btn
            :disable="(!currentCertInfo.caminho_atual && !selectedFile) || validating"
            :loading="validating"
            outline
            color="info"
            icon="rule"
            label="Validar Certificado"
            class="full-width"
            @click="validarCertificado"
          >
            <template v-slot:loading>
              <q-spinner-gears />
              Validando...
            </template>
          </q-btn>
        </div>
      </div>
    </q-card-section>

    <q-inner-loading :showing="loading">
      <q-spinner-gears size="50px" color="primary" />
    </q-inner-loading>
  </q-card>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";

const { apiRequest } = useApiRequest();
const $q = useQuasar();

// Estados
const selectedFile = ref(null);
const uploading = ref(false);
const validating = ref(false);
const loading = ref(false);
const currentCertInfo = ref({
  caminho_atual: "",
  existe_arquivo: false,
  tamanho: 0,
  ultima_modificacao: 0
});
const showDetails = ref(false);

// Funções
const onFileSelected = (file) => {
  if (file) {
    // Verifica o tipo de arquivo
    if (!file.name.toLowerCase().endsWith('.pfx')) {
      $q.notify({
        type: "negative",
        message: "Formato de arquivo inválido. Apenas arquivos .pfx são aceitos.",
        position: "top-right",
      });
      selectedFile.value = null;
      return;
    }

    // Verifica o tamanho (máximo 10MB)
    if (file.size > 10 * 1024 * 1024) {
      $q.notify({
        type: "negative",
        message: "Arquivo muito grande. O tamanho máximo é 10MB.",
        position: "top-right",
      });
      selectedFile.value = null;
      return;
    }
  }
};

const uploadCertificado = async () => {
  if (!selectedFile.value) {
    $q.notify({
      type: "warning",
      message: "Por favor, selecione um arquivo .pfx primeiro.",
      position: "top-right",
    });
    return;
  }

  try {
    uploading.value = true;

    // Criar FormData para o upload
    const formData = new FormData();
    formData.append('certificado', selectedFile.value);

    // Fazer a requisição POST para o endpoint de upload usando apiRequest
    const result = await apiRequest('/api/certificado/upload', 'POST', formData);

    if (result.success) {
      $q.notify({
        type: "positive",
        message: result.message,
        position: "top-right",
        timeout: 5000,
      });

      // Limpar o arquivo selecionado
      selectedFile.value = null;
      
      // Recarregar informações do certificado
      loadCertInfo();
    } else {
      $q.notify({
        type: "negative",
        message: result.message,
        position: "top-right",
      });
    }
  } catch (error) {
    console.error('Erro no upload:', error);
    $q.notify({
      type: "negative",
      message: `Erro ao enviar certificado: ${error.message}`,
      position: "top-right",
    });
  } finally {
    uploading.value = false;
  }
};

const loadCertInfo = async () => {
  try {
    loading.value = true;
    const response = await apiRequest('/api/certificado/info');

    if (response) {
      currentCertInfo.value = response;
    }
  } catch (error) {
    console.error('Erro ao carregar info do certificado:', error);
    $q.notify({
      type: "negative",
      message: "Erro ao carregar informações do certificado",
      position: "top-right",
    });
  } finally {
    loading.value = false;
  }
};

const getFileName = (path) => {
  if (!path) return 'Nenhum certificado';
  return path.split('/').pop();
};

const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

const formatDate = (timestamp) => {
  return new Date(timestamp).toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
};

const validarCertificado = async () => {
  try {
    validating.value = true;

    let result;

    if (selectedFile.value) {
      // Se um arquivo está selecionado, validamos o arquivo selecionado
      const formData = new FormData();
      formData.append('certificado', selectedFile.value);

      result = await apiRequest('/api/certificado/validar', 'POST', formData);
    } else {
      // Caso contrário, validamos o certificado atualmente configurado
      result = await apiRequest('/api/certificado/validar', 'POST');
    }

    if (result.valid) {
      $q.notify({
        type: "positive",
        message: result.message || "Certificado válido!",
        position: "top-right",
        timeout: 5000,
      });

      // Atualizar informações do certificado
      loadCertInfo();
    } else {
      $q.notify({
        type: "negative",
        message: result.message || result.error || "Certificado inválido",
        position: "top-right",
        timeout: 5000,
      });
    }

    // Mostrar informações detalhadas do certificado se disponíveis
    if (result.info) {
      console.log('Informações do certificado:', result.info);

      // Mostrar popup com informações detalhadas
      $q.dialog({
        title: 'Informações do Certificado',
        message: `
          <div class="text-left">
            <p><strong>Assunto:</strong> ${result.info.subject || 'N/A'}</p>
            <p><strong>Emitido por:</strong> ${result.info.issuer || 'N/A'}</p>
            <p><strong>Válido de:</strong> ${result.info.validFrom ? new Date(result.info.validFrom).toLocaleDateString('pt-BR') : 'N/A'}</p>
            <p><strong>Válido até:</strong> ${result.info.validUntil ? new Date(result.info.validUntil).toLocaleDateString('pt-BR') : 'N/A'}</p>
            <p><strong>Número de série:</strong> ${result.info.serialNumber || 'N/A'}</p>
            <p><strong>Status:</strong>
              <span class="${result.valid ? 'text-green' : 'text-red'}">
                ${result.valid ? 'VÁLIDO' : 'INVÁLIDO'}
              </span>
            </p>
          </div>
        `,
        html: true,
        ok: 'Fechar'
      }).onOk(() => {
        console.log('Diálogo de informações fechado');
      });
    }
  } catch (error) {
    console.error('Erro na validação do certificado:', error);
    $q.notify({
      type: "negative",
      message: `Erro ao validar certificado: ${error.message}`,
      position: "top-right",
    });
  } finally {
    validating.value = false;
  }
};

onMounted(() => {
  loadCertInfo();
});
</script>

<style lang="scss" scoped>
.certificado-upload-card {
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12), 0 1px 2px rgba(0, 0, 0, 0.24);

  .card-header {
    background: linear-gradient(135deg, #e3f2fd 0%, #bbdefb 100%);

    .header-content {
      display: flex;
      align-items: center;
      gap: 16px;

      .icon-container {
        padding: 12px;
        border-radius: 8px;
        background-color: rgba(25, 118, 210, 0.1);
      }

      .content {
        flex: 1;
      }
    }
  }

  .q-card__section {
    padding: 16px;
  }
}

@media (max-width: 768px) {
  .certificado-upload-card {
    .header-content {
      flex-direction: column;
      text-align: center;
      align-items: flex-start;
      
      .icon-container {
        align-self: center;
      }
    }
  }
}
</style>