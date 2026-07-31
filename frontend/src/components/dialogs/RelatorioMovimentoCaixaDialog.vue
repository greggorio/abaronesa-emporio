<template>
  <q-dialog v-model="dialogVisible" persistent>
    <q-card style="width: 600px; max-width: 90vw">
      <!-- Header com gradiente -->
      <q-toolbar class="bg-gradient text-white">
        <q-icon name="o_account_balance_wallet" size="28px" class="q-mr-sm" />
        <q-toolbar-title>
          <div class="text-h6">Relatório de Movimento de Caixa</div>
          <div class="text-caption">Gerar relatório em PDF</div>
        </q-toolbar-title>
        <q-space />
        <q-btn flat round dense icon="close" @click="fechar" />
      </q-toolbar>

      <!-- Conteúdo -->
      <q-card-section>
        <q-form @submit.prevent="gerarRelatorio" ref="formRef">
          <div class="row q-col-gutter-md">
            <!-- Data -->
            <div class="col-12">
              <div class="text-subtitle1 text-grey-8 q-mb-sm">
                <q-icon name="o_calendar_today" size="20px" class="q-mr-xs" />
                Data do Movimento
              </div>
              <q-input
                v-model="filtros.data"
                label="Selecione a data"
                filled
                mask="##/##/####"
                placeholder="dd/mm/aaaa"
                :rules="[
                  (val) => !!val || 'Data é obrigatória',
                  (val) => validarData(val) || 'Data inválida',
                  (val) => !dataFutura || 'Data não pode ser futura',
                ]"
              >
                <template v-slot:prepend>
                  <q-icon name="o_event" />
                </template>
                <template v-slot:append>
                  <q-icon name="o_event" class="cursor-pointer">
                    <q-popup-proxy cover transition-show="scale" transition-hide="scale">
                      <q-date v-model="filtros.data" mask="DD/MM/YYYY" :options="optionsData">
                        <div class="row items-center justify-end">
                          <q-btn v-close-popup label="Fechar" color="primary" flat />
                        </div>
                      </q-date>
                    </q-popup-proxy>
                  </q-icon>
                </template>
              </q-input>
            </div>

            <!-- Informações adicionais -->
            <div class="col-12">
              <q-card flat class="bg-blue-1 q-pa-md">
                <div class="row items-center">
                  <q-icon name="o_info" size="24px" color="blue-8" class="q-mr-sm" />
                  <div>
                    <div class="text-subtitle2 text-blue-10">Informações do Relatório</div>
                    <div class="text-body2 text-blue-9">
                      • Exibe todas as movimentações que afetam o caixa
                      <br />
                      • Inclui saldo inicial calculado até o dia anterior
                      <br />
                      • Mostra evolução do saldo ao longo do dia
                      <br />
                      • Agrupa por forma de pagamento e tipo de movimento
                    </div>
                  </div>
                </div>
              </q-card>
            </div>

            <!-- Atalhos de data -->
            <div class="col-12">
              <div class="text-caption text-grey-7 q-mb-xs">Atalhos rápidos:</div>
              <div class="q-gutter-sm">
                <q-btn
                  v-for="atalho in atalhosData"
                  :key="atalho.id"
                  :label="atalho.label"
                  size="sm"
                  dense
                  outline
                  color="primary"
                  @click="aplicarAtalhoData(atalho.id)"
                />
              </div>
            </div>

            <!-- Preview -->
            <div class="col-12" v-if="filtros.data">
              <q-separator class="q-my-md" />
              <div class="text-center">
                <div class="text-caption text-grey-7">O relatório será gerado para:</div>
                <div class="text-h6 text-primary q-mt-xs">
                  {{ dataFormatadaExtenso }}
                </div>
              </div>
            </div>
          </div>
        </q-form>
      </q-card-section>

      <!-- Ações -->
      <q-card-actions align="right" class="q-px-md q-pb-md">
        <q-btn flat label="Cancelar" color="grey-8" @click="fechar" />
        <q-btn unelevated label="Gerar Relatório" color="primary" icon="o_picture_as_pdf" @click="gerarRelatorio" :loading="gerando" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { ref, computed, watch } from "vue";
import { useQuasar, date } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(["update:modelValue"]);

const $q = useQuasar();
const { apiRequest } = useApiRequest();

// State
const formRef = ref(null);
const gerando = ref(false);
const filtros = ref({
  data: null,
});

// Computed
const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

const atalhosData = [
  { id: "hoje", label: "Hoje" },
  { id: "ontem", label: "Ontem" },
  { id: "anteontem", label: "Anteontem" },
  { id: "7dias", label: "Há 7 dias" },
  { id: "30dias", label: "Há 30 dias" },
];

const dataFutura = computed(() => {
  if (!filtros.value.data) return false;
  const data = converterParaData(filtros.value.data);
  const hoje = new Date();
  hoje.setHours(0, 0, 0, 0);
  return data > hoje;
});

