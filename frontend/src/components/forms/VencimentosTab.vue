<template>
  <div class="vencimentos-container">
    <!-- Gerador de Parcelas (apenas para novos registros ou quando não há parcelas) -->
    <div v-if="!isEditMode || parcelas.length === 0" class="q-mb-md">
      <q-card flat bordered>
        <q-card-section>
          <div class="text-subtitle2 q-mb-sm">Gerar Parcelas</div>
          <div class="row q-col-gutter-sm">
            <div class="col-12">
              <q-btn color="primary" label="Gerar Parcelas" icon="calculate" outline @click="gerarParcelas" :disable="!podeGerarParcelas" />
              <q-btn v-if="parcelas.length > 0" color="negative" label="Limpar" icon="clear" outline class="q-ml-sm" @click="limparParcelas" />
            </div>
          </div>
        </q-card-section>
      </q-card>
    </div>

    <!-- Tabela de Parcelas -->
    <div v-if="parcelas.length > 0">
      <q-table flat bordered :rows="parcelas" :columns="columns" row-key="index" :pagination="{ rowsPerPage: 10 }" class="parcelas-table">
        <!-- Coluna Parcela -->
        <template v-slot:body-cell-parcela="props">
          <q-td :props="props">
            {{ props.row.numeroParcela }}
          </q-td>
        </template>

        <!-- Coluna Vencimento -->
        <template v-slot:body-cell-vencimento="props">
          <q-td :props="props">
            <q-input
              v-model="props.row.dataVencimento"
              type="date"
              dense
              borderless
              :rules="[(val) => !!val || 'Obrigatório']"
              @update:model-value="onParcelaChanged"
            />
          </q-td>
        </template>

        <!-- Coluna Valor -->
        <template v-slot:body-cell-valor="props">
          <q-td :props="props">
            <q-input
              v-model.number="props.row.valor"
              type="number"
              dense
              borderless
              prefix="R$"
              :step="0.01"
              :min="0.01"
              :rules="[(val) => val > 0 || 'Valor inválido']"
              @update:model-value="onParcelaChanged"
            />
          </q-td>
        </template>

        <!-- Coluna Data Pagamento -->
        <template v-slot:body-cell-dataPagamento="props">
          <q-td :props="props">
            <q-input
              v-if="!props.row.paga"
              v-model="props.row.dataPagamento"
              type="date"
              dense
              borderless
              placeholder="Não pago"
              @update:model-value="(val) => onDataPagamentoChanged(props.row, val)"
            />
            <div v-else class="text-positive">
              {{ formatDate(props.row.dataPagamento) }}
            </div>
          </q-td>
        </template>

        <!-- Coluna Forma -->
        <template v-slot:body-cell-forma="props">
          <q-td :props="props">
            <q-select
              v-if="props.row.dataPagamento"
              v-model="props.row.formaPagamento"
              :options="formasPagamento"
              dense
              borderless
              emit-value
              map-options
              @update:model-value="onParcelaChanged"
            />
            <span v-else class="text-grey-6">-</span>
          </q-td>
        </template>

        <!-- Status -->
        <template v-slot:body-cell-status="props">
          <q-td :props="props">
            <q-badge :color="getStatusColor(props.row)" :label="getStatusLabel(props.row)" />
          </q-td>
        </template>

        <!-- Ações -->
        <template v-slot:body-cell-acoes="props">
          <q-td :props="props">
            <q-btn
              v-if="props.row.dataPagamento && !props.row.paga"
              size="sm"
              color="positive"
              icon="check"
              flat
              dense
              @click="confirmarPagamento(props.row)"
            >
              <q-tooltip>Confirmar Pagamento</q-tooltip>
            </q-btn>
            <q-btn v-if="props.row.paga" size="sm" color="warning" icon="undo" flat dense @click="desfazerPagamento(props.row)">
              <q-tooltip>Desfazer Pagamento</q-tooltip>
            </q-btn>
          </q-td>
        </template>

        <!-- Rodapé com totais -->
        <template v-slot:bottom>
          <div class="row full-width q-pa-sm">
            <div class="col-4">
              <div class="text-caption text-grey">Total de Parcelas</div>
              <div class="text-weight-bold">{{ parcelas.length }}</div>
            </div>
            <div class="col-4">
              <div class="text-caption text-grey">Valor Total</div>
              <div class="text-weight-bold">{{ formatCurrency(valorTotalParcelas) }}</div>
            </div>
            <div class="col-4">
              <div class="text-caption text-grey">Valor Pago</div>
              <div class="text-weight-bold text-positive">{{ formatCurrency(valorPago) }}</div>
            </div>
          </div>
        </template>
      </q-table>
    </div>

    <!-- Mensagem quando não há parcelas -->
    <div v-else class="text-center q-pa-lg text-grey-6">
      <q-icon name="event_busy" size="48px" />
      <div class="q-mt-sm">Nenhuma parcela gerada</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from "vue";
