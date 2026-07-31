<template>
  <div class="q-pa-md">
    <q-card flat bordered>
      <q-card-section class="row items-center justify-between">
        <div>
          <div class="text-h6">WhatsApp</div>
          <div class="text-caption">Gerencie a sessão e teste de envio via microserviço</div>
        </div>
        <q-badge :color="status.conectado ? 'positive' : 'negative'" outline>
          {{ status.conectado ? 'Conectado' : 'Desconectado' }}
        </q-badge>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row q-col-gutter-md">
          <div class="col-12 col-md-6">
            <q-card flat bordered>
              <q-card-section>
                <div class="text-subtitle1">Sessão</div>
                <div class="text-caption">Conecte ou desconecte a sessão do WhatsApp</div>
              </q-card-section>
              <q-card-actions>
                <q-btn v-if="!status.conectado" color="primary" label="Conectar (QR)" @click="abrirQr"/>
                <q-btn v-else color="negative" label="Desconectar" @click="desconectar"/>
                <q-btn flat label="Atualizar status" @click="carregarStatus"/>
              </q-card-actions>
              <q-card-section v-if="status.conectado && conta.wid">
                <div class="text-caption">Conta</div>
                <div class="q-mt-xs">Número: {{ conta.wid }}</div>
                <div>Nome: {{ conta.pushname || '-' }}</div>
              </q-card-section>
            </q-card>
          </div>

          <div class="col-12 col-md-6">
            <q-card flat bordered>
              <q-card-section>
                <div class="text-subtitle1">Teste de Envio</div>
                <div class="text-caption">Envie um PDF de teste para validar a sessão</div>
              </q-card-section>
              <q-card-section>
                <q-input v-model="telefoneTeste" label="Telefone (WhatsApp)" outlined dense mask="(##) #####-####" unmasked-value />
              </q-card-section>
              <q-card-actions>
                <q-btn color="primary" label="Enviar PDF de teste" @click="enviarTeste" :loading="loading.enviarTeste"/>
              </q-card-actions>
            </q-card>
          </div>

          <div class="col-12">
            <q-card flat bordered>
              <q-card-section>
                <div class="text-subtitle1">Configurações</div>
                <div class="text-caption">URL do serviço e habilitação</div>
              </q-card-section>
              <q-card-section class="row q-col-gutter-md">
                <div class="col-12 col-md-6">
                  <q-input v-model="serviceUrl" label="URL do serviço (Backend → WhatsApp)" outlined dense />
                </div>
                <div class="col-12 col-md-3 flex items-center">
                  <q-toggle v-model="enabledFlag" label="Habilitar WhatsApp" />
                </div>
              </q-card-section>
              <q-card-actions>
                <q-btn flat label="Salvar" color="primary" @click="salvarConfigs" :loading="loading.salvar"/>
                <q-btn flat label="Testar conexão" @click="carregarStatus"/>
              </q-card-actions>
            </q-card>
          </div>
        </div>
      </q-card-section>
    </q-card>

    <q-dialog v-model="qrDialog">
      <q-card style="min-width: 360px; max-width: 420px">
        <q-card-section>
          <div class="text-h6">Conectar WhatsApp</div>
          <div class="text-caption">Escaneie este QR pelo WhatsApp</div>
        </q-card-section>
        <q-card-section class="flex flex-center">
          <img v-if="qrPng" :src="qrPng" alt="QR Code" style="max-width: 320px" />
          <div v-else style="height: 280px" class="flex flex-center">
            <q-spinner-dots color="primary" size="40px" />
          </div>
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="Fechar" v-close-popup />
          <q-btn flat label="Atualizar" color="primary" @click="carregarQr" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { useQuasar } from 'quasar';
import { useApiRequest } from '@/composables/useApiRequest';

const $q = useQuasar();
const { apiRequest } = useApiRequest();

const status = ref({ conectado: false, hasQr: false });
const conta = ref({ wid: null, pushname: null });
const telefoneTeste = ref("");
const qrDialog = ref(false);
const qrPng = ref("");
const serviceUrl = ref("");
const enabledFlag = ref(false);

