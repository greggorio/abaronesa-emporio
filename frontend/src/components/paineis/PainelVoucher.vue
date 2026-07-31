<template>
  <div class="dashboard-card-container" style="height: 320px">
    <q-card flat class="full-width dashboard-card">
      <q-card-section class="header-section">
        <div class="header-content">
          <div class="title-wrapper">
            <q-icon name="o_wallet" color="primary" size="xs" />
            <h5 class="panel-title q-my-none q-ml-sm">Consumo de Voucher (Mês)</h5>
          </div>
          <div class="controls-wrapper">
            <q-btn-toggle
              v-model="ordenacao"
              dense
              size="sm"
              toggle-color="primary"
              :options="[
                { label: '% Usado', value: 'percent' },
                { label: 'Excedente', value: 'excedente' }
              ]"
            />
          </div>
        </div>
        <q-scroll-area
          :thumb-style="thumbStyle"
          class="summary-scroll q-mt-sm"
        >
          <div class="summary-row">
            <div class="summary-chip">
              <div class="summary-label">Consumido</div>
              <div class="summary-value">{{ formatarMoeda(totalConsumido) }}</div>
            </div>
            <div class="summary-chip">
              <div class="summary-label">Voucher total</div>
              <div class="summary-value">{{ formatarMoeda(totalVoucher) }}</div>
            </div>
            <div class="summary-chip">
              <div class="summary-label">Excedente</div>
              <div class="summary-value text-negative">{{ formatarMoeda(totalExcedente) }}</div>
            </div>
          </div>
        </q-scroll-area>
      </q-card-section>

      <div v-if="isLoading" class="flex justify-center items-center" style="height: 220px">
        <q-spinner color="primary" size="3em" />
        <span class="q-ml-md text-grey">Carregando dados...</span>
      </div>

      <q-card-section v-else class="list-section">
        <template v-if="ordenados.length">
          <q-scroll-area :thumb-style="thumbStyle" style="height: 170px">
            <div v-for="item in ordenados" :key="item.usuarioId || item.nome" class="voucher-item">
              <div class="item-header">
                <div class="item-name">
                  {{ item.nome || 'Sem nome' }}
                  <span v-if="item.voucherVr" class="item-sub">Voucher {{ formatarMoeda(item.voucherVr) }}</span>
                  <span v-else class="item-sub text-grey-7">Sem voucher definido</span>
                </div>
                <div class="item-values">
                  <span class="text-weight-bold">{{ formatarMoeda(item.totalConsumido) }}</span>
                  <q-badge
                    v-if="item.excedente && greaterThanZero(item.excedente)"
                    color="negative"
                    text-color="white"
                    class="q-ml-sm"
                  >
                    +{{ formatarMoeda(item.excedente) }}
                  </q-badge>
                </div>
              </div>
              <q-linear-progress
                :value="progressValue(item)"
                :color="progressColor(item)"
                track-color="#f0e5db"
                size="12px"
                class="item-progress"
              />
            </div>
          </q-scroll-area>
        </template>
        <div v-else class="no-data-message">
          <q-icon name="o_info" size="36px" color="grey-6" />
          <div class="text-grey-7 text-subtitle1 q-mt-md">Sem dados para exibir.</div>
        </div>
      </q-card-section>
    </q-card>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useFormatarMoeda } from '@/composables/formatarMoeda';

const props = defineProps({
  data: {
    type: Array,
    default: () => [],
  },
  isLoading: {
    type: Boolean,
    default: false,
  }
});

const ordenacao = ref('percent');
const { formatarMoeda } = useFormatarMoeda();

const thumbStyle = {
  right: '4px',
  width: '5px',
  backgroundColor: '#C67C48',
  opacity: 0.75,
  borderRadius: '4px',
};

const totalConsumido = computed(() =>
  props.data.reduce((acc, item) => acc + Number(item.totalConsumido || 0), 0)
);
const totalVoucher = computed(() =>
  props.data.reduce((acc, item) => acc + Number(item.voucherVr || 0), 0)
);
const totalExcedente = computed(() =>
  props.data.reduce((acc, item) => acc + Number(item.excedente || 0), 0)
);

const ordenados = computed(() => {
  const list = [...(props.data || [])];
  if (ordenacao.value === 'excedente') {
    return list.sort((a, b) => Number(b.excedente || 0) - Number(a.excedente || 0));
  }
  return list.sort((a, b) => progressValue(b) - progressValue(a));
});

const greaterThanZero = (val) => Number(val || 0) > 0;

const progressValue = (item) => {
  if (item.voucherVr && Number(item.voucherVr) > 0) {
    return Math.min(Number(item.totalConsumido || 0) / Number(item.voucherVr || 1), 1.2); // permite passar um pouco para mostrar excedente
  }
  const maxConsumido = Math.max(...props.data.map(d => Number(d.totalConsumido || 0)), 1);
  return Number(item.totalConsumido || 0) / maxConsumido;
};

const percentText = (item) => {
  if (item.voucherVr && Number(item.voucherVr) > 0) {
    const pct = (Number(item.totalConsumido || 0) / Number(item.voucherVr)) * 100;
    return `${pct.toFixed(0)}%`;
  }
  return '';
};

const progressColor = (item) => {
  if (item.voucherVr && Number(item.voucherVr) > 0) {
    const ratio = Number(item.totalConsumido || 0) / Number(item.voucherVr);
    if (ratio > 1) return 'negative';
    if (ratio > 0.8) return 'warning';
    return 'secondary';
  }
  return 'info';
};
</script>

<style scoped>
.dashboard-card {
  background-color: #FBF6F2;
}

.header-section {
  background: linear-gradient(to right, #FBF6F2, #ffffff);
  border-bottom: 1px solid #D7B899;
  padding: 12px 16px 8px;
}

.panel-title {
  color: #2A1F1B;
}

.controls-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.summary-row {
  display: inline-flex;
  gap: 12px;
  min-width: 480px;
  width: max-content;
}

.summary-chip {
  background: #fff;
  border: 1px solid rgba(107, 62, 38, 0.12);
  border-radius: 10px;
  padding: 8px 12px;
  min-width: 150px;
  flex: 1 0 150px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.summary-scroll {
  height: 72px;
  max-width: 100%;
  border-radius: 8px;
}

.summary-label {
  font-size: 0.78rem;
  color: #8B7355;
}

.summary-value {
  font-weight: 700;
  color: #2A1F1B;
  font-size: 0.95rem;
}

.list-section {
  padding: 8px 16px 12px;
}

.voucher-item {
  margin-bottom: 14px;
  padding: 8px 0;
  border-bottom: 1px solid rgba(107, 62, 38, 0.08);
}

.voucher-item:last-child {
  border-bottom: none;
  margin-bottom: 0;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 6px;
  gap: 8px;
}

.item-name {
  font-size: 0.88rem;
  font-weight: 600;
  color: #2A1F1B;
  line-height: 1.2;
}

.item-sub {
  display: block;
  font-size: 0.75rem;
  color: #8B7355;
  font-weight: 400;
}

.item-values {
  display: flex;
  align-items: center;
  font-size: 0.9rem;
  color: #2A1F1B;
}

.item-progress {
  border-radius: 6px;
  overflow: hidden;
}

</style>