import { useQuasar } from "quasar";
import { format, addMonths } from "date-fns";
import { ptBR } from "date-fns/locale";
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

// Estado local
const parcelas = ref([]);
const isEditMode = computed(() => !!props.recordId);

// Debug logs
console.log("VencimentosTab montado");
watch(
  () => props.modelValue,
  (newVal) => {
    console.log("VencimentosTab recebeu modelValue:", newVal);
  },
  { deep: true, immediate: true }
);

// Opções
const formasPagamento = [
  { label: "PIX", value: "PIX" },
  { label: "Boleto", value: "BOLETO" },
  { label: "Cartão", value: "CARTAO" },
  { label: "Dinheiro", value: "DINHEIRO" },
  { label: "Transferência", value: "TRANSFERENCIA" },
];

// Colunas da tabela
const columns = [
  {
    name: "parcela",
    label: "Parcela",
    align: "center",
    field: "numeroParcela",
    sortable: true,
    style: "width: 80px",
  },
  {
    name: "vencimento",
    label: "Vencimento",
    align: "left",
    field: (row) => formatDate(row.dataVencimento),
    sortable: true,
    style: "width: 150px",
  },
  {
    name: "valor",
    label: "Valor",
    align: "left",
    field: "valor",
    sortable: true,
    style: "width: 150px",
  },
  {
    name: "dataPagamento",
    label: "Data Pagto.",
    align: "left",
    field: "dataPagamento",
    style: "width: 150px",
  },
  {
    name: "forma",
    label: "Forma",
    align: "left",
    field: "formaPagamento",
    style: "width: 150px",
  },
  {
    name: "status",
    label: "Status",
    align: "center",
    style: "width: 100px",
  },
  {
    name: "acoes",
    label: "Ações",
    align: "center",
    style: "width: 80px",
  },
];

// Computed
const podeGerarParcelas = computed(() => {
  console.log("podeGerarParcelas - valorTotal:", props.modelValue.valorTotal, "numeroParcelas:", props.modelValue.numeroParcelas);
  return props.modelValue.valorTotal > 0 && props.modelValue.numeroParcelas > 0;
});

const valorTotalParcelas = computed(() => {
  return parcelas.value.reduce((sum, p) => sum + (p.valor || 0), 0);
});

const valorPago = computed(() => {
  return parcelas.value.filter((p) => p.paga).reduce((sum, p) => sum + (p.valor || 0), 0);
});

// Métodos
function gerarParcelas() {
  console.log("gerarParcelas chamado");
  if (!podeGerarParcelas.value) {
    console.log("Não pode gerar parcelas - condições não atendidas");
    return;
  }

  const valorTotal = props.modelValue.valorTotal;
  const numParcelas = props.modelValue.numeroParcelas;

  console.log("Gerando parcelas - Total:", valorTotal, "Parcelas:", numParcelas);

  // Calcula valor de cada parcela
  const valorParcela = Math.floor((valorTotal / numParcelas) * 100) / 100;
  const diferenca = valorTotal - valorParcela * numParcelas;

  // Gera as parcelas
  const novasParcelas = [];
  const hoje = new Date();

  for (let i = 0; i < numParcelas; i++) {
    const vencimento = addMonths(hoje, i + 1);

    novasParcelas.push({
      index: i,
      numeroParcela: i + 1,
      valor: i === numParcelas - 1 ? valorParcela + diferenca : valorParcela,
      dataVencimento: format(vencimento, "yyyy-MM-dd"),
      dataPagamento: null,
      formaPagamento: null,
      paga: false,
    });
  }

  parcelas.value = novasParcelas;
  console.log("Parcelas geradas:", parcelas.value);
  emitirMudanca();
}

function limparParcelas() {
  $q.dialog({
    title: "Confirmar",
    message: "Deseja limpar todas as parcelas?",
    cancel: true,
    persistent: true,
  }).onOk(() => {
    parcelas.value = [];
    emitirMudanca();
  });
}

function onParcelaChanged() {
  emitirMudanca();
}

function onDataPagamentoChanged(parcela, valor) {
  if (valor && !parcela.formaPagamento) {
    parcela.formaPagamento = "PIX"; // Default
  }
  emitirMudanca();
}

