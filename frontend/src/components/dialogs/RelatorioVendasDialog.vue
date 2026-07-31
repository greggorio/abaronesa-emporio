<template>
  <q-dialog v-model="dialogVisible" persistent>
    <q-card style="width: 640px; max-width: 92vw">
      <q-toolbar class="bg-gradient text-white">
        <q-icon name="o_assessment" size="28px" class="q-mr-sm" />
        <q-toolbar-title>
          <div class="text-h6">Relatório de Vendas</div>
          <div class="text-caption">Gerar relatório em PDF por período</div>
        </q-toolbar-title>
        <q-space />
        <q-btn flat round dense icon="close" @click="fechar" />
      </q-toolbar>

      <q-card-section>
        <q-form @submit.prevent="gerarRelatorio" ref="formRef">
          <div class="row q-col-gutter-md">
            <div class="col-12">
              <div class="text-subtitle1 text-grey-8 q-mb-sm">
                <q-icon name="o_date_range" size="20px" class="q-mr-xs" />
                Período de Vendas
              </div>
            </div>

            <div class="col-12 col-md-6">
              <q-input
                v-model="filtros.dataInicio"
                label="Data inicial"
                filled
                mask="##/##/####"
                placeholder="dd/mm/aaaa"
                :rules="regrasDataInicio"
              >
                <template #prepend>
                  <q-icon name="o_event" />
                </template>
                <template #append>
                  <q-icon name="o_event" class="cursor-pointer">
                    <q-popup-proxy cover transition-show="scale" transition-hide="scale">
                      <q-date v-model="filtros.dataInicio" mask="DD/MM/YYYY" :options="optionsData">
                        <div class="row items-center justify-end">
                          <q-btn v-close-popup label="Fechar" color="primary" flat />
                        </div>
                      </q-date>
                    </q-popup-proxy>
                  </q-icon>
                </template>
              </q-input>
            </div>

            <div class="col-12 col-md-6">
              <q-input
                v-model="filtros.dataFim"
                label="Data final"
                filled
                mask="##/##/####"
                placeholder="dd/mm/aaaa"
                :rules="regrasDataFim"
              >
                <template #prepend>
                  <q-icon name="o_event" />
                </template>
                <template #append>
                  <q-icon name="o_event" class="cursor-pointer">
                    <q-popup-proxy cover transition-show="scale" transition-hide="scale">
                      <q-date v-model="filtros.dataFim" mask="DD/MM/YYYY" :options="optionsData">
                        <div class="row items-center justify-end">
                          <q-btn v-close-popup label="Fechar" color="primary" flat />
                        </div>
                      </q-date>
                    </q-popup-proxy>
                  </q-icon>
                </template>
              </q-input>
            </div>

            <div class="col-12">
              <q-card flat class="bg-blue-1 q-pa-md">
                <div class="row items-center no-wrap">
                  <q-icon name="o_info" size="24px" color="blue-8" class="q-mr-sm" />
                  <div>
                    <div class="text-subtitle2 text-blue-10">Informações do Relatório</div>
                    <div class="text-body2 text-blue-9">
                      • Exibe vendas consolidadas por período
                      <br />
                      • Inclui totais e detalhamento para auditoria
                      <br />
                      • Intervalo máximo permitido de 90 dias
                      <br />
                      • Datas futuras não são permitidas
                    </div>
                  </div>
                </div>
              </q-card>
            </div>

            <div class="col-12">
              <div class="text-caption text-grey-7 q-mb-xs">Atalhos rápidos:</div>
              <div class="q-gutter-sm">
                <q-btn
                  v-for="atalho in atalhosPeriodo"
                  :key="atalho.id"
                  :label="atalho.label"
                  size="sm"
                  dense
                  outline
                  color="primary"
                  @click="aplicarAtalhoPeriodo(atalho.id)"
                />
              </div>
            </div>

            <div class="col-12" v-if="filtros.dataInicio && filtros.dataFim">
              <q-separator class="q-my-md" />
              <div class="text-center">
                <div class="text-caption text-grey-7">Período selecionado:</div>
                <div class="text-h6 text-primary q-mt-xs">{{ previewPeriodo }}</div>
              </div>
            </div>
          </div>
        </q-form>
      </q-card-section>

      <q-card-actions align="right" class="q-px-md q-pb-md">
        <q-btn flat label="Cancelar" color="grey-8" @click="fechar" />
        <q-btn
          unelevated
          label="Gerar Relatório"
          color="primary"
          icon="o_picture_as_pdf"
          @click="gerarRelatorio"
          :loading="gerando"
          :disable="!formularioValido || gerando"
        />
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

