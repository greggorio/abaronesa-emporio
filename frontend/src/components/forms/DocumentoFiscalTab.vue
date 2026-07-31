<template>
  <div class="documento-fiscal-container">
    <!-- Quando não há documento fiscal -->
    <div v-if="!temDocumentoFiscal" class="text-center q-pa-xl">
      <q-icon name="o_receipt_long" size="64px" color="grey-5" />
      <div class="text-h6 q-mt-md text-grey-7">Documento Fiscal</div>
      <div class="text-body2 text-grey-6 q-mt-sm">
        Clique no botão abaixo para gerar o documento fiscal desta venda
        <!-- Dialog de E-mail -->
        <q-dialog v-model="emailDialog">
          <q-card style="min-width: 400px">
            <q-card-section>
              <div class="text-h6">Enviar {{ nfeDetalhes?.tipoDocumento || tipoDocumento }} por E-mail</div>
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
              <q-btn label="Cancelar" color="grey" flat v-close-popup />
              <q-btn label="Enviar" color="primary" @click="confirmarEnvioEmail" :loading="loadingEmail" />
            </q-card-actions>
          </q-card>
        </q-dialog>
      </div>

      <q-btn
        :label="`Gerar ${tipoDocumento}`"
        :icon="tipoDocumento === 'NFC-e' ? 'o_point_of_sale' : 'o_description'"
        color="primary"
        size="lg"
        class="q-mt-lg"
        @click="gerarDocumentoFiscal"
        :loading="loading"
        :disable="!podeEmitir"
      />

      <div v-if="!podeEmitir && props.modelValue?.status === 'CANCELADA'" class="text-caption text-negative q-mt-md">
        Não é possível emitir documento fiscal para vendas canceladas
      </div>
    </div>

    <!-- Quando há documento fiscal -->
    <div v-else>
      <!-- Card de Status -->
      <q-card flat bordered class="q-mb-md">
        <q-card-section>
          <div class="row items-center">
            <q-icon
              :name="nfeDetalhes.isNFCe ? 'o_point_of_sale' : 'o_description'"
              size="32px"
              :color="getStatusColor(nfeDetalhes.status)"
              class="q-mr-md"
            />
            <div class="col">
              <div class="text-h6">{{ nfeDetalhes.tipoDocumento }} #{{ nfeDetalhes.numero }}</div>
              <div class="text-caption text-grey-7">Emitida em {{ formatDateTime(nfeDetalhes.dataEmissao) }}</div>
            </div>
            <q-badge :color="getStatusColor(nfeDetalhes.status)" :label="getStatusLabel(nfeDetalhes.status)" class="q-pa-sm text-subtitle2" />
          </div>
        </q-card-section>
      </q-card>

      <!-- Informações do Documento -->
      <q-card flat bordered>
        <q-card-section>
          <div class="row q-col-gutter-md">
            <!-- Coluna 1 -->
            <div class="col-12 col-md-6">
              <div class="info-group q-gutter-sm">
                <div class="info-row">
                  <span class="info-label">Série:</span>
                  <span class="info-value">{{ nfeDetalhes.serie }}</span>
                </div>
                <q-separator />
                <div class="info-row">
                  <span class="info-label">Protocolo:</span>
                  <span class="info-value">{{ nfeDetalhes.protocolo || "-" }}</span>
                </div>
                <q-separator />
                <div class="info-row">
                  <span class="info-label">Valor Total:</span>
                  <span class="info-value text-weight-bold">{{ formatCurrency(nfeDetalhes.valorTotal) }}</span>
                </div>
              </div>
            </div>

            <!-- Coluna 2 -->
            <div class="col-12 col-md-6">
              <div class="info-group q-gutter-sm">
                <div class="info-row">
                  <span class="info-label">Chave de Acesso:</span>
                  <span class="info-value chave-acesso">
                    {{ formatChaveAcesso(nfeDetalhes.chaveAcesso) }}
                    <q-btn icon="o_content_copy" size="xs" flat dense @click="copiarChaveAcesso" class="q-ml-xs">
                      <q-tooltip>Copiar chave</q-tooltip>
                    </q-btn>
                  </span>
                </div>
                <q-separator />
                <div class="info-row">
                  <span class="info-label">Ambiente:</span>
                  <span class="info-value">
                    <q-badge
                      :color="nfeDetalhes.ambiente === 1 ? 'positive' : 'warning'"
                      :label="nfeDetalhes.ambiente === 1 ? 'Produção' : 'Homologação'"
                    />
                  </span>
                </div>
              </div>
            </div>
          </div>

          <!-- Motivo de rejeição (se houver) -->
          <div v-if="nfeDetalhes.motivoRejeicao" class="q-mt-md">
            <q-banner class="bg-negative text-white">
              <template v-slot:avatar>
                <q-icon name="o_error" />
              </template>
              {{ nfeDetalhes.motivoRejeicao }}
            </q-banner>
          </div>
        </q-card-section>

        <!-- Ações -->
        <q-separator />
        <q-card-actions class="q-pa-md">
          <!-- Ações para nota REJEITADA -->
          <template v-if="nfeDetalhes.status === 'REJEITADA'">
            <q-btn label="Tentar Novamente" icon="o_refresh" color="primary" @click="tentarNovamente" :loading="loading" />
            <q-btn label="Como Corrigir?" icon="o_help" color="info" flat @click="mostrarAjuda" class="q-ml-sm" />
          </template>

          <!-- Ações para nota AUTORIZADA -->
          <template v-else>
            <q-btn
              v-if="nfeDetalhes.temXmlAssinado"
              label="Baixar PDF"
              icon="o_download"
              color="primary"
              outline
              @click="baixarDanfe"
              :loading="loadingDanfe"
            />
            <q-btn
              v-if="nfeDetalhes.temXmlAssinado"
              label="Baixar XML"
              icon="o_code"
              color="primary"
              outline
              @click="baixarXml"
              :loading="loadingXml"
              class="q-ml-sm"
            />
            <q-btn label="Enviar por E-mail" icon="o_email" color="primary" outline @click="enviarPorEmail" class="q-ml-sm" />
            <q-btn label="Enviar por WhatsApp" icon="o_chat" color="positive" outline @click="enviarPorWhatsApp" class="q-ml-sm" />
            <q-space />
            <q-btn
              v-if="nfeDetalhes.status === 'AUTORIZADA' && nfeDetalhes.isNFCe"
              label="Reimprimir"
              icon="o_print"
              color="secondary"
              outline
              @click="reimprimir"
            />
          </template>
        </q-card-actions>
      </q-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from "vue";
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

