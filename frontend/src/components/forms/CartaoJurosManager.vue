<template>
  <div class="cartao-juros-manager">
    <!-- Informações do cartão -->
    <div v-if="cartaoInfo" class="q-mb-md">
      <q-banner class="bg-info text-white" rounded>
        <template v-slot:avatar>
          <q-icon name="info" />
        </template>
        <div class="text-subtitle2">
          Configurando juros para até {{ maxParcelas }} parcelas.
          <span v-if="parcelasSemJuros > 0">As primeiras {{ parcelasSemJuros }} parcelas são sem juros.</span>
        </div>
      </q-banner>
    </div>

    <!-- Tabela de juros -->
    <q-table
      flat
      dense
      :rows="jurosRows"
      :columns="columns"
      row-key="numeroParcelas"
      :pagination="{ rowsPerPage: 0 }"
      hide-bottom
      class="juros-table"
    >
      <template v-slot:body="props">
        <q-tr :props="props">
          <!-- Número de parcelas -->
          <q-td key="numeroParcelas" :props="props">
            <div class="text-center">
              <strong>{{ props.row.numeroParcelas }}x</strong>
              <q-chip v-if="props.row.numeroParcelas <= parcelasSemJuros" size="xs" color="positive" text-color="white" dense>Sem juros</q-chip>
            </div>
          </q-td>

          <!-- Percentual de juros -->
          <q-td key="percentualJuros" :props="props">
            <q-input
              :model-value="props.row.percentualJuros"
              type="number"
              dense
              outlined
              suffix="%"
              :min="0"
              :max="100"
              :step="0.01"
              :readonly="props.row.numeroParcelas <= parcelasSemJuros"
              @update:model-value="(value) => updateJuros(props.row.numeroParcelas, value)"
              style="max-width: 120px; margin: 0 auto"
            >
              <template v-slot:append v-if="props.row.percentualJuros > 0">
                <q-icon name="close" @click="updateJuros(props.row.numeroParcelas, 0)" class="cursor-pointer" size="xs" />
              </template>
            </q-input>
          </q-td>

          <!-- Exemplo de cálculo -->
          <q-td key="exemplo" :props="props">
            <div v-if="props.row.percentualJuros > 0" class="text-caption">
              <div>Valor base: {{ formatCurrency(1000) }}</div>
              <div>Com juros: {{ formatCurrency(calcularComJuros(1000, props.row.percentualJuros)) }}</div>
              <div class="text-orange">
                Parcela: {{ formatCurrency(calcularComJuros(1000, props.row.percentualJuros) / props.row.numeroParcelas) }}
              </div>
            </div>
            <div v-else class="text-grey text-center">-</div>
          </q-td>

          <!-- Status -->
          <q-td key="status" :props="props">
            <q-icon
              :name="props.row.percentualJuros > 0 ? 'check_circle' : 'radio_button_unchecked'"
              :color="props.row.percentualJuros > 0 ? 'positive' : 'grey'"
              size="sm"
            />
          </q-td>
        </q-tr>
      </template>
    </q-table>

    <!-- Ações rápidas -->
    <div class="q-mt-md row q-gutter-sm">
      <q-btn outline size="sm" icon="content_copy" label="Aplicar juros progressivo" @click="aplicarJurosProgressivo" />
      <q-btn outline size="sm" icon="clear" label="Limpar todos" @click="limparTodos" />
    </div>

    <!-- Loading -->
    <q-inner-loading :showing="loading">
      <q-spinner-gears size="50px" color="primary" />
    </q-inner-loading>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, inject } from "vue";
import { useQuasar, debounce } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";
import { formatCurrency } from "@/utils/formatters";

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

// Composables
const $q = useQuasar();
const { apiRequest } = useApiRequest();

// Inject do contexto do formulário
const { formData, isEditing } = inject("formContext", {});

// Estado
const loading = ref(false);
const jurosMap = ref({}); // Usar objeto para armazenar juros por número de parcelas

// Computed
const cartaoInfo = computed(() => ({
  id: props.recordId || formData?.value?.id,
  maxParcelas: formData?.value?.parcelaMaxima || props.modelValue?.parcelaMaxima || 12,
  parcelasSemJuros: formData?.value?.parcelasSemJuros || props.modelValue?.parcelasSemJuros || 0,
}));

const maxParcelas = computed(() => cartaoInfo.value.maxParcelas);
const parcelasSemJuros = computed(() => cartaoInfo.value.parcelasSemJuros);

// Gerar linhas para a tabela
const jurosRows = computed(() => {
  const rows = [];

  for (let i = 1; i <= maxParcelas.value; i++) {
    rows.push({
      numeroParcelas: i,
      percentualJuros: jurosMap.value[i] || 0,
    });
  }

  return rows;
});

