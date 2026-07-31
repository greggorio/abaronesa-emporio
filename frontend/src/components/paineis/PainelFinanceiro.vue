<template>
  <div class="dashboard-card-container" style="height: 310px">
    <q-card flat class="full-width dashboard-card">
      <q-card-section class="header-section">
        <div class="header-content">
          <div class="title-wrapper">
            <q-icon name="o_payments" color="primary" size="xs" />
            <h5 class="panel-title q-my-none q-ml-sm">Financeiro</h5>
          </div>
        </div>
      </q-card-section>

      <div v-if="isLoading" class="flex justify-center items-center" style="height: 200px">
        <q-spinner color="primary" size="3em" />
        <span class="q-ml-md text-grey">Carregando dados...</span>
      </div>

      <q-card-section v-else class="financial-content-section">
        <!-- Progresso -->
        <div class="progress-section">
          <div class="progress-group">
            <div class="financial-item-header">
              <span class="progress-label">Recebimentos</span>
            </div>
            <div class="progress-bars">
              <div class="bar-wrapper">
                <div class="bar-label">Total</div>
                <div class="bar-value">{{ formatarMoeda(financeiroData.recebimentos) }}</div>
                <q-linear-progress :value="1" color="primary" size="8px" track-color="grey-3" />
              </div>
              <div class="bar-wrapper">
                <div class="bar-label">Recebido</div>
                <div class="bar-value">{{ formatarMoeda(financeiroData.total_recebido) }}</div>
                <q-linear-progress
                  :value="getProgressValue(financeiroData.total_recebido, 'recebido')"
                  color="secondary"
                  size="8px"
                  track-color="grey-3"
                />
              </div>
            </div>
          </div>

          <div class="progress-group">
            <div class="financial-item-header">
              <span class="progress-label">Pagamentos</span>
            </div>
            <div class="progress-bars">
              <div class="bar-wrapper">
                <div class="bar-label">Total</div>
                <div class="bar-value">{{ formatarMoeda(financeiroData.pagamentos) }}</div>
                <q-linear-progress :value="1" color="positive" size="8px" track-color="grey-3" />
              </div>
              <div class="bar-wrapper">
                <div class="bar-label">Pago</div>
                <div class="bar-value">{{ formatarMoeda(financeiroData.total_pago) }}</div>
                <q-linear-progress
                  :value="getProgressValue(financeiroData.total_pago, 'pago')"
                  color="accent"
                  size="8px"
                  track-color="grey-3"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- Pendências -->
        <div class="pendencies-section">
          <div class="section-title">Pendências</div>
          <div class="pendencies-grid">
            <div class="pendency-item">
              <div class="pendency-info">
                <span class="pendency-label">A receber</span>
                <span class="pendency-value">{{
                  formatarMoeda(financeiroData.pendencias_recebimento)
                }}</span>
              </div>
              <q-linear-progress
                :value="getPendingPercentage(financeiroData.pendencias_recebimento)"
                color="warning"
                size="6px"
                track-color="grey-3"
              />
            </div>
            <div class="pendency-item">
              <div class="pendency-info">
                <span class="pendency-label">A pagar</span>
                <span class="pendency-value">{{
                  formatarMoeda(financeiroData.pendencias_pagamento)
                }}</span>
              </div>
              <q-linear-progress
                :value="getPendingPercentage(financeiroData.pendencias_pagamento)"
                color="secondary"
                size="6px"
                track-color="grey-3"
              />
            </div>
          </div>
        </div>
      </q-card-section>
    </q-card>
  </div>
</template>

<script setup>
import { useFormatarMoeda } from '@/composables/formatarMoeda';
import { computed } from 'vue';

const props = defineProps({
  financeiro: {
    type: Object,
    default: () => null,
  },
  isLoading: {
    type: Boolean,
    default: false,
  },
});

// Objeto financeiro seguro com valores padrão
const financeiroData = computed(() => {
  return (
    props.financeiro || {
      recebimentos: 0,
      total_recebido: 0,
      pagamentos: 0,
      total_pago: 0,
      pendencias_pagamento: 0,
      pendencias_recebimento: 0,
    }
  );
});

const { formatarMoeda } = useFormatarMoeda();

// Valor relativo para barras de progresso
const getProgressValue = (value, type) => {
  const safeValue = Number(value) || 0;

  if (type === 'pago') {
    const totalPayments = Number(financeiroData.value.pagamentos) || 1;
    return safeValue / totalPayments;
  } else if (type === 'recebido') {
    const totalReceipts = Number(financeiroData.value.recebimentos) || 1;
    return safeValue / totalReceipts;
  }

  return safeValue;
};

// Cálculo do total de pendências de forma segura
const totalPendencias = computed(() => {
  const pagamento = Number(financeiroData.value.pendencias_pagamento) || 0;
  const recebimento = Number(financeiroData.value.pendencias_recebimento) || 0;
  return pagamento + recebimento || 1;
});

// Percentual para pendências
const getPendingPercentage = (value) => {
  const safeValue = Number(value) || 0;
  return safeValue / totalPendencias.value;
};
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

.financial-content-section {
  padding: 8px 16px 12px;
}

/* Progresso */
.progress-section {
  margin-bottom: 14px;
}

.progress-group {
  margin-bottom: 10px;
}

.financial-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.progress-label {
  font-size: 0.8rem;
  font-weight: 500;
  color: #2A1F1B;
}

.progress-bars {
  background-color: rgba(107, 62, 38, 0.04);
  border-radius: 6px;
  padding: 8px;
}

.bar-wrapper {
  display: flex;
  align-items: center;
  margin-bottom: 6px;
}

.bar-wrapper:last-child {
  margin-bottom: 0;
}

.bar-label {
  width: 60px;
  font-size: 0.75rem;
  color: #8B7355;
}

.bar-value {
  width: 80px;
  font-size: 0.75rem;
  font-weight: 500;
  color: #2A1F1B;
  text-align: right;
  padding-right: 8px;
}

/* Pendências */
.pendencies-section {
  margin-top: 12px;
}

.section-title {
  font-size: 0.8rem;
  font-weight: 500;
  color: #8B7355;
  margin-bottom: 8px;
}

.pendencies-grid {
  display: flex;
  gap: 10px;
}

.pendency-item {
  flex: 1;
  background-color: rgba(107, 62, 38, 0.04);
  border-radius: 6px;
  padding: 8px;
  transition: all 0.2s ease;
}

.pendency-item:hover {
  background-color: rgba(107, 62, 38, 0.08);
  transform: translateY(-1px);
}

.pendency-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}

.pendency-label {
  font-size: 0.75rem;
  color: #8B7355;
}

.pendency-value {
  font-size: 0.8rem;
  font-weight: 600;
  color: #2A1F1B;
}
</style>
