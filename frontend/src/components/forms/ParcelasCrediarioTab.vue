<template>
  <div class="parcelas-crediario-container">
    <!-- Resumo do Crediário -->
    <div class="q-mb-md">
      <q-card flat bordered>
        <q-card-section>
          <div class="row q-col-gutter-md">
            <div class="col-12 col-md-3">
              <div class="text-caption text-grey">Total do Crediário</div>
              <div class="text-h6 text-weight-bold">{{ formatCurrency(totalCrediario) }}</div>
            </div>
            <div class="col-12 col-md-3">
              <div class="text-caption text-grey">Total Pago</div>
              <div class="text-h6 text-weight-bold text-positive">{{ formatCurrency(totalPago) }}</div>
            </div>
            <div class="col-12 col-md-3">
              <div class="text-caption text-grey">Saldo Devedor</div>
              <div class="text-h6 text-weight-bold text-negative">{{ formatCurrency(saldoDevedor) }}</div>
            </div>
            <div class="col-12 col-md-3">
              <div class="text-caption text-grey">Status</div>
              <div class="text-h6">
                <q-badge :color="statusColor" :label="statusLabel" />
              </div>
            </div>
          </div>
        </q-card-section>
      </q-card>
    </div>

    <!-- Tabela de Parcelas -->
    <div v-if="parcelas.length > 0">
      <q-table flat bordered :rows="parcelas" :columns="columns" row-key="id" :pagination="{ rowsPerPage: 10 }" class="parcelas-table">
        <!-- Coluna Parcela -->
        <template v-slot:body-cell-numeroParcela="props">
          <q-td :props="props">{{ props.row.numeroParcela }}/{{ totalParcelas }}</q-td>
        </template>

        <!-- Coluna Vencimento -->
        <template v-slot:body-cell-dataVencimento="props">
          <q-td :props="props">
            <q-input
              v-model="props.row.dataVencimento"
              type="date"
              dense
              borderless
              :disable="props.row.status === 'PAGA'"
              @update:model-value="onParcelaChanged(props.row)"
            />
          </q-td>
        </template>

        <!-- Coluna Valor -->
        <template v-slot:body-cell-valorParcela="props">
          <q-td :props="props">
            {{ formatCurrency(props.row.valorParcela) }}
          </q-td>
        </template>

        <!-- Coluna Data Pagamento -->
        <template v-slot:body-cell-dataPagamento="props">
          <q-td :props="props">
            <q-input
              v-if="props.row.status !== 'PAGA'"
              v-model="props.row.dataPagamento"
              type="date"
              dense
              borderless
              placeholder="Não recebido"
              @update:model-value="(val) => onDataPagamentoChanged(props.row, val)"
            />
            <div v-else class="text-positive">
              {{ formatDate(props.row.dataPagamento) }}
            </div>
          </q-td>
        </template>

        <!-- Coluna Forma Pagamento -->
        <template v-slot:body-cell-formaPagamento="props">
          <q-td :props="props">
            <q-select
              v-if="props.row.dataPagamento && props.row.status !== 'PAGA'"
              v-model="props.row.formaPagamento"
              :options="formasPagamento"
              dense
              borderless
              emit-value
              map-options
              @update:model-value="onParcelaChanged(props.row)"
            />
            <span v-else-if="props.row.status === 'PAGA' && props.row.formaPagamento" class="text-positive">
              {{ getFormaPagamentoLabel(props.row.formaPagamento) }}
            </span>
            <span v-else class="text-grey-6">-</span>
          </q-td>
        </template>

        <!-- Coluna Valor Pago -->
        <template v-slot:body-cell-valorPago="props">
          <q-td :props="props">
            <div v-if="props.row.status === 'PAGA'">
              <div class="text-positive text-weight-bold">
                {{ formatCurrency(props.row.valorPago) }}
              </div>
              <div v-if="props.row.valorJuros > 0" class="text-caption text-orange">Juros: {{ formatCurrency(props.row.valorJuros) }}</div>
              <div v-if="props.row.valorMulta > 0" class="text-caption text-red">Multa: {{ formatCurrency(props.row.valorMulta) }}</div>
            </div>
            <span v-else class="text-grey-6">-</span>
          </q-td>
        </template>

        <!-- Status -->
        <template v-slot:body-cell-status="props">
          <q-td :props="props">
            <q-badge :color="getStatusColor(props.row)" :label="getStatusLabel(props.row)">
              <q-tooltip v-if="props.row.diasAtraso > 0">{{ props.row.diasAtraso }} dias de atraso</q-tooltip>
            </q-badge>
          </q-td>
        </template>

        <!-- Ações -->
        <template v-slot:body-cell-acoes="props">
          <q-td :props="props">
            <q-btn
              v-if="props.row.dataPagamento && props.row.formaPagamento && props.row.status !== 'PAGA'"
              size="sm"
              color="positive"
              icon="check"
              flat
              dense
              @click="confirmarPagamento(props.row)"
              :loading="props.row.salvando"
            >
              <q-tooltip>Confirmar Pagamento</q-tooltip>
            </q-btn>
            <q-btn v-if="props.row.status === 'PAGA'" size="sm" color="warning" icon="undo" flat dense @click="desfazerPagamento(props.row)">
              <q-tooltip>Desfazer Pagamento</q-tooltip>
            </q-btn>
          </q-td>
        </template>
      </q-table>
    </div>

    <!-- Mensagem quando não há parcelas -->
    <div v-else class="text-center q-pa-lg text-grey-6">
      <q-icon name="event_busy" size="48px" />
      <div class="q-mt-sm">Nenhuma parcela de crediário encontrada</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from "vue";
