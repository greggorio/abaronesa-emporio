<template>
  <q-dialog v-model="dialogVisible" persistent>
    <q-card style="width: 640px; max-width: 92vw">
      <q-toolbar class="bg-gradient text-white">
        <q-icon name="o_assessment" size="28px" class="q-mr-sm" />
        <q-toolbar-title>
          <div class="text-h6">Relatório de Vendas por Produto</div>
          <div class="text-caption">Gerar relatório em PDF por período e produto</div>
        </q-toolbar-title>
        <q-space />
        <q-btn flat round dense icon="close" @click="fechar" />
      </q-toolbar>

      <q-card-section>
        <q-form @submit.prevent="gerarRelatorio" ref="formRef">
          <div class="row q-col-gutter-md">
            <div class="col-12">
              <div class="text-subtitle1 text-primary q-mb-sm">
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
              <div class="text-subtitle1 text-primary q-mb-sm">
                <q-icon name="o_inventory_2" size="20px" class="q-mr-xs" />
                Produto (Opcional)
              </div>
              <q-select
                v-model="filtros.produto"
                use-input
                filled
                label="Buscar produto"
                :options="produtoOptions"
                option-label="label"
                option-value="id"
                @filter="filterProdutos"
                @filter-abort="abortFilterProdutos"
                clearable
                :loading="loadingProdutos"
                input-debounce="300"
                hint="Digite o nome ou código do produto"
              >
                <template #no-option>
                  <q-item>
                    <q-item-section class="text-grey">Nenhum produto encontrado</q-item-section>
                  </q-item>
                </template>
              </q-select>
            </div>

            <div class="col-12">
              <q-item tag="label" v-ripple class="toggle-item rounded-borders">
                <q-item-section>
                  <q-item-label>Relatório Detalhado</q-item-label>
                  <q-item-label caption>Listar cada venda individualmente</q-item-label>
                </q-item-section>
                <q-item-section side>
                  <q-toggle v-model="filtros.detalhado" color="primary" />
                </q-item-section>
              </q-item>
            </div>

            <div class="col-12">
              <q-card flat class="info-card q-pa-md">
                <div class="row items-center no-wrap">
                  <q-icon name="o_info" size="24px" color="primary" class="q-mr-sm" />
                  <div>
                    <div class="text-subtitle2 text-primary">Informações do Relatório</div>
                    <div class="text-body2 text-info">
                      • Resumido: vendas consolidadas por produto
                      <br />
                      • Detalhado: cada venda individualmente
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
          label="Gerar PDF"
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
const loadingProdutos = ref(false);
const filtros = ref({
  dataInicio: null,
  dataFim: null,
  produto: null,
  detalhado: false,
});
const produtoOptions = ref([]);

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

async function filterProdutos(val, update, abort) {
  if (val.length < 2) {
    abort();
    return;
  }

  loadingProdutos.value = true;
  try {
    const data = await apiRequest(`/api/produtos/lookup/search?search=${encodeURIComponent(val)}`);
    update(() => {
      produtoOptions.value = (data || []).map((p) => ({
        label: p.descricao || p.nome,
        id: p.id,
      }));
    });
  } catch (e) {
    console.error(e);
    update(() => {
      produtoOptions.value = [];
    });
  } finally {
    loadingProdutos.value = false;
  }
}

function abortFilterProdutos(abortFn) {
  abortFn();
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
      detalhado: filtros.value.detalhado,
    });

    if (filtros.value.produto?.id) {
      params.append("produtoId", filtros.value.produto.id);
    }

    const url = `/api/relatorios/vendas-produtos/pdf?${params.toString()}`;
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
    console.error("Erro ao gerar relatório de vendas por produto:", error);
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
    produto: null,
    detalhado: false,
  };
  produtoOptions.value = [];

  dialogVisible.value = false;
}
</script>

<style scoped>
.bg-gradient {
  background: linear-gradient(135deg, #6B3E26 0%, #C67C48 100%);
}

.toggle-item {
  background: rgba(107, 62, 38, 0.08);
}

.info-card {
  background: rgba(139, 115, 85, 0.08);
}
</style>
