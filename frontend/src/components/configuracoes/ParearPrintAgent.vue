<template>
  <div class="q-pa-md">
    <div class="row items-center q-mb-md">
      <q-icon name="print" size="2rem" class="q-mr-md" />
      <h5 class="text-h5 q-ma-none">Parear Print Agent</h5>
    </div>

    <!-- Status card when already paired -->
    <q-card v-if="statusPareamento.paired" class="q-pa-lg" style="max-width: 600px; margin-bottom: 20px;">
      <q-card-section>
        <div class="row items-center">
          <q-icon name="check_circle" size="2rem" color="positive" class="q-mr-md" />
          <div>
            <h6 class="text-h6 q-ma-none">✅ Estabelecimento Pareado</h6>
            <p class="q-ma-none text-subtitle2">{{ statusPareamento.store_name || 'ERP' }}</p>
          </div>
        </div>
        <div class="q-mt-md">
          <q-chip :color="statusPareamento.connected ? 'positive' : 'warning'" text-color="white">
            {{ statusPareamento.connected ? 'Conectado' : 'Aguardando conexão...' }}
          </q-chip>
        </div>
        <div class="q-mt-md">
          <q-btn
            label="Reiniciar pareamento"
            color="negative"
            @click="resetarPareamento"
            :loading="loadingReset"
          >
            <template v-slot:loading>
              <q-spinner-hourglass class="on-left" />
              Reiniciando...
            </template>
          </q-btn>
        </div>
      </q-card-section>
    </q-card>

    <!-- Pairing form when not paired -->
    <q-card v-else class="q-pa-lg" style="max-width: 600px;">
      <q-card-section>
        <div class="q-gutter-y-md">
          <q-input
            v-model="pairingCode"
            label="Código de Pareamento"
            outlined
            dense
            placeholder="Insira o código exibido no Print Agent"
          />

          <div class="q-mt-md">
            <q-btn
              label="Parear Agora"
              color="primary"
              @click="parearAgente"
              :loading="loadingPareamento"
              :disable="!pairingCode"
            >
              <template v-slot:loading>
                <q-spinner-hourglass class="on-left" />
                Pareando...
              </template>
            </q-btn>
          </div>

          <div v-if="resultado" class="q-mt-md">
            <q-banner
              :class="resultado.success ? 'bg-green-2 text-green' : 'bg-red-2 text-red'"
              rounded
              inline-actions
            >
              <template v-slot:avatar>
                <q-icon :name="resultado.success ? 'check' : 'error'" :color="resultado.success ? 'green' : 'red'" />
              </template>
              {{ resultado.mensagem }}
            </q-banner>
          </div>
        </div>
      </q-card-section>
    </q-card>
  </div>
</template>

<script>
import { ref, onMounted, onUnmounted } from 'vue';
import { useApiRequest } from '@/composables/useApiRequest';

export default {
  name: 'ParearPrintAgent',
  setup() {
    const pairingCode = ref('');
    const loadingPareamento = ref(false);
    const loadingReset = ref(false);
    const resultado = ref(null);
    const statusPareamento = ref({
      paired: false,
      connected: false,
      store_name: null
    });
    const { apiRequest } = useApiRequest();

    let pollingInterval = null;

    // Carregar status do pareamento
    const carregarStatus = async () => {
      try {
        const response = await apiRequest('/api/print-agent/status', 'GET');
        statusPareamento.value = {
          paired: response.paired || false,
          connected: response.connected || false,
          store_name: response.store_name || null
        };
      } catch (error) {
        console.error('Erro ao carregar status do pareamento:', error);
        // Keep current status or set to unpaired if error
        statusPareamento.value = {
          paired: false,
          connected: false,
          store_name: null
        };
      }
    };

    // Realizar o pareamento com o agente
    const parearAgente = async () => {
      loadingPareamento.value = true;
      resultado.value = null;

      try {
        const response = await apiRequest('/api/print-agent/pair', 'POST', {
          pairing_code: pairingCode.value
        });

        resultado.value = {
          success: true,
          mensagem: response.message || 'Pareado com sucesso'
        };

        // Reload status after successful pairing
        await carregarStatus();
      } catch (error) {
        resultado.value = {
          success: false,
          mensagem: error?.message || error?.detail || 'Erro ao parear com o agente de impressão'
        };
      } finally {
        loadingPareamento.value = false;
      }
    };

    // Resetar o pareamento
    const resetarPareamento = async () => {
      loadingReset.value = true;
      resultado.value = null;

      try {
        const response = await apiRequest('/api/print-agent/reset', 'POST');

        resultado.value = {
          success: true,
          mensagem: response.message || 'Pareamento reiniciado com sucesso'
        };

        // Reload status after reset
        await carregarStatus();
      } catch (error) {
        resultado.value = {
          success: false,
          mensagem: error?.message || error?.detail || 'Erro ao reiniciar o pareamento'
        };
      } finally {
        loadingReset.value = false;
      }
    };

    // Setup polling to update status periodically
    onMounted(async () => {
      await carregarStatus();
      // Poll every 5 seconds to update status
      pollingInterval = setInterval(carregarStatus, 5000);
    });

    // Cleanup polling on component unmount
    onUnmounted(() => {
      if (pollingInterval) {
        clearInterval(pollingInterval);
      }
    });

    return {
      pairingCode,
      loadingPareamento,
      loadingReset,
      resultado,
      statusPareamento,
      parearAgente,
      resetarPareamento,
      carregarStatus
    };
  }
};
</script>

<style scoped>
.q-card {
  width: 100%;
}
</style>
