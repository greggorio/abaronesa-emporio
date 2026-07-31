<template>
  <div class="dashboard-card-container" style="height: 310px">
    <q-card flat class="full-width dashboard-card">
      <q-card-section class="header-section">
        <div class="header-content">
          <div class="title-wrapper">
            <q-icon name="trending_up" color="primary" size="xs" />
            <h5 class="panel-title q-my-none q-ml-sm">Vendas no período</h5>
          </div>
          <q-btn-toggle
            v-model="periodoSelecionado"
            dense
            size="sm"
            toggle-color="primary"
            :options="[
              { label: 'Hoje', value: 'hoje' },
              { label: '7D', value: '7d' },
              { label: '30D', value: '30d' }
            ]"
            @update:model-value="$emit('update:periodo', periodoSelecionado)"
          />
        </div>
      </q-card-section>

      <div v-if="isLoading" class="flex justify-center items-center" style="height: 180px">
        <q-spinner color="primary" size="3em" />
        <span class="q-ml-md text-grey">Carregando dados...</span>
      </div>

      <q-card-section v-else class="metrics-section">
        <div class="row q-col-gutter-md">
          <!-- Faturamento -->
          <div class="col-4 metric-column">
            <div class="metric-card faturamento-card">
              <div class="metric-content">
                <div class="metric-label">Faturamento</div>
                <div class="metric-value">{{ formatarMoeda(vendas.faturamento) }}</div>
              </div>
            </div>
          </div>

          <!-- Quantidade de Vendas -->
          <div class="col-4 metric-column">
            <div class="metric-card vendas-card">
              <div class="metric-content">
                <div class="metric-label">Vendas</div>
                <div class="metric-value">{{ vendas.quantidade_vendas }}</div>
              </div>
            </div>
          </div>

          <!-- Ticket Médio -->
          <div class="col-4 metric-column">
            <div class="metric-card ticket-card">
              <div class="metric-content">
                <div class="metric-label">Ticket Médio</div>
                <div class="metric-value">{{ formatarMoeda(vendas.ticket_medio) }}</div>
              </div>
            </div>
          </div>
        </div>
      </q-card-section>

      <q-card-section class="progress-section">
        <div class="progress-container">
          <div
            v-for="(segment, index) in filteredPaymentSegments"
            :key="index"
            :style="{
              width: segment.percentual + '%',
              backgroundColor: segment.color,
            }"
            class="progress-segment"
          >
            <span v-if="segment.percentualExibicao > 5">{{ segment.percentualExibicao }}%</span>
          </div>
        </div>
      </q-card-section>

      <q-card-section class="payment-types-section">
        <div class="row q-col-gutter-sm">
          <div
            class="payment-type-col"
            :class="getPaymentColClass(filteredPaymentTypes.length)"
            v-for="type in filteredPaymentTypes"
            :key="type.key"
          >
            <div class="payment-type-wrapper">
              <div class="payment-type-content">
                <div class="payment-type-label">
                  <div class="indicator-line" :style="{ backgroundColor: type.color }"></div>
                  {{ type.key }}
                  <span class="percentage">({{ type.percentualExibicao }}%)</span>
                </div>
                <div class="payment-type-value">{{ formatarMoeda(type.valor) }}</div>
              </div>
            </div>
          </div>
        </div>
      </q-card-section>
    </q-card>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useFormatarMoeda } from '@/composables/formatarMoeda';

const props = defineProps({
  vendas: {
    type: Object,
    default: () => ({
      faturamento: 0,
      quantidade_vendas: 0,
      ticket_medio: 0,
      dinheiro: 0,
      pix: 0,
      cartaoCredito: 0,
      cartaoDebito: 0,
      voucher: 0,
    }),
  },
  isLoading: {
    type: Boolean,
    default: false,
  },
  periodo: {
    type: String,
    default: '7d',
  },
});

const emit = defineEmits(['update:periodo']);

const periodoSelecionado = ref(props.periodo);

const { formatarMoeda } = useFormatarMoeda();