const dataFormatadaExtenso = computed(() => {
  if (!filtros.value.data) return "";

  const data = converterParaData(filtros.value.data);
  const hoje = new Date();
  hoje.setHours(0, 0, 0, 0);

  const ontem = new Date(hoje);
  ontem.setDate(ontem.getDate() - 1);

  // Verificar se é hoje
  if (data.getTime() === hoje.getTime()) {
    return "Hoje - " + filtros.value.data;
  }

  // Verificar se é ontem
  if (data.getTime() === ontem.getTime()) {
    return "Ontem - " + filtros.value.data;
  }

  // Retornar com dia da semana
  const diasSemana = ["Domingo", "Segunda-feira", "Terça-feira", "Quarta-feira", "Quinta-feira", "Sexta-feira", "Sábado"];
  const diaSemana = diasSemana[data.getDay()];

  return `${diaSemana} - ${filtros.value.data}`;
});

// Options para o calendário - limitar datas futuras
const optionsData = (data) => {
  return data <= date.formatDate(new Date(), "YYYY/MM/DD");
};

// Watchers
watch(dialogVisible, (newVal) => {
  if (newVal) {
    // Definir data padrão (hoje)
    aplicarAtalhoData("hoje");
  }
});

// Methods
function validarData(dataStr) {
  if (!dataStr || dataStr.length !== 10) return false;

  const partes = dataStr.split("/");
  if (partes.length !== 3) return false;

  const dia = parseInt(partes[0]);
  const mes = parseInt(partes[1]);
  const ano = parseInt(partes[2]);

  if (isNaN(dia) || isNaN(mes) || isNaN(ano)) return false;
  if (dia < 1 || dia > 31) return false;
  if (mes < 1 || mes > 12) return false;
  if (ano < 1900 || ano > 2100) return false;

  // Validar se a data é válida
  const dataObj = new Date(ano, mes - 1, dia);
  return dataObj.getDate() === dia && dataObj.getMonth() === mes - 1 && dataObj.getFullYear() === ano;
}

function converterParaData(dataStr) {
  if (!dataStr) return null;
  const partes = dataStr.split("/");
  return new Date(partes[2], partes[1] - 1, partes[0]);
}

function converterParaISO(dataStr) {
  if (!dataStr) return null;
  const partes = dataStr.split("/");
  return `${partes[2]}-${partes[1].padStart(2, "0")}-${partes[0].padStart(2, "0")}`;
}

function aplicarAtalhoData(atalhoId) {
  const hoje = new Date();
  let data;

  switch (atalhoId) {
    case "hoje":
      data = hoje;
      break;

    case "ontem":
      data = date.subtractFromDate(hoje, { days: 1 });
      break;

    case "anteontem":
      data = date.subtractFromDate(hoje, { days: 2 });
      break;

    case "7dias":
      data = date.subtractFromDate(hoje, { days: 7 });
      break;

    case "30dias":
      data = date.subtractFromDate(hoje, { days: 30 });
      break;
  }

  // Formatar a data para o formato brasileiro
  filtros.value.data = date.formatDate(data, "DD/MM/YYYY");
}

async function gerarRelatorio() {
  // Validar formulário
  const valid = await formRef.value.validate();
  if (!valid) return;

  gerando.value = true;

  try {
    // Montar query string
    const params = new URLSearchParams();

    // Adicionar data apenas se selecionada (se não, o backend usará hoje)
    if (filtros.value.data) {
      params.append("data", converterParaISO(filtros.value.data));
    }

    // URL relativa para usar apiRequest e garantir envio do token
    const url = `/api/relatorios/movimento-caixa/pdf?${params.toString()}`;

    // Buscar o PDF com cabeçalho de autorização e abrir em nova aba
    const pdfArrayBuffer = await apiRequest(url, "GET", null, { responseType: "arraybuffer" });
    const blob = new Blob([pdfArrayBuffer], { type: "application/pdf" });
    const blobUrl = window.URL.createObjectURL(blob);
    window.open(blobUrl, "_blank");

    $q.notify({
      type: "positive",
      message: "Relatório sendo gerado...",
      caption: "O download iniciará em instantes",
    });

    // Fechar dialog após pequeno delay
    setTimeout(() => {
      fechar();
    }, 1500);
  } catch (error) {
    console.error("Erro ao gerar relatório:", error);
    $q.notify({
      type: "negative",
      message: "Erro ao gerar relatório",
      caption: error.message || "Tente novamente",
    });
  } finally {
    gerando.value = false;
  }
}

function fechar() {
  // Limpar filtros
  filtros.value = {
    data: null,
  };

  dialogVisible.value = false;
}
</script>

<style scoped>
.bg-gradient {
  background: linear-gradient(135deg, #1976d2 0%, #1565c0 100%);
}
</style>