// Colunas da tabela
const columns = [
  {
    name: "numeroParcelas",
    label: "Parcelas",
    field: "numeroParcelas",
    align: "center",
    style: "width: 150px",
  },
  {
    name: "percentualJuros",
    label: "Juros (%)",
    field: "percentualJuros",
    align: "center",
    style: "width: 150px",
  },
  {
    name: "exemplo",
    label: "Exemplo de cálculo (R$ 1.000,00)",
    field: "exemplo",
    align: "left",
  },
  {
    name: "status",
    label: "Status",
    field: "status",
    align: "center",
    style: "width: 80px",
  },
];

// Métodos
const calcularComJuros = (valor, percentual) => {
  return valor * (1 + percentual / 100);
};

const updateJuros = (numeroParcelas, valor) => {
  // Atualizar no mapa
  jurosMap.value[numeroParcelas] = Number(valor) || 0;

  // Se for parcela sem juros, zerar
  if (numeroParcelas <= parcelasSemJuros.value) {
    jurosMap.value[numeroParcelas] = 0;
  }

  emitirMudanca();
};

const carregarJuros = async () => {
  if (!isEditing.value || !cartaoInfo.value.id) return;

  loading.value = true;
  try {
    const response = await apiRequest(`/api/cartoes/${cartaoInfo.value.id}/juros`, "GET");
    const jurosData = response.data || [];

    // Converter array para mapa
    jurosData.forEach((item) => {
      jurosMap.value[item.numeroParcelas] = item.percentualJuros;
    });
  } catch (error) {
    console.error("Erro ao carregar juros:", error);
    $q.notify({
      type: "negative",
      message: "Erro ao carregar configuração de juros",
      position: "top",
    });
  } finally {
    loading.value = false;
  }
};

// Debounce para evitar múltiplas chamadas
const emitirMudanca = debounce(() => {
  // Converter mapa para array, filtrando apenas valores > 0
  const jurosConfigurados = Object.entries(jurosMap.value)
    .filter(([_, percentual]) => percentual > 0)
    .map(([numeroParcelas, percentualJuros]) => ({
      numeroParcelas: Number(numeroParcelas),
      percentualJuros: Number(percentualJuros),
    }));

  emit("update:modelValue", {
    ...props.modelValue,
    jurosParcelas: jurosConfigurados,
  });
}, 500);

const aplicarJurosProgressivo = () => {
  $q.dialog({
    title: "Aplicar Juros Progressivo",
    message: "Informe o percentual inicial:",
    prompt: {
      model: "1.0",
      type: "number",
      suffix: "%",
    },
    cancel: true,
    persistent: true,
  }).onOk((data) => {
    const percentualInicial = parseFloat(data);
    if (isNaN(percentualInicial) || percentualInicial <= 0) {
      $q.notify({
        type: "negative",
        message: "Percentual inválido",
        position: "top",
      });
      return;
    }

    // Aplicar juros progressivo
    for (let i = 1; i <= maxParcelas.value; i++) {
      if (i > parcelasSemJuros.value) {
        const multiplicador = i - parcelasSemJuros.value;
        jurosMap.value[i] = parseFloat((percentualInicial * multiplicador).toFixed(2));
      }
    }

    emitirMudanca();

    $q.notify({
      type: "positive",
      message: "Juros progressivo aplicado com sucesso",
      position: "top",
    });
  });
};

const limparTodos = () => {
  $q.dialog({
    title: "Confirmar",
    message: "Deseja realmente limpar todos os juros configurados?",
    cancel: true,
    persistent: true,
  }).onOk(() => {
    // Limpar mapa
    jurosMap.value = {};

    emitirMudanca();

    $q.notify({
      type: "positive",
      message: "Juros limpos com sucesso",
      position: "top",
    });
  });
};

// Lifecycle
onMounted(() => {
  // Inicializar com dados do modelValue se existirem
  if (props.modelValue?.jurosParcelas) {
    props.modelValue.jurosParcelas.forEach((item) => {
      jurosMap.value[item.numeroParcelas] = item.percentualJuros;
    });
  }

  carregarJuros();
});

// Watchers
watch(
  () => cartaoInfo.value.id,
  () => {
    carregarJuros();
  }
);

watch(
  () => formData?.value?.parcelaMaxima,
  (newVal) => {
    // Limpar juros acima do novo máximo
    if (newVal) {
      Object.keys(jurosMap.value).forEach((key) => {
        if (Number(key) > newVal) {
          delete jurosMap.value[key];
        }
      });
      emitirMudanca();
    }
  }
);
</script>

<style lang="scss" scoped>
.cartao-juros-manager {
  .juros-table {
    :deep(tbody tr) {
      &:hover {
        background-color: rgba(0, 0, 0, 0.02);
      }
    }

    :deep(.q-field--dense) {
      .q-field__control {
        height: 32px;
      }
    }
  }
}
</style>