// NF-e não implementado neste sistema
const gerarNfe = () => console.warn("NF-e não implementado");
const gerarDanfe = () => console.warn("DANFE não implementado");
const enviarNfePorEmail = () => console.warn("Envio de NF-e não implementado");

// Estado
const loading = ref(false);
const loadingDanfe = ref(false);
const loadingXml = ref(false);
const loadingEmail = ref(false);
const emailDialog = ref(false);
const emailDestinatario = ref("");

// Computed
const temDocumentoFiscal = computed(() => {
  return (props.modelValue?.statusNfe === "AUTORIZADA" || props.modelValue?.statusNfe === "REJEITADA") && props.modelValue?.nfeDetalhes;
});

const nfeDetalhes = computed(() => {
  return props.modelValue?.nfeDetalhes || {};
});

const tipoDocumento = computed(() => {
  return props.modelValue?.origem === "LOJA_FISICA" ? "NFC-e" : "NF-e";
});

const podeEmitir = computed(() => {
  // Não permite se venda cancelada
  if (props.modelValue?.status === "CANCELADA") return false;

  // Não permite se já tem documento autorizado
  if (props.modelValue?.statusNfe === "AUTORIZADA") return false;

  // Permite em todos outros casos (NAO_EMITIDA, REJEITADA, etc)
  return true;
});

