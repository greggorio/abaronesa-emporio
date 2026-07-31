<template>
  <div class="dashboard-card-container" style="height: 310px">
    <q-card flat class="full-width dashboard-card">
      <q-card-section class="header-section">
        <div class="header-content">
          <div class="title-wrapper">
            <q-icon name="account_balance_wallet" color="primary" size="xs" />
            <h5 class="panel-title q-my-none q-ml-sm">Movimento de Caixa</h5>
          </div>
        </div>
      </q-card-section>

      <div v-if="isLoading" class="flex justify-center items-center" style="height: 200px">
        <q-spinner color="primary" size="3em" />
        <span class="q-ml-md text-grey">Carregando dados...</span>
      </div>

      <q-card-section v-else class="metrics-section">
        <div class="row q-col-gutter-md">
          <!-- Entradas -->
          <div class="col-4 metric-column">
            <div class="metric-card entrada-card">
              <div class="metric-icon">
                <q-icon name="arrow_downward" size="xs" />
              </div>
              <div class="metric-content">
                <div class="metric-label">Entradas</div>
                <div class="metric-value">{{ formatarMoeda(movimentoCaixa?.entradas || 0) }}</div>
              </div>
            </div>
          </div>

          <!-- Saídas -->
          <div class="col-4 metric-column">
            <div class="metric-card saida-card">
              <div class="metric-icon">
                <q-icon name="arrow_upward" size="xs" />
              </div>
              <div class="metric-content">
                <div class="metric-label">Saídas</div>
                <div class="metric-value">{{ formatarMoeda(movimentoCaixa?.saidas || 0) }}</div>
              </div>
            </div>
          </div>

          <!-- Saldo -->
          <div class="col-4 metric-column">
            <div class="metric-card saldo-card">
              <div class="metric-icon">
                <q-icon
                  :name="(movimentoCaixa?.saldo || 0) >= 0 ? 'trending_up' : 'trending_down'"
                  size="xs"
                />
              </div>
              <div class="metric-content">
                <div class="metric-label">Saldo</div>
                <div class="metric-value">{{ formatarMoeda(movimentoCaixa?.saldo || 0) }}</div>
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
            <span v-if="segment.percentual > 10" class="segment-percentage">{{ segment.percentual }}%</span>
          </div>
        </div>
      </q-card-section>

      <q-card-section class="payment-types-section">
        <div class="payment-types-grid">
          <div class="payment-type-item" v-for="type in filteredPaymentTypes" :key="type.key">
            <div class="payment-type-wrapper">
              <div class="payment-type-content">
                <div class="payment-type-label">
                  <div class="indicator-line" :style="{ backgroundColor: type.color }"></div>
                  {{ type.label }}
                  <span class="percentage">({{ type.percentual }}%)</span>
                </div>
                <div class="payment-type-value">{{ formatarMoeda(type.value) }}</div>
              </div>
            </div>
          </div>
        </div>
      </q-card-section>
    </q-card>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useFormatarMoeda } from '@/composables/formatarMoeda';

const props = defineProps({
  movimentoCaixa: {
    type: Object,
    default: () => ({
      entradas: 0,
      saidas: 0,
      saldo: 0,
      detalhesPorTipo: {},
    }),
  },
  isLoading: {
    type: Boolean,
    default: false,
  },
});

const { formatarMoeda } = useFormatarMoeda();

// Mapear os valores do detalhesPorTipo
const dinheiro = computed(() => props.movimentoCaixa?.detalhesPorTipo?.DINHEIRO || 0);
const pix = computed(() => props.movimentoCaixa?.detalhesPorTipo?.PIX || 0);
const credito = computed(() => props.movimentoCaixa?.detalhesPorTipo?.CARTAO_CREDITO || 0);
const debito = computed(() => props.movimentoCaixa?.detalhesPorTipo?.CARTAO_DEBITO || 0);
const voucher = computed(() => props.movimentoCaixa?.detalhesPorTipo?.VOUCHER || 0);

const totalMovimento = computed(() => {
  if (!props.movimentoCaixa?.detalhesPorTipo) return 0;
  return Object.values(props.movimentoCaixa.detalhesPorTipo).reduce(
    (sum, value) => sum + Number(value),
    0
  );
});

const paymentSegments = computed(() => {
  const total = totalMovimento.value;
  if (total === 0) return [];

  const segments = [];

  if (dinheiro.value > 0) {
    segments.push({
      percentual: Math.round((Number(dinheiro.value) / total) * 100),
      key: 'dinheiro',
      label: 'Dinheiro',
      value: dinheiro.value,
      color: '#6B3E26', // Espresso
    });
  }

  if (pix.value > 0) {
    segments.push({
      percentual: Math.round((Number(pix.value) / total) * 100),
      key: 'pix',
      label: 'Pix',
      value: pix.value,
      color: '#C67C48', // Caramelo
    });
  }

  if (credito.value > 0) {
    segments.push({
      percentual: Math.round((Number(credito.value) / total) * 100),
      key: 'credito',
      label: 'Crédito',
      value: credito.value,
      color: '#B5854C', // Bronze Dourado
    });
  }

  if (debito.value > 0) {
    segments.push({
      percentual: Math.round((Number(debito.value) / total) * 100),
      key: 'debito',
      label: 'Débito',
      value: debito.value,
      color: '#D7B899', // Latte
    });
  }

  if (voucher.value > 0) {
    segments.push({
      percentual: Math.round((Number(voucher.value) / total) * 100),
      key: 'voucher',
      label: 'Voucher',
      value: voucher.value,
      color: '#8B5A2B', // Marrom médio
    });
  }

  return segments;
});

const filteredPaymentSegments = computed(() => paymentSegments.value);
const filteredPaymentTypes = computed(() => paymentSegments.value);
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

.metric-card {
  padding: 10px 12px;
  background-color: #faf8f6;
  gap: 8px;
}

/* Cards com cores específicas */
.entrada-card {
  border-bottom-color: #6B3E26;
}

.saida-card {
  border-bottom-color: #D65A31;
}

.saldo-card {
  border-bottom-color: #B5854C;
}

/* Ícones personalizados */
.metric-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  flex-shrink: 0;
}

.entrada-card .metric-icon {
  background-color: #f5f1ed;
  color: #6B3E26;
}

.saida-card .metric-icon {
  background-color: #fef0ed;
  color: #D65A31;
}

.saldo-card .metric-icon {
  background-color: #f5ede6;
  color: #B5854C;
}

.metric-value {
  color: #2A1F1B;
}

.progress-section {
  padding: 0 16px 8px;
}

.progress-container {
  background-color: #F5EDE6;
  box-shadow: inset 0 1px 3px rgba(42, 31, 27, 0.08);
}

.segment-percentage {
  font-size: 0.65rem;
  font-weight: 600;
  color: white;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}

.payment-types-section {
  padding: 8px 16px 12px;
}

.payment-types-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.payment-type-item {
  background: transparent;
  border-radius: 8px;
  transition: all 0.2s ease;
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