import { useQuasar } from "quasar";
import { format } from "date-fns";
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

const emit = defineEmits(["update:modelValue", "pagamento-registrado"]);

const $q = useQuasar();
const { apiRequest } = useApiRequest();

// Estado local
const parcelas = ref([]);

// Opções
const formasPagamento = [
  { label: "PIX", value: "PIX" },
  { label: "Dinheiro", value: "DINHEIRO" },
  { label: "Cartão", value: "CARTAO" },
  { label: "Transferência", value: "TRANSFERENCIA" },
  { label: "Boleto", value: "BOLETO" },
];

// Colunas da tabela
const columns = [
  {
    name: "numeroParcela",
    label: "Parcela",
    align: "center",
    field: "numeroParcela",
    sortable: true,
    style: "width: 80px",
  },
  {
    name: "dataVencimento",
    label: "Vencimento",
    align: "left",
    field: "dataVencimento",
    sortable: true,
    style: "width: 120px",
  },
  {
    name: "valorParcela",
    label: "Valor",
    align: "left",
    field: "valorParcela",
    sortable: true,
    style: "width: 120px",
  },
  {
    name: "dataPagamento",
    label: "Data Pagamento",
    align: "left",
    field: "dataPagamento",
    style: "width: 140px",
  },
  {
    name: "formaPagamento",
    label: "Forma",
    align: "left",
    field: "formaPagamento",
    style: "width: 140px",
  },
  {
    name: "valorPago",
    label: "Valor Pago",
    align: "left",
    field: "valorPago",
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
const totalParcelas = computed(() => parcelas.value.length);

const totalCrediario = computed(() => {
  return parcelas.value.reduce((sum, p) => sum + (p.valorParcela || 0), 0);
});

const totalPago = computed(() => {
  return parcelas.value.filter((p) => p.status === "PAGA").reduce((sum, p) => sum + (p.valorPago || 0), 0);
});

const saldoDevedor = computed(() => totalCrediario.value - totalPago.value);

const statusColor = computed(() => {
  if (saldoDevedor.value === 0) return "positive";
  if (parcelas.value.some((p) => p.vencida && p.status !== "PAGA")) return "negative";
  return "warning";
});

const statusLabel = computed(() => {
  if (saldoDevedor.value === 0) return "Quitado";
  if (parcelas.value.some((p) => p.vencida && p.status !== "PAGA")) return "Com Atraso";
  return "Em Dia";
});

// Métodos
async function carregarParcelas() {
  if (!props.recordId) return;

  try {
    const response = await apiRequest(`/api/pedidos/${props.recordId}/parcelas-crediario`);

    if (response.success && response.data) {
      parcelas.value = response.data.map((p) => ({
        ...p,
        salvando: false,
        formaPagamento: p.formaPagamento || null,
      }));
    }
  } catch (error) {
    console.error("Erro ao carregar parcelas:", error);
    $q.notify({
      type: "negative",
      message: "Erro ao carregar parcelas do crediário",
    });
  }
}

function onParcelaChanged(parcela) {
  // Apenas marca que houve mudança
  emit("update:modelValue", { ...props.modelValue });
}

function onDataPagamentoChanged(parcela, valor) {
  if (valor && !parcela.formaPagamento) {
    parcela.formaPagamento = "PIX"; // Default
  }
  onParcelaChanged(parcela);
}

async function confirmarPagamento(parcela) {
  if (!parcela.dataPagamento || !parcela.formaPagamento) {
    $q.notify({
      type: "warning",
      message: "Informe a data e forma de pagamento",
    });
    return;
  }

  parcela.salvando = true;

  try {
    const response = await apiRequest(`/api/pedidos/parcelas-crediario/${parcela.id}/pagar`, "POST", {
      dataPagamento: parcela.dataPagamento,
    });

    if (response.success) {
      $q.notify({
        type: "positive",
        message: "Pagamento confirmado com sucesso!",
      });

      // Recarregar parcelas
      await carregarParcelas();

      // Emitir evento
      emit("pagamento-registrado", {
        parcelaId: parcela.id,
      });
    }
  } catch (error) {
    console.error("Erro ao confirmar pagamento:", error);
    $q.notify({
      type: "negative",
      message: error.message || "Erro ao confirmar pagamento",
    });
  } finally {
    parcela.salvando = false;
  }
}

function desfazerPagamento(parcela) {
  $q.dialog({
    title: "Confirmar",
    message: "Deseja desfazer este pagamento?",
    cancel: true,
    persistent: true,
  }).onOk(() => {
    $q.dialog({
      title: "Motivo",
      message: "Informe o motivo para desfazer o pagamento:",
      prompt: {
        model: "",
        type: "text",
      },
      cancel: true,
      persistent: true,
    }).onOk(async (motivo) => {
      if (!motivo || motivo.trim() === "") {
        $q.notify({
          type: "warning",
          message: "Motivo é obrigatório",
        });
        return;
      }

      try {
        const response = await apiRequest(`/api/pedidos/parcelas-crediario/${parcela.id}/cancelar-pagamento`, "POST", {
          motivo: motivo,
        });

        if (response.success) {
          $q.notify({
            type: "positive",
            message: "Pagamento desfeito com sucesso!",
          });

          // Recarregar parcelas
          await carregarParcelas();
        }
      } catch (error) {
        console.error("Erro ao desfazer pagamento:", error);
        $q.notify({
          type: "negative",
          message: error.message || "Erro ao desfazer pagamento",
        });
      }
    });
  });
}

function getStatusColor(parcela) {
  if (parcela.status === "PAGA") return "positive";
  if (parcela.vencida) return "negative";
  return "grey";
}

function getStatusLabel(parcela) {
  switch (parcela.status) {
    case "PAGA":
      return "Paga";
    case "PENDENTE":
      return parcela.vencida ? "Vencida" : "Pendente";
    default:
      return parcela.status;
  }
}

function getFormaPagamentoLabel(value) {
  const forma = formasPagamento.find((f) => f.value === value);
  return forma ? forma.label : value;
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

// Lifecycle
onMounted(() => {
  carregarParcelas();
});

// Watchers
watch(
  () => props.recordId,
  (newId) => {
    if (newId) {
      carregarParcelas();
    }
  }
);
</script>

<style scoped>
.parcelas-crediario-container {
  padding: 0;
}

.parcelas-table {
  margin-top: 16px;
}

.parcelas-table :deep(.q-table__bottom) {
  background-color: #f5f5f5;
}

.parcelas-table :deep(thead th) {
  font-weight: 600;
  font-size: 12px;
}

.parcelas-table :deep(tbody td) {
  font-size: 13px;
}
</style>
