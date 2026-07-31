<template>
  <div class="comprovante-fiscal-tab">
    <div v-if="!temDocumentoFiscal" class="text-center q-pa-xl">
      <q-icon name="o_receipt_long" size="64px" color="grey-5" />
      <div class="text-h6 q-mt-md text-grey-7">Documento Fiscal</div>
      <div class="text-body2 text-grey-6 q-mt-sm">
        Clique no botão abaixo para emitir o documento fiscal desta venda.
      </div>
      <q-btn
        class="q-mt-lg"
        :label="`Emitir ${tipoDocumento}`"
        :icon="tipoDocumento === 'NFC-e' ? 'o_point_of_sale' : 'o_description'"
        color="primary"
        size="lg"
        @click="emitirNfce"
        :loading="loadingEmitir"
        :disable="!podeEmitir || loadingEmitir || !recordId"
      />
      <div v-if="!recordId" class="text-caption text-negative q-mt-md">
        Salve o registro para gerar o documento fiscal.
      </div>
    </div>

    <div v-else>
      <q-card flat bordered class="q-mb-md">
        <q-card-section>
          <div class="row items-center">
            <q-icon
              :name="tipoDocumento === 'NFC-e' ? 'o_point_of_sale' : 'o_description'"
              :color="getStatusColor(nfeDetalhes.status)"
              size="32px"
              class="q-mr-md"
            />
            <div class="col">
              <div class="text-h6">{{ tipoDocumento }} #{{ nfeDetalhes.numero || "—" }}</div>
              <div class="text-caption text-grey-7">Emitida em {{ formatDateTime(nfeDetalhes.dataEmissao) }}</div>
            </div>
            <q-badge :color="getStatusColor(nfeDetalhes.status)" :label="getStatusLabel(nfeDetalhes.status)" class="text-subtitle2" />
          </div>
        </q-card-section>
      </q-card>

      <q-card flat bordered>
        <q-card-section>
          <div class="row q-col-gutter-md">
            <div class="col-12 col-md-4">
              <div class="info-label">Chave de Acesso</div>
              <div class="text-body2 chave" v-if="nfeDetalhes.chaveAcesso">{{ formatChave(nfeDetalhes.chaveAcesso) }}</div>
              <div v-else class="text-grey-6">Não disponível</div>
            </div>
            <div class="col-6 col-md-4">
              <div class="info-label">Ambiente</div>
              <div class="text-body2">{{ ambienteLabel }}</div>
            </div>
            <div class="col-6 col-md-4">
              <div class="info-label">Status</div>
              <div class="text-body2">{{ getStatusLabel(nfeDetalhes.status) }}</div>
            </div>
          </div>
        </q-card-section>

        <q-separator />
        <q-card-actions class="q-pa-md">
          <q-btn
            :label="`Baixar ${tipoDocumento === 'NFe' ? 'DANFE' : 'DANFCE'}`"
            icon="o_download"
            color="secondary"
            outline
            @click="baixarDanfe"
            :loading="loadingPdf"
            :disable="!nfeDetalhes.id"
          />
          <q-btn
            label="Enviar por E-mail"
            icon="o_email"
            color="primary"
            outline
            class="q-ml-sm"
            @click="abrirEmailDialog"
            :disable="!nfeDetalhes.id"
          />
        </q-card-actions>
      </q-card>
    </div>
  </div>

  <q-dialog v-model="emailDialog">
    <q-card style="min-width: 400px">
      <q-card-section>
        <div class="text-h6">Enviar DANFCE por E-mail</div>
      </q-card-section>
      <q-card-section>
        <q-input
          v-model="emailDestinatario"
          label="E-mail do destinatário"
          type="email"
          outlined
          :rules="[(val) => !!val || 'E-mail é obrigatório', (val) => isValidEmail(val) || 'E-mail inválido']"
        />
      </q-card-section>
      <q-card-actions align="right">
        <q-btn flat label="Cancelar" v-close-popup />
        <q-btn label="Enviar" color="primary" :loading="loadingEmail" @click="enviarEmail" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";