// Métodos
async function gerarDocumentoFiscal() {
  loading.value = true;

  try {
    const response = await gerarNfe(props.recordId);

    if (response.success && response.data) {
      // Atualizar o modelValue com os novos dados da NFe
      const updatedModel = {
        ...props.modelValue,
        statusNfe: response.data.status,
        numeroNfe: response.data.numero,
        serieNfe: response.data.serie,
        chaveNfe: response.data.chaveAcesso,
        nfeDetalhes: {
          id: response.data.id,
          numero: response.data.numero,
          protocolo: response.data.protocolo,
          motivoRejeicao: response.data.motivoRejeicao,
          serie: response.data.serie,
          chaveAcesso: response.data.chaveAcesso,
          status: response.data.status,
          dataEmissao: response.data.dataEmissao,
          valorTotal: response.data.valorTotal,
          ambiente: response.data.ambiente,
          modelo: response.data.modelo,
          tipoDocumento: response.data.tipoDocumento,
          isNFCe: response.data.nfce || response.data.isNFCe,
          isNFe: response.data.nfe || response.data.isNFe,
          temXmlAssinado: !!response.data.xmlAssinado,
          temXmlRetorno: !!response.data.xmlRetorno,
        },
      };

      // Emitir o evento para atualizar o componente pai
      emit("update:modelValue", updatedModel);

      // Notificação de sucesso ou erro baseado no status
      if (response.data.status === "AUTORIZADA") {
        $q.notify({
          type: "positive",
          message: `${tipoDocumento.value} autorizada com sucesso!`,
          position: "top",
        });
      } else if (response.data.status === "REJEITADA") {
        $q.notify({
          type: "negative",
          message: `${tipoDocumento.value} rejeitada: ${response.data.motivoRejeicao}`,
          position: "top",
          timeout: 5000,
        });
      }
    }
  } catch (error) {
    console.error("Erro ao gerar documento fiscal:", error);
    $q.notify({
      type: "negative",
      message: `Erro ao gerar ${tipoDocumento.value}: ${error.message || "Erro desconhecido"}`,
      position: "top",
    });
  } finally {
    loading.value = false;
  }
}

async function baixarDanfe() {
  if (!nfeDetalhes.value?.id) return;

  loadingDanfe.value = true;
  try {
    const pdfData = await gerarDanfe(nfeDetalhes.value.id);

    // Criar blob e download
    const blob = new Blob([pdfData], { type: "application/pdf" });
    const url = window.URL.createObjectURL(blob);
    const fileName = `${nfeDetalhes.value.tipoDocumento.toLowerCase()}_${nfeDetalhes.value.numero}.pdf`;

    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);

    $q.notify({
      type: "positive",
      message: "PDF baixado com sucesso",
      position: "top",
    });
  } catch (error) {
    $q.notify({
      type: "negative",
      message: "Erro ao baixar PDF",
      position: "top",
    });
  } finally {
    loadingDanfe.value = false;
  }
}

async function baixarXml() {
  if (!nfeDetalhes.value?.id) return;

  loadingXml.value = true;
  try {
    const response = await apiRequest(`/api/nfe/xml/${nfeDetalhes.value.id}`, "GET", null, {
      responseType: "arraybuffer",
    });

    const blob = new Blob([response], { type: "text/xml" });
    const url = window.URL.createObjectURL(blob);
    const fileName = `${nfeDetalhes.value.tipoDocumento.toLowerCase()}_${nfeDetalhes.value.numero}.xml`;

    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);

    $q.notify({
      type: "positive",
      message: "XML baixado com sucesso",
      position: "top",
    });
  } catch (error) {
    $q.notify({
      type: "negative",
      message: "Erro ao baixar XML",
      position: "top",
    });
  } finally {
    loadingXml.value = false;
  }
}

