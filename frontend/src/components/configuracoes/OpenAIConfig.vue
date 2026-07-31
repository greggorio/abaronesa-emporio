<template>
  <div>
    <!-- Status da Configuração - Compacto -->
    <q-banner
      :class="statusConfig.cor"
      rounded
      dense
      class="q-mb-md"
    >
      <template v-slot:avatar>
        <q-icon :name="statusConfig.icone" size="sm" />
      </template>
      <span class="text-caption">{{ statusConfig.mensagem }}</span>
      <template v-slot:action v-if="config.habilitado && !testando">
        <q-btn
          flat
          dense
          size="sm"
          color="white"
          label="Testar Conexão"
          @click="testarConexao"
          :loading="testando"
        />
      </template>
    </q-banner>

    <!-- Configurações Principais -->
    <q-card class="q-mb-md">
      <q-card-section>
        <div class="text-h6 q-mb-md">Configurações</div>

        <div class="row q-col-gutter-md">
          <!-- Habilitar/Desabilitar -->
          <div class="col-12">
            <q-toggle
              v-model="config.habilitado"
              label="Habilitar integração OpenAI"
              :disable="loading"
              color="primary"
            />
            <div class="text-caption text-grey">Ative para usar funcionalidades de IA no sistema</div>
          </div>

          <!-- API Key -->
          <div class="col-md-12 col-sm-12">
            <q-input
              v-model="config.apiKey"
              label="API Key *"
              outlined
              dense
              :type="mostrarApiKey ? 'text' : 'password'"
              :disable="loading || !config.habilitado"
            >
              <template v-slot:append>
                <q-btn
                  flat
                  dense
                  round
                  icon="help_outline"
                  size="sm"
                  color="grey-6"
                  href="https://platform.openai.com/api-keys"
                  target="_blank"
                >
                  <q-tooltip>Obter API Key no site da OpenAI</q-tooltip>
                </q-btn>
                <q-icon
                  :name="mostrarApiKey ? 'visibility' : 'visibility_off'"
                  class="cursor-pointer"
                  @click="mostrarApiKey = !mostrarApiKey"
                />
              </template>
            </q-input>
          </div>

          <!-- Modelo -->
          <div class="col-md-6 col-sm-12">
            <q-select
              v-model="config.model"
              :options="modelosDisponiveis"
              label="Modelo *"
              outlined
              dense
              :disable="loading || !config.habilitado"
              hint="Modelo de IA a ser utilizado"
            />
          </div>

          <!-- Max Tokens -->
          <div class="col-md-3 col-sm-12">
            <q-input
              v-model.number="config.maxTokens"
              label="Max Tokens *"
              outlined
              dense
              type="number"
              min="100"
              max="4000"
              :disable="loading || !config.habilitado"
              hint="Máximo de tokens por resposta"
            />
          </div>

          <!-- Timeout -->
          <div class="col-md-3 col-sm-12">
            <q-input
              v-model.number="config.timeout"
              label="Timeout (segundos) *"
              outlined
              dense
              type="number"
              min="10"
              max="120"
              :disable="loading || !config.habilitado"
              hint="Tempo máximo de espera"
            />
          </div>
        </div>
      </q-card-section>
    </q-card>

    <!-- Card de Teste -->
    <q-card class="q-mb-md" v-if="config.habilitado">
      <q-card-section>
        <div class="text-h6 q-mb-md">Testar Integração</div>
        <div class="text-caption text-grey q-mb-md">Faça uma pergunta para testar a comunicação com a OpenAI</div>

        <q-input
          v-model="testPrompt"
          label="Digite sua pergunta"
          outlined
          dense
          type="textarea"
          rows="3"
          :disable="testando"
          hint="Exemplo: Qual é a capital da França?"
          @keyup.ctrl.enter="testarPrompt"
        >
          <template v-slot:append>
            <q-btn
              round
              dense
              flat
              icon="send"
              color="primary"
              @click="testarPrompt"
              :loading="testando"
              :disable="!testPrompt || testando"
            >
              <q-tooltip>Enviar pergunta (Ctrl+Enter)</q-tooltip>
            </q-btn>
          </template>
        </q-input>

        <!-- Resposta do teste -->
        <transition name="fade">
          <q-card v-if="testResponse" flat bordered class="q-mt-md bg-grey-1">
            <q-card-section>
              <div class="text-subtitle2 text-primary q-mb-sm">
                <q-icon name="smart_toy" /> Resposta da IA:
              </div>
              <div class="text-body2" style="white-space: pre-wrap">{{ testResponse }}</div>
            </q-card-section>
          </q-card>
        </transition>

        <!-- Erro do teste -->
        <transition name="fade">
          <q-banner v-if="testError" class="bg-negative text-white q-mt-md" rounded dense>
            <template v-slot:avatar>
              <q-icon name="error" />
            </template>
            {{ testError }}
          </q-banner>
        </transition>
      </q-card-section>
    </q-card>


    <!-- Botões de ação -->
    <div class="row justify-end q-gutter-sm">
      <q-btn
        label="Testar Conexão"
        color="secondary"
        outline
        @click="testarConexao"
        :loading="testando"
        :disable="loading || !isFormValid || !config.habilitado"
      />
      <q-btn
        label="Salvar"
        color="primary"
        @click="salvar"
        :loading="loading"
        :disable="!isFormValid"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";

const { apiRequest } = useApiRequest();
const $q = useQuasar();

// Estados
const loading = ref(false);
const testando = ref(false);
const mostrarApiKey = ref(false);
const testPrompt = ref('');
const testResponse = ref('');
const testError = ref('');