const props = defineProps({
  modelValue: {
    type: Object,
    required: true,
  },
  recordId: {
    type: Number,
    default: null,
  },
});

const emit = defineEmits(["update:modelValue"]);

const $q = useQuasar();
const { apiRequest } = useApiRequest();

const loadingEmitir = ref(false);
const loadingPdf = ref(false);
const loadingEmail = ref(false);
const nfeCache = ref(null);
const emailDialog = ref(false);
const emailDestinatario = ref("");

const temDocumentoFiscal = computed(() => {
  return !!nfeDetalhes.value?.id;
});

const nfeDetalhes = computed(() => {
  const fallback = props.modelValue?.nfce || {};
  const fromFields = {
    id: props.modelValue?.nfeId,
    numero: props.modelValue?.nfeNumero,
    status: props.modelValue?.nfeStatus,
    chaveAcesso: props.modelValue?.nfeChaveAcesso,
    ambiente: props.modelValue?.nfeAmbiente,
    dataEmissao: props.modelValue?.nfeDataEmissao,
  };

  return {
    ...fallback,
    ...fromFields,
    ...nfeCache.value,
  };
});

const tipoDocumento = computed(() => {
  const clienteTipoPessoa = nfeDetalhes.value?.clienteTipoPessoa || props.modelValue?.clienteTipoPessoa;
  if (clienteTipoPessoa === "PJ") return "NFe";
  return "NFC-e";
});

const podeEmitir = computed(() => {
  if (!props.recordId) return false;
  if (nfeDetalhes.value?.status === "AUTORIZADA") return false;
  return true;
});

const ambienteLabel = computed(() => {
  if (!nfeDetalhes.value?.ambiente) return "—";
  if (+nfeDetalhes.value.ambiente === 1) return "Produção";
  if (+nfeDetalhes.value.ambiente === 2) return "Homologação";
  return "—";
});

const refreshNfeData = async () => {
  if (!props.recordId) {
    nfeCache.value = null;
    return;
  }

  try {
    const response = await apiRequest(`/api/danfce/pagamento/${props.recordId}`);
    const payload = response?.data;
    if (payload?.exists) {
      // Já existe documento fiscal: usar dados da NFe/NFCe e anexar info de cliente, se vier fora
      nfeCache.value = payload.nfe || null;
      if (payload?.clienteTipoPessoa || payload?.clienteCpf || payload?.clienteCnpj) {
        nfeCache.value = {
          ...nfeCache.value,
          clienteTipoPessoa: payload.clienteTipoPessoa,
          clienteCpf: payload.clienteCpf,
          clienteCnpj: payload.clienteCnpj,
        };
      }
    } else {
      // Ainda não existe documento fiscal: manter info de cliente para decidir NF-e/NFC-e
      if (payload && (payload.clienteTipoPessoa || payload.clienteCpf || payload.clienteCnpj)) {
        nfeCache.value = {
          clienteTipoPessoa: payload.clienteTipoPessoa,
          clienteCpf: payload.clienteCpf,
          clienteCnpj: payload.clienteCnpj,
        };
      } else {
        nfeCache.value = null;
      }
    }
  } catch (error) {
    console.error("Erro ao buscar NFC-e existente:", error);
    nfeCache.value = null;
  }
};

watch(
  () => props.recordId,
  async (newId) => {
    if (newId) {
      await refreshNfeData();
    } else {
      nfeCache.value = null;
    }
  },
  { immediate: true }
);