async function confirmarPagamento(parcela) {
  if (!parcela.dataPagamento || !parcela.formaPagamento) {
    $q.notify({
      type: "warning",
      message: "Informe a data e forma de pagamento",
    });
    return;
  }

  // Validar se a parcela tem ID (necessário para modo de edição)
  if (!parcela.id) {
    $q.notify({
      type: "warning",
      message: "Salve o registro antes de confirmar o pagamento",
    });
    return;
  }

  try {
    console.log("Confirmando pagamento da parcela:", parcela.id);

    // Fazer chamada de API
    await apiRequest(
      `/api/contas-pagar/parcela/${parcela.id}/pagar?dataPagamento=${parcela.dataPagamento}&formaPagamento=${parcela.formaPagamento}`,
      "POST"
    );

    // Só atualiza localmente após sucesso
    parcela.paga = true;
    emitirMudanca();

    $q.notify({
      type: "positive",
      message: "Pagamento confirmado com sucesso",
      position: "top-right",
    });
  } catch (error) {
    console.error("Erro ao confirmar pagamento:", error);
    $q.notify({
      type: "negative",
      message: "Erro ao confirmar pagamento",
      position: "top-right",
    });
  }
}

async function desfazerPagamento(parcela) {
  // Validar se a parcela tem ID
  if (!parcela.id) {
    $q.notify({
      type: "warning",
      message: "Parcela sem identificação",
    });
    return;
  }

  $q.dialog({
    title: "Confirmar",
    message: "Deseja desfazer este pagamento?",
    cancel: true,
    persistent: true,
  }).onOk(async () => {
    try {
      console.log("Desfazendo pagamento da parcela:", parcela.id);

      // Fazer chamada de API
      await apiRequest(`/api/contas-pagar/parcela/${parcela.id}/cancelar-pagamento`, "POST");

      // Só atualiza localmente após sucesso
      parcela.paga = false;
      parcela.dataPagamento = null;
      parcela.formaPagamento = null;
      emitirMudanca();

      $q.notify({
        type: "positive",
        message: "Pagamento cancelado com sucesso",
        position: "top-right",
      });
    } catch (error) {
      console.error("Erro ao cancelar pagamento:", error);
      $q.notify({
        type: "negative",
        message: "Erro ao cancelar pagamento",
        position: "top-right",
      });
    }
  });
}

function getStatusColor(parcela) {
  if (parcela.paga) return "positive";
  if (parcela.dataPagamento) return "warning";

  const hoje = new Date();
  const vencimento = new Date(parcela.dataVencimento);

  if (vencimento < hoje) return "negative";
  return "grey";
}

function getStatusLabel(parcela) {
  if (parcela.paga) return "Pago";
  if (parcela.dataPagamento) return "Aguardando";

  const hoje = new Date();
  const vencimento = new Date(parcela.dataVencimento);

  if (vencimento < hoje) return "Vencido";
  return "Em aberto";
}

function formatDate(date) {
  if (!date) return "-";
  try {
    const dateObj = new Date(date + "T00:00:00");
    return format(dateObj, "dd/MM/yyyy", { locale: ptBR });
  } catch (e) {
    return date;
  }
}

function formatCurrency(value) {
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL",
  }).format(value || 0);
}

// Métodos com debounce para evitar múltiplas chamadas
let updateTimeout = null;

function emitirMudanca() {
  // Limpa timeout anterior se existir
  if (updateTimeout) {
    clearTimeout(updateTimeout);
  }

  // Define novo timeout para evitar múltiplas emissões
  updateTimeout = setTimeout(() => {
    console.log("Emitindo mudança - parcelas:", parcelas.value);
    emit("update:modelValue", {
      ...props.modelValue,
      parcelas: parcelas.value,
    });
  }, 300); // 300ms de debounce
}

// Watchers
watch(
  () => props.modelValue.numeroParcelas,
  (newVal, oldVal) => {
    console.log("numeroParcelas mudou de", oldVal, "para", newVal);
    if (newVal !== oldVal && !isEditMode.value && parcelas.value.length > 0) {
      // Regenera parcelas se mudou o número
      gerarParcelas();
    }
  }
);

watch(
  () => props.modelValue.valorTotal,
  (newVal, oldVal) => {
    console.log("valorTotal mudou de", oldVal, "para", newVal);
    if (newVal !== oldVal && !isEditMode.value && parcelas.value.length > 0) {
      // Regenera parcelas se mudou o valor
      gerarParcelas();
    }
  }
);

// Lifecycle
onMounted(() => {
  console.log("VencimentosTab onMounted - isEditMode:", isEditMode.value);
  // Se em modo de edição e tem parcelas, carrega
  if (isEditMode.value && props.modelValue.parcelas) {
    parcelas.value = props.modelValue.parcelas.map((p, index) => ({
      ...p,
      index,
    }));
  }
});
</script>

<style scoped>
.vencimentos-container {
  padding: 0;
}

.parcelas-table {
  margin-top: 16px;
}

.parcelas-table :deep(.q-table__bottom) {
  background-color: #f5f5f5;
}
</style>