const loading = ref({ enviarTeste: false, salvar: false });
const qrPollTimer = ref(null);
const statusPollTimer = ref(null);

async function carregarStatus() {
  try {
    const resp = await apiRequest('/api/whatsapp/status', 'GET');
    status.value = resp || { conectado: false, hasQr: false };
    if (status.value.conectado) {
      const me = await apiRequest('/api/whatsapp/me', 'GET');
      if (me?.success) conta.value = { wid: me.wid, pushname: me.pushname };
      else conta.value = { wid: null, pushname: null };
    } else {
      conta.value = { wid: null, pushname: null };
    }
  } catch (e) {
    status.value = { conectado: false, hasQr: false };
  }
}

async function abrirQr() {
  qrDialog.value = true;
  qrPng.value = "";
  try { await apiRequest('/api/whatsapp/start', 'POST', {}); } catch (e) {}
  await carregarQr();
  if (!qrPollTimer.value) {
    qrPollTimer.value = setInterval(async () => {
      if (!status.value.conectado) await carregarQr();
    }, 1500);
  }
  if (!statusPollTimer.value) {
    statusPollTimer.value = setInterval(async () => {
      await carregarStatus();
      if (status.value.conectado) {
        if (qrPollTimer.value) { clearInterval(qrPollTimer.value); qrPollTimer.value = null; }
        if (statusPollTimer.value) { clearInterval(statusPollTimer.value); statusPollTimer.value = null; }
        qrDialog.value = false;
        $q.notify({ type: 'positive', message: 'Conectado com sucesso' });
      }
    }, 2000);
  }
}

async function carregarQr() {
  try {
    const resp = await apiRequest('/api/whatsapp/qr', 'GET');
    if (resp?.success && resp?.png) qrPng.value = resp.png; else qrPng.value = "";
  } catch (e) { qrPng.value = ""; }
}

async function desconectar() {
  try {
    await apiRequest('/api/whatsapp/disconnect', 'POST', {});
    await carregarStatus();
    $q.notify({ type: 'info', message: 'Sessão desconectada' });
  } catch (e) {
    $q.notify({ type: 'negative', message: 'Erro ao desconectar' });
  }
}

async function enviarTeste() {
  if (!telefoneTeste.value || telefoneTeste.value.length < 10) {
    return $q.notify({ type: 'warning', message: 'Informe um telefone válido' });
  }
  loading.value.enviarTeste = true;
  try {
    await apiRequest('/api/whatsapp/enviar-teste', 'POST', { telefone: telefoneTeste.value });
    $q.notify({ type: 'positive', message: 'PDF de teste enviado' });
  } catch (e) {
    $q.notify({ type: 'negative', message: 'Erro ao enviar teste' });
  } finally {
    loading.value.enviarTeste = false;
  }
}

async function carregarConfigs() {
  try {
    const url = await apiRequest('/api/configs/config/whatsapp_service_url', 'GET');
    serviceUrl.value = url || '';
  } catch (e) {
    serviceUrl.value = '';
  }
  try {
    const flag = await apiRequest('/api/configs/config/whatsapp_enabled', 'GET');
    enabledFlag.value = String(flag).toLowerCase() === 'true';
  } catch (e) {
    enabledFlag.value = false;
  }
}

async function salvarConfigs() {
  loading.value.salvar = true;
  try {
    await apiRequest('/api/configs/config/whatsapp_service_url', 'PUT', { valor: serviceUrl.value || '' });
    await apiRequest('/api/configs/config/whatsapp_enabled', 'PUT', { valor: enabledFlag.value ? 'true' : 'false' });
    $q.notify({ type: 'positive', message: 'Configurações salvas' });
  } catch (e) {
    $q.notify({ type: 'negative', message: 'Erro ao salvar configurações' });
  } finally {
    loading.value.salvar = false;
  }
}

onMounted(async () => {
  await carregarStatus();
  await carregarConfigs();
});

onUnmounted(() => {
  if (qrPollTimer.value) { clearInterval(qrPollTimer.value); }
  if (statusPollTimer.value) { clearInterval(statusPollTimer.value); }
});
</script>

<style scoped>
</style>