// Configurações OpenAI
const config = ref({
  apiKey: '',
  model: 'gpt-3.5-turbo',
  maxTokens: 500,
  timeout: 30,
  habilitado: false
});

// Status da configuração
const statusConfig = ref({
  mensagem: 'OpenAI não configurada',
  cor: 'bg-grey-3 text-grey-8',
  icone: 'help'
});

// Modelos disponíveis
const modelosDisponiveis = [
  'gpt-3.5-turbo',
  'gpt-4',
  'gpt-4-turbo',
  'gpt-4o',
  'gpt-4o-mini'
];

// Computed
const isFormValid = computed(() => {
  if (!config.value.habilitado) return true; // Se desabilitado, pode salvar
  return config.value.apiKey &&
         config.value.model &&
         config.value.maxTokens > 0 &&
         config.value.timeout > 0;
});

// Métodos
const carregarConfiguracoes = async () => {
  loading.value = true;
  try {
    const response = await apiRequest('/api/openai/config');
    if (response) {
      config.value = {
        apiKey: response.apiKey || '',
        model: response.model || 'gpt-3.5-turbo',
        maxTokens: response.maxTokens || 500,
        timeout: response.timeout || 30,
        habilitado: response.habilitado || false
      };

      atualizarStatus();
    }
  } catch (error) {
    console.error('Erro ao carregar configurações OpenAI:', error);
  } finally {
    loading.value = false;
  }
};

const atualizarStatus = () => {
  if (!config.value.habilitado) {
    statusConfig.value = {
      mensagem: 'OpenAI desabilitada',
      cor: 'bg-grey-3 text-grey-8',
      icone: 'power_settings_new'
    };
  } else if (!config.value.apiKey || config.value.apiKey.trim() === '') {
    statusConfig.value = {
      mensagem: 'API Key não configurada',
      cor: 'bg-warning text-white',
      icone: 'warning'
    };
  } else {
    // Se chegou aqui, está habilitado e tem API key (mesmo que seja ********)
    statusConfig.value = {
      mensagem: 'OpenAI configurada - Clique em "Testar Conexão" para verificar',
      cor: 'bg-info text-white',
      icone: 'info'
    };
  }
};

const testarConexao = async () => {
  if (!isFormValid.value || !config.value.habilitado) {
    $q.notify({
      type: 'negative',
      message: 'Preencha todos os campos obrigatórios e habilite a integração',
      position: 'top'
    });
    return;
  }

  testando.value = true;
  try {
    const payload = {
      apiKey: config.value.apiKey,
      model: config.value.model,
      maxTokens: config.value.maxTokens,
      timeout: config.value.timeout,
      habilitado: config.value.habilitado
    };

    const response = await apiRequest('/api/openai/testar-conexao', 'POST', payload);

    if (response?.sucesso) {
      statusConfig.value = {
        mensagem: 'Conexão estabelecida com sucesso!',
        cor: 'bg-positive text-white',
        icone: 'check_circle'
      };
      $q.notify({
        type: 'positive',
        message: 'Conexão com OpenAI estabelecida com sucesso!',
        position: 'top'
      });
    } else {
      throw new Error(response?.erro || 'Erro na conexão');
    }
  } catch (error) {
    statusConfig.value = {
      mensagem: `Erro: ${error.message}`,
      cor: 'bg-negative text-white',
      icone: 'error'
    };
    $q.notify({
      type: 'negative',
      message: 'Erro ao testar conexão: ' + error.message,
      position: 'top'
    });
  } finally {
    testando.value = false;
  }
};

const salvar = async () => {
  if (!isFormValid.value) return;

  loading.value = true;
  try {
    const payload = {
      apiKey: config.value.apiKey,
      model: config.value.model,
      maxTokens: config.value.maxTokens,
      timeout: config.value.timeout,
      habilitado: config.value.habilitado
    };

    await apiRequest('/api/openai/salvar', 'PUT', payload);

    $q.notify({
      type: 'positive',
      message: 'Configurações OpenAI salvas com sucesso!',
      position: 'top'
    });

    atualizarStatus();
  } catch (error) {
    console.error('Erro ao salvar configurações OpenAI:', error);
    $q.notify({
      type: 'negative',
      message: 'Erro ao salvar configurações OpenAI',
      position: 'top'
    });
  } finally {
    loading.value = false;
  }
};

const testarPrompt = async () => {
  if (!testPrompt.value.trim()) {
    $q.notify({
      type: 'warning',
      message: 'Digite uma pergunta para testar',
      position: 'top'
    });
    return;
  }

  testando.value = true;
  testResponse.value = '';
  testError.value = '';

  try {
    const payload = {
      prompt: testPrompt.value
    };

    const response = await apiRequest('/api/openai/testar-prompt', 'POST', payload);

    if (response?.sucesso) {
      testResponse.value = response.resposta;
      $q.notify({
        type: 'positive',
        message: 'Pergunta processada com sucesso!',
        position: 'top'
      });
    } else {
      testError.value = response?.erro || 'Erro ao processar pergunta';
    }
  } catch (error) {
    console.error('Erro ao testar prompt:', error);
    testError.value = error.message || 'Erro ao comunicar com o servidor';
    $q.notify({
      type: 'negative',
      message: 'Erro ao testar prompt: ' + (error.message || 'Erro desconhecido'),
      position: 'top'
    });
  } finally {
    testando.value = false;
  }
};

// Lifecycle
onMounted(async () => {
  await carregarConfiguracoes();
});
</script>

<style scoped>
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