function calcularPercentualPreciso(valor) {
  if (!valor || totalVendas.value === 0) return 0;
  return (Number(valor) / totalVendas.value) * 100;
}

function formatInt(value) {
  const parsed = parseFloat(value).toFixed(1);
  return isNaN(parsed) ? 0 : parsed;
}

// Calcular o total de vendas para determinar percentuais
const totalVendas = computed(() => {
  if (!props.vendas) return 0;
  const { dinheiro = 0, pix = 0, cartaoCredito = 0, cartaoDebito = 0, voucher = 0 } = props.vendas;
  return Number(dinheiro) + Number(pix) + Number(cartaoCredito) + Number(cartaoDebito) + Number(voucher);
});

// Função para calcular percentual
function calcularPercentual(valor) {
  if (!valor || totalVendas.value === 0) return 0;
  return Math.round((Number(valor) / totalVendas.value) * 100);
}

// Função para determinar a classe da coluna baseada no número de itens
function getPaymentColClass(itemCount) {
  if (itemCount <= 2) return 'col-6 col-2-items';
  if (itemCount === 3) return 'col-4 col-3-items';
  if (itemCount === 4) return 'col-6 col-4-items';
  if (itemCount === 5) return 'col-4 col-5-items';
  return 'col-4 col-6-items';
}

const paymentSegments = computed(() => {
  if (!props.vendas) return [];

  return [
    {
      percentual: calcularPercentualPreciso(props.vendas.dinheiro),
      percentualExibicao: calcularPercentual(props.vendas.dinheiro),
      key: 'Dinheiro',
      color: '#6B3E26', // Café Espresso
      valor: props.vendas.dinheiro || 0,
    },
    {
      percentual: calcularPercentualPreciso(props.vendas.pix),
      percentualExibicao: calcularPercentual(props.vendas.pix),
      key: 'PIX',
      color: '#C67C48', // Caramelo
      valor: props.vendas.pix || 0,
    },
    {
      percentual: calcularPercentualPreciso(props.vendas.cartaoCredito),
      percentualExibicao: calcularPercentual(props.vendas.cartaoCredito),
      key: 'Crédito',
      color: '#B5854C', // Bronze Dourado
      valor: props.vendas.cartaoCredito || 0,
    },
    {
      percentual: calcularPercentualPreciso(props.vendas.cartaoDebito),
      percentualExibicao: calcularPercentual(props.vendas.cartaoDebito),
      key: 'Débito',
      color: '#9C6F4F', // slightly darker brown
      valor: props.vendas.cartaoDebito || 0,
    },
    {
      percentual: calcularPercentualPreciso(props.vendas.voucher),
      percentualExibicao: calcularPercentual(props.vendas.voucher),
      key: 'Voucher',
      color: '#8B7355', // darker brown
      valor: props.vendas.voucher || 0,
    },
  ];
});

// Filtrar segmentos com percentual > 0
const filteredPaymentSegments = computed(() =>
  paymentSegments.value.filter((segment) => segment.percentual > 0)
);

// Filtrar tipos de pagamento com valor > 0
const filteredPaymentTypes = computed(() =>
  paymentSegments.value.filter((segment) => Number(segment.valor) > 0)
);
</script>

<style scoped>
/* Customizações específicas do painel */
.dashboard-card {
  background-color: #FBF6F2;
}

.header-section {
  background: linear-gradient(to right, #FBF6F2, #ffffff);
  border-bottom: 1px solid #D7B899;
  padding: 12px 16px 0;
}

.panel-title {
  color: #2A1F1B;
}

.metrics-section {
  padding: 8px 12px;
}

.metric-value {
  color: #2A1F1B;
}

.progress-section {
  padding: 0 16px 8px;
}

.progress-container {
  background-color: #F5EDE6;
}

.payment-types-section {
  padding: 8px 16px 12px;
}

.payment-type-wrapper:hover {
  background-color: #FBF6F2;
}

.payment-type-label {
  color: #2A1F1B;
}

.payment-type-value {
  color: #2A1F1B;
}
</style>
