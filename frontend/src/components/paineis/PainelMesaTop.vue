<template>
  <div class="dashboard-card-container" style="height: 310px">
    <q-card flat class="full-width dashboard-card">
      <q-card-section class="header-section">
        <div class="header-content">
          <div class="title-wrapper">
            <q-icon name="restaurant" color="primary" size="xs" />
            <h5 class="panel-title q-my-none q-ml-sm">Mesa com maior consumo</h5>
          </div>
        </div>
      </q-card-section>

      <!-- Loading state -->
      <div v-if="isLoading" class="flex justify-center items-center" style="height: 200px">
        <q-spinner color="primary" size="3em" />
        <span class="q-ml-md text-grey">Carregando dados...</span>
      </div>

      <!-- Content -->
      <q-card-section v-else class="content-section">
        <div v-if="!topMesa" class="no-data-message">
          <q-icon name="o_info" size="36px" color="grey-6" />
          <div class="text-grey-7 text-subtitle1 q-mt-md">Sem mesas ativas no momento.</div>
        </div>
        <div v-else class="row items-center q-col-gutter-md">
          <div class="col-12 col-sm-6">
            <div class="text-subtitle1 text-weight-medium">{{ topMesa.mesaRotulo || topMesa.mesaSlug }}</div>
            <div class="q-mt-sm">
              <q-badge color="secondary" class="q-pa-xs q-mr-sm">Total {{ formatCurrency(topMesa.totalMesaCentavos) }}</q-badge>
              <q-badge outline color="orange" class="q-pa-xs">Em aberto {{ formatCurrency(topMesa.devidoCentavos) }}</q-badge>
            </div>
            <div class="text-caption text-grey-7 q-mt-sm">Pessoas: {{ topMesa.pessoas || 0 }}</div>
          </div>
          <div class="col-12 col-sm-6">
            <div class="text-caption text-grey-7 q-mb-xs">Progresso de pagamento</div>
            <q-linear-progress :value="paidPercent" color="positive" track-color="grey-3" size="12px" rounded class="q-mb-xs" />
            <div class="row justify-between text-caption text-grey-7">
              <div>Pago {{ formatCurrency(paidCentavos) }}</div>
              <div>Restante {{ formatCurrency(topMesa.devidoCentavos) }}</div>
            </div>
          </div>
        </div>
      </q-card-section>
    </q-card>
  </div>
</template>

<script setup>
import { toRefs } from 'vue';

const props = defineProps({
  topMesa: { type: Object, default: null },
  paidPercent: { type: Number, default: 0 },
  paidCentavos: { type: Number, default: 0 },
  isLoading: { type: Boolean, default: false },
});

const { topMesa, paidPercent, paidCentavos, isLoading } = toRefs(props);

const formatCurrency = (centavos) => {
  const v = (Number(centavos || 0) / 100);
  try {
    return v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  } catch {
    return `R$ ${v.toFixed(2)}`;
  }
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

.content-section {
  padding: 8px 16px 12px;
}

.no-data-message {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  text-align: center;
  opacity: 0.8;
}

.no-data-message .text-subtitle1 {
  font-weight: 400;
}

/* Textos */
:deep(.text-subtitle1) {
  color: #2A1F1B;
  font-weight: 600;
}

:deep(.text-caption) {
  color: #8B7355;
}

:deep(.text-grey-7) {
  color: #8B7355;
}

/* Badges */
:deep(.q-badge) {
  background-color: #C67C48;
  color: #ffffff;
}

:deep(.q-badge--outline) {
  background-color: transparent;
  color: #E6A157;
  border-color: #E6A157;
}

/* Progress bar */
:deep(.q-linear-progress) {
  background-color: #F5EDE6;
}

:deep(.q-linear-progress__track) {
  background-color: #F5EDE6;
}
</style>