const formRef = ref(null);
const gerando = ref(false);
const filtros = ref({
  dataInicio: null,
  dataFim: null,
});

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

const atalhosPeriodo = [
  { id: "hoje", label: "Hoje" },
  { id: "ontem", label: "Ontem" },
  { id: "7dias", label: "Últimos 7 dias" },
  { id: "30dias", label: "Últimos 30 dias" },
];

const regrasDataInicio = [
  (val) => !!val || "Data inicial é obrigatória",
  (val) => validarData(val) || "Data inicial inválida",
  () => !dataInicioFutura.value || "Data inicial não pode ser futura",
  () => periodoOrdenado.value || "Data inicial deve ser menor ou igual à data final",
  () => intervaloDentroLimite.value || "O período máximo é de 90 dias",
];

const regrasDataFim = [
  (val) => !!val || "Data final é obrigatória",
  (val) => validarData(val) || "Data final inválida",
  () => !dataFimFutura.value || "Data final não pode ser futura",
  () => periodoOrdenado.value || "Data final deve ser maior ou igual à data inicial",
  () => intervaloDentroLimite.value || "O período máximo é de 90 dias",
];

const dataInicioFutura = computed(() => {
  if (!validarData(filtros.value.dataInicio)) return false;
  return converterParaData(filtros.value.dataInicio) > hojeSemHorario();
});

const dataFimFutura = computed(() => {
  if (!validarData(filtros.value.dataFim)) return false;
  return converterParaData(filtros.value.dataFim) > hojeSemHorario();
});

const periodoOrdenado = computed(() => {
  if (!validarData(filtros.value.dataInicio) || !validarData(filtros.value.dataFim)) return true;
  return converterParaData(filtros.value.dataInicio) <= converterParaData(filtros.value.dataFim);
});

const intervaloDentroLimite = computed(() => {
  if (!validarData(filtros.value.dataInicio) || !validarData(filtros.value.dataFim)) return true;

  const inicio = converterParaData(filtros.value.dataInicio);
  const fim = converterParaData(filtros.value.dataFim);
  const diffDias = Math.floor((fim.getTime() - inicio.getTime()) / 86400000) + 1;

  return diffDias <= 90;
});

const formularioValido = computed(() => {
  return (
    validarData(filtros.value.dataInicio) &&
    validarData(filtros.value.dataFim) &&
    !dataInicioFutura.value &&
    !dataFimFutura.value &&
    periodoOrdenado.value &&
    intervaloDentroLimite.value
  );
});

const previewPeriodo = computed(() => {
  if (!formularioValido.value) return "";

  const inicio = converterParaData(filtros.value.dataInicio);
  const fim = converterParaData(filtros.value.dataFim);
  const dias = Math.floor((fim.getTime() - inicio.getTime()) / 86400000) + 1;

  if (filtros.value.dataInicio === filtros.value.dataFim) {
    return `1 dia (${filtros.value.dataInicio})`;
  }

  return `${filtros.value.dataInicio} até ${filtros.value.dataFim} (${dias} dias)`;
});

const optionsData = (dataString) => {
  return dataString <= date.formatDate(new Date(), "YYYY/MM/DD");
};

