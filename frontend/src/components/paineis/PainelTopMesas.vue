<template>
  <div class="dashboard-card-container" style="height: 310px">
    <q-card flat class="full-width dashboard-card">
      <q-card-section class="header-section">
        <div class="header-content">
          <div class="title-wrapper">
            <q-icon name="leaderboard" color="primary" size="xs" />
            <h5 class="panel-title q-my-none q-ml-sm">Top 5 Mesas</h5>
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
        <div v-if="!topList || topList.length === 0" class="no-data-message">
          <q-icon name="o_info" size="36px" color="grey-6" />
          <div class="text-grey-7 text-subtitle1 q-mt-md">Sem mesas ativas no momento.</div>
        </div>
        <div v-else class="column q-gutter-sm">
          <div
            v-for="(m, idx) in topList"
            :key="m.mesaSlug + '-' + idx"
            class="row items-center no-wrap q-col-gutter-sm"
          >
            <div class="col-auto">
              <q-badge color="grey-7" outline class="q-px-sm">{{ idx + 1 }}</q-badge>
            </div>
            <div class="col">
              <div class="row items-center q-col-gutter-sm">
                <div class="col-7">
                  <div class="text-body2 text-weight-medium">{{ m.mesaRotulo || m.mesaSlug }}</div>
                  <div class="mini-bar">
                    <div class="mini-bar__fill" :style="{ width: computeBarWidth(m) }"></div>
                  </div>
                </div>
                <div class="col-5 text-right">
                  <div class="text-body2 text-weight-bold">{{ formatCurrency(m.totalMesaCentavos) }}</div>
                  <div class="text-caption text-orange-8">
                    Devido {{ formatCurrency(m.devidoCentavos) }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </q-card-section>
    </q-card>
  </div>
</template>

<script setup>
import { toRefs, computed } from 'vue';

const props = defineProps({
  topList: { type: Array, default: () => [] },
  isLoading: { type: Boolean, default: false },
});

const { topList, isLoading } = toRefs(props);

const maxTotal = computed(() => {
  const values = (topList.value || []).map((m) => Number(m.totalMesaCentavos || 0));
  const max = Math.max(1, ...values);
  return max;
});

const computeBarWidth = (m) => {
  const v = Number(m.pagoCentavos || 0);
  const percent = Math.round((v / maxTotal.value) * 100);
  return `${percent}%`;
};

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
:deep(.text-body2) {
  color: #2A1F1B;
}

:deep(.text-caption) {
  color: #8B7355;
}

:deep(.text-grey-7) {
  color: #8B7355;
}

:deep(.text-orange-8) {
  color: #C67C48;
}

/* Barra de progresso */
.mini-bar {
  position: relative;
  height: 10px;
  width: 100%;
  background: #F5EDE6;
  border-radius: 999px;
  overflow: hidden;
}

.mini-bar__fill {
  height: 100%;
  background: linear-gradient(90deg, #6B3E26 0%, #C67C48 100%);
}

/* Badge */
:deep(.q-badge) {
  color: #2A1F1B;
  border-color: #D7B899;
}
</style>