async function emitirNfce() {
  if (!props.recordId || loadingEmitir.value) return;

  loadingEmitir.value = true;
  try {
    const response = await apiRequest(`/api/admin/nfce/pagamentos/${props.recordId}/emitir`, "POST");

    if (response?.nfeId) {
      if (response?.status && response.status !== "AUTORIZADA") {
        const motivo = response?.motivoRejeicao || "Documento rejeitado.";
        $q.notify({ type: "negative", message: motivo, position: "top" });
        return;
      }
      const updated = {
        ...props.modelValue,
        nfeId: response.nfeId,
        nfeNumero: response.numero,
        nfeStatus: response.status,
        nfeChaveAcesso: response.chaveAcesso,
        nfce: {
          id: response.nfeId,
          numero: response.numero,
          status: response.status,
          chaveAcesso: response.chaveAcesso,
        },
      };
      emit("update:modelValue", updated);
      await refreshNfeData();
      $q.notify({ type: "positive", message: "NFC-e emitida com sucesso", position: "top" });
    } else {
      throw new Error("Resposta inválida");
    }
  } catch (error) {
    console.error("Erro ao emitir NFC-e", error);
    const msg =
      error?.error?.message ||
      error?.response?.data?.error?.message ||
      error?.message ||
      "Não foi possível emitir o documento fiscal";
    $q.notify({ type: "negative", message: msg, position: "top" });
  } finally {
    loadingEmitir.value = false;
  }
}

function abrirEmailDialog() {
  emailDestinatario.value = "";
  emailDialog.value = true;
}

async function enviarEmail() {
  if (!nfeDetalhes.value?.id || loadingEmail.value) return;
  if (!isValidEmail(emailDestinatario.value)) {
    $q.notify({ type: "warning", message: "E-mail inválido", position: "top" });
    return;
  }

  loadingEmail.value = true;
  try {
    const isNfe = nfeDetalhes.value?.modelo === 55 || tipoDocumento.value === "NFe";
    const endpoint = isNfe
      ? `/api/admin/nfe/pagamentos/${props.recordId}/email`
      : `/api/admin/nfce/pagamentos/${props.recordId}/email`;

    await apiRequest(endpoint, "POST", {
      email: emailDestinatario.value,
    });

    emailDialog.value = false;
    $q.notify({ type: "positive", message: "E-mail enviado (ou agendado) com sucesso", position: "top" });
  } catch (error) {
    console.error("Erro ao enviar e-mail:", error);
    $q.notify({ type: "negative", message: "Erro ao enviar e-mail", position: "top" });
  } finally {
    loadingEmail.value = false;
  }
}

async function baixarDanfe() {
  if (!nfeDetalhes.value?.id || loadingPdf.value) return;

  loadingPdf.value = true;
  try {
    const isNfe = nfeDetalhes.value?.modelo === 55;
    const url = isNfe
      ? `/api/admin/nfe/${nfeDetalhes.value.id}/danfe.pdf`
      : `/api/danfce/${nfeDetalhes.value.id}/pdf`;
    const pdf = await apiRequest(url, "GET", null, {
      responseType: "arraybuffer",
    });

    const fileName = `${isNfe ? "danfe" : "danfce"}_${nfeDetalhes.value.numero || nfeDetalhes.value.id}.pdf`;
    const blob = new Blob([pdf], { type: "application/pdf" });
    const blobUrl = window.URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = blobUrl;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(blobUrl);

    $q.notify({ type: "positive", message: "Download iniciado", position: "top" });
  } catch (error) {
    console.error("Erro ao baixar DANFCE", error);
    $q.notify({ type: "negative", message: "Não foi possível baixar o PDF", position: "top" });
  } finally {
    loadingPdf.value = false;
  }
}

function getStatusLabel(status) {
  if (!status) return "PROCESSANDO";
  return status.replace("_", " ");
}

function getStatusColor(status) {
  const colors = {
    AUTORIZADA: "positive",
    REJEITADA: "negative",
    PROCESSANDO: "warning",
  };
  return colors[status] || "grey";
}

function formatChave(chave) {
  if (!chave) return "";
  return chave.match(/.{1,4}/g)?.join(" ") || chave;
}

function formatDateTime(value) {
  if (!value) return "—";
  if (typeof value === "string" && value.match(/^[0-9]{4}-[0-9]{2}-[0-9]{2}T/)) {
    return new Date(value).toLocaleString("pt-BR");
  }
  return value;
}

function isValidEmail(email) {
  const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return re.test(email);
}
</script>

<style scoped>
.comprovante-fiscal-tab {
  width: 100%;
}

.info-label {
  font-size: 12px;
  color: #666;
  text-transform: uppercase;
  font-weight: 500;
}

.chave {
  font-size: 13px;
  font-family: monospace;
  word-break: break-all;
}
</style>