watch(dialogVisible, (novoValor) => {
  if (novoValor) {
    aplicarAtalhoPeriodo("hoje");
  }
});

function hojeSemHorario() {
  const hoje = new Date();
  hoje.setHours(0, 0, 0, 0);
  return hoje;
}

function validarData(dataStr) {
  if (!dataStr || dataStr.length !== 10) return false;

  const partes = dataStr.split("/");
  if (partes.length !== 3) return false;

  const dia = parseInt(partes[0], 10);
  const mes = parseInt(partes[1], 10);
  const ano = parseInt(partes[2], 10);

  if (Number.isNaN(dia) || Number.isNaN(mes) || Number.isNaN(ano)) return false;
  if (dia < 1 || dia > 31) return false;
  if (mes < 1 || mes > 12) return false;
  if (ano < 1900 || ano > 2100) return false;

  const dataObj = new Date(ano, mes - 1, dia);
  dataObj.setHours(0, 0, 0, 0);

  return dataObj.getDate() === dia && dataObj.getMonth() === mes - 1 && dataObj.getFullYear() === ano;
}

function converterParaData(dataStr) {
  const [dia, mes, ano] = dataStr.split("/").map((valor) => parseInt(valor, 10));
  const dataObj = new Date(ano, mes - 1, dia);
  dataObj.setHours(0, 0, 0, 0);
  return dataObj;
}

function converterParaISO(dataStr) {
  const [dia, mes, ano] = dataStr.split("/");
  return `${ano}-${mes.padStart(2, "0")}-${dia.padStart(2, "0")}`;
}

function aplicarAtalhoPeriodo(atalhoId) {
  const hoje = hojeSemHorario();
  let inicio = new Date(hoje);
  let fim = new Date(hoje);

  if (atalhoId === "ontem") {
    inicio = date.subtractFromDate(hoje, { days: 1 });
    fim = date.subtractFromDate(hoje, { days: 1 });
  }

  if (atalhoId === "7dias") {
    inicio = date.subtractFromDate(hoje, { days: 6 });
    fim = hoje;
  }

  if (atalhoId === "30dias") {
    inicio = date.subtractFromDate(hoje, { days: 29 });
    fim = hoje;
  }

  filtros.value.dataInicio = date.formatDate(inicio, "DD/MM/YYYY");
  filtros.value.dataFim = date.formatDate(fim, "DD/MM/YYYY");
}

async function gerarRelatorio() {
  if (!formularioValido.value) {
    await formRef.value?.validate();
    return;
  }

  gerando.value = true;

  try {
    const params = new URLSearchParams({
      dataInicio: converterParaISO(filtros.value.dataInicio),
      dataFim: converterParaISO(filtros.value.dataFim),
    });

    const url = `/api/relatorios/vendas/pdf?${params.toString()}`;
    const pdfArrayBuffer = await apiRequest(url, "GET", null, { responseType: "arraybuffer" });

    const blob = new Blob([pdfArrayBuffer], { type: "application/pdf" });
    const blobUrl = window.URL.createObjectURL(blob);
    window.open(blobUrl, "_blank");

    $q.notify({
      type: "positive",
      message: "Relatório sendo gerado...",
      caption: "O download iniciará em instantes",
    });

    setTimeout(() => {
      fechar();
    }, 1500);
  } catch (error) {
    console.error("Erro ao gerar relatório de vendas:", error);
    $q.notify({
      type: "negative",
      message: "Erro ao gerar relatório",
      caption: error?.message || "Tente novamente",
    });
  } finally {
    gerando.value = false;
  }
}

function fechar() {
  filtros.value = {
    dataInicio: null,
    dataFim: null,
  };

  dialogVisible.value = false;
}
</script>

<style scoped>
.bg-gradient {
  background: linear-gradient(135deg, #1976d2 0%, #1565c0 100%);
}
</style>