function enviarPorEmail() {
  emailDestinatario.value = "";
  emailDialog.value = true;
}

async function confirmarEnvioEmail() {
  if (!isValidEmail(emailDestinatario.value)) {
    $q.notify({
      type: "warning",
      message: "E-mail inválido",
      position: "top",
    });
    return;
  }

  loadingEmail.value = true;
  try {
    await enviarNfePorEmail(nfeDetalhes.value.id, emailDestinatario.value);

    emailDialog.value = false;

    $q.notify({
      type: "positive",
      message: "E-mail enviado com sucesso!",
      position: "top",
    });
  } catch (error) {
    $q.notify({
      type: "negative",
      message: "Erro ao enviar e-mail",
      position: "top",
    });
  } finally {
    loadingEmail.value = false;
  }
}

function enviarPorWhatsApp() {
  const telefone = props.modelValue?.clienteTelefone;

  if (!telefone) {
    $q.notify({
      type: "warning",
      message: "Cliente não possui telefone cadastrado",
      position: "top",
    });
    return;
  }

  // Formatar número para WhatsApp
  const numeroFormatado = telefone.replace(/\D/g, "");
  const numeroWhatsApp = numeroFormatado.startsWith("55") ? numeroFormatado : `55${numeroFormatado}`;

  // Mensagem padrão
  const mensagem =
    `Olá! Segue o ${nfeDetalhes.value.tipoDocumento} da sua compra:\n\n` +
    `Número: ${nfeDetalhes.value.numero}\n` +
    `Chave: ${nfeDetalhes.value.chaveAcesso}\n\n` +
    `Para baixar o PDF, acesse: ${window.location.origin}/consulta-nfe/${nfeDetalhes.value.chaveAcesso}`;

  // Abrir WhatsApp Web
  const url = `https://wa.me/${numeroWhatsApp}?text=${encodeURIComponent(mensagem)}`;
  window.open(url, "_blank");
}

function reimprimir() {
  // Para NFC-e, podemos chamar o endpoint de impressão térmica
  if (nfeDetalhes.value.isNFCe) {
    $q.dialog({
      title: "Reimprimir NFC-e",
      message: "Selecione o tipo de impressão:",
      options: {
        type: "radio",
        model: "termica",
        items: [
          { label: "Impressora Térmica 80mm", value: "termica" },
          { label: "Download PDF", value: "pdf" },
        ],
      },
      cancel: true,
    }).onOk((tipo) => {
      if (tipo === "pdf") {
        baixarDanfe();
      } else {
        imprimirTermica();
      }
    });
  } else {
    baixarDanfe();
  }
}

async function imprimirTermica() {
  try {
    await apiRequest(`/api/danfce/${nfeDetalhes.value.id}/imprimir`, "POST", {
      tipo: "TERMICA_80MM",
    });

    $q.notify({
      type: "positive",
      message: "Enviado para impressão",
      position: "top",
    });
  } catch (error) {
    $q.notify({
      type: "negative",
      message: "Erro ao imprimir",
      position: "top",
    });
  }
}

async function tentarNovamente() {
  $q.dialog({
    title: "Tentar Novamente",
    message: "Antes de gerar uma nova nota fiscal, certifique-se de que o problema foi corrigido. Deseja continuar?",
    cancel: true,
    persistent: true,
  }).onOk(() => {
    gerarDocumentoFiscal();
  });
}

function mostrarAjuda() {
  const motivo = nfeDetalhes.value.motivoRejeicao || "Erro desconhecido";
  let sugestao = "";

  // Analisar o motivo e dar sugestões específicas
  if (motivo.includes("CPF") && motivo.includes("inválido")) {
    sugestao = "Verifique se o CPF do cliente está correto no cadastro. Acesse o cadastro do cliente e corrija o CPF antes de tentar novamente.";
  } else if (motivo.includes("CNPJ") && motivo.includes("inválido")) {
    sugestao = "Verifique se o CNPJ do cliente está correto no cadastro. Acesse o cadastro do cliente e corrija o CNPJ antes de tentar novamente.";
  } else if (motivo.includes("CEP")) {
    sugestao = "O CEP do cliente está inválido ou não foi informado. Atualize o endereço do cliente com um CEP válido.";
  } else if (motivo.includes("NCM")) {
    sugestao = "O código NCM de um ou mais produtos está inválido. Verifique o cadastro dos produtos desta venda.";
  } else if (motivo.includes("CFOP")) {
    sugestao = "O CFOP utilizado está incorreto. Entre em contato com o suporte técnico.";
  } else if (motivo.includes("Certificado")) {
    sugestao = "Problema com o certificado digital. Verifique se o certificado está válido e instalado corretamente.";
  } else {
    sugestao = "Entre em contato com o suporte técnico informando o erro acima para obter ajuda.";
  }

  $q.dialog({
    title: "Como Corrigir o Problema",
    message: `<div class="q-mb-md"><strong>Motivo da Rejeição:</strong><br>${motivo}</div>
              <div><strong>Sugestão:</strong><br>${sugestao}</div>`,
    html: true,
    ok: {
      label: "Entendi",
      color: "primary",
    },
  });
}

function copiarChaveAcesso() {
  if (!nfeDetalhes.value?.chaveAcesso) return;

  navigator.clipboard
    .writeText(nfeDetalhes.value.chaveAcesso)
    .then(() => {
      $q.notify({
        type: "positive",
        message: "Chave de acesso copiada!",
        position: "top",
      });
    })
    .catch(() => {
      $q.notify({
        type: "negative",
        message: "Erro ao copiar chave",
        position: "top",
      });
    });
}

// Helpers
function getStatusColor(status) {
  const colors = {
    AUTORIZADA: "positive",
    AUTORIZADO: "positive",
    REJEITADA: "negative",
    REJEITADO: "negative",
    PROCESSANDO: "warning",
    EM_PROCESSAMENTO: "warning",
    CANCELADA: "negative",
    ERRO: "negative",
  };
  return colors[status] || "grey";
}

function getStatusLabel(status) {
  const labels = {
    AUTORIZADA: "Autorizada",
    AUTORIZADO: "Autorizado",
    REJEITADA: "Rejeitada",
    REJEITADO: "Rejeitado",
    PROCESSANDO: "Processando",
    EM_PROCESSAMENTO: "Em Processamento",
    CANCELADA: "Cancelada",
    ERRO: "Erro",
  };
  return labels[status] || status;
}

function formatDateTime(dateStr) {
  if (!dateStr) return "";
  // Se já está no formato DD/MM/YYYY HH:mm:ss, retornar como está
  if (dateStr.match(/^\d{2}\/\d{2}\/\d{4} \d{2}:\d{2}:\d{2}$/)) {
    return dateStr;
  }
  // Caso contrário, formatar
  const date = new Date(dateStr);
  return date.toLocaleString("pt-BR");
}

function formatCurrency(value) {
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL",
  }).format(value || 0);
}

function formatChaveAcesso(chave) {
  if (!chave) return "";
  // Formatar em blocos de 4 dígitos
  return chave.match(/.{1,4}/g)?.join(" ") || chave;
}

function isValidEmail(email) {
  const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return re.test(email);
}
</script>

<style scoped>
.documento-fiscal-container {
  padding: 0;
}

.info-group {
  display: flex;
  flex-direction: column;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
}

.info-label {
  color: #666;
  font-size: 13px;
}

.info-value {
  font-size: 14px;
  text-align: right;
}

.chave-acesso {
  font-family: monospace;
  font-size: 12px;
  display: flex;
  align-items: center;
  word-break: break-all;
}
</style>
