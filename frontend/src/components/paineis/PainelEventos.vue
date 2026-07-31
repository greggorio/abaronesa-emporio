<template>
  <div class="dashboard-card-container" style="height: 310px">
    <q-card flat class="full-width dashboard-card">
      <q-card-section class="header-section">
        <div class="header-content">
          <div class="title-wrapper">
            <q-icon name="event" color="primary" size="xs" />
            <h5 class="panel-title q-my-none q-ml-sm">Faturamento por Evento</h5>
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

      <q-card-section v-else-if="props.mensagemErro" class="error-section">
        <q-banner dense rounded class="error-banner">
          <template #avatar>
            <q-icon name="warning" color="warning" class="q-mr-sm" />
          </template>
          {{ props.mensagemErro }}
        </q-banner>
      </q-card-section>

      <q-card-section v-else class="metrics-section">
        <div class="row q-col-gutter-md">
          <!-- Total Faturado no Evento Atual -->
          <div class="col-4 metric-column">
            <div class="metric-card couver-card">
              <div class="metric-content">
                <div class="metric-label">Couvert Evento Atual</div>
                <div class="metric-value">{{ formatarMoeda(props.dadosEventos.totalCouverAtual) }}</div>
              </div>
            </div>
          </div>

          <!-- Total Faturado no Período -->
          <div class="col-4 metric-column">
            <div class="metric-card total-30d-card">
              <div class="metric-content">
                <div class="metric-label">Total Período</div>
                <div class="metric-value">{{ formatarMoeda(props.dadosEventos.totalFaturamento30d) }}</div>
              </div>
            </div>
          </div>

          <!-- Média de Faturamento por Evento -->
          <div class="col-4 metric-column">
            <div class="metric-card media-card">
              <div class="metric-content">
                <div class="metric-label">Média por Evento</div>
                <div class="metric-value">{{ formatarMoeda(props.dadosEventos.mediaFaturamento) }}</div>
              </div>
            </div>
          </div>
        </div>
      </q-card-section>

      <q-card-section v-if="!isLoading && !props.mensagemErro" class="top-events-section">
        <div class="top-events-title">Top 3 Eventos ({{ eventos.length }} eventos - {{ periodo === 'hoje' ? 'Hoje' : periodo === '7d' ? '7 dias' : '30 dias' }})</div>
        <div v-for="(evento, index) in eventosVisiveis" :key="index" class="event-row">
          <div class="event-row-line">
            <span class="event-name" :title="evento.nome">{{ evento.nome }}</span>
            <span class="event-total">{{ formatarMoeda(evento.total) }}</span>
            <div class="progress-indicator" role="progressbar" :aria-label="`Progresso ${evento.nome}`">
              <div
                class="progress-indicator-fill"
                :style="{ width: evento.progressWidth + '%' }"
              ></div>
              <span class="progress-indicator-label">{{ evento.progressWidth }}%</span>
            </div>
          </div>
        </div>
      </q-card-section>

      <q-card-section
        v-if="!isLoading && !props.mensagemErro && eventosRestantes > 0"
        class="action-section"
      >
        <div class="text-center full-width action-row">
          <q-btn
            :label="`Ver Mais (+${eventosRestantes})`"
            color="primary"
            flat
            dense
            @click="onVerDetalhesClick"
          />
        </div>
      </q-card-section>
    </q-card>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useFormatarMoeda } from '@/composables/formatarMoeda';

const props = defineProps({
  dadosEventos: {
    type: Object,
    default: () => ({}),
  },
  isLoading: {
    type: Boolean,
    default: false,
  },
  periodo: {
    type: String,
    default: '30d',
  },
  mensagemErro: {
    type: String,
    default: '',
  },
});

const emit = defineEmits(['update:periodo', 'ver-detalhes']);

const periodoSelecionado = ref(props.periodo);

const { formatarMoeda } = useFormatarMoeda();

const eventosBrutos = computed(() => props.dadosEventos.eventosLista || []);
const totalFaturamento = computed(() => Number(props.dadosEventos.totalFaturamento30d) || 0);

const eventos = computed(() => {
  const total = totalFaturamento.value;
  return eventosBrutos.value.map((evento) => {
    const totalEvento = Number(evento.total) || 0;
    const progressWidth = total > 0 ? Math.min(100, Math.round((totalEvento / total) * 100)) : 0;
    return { ...evento, progressWidth };
  });
});

const eventosVisiveis = computed(() => eventos.value.slice(0, 3));
const eventosRestantes = computed(() => Math.max(0, eventos.value.length - eventosVisiveis.value.length));

const onVerDetalhesClick = () => {
  emit('ver-detalhes');
};
</script>

<style scoped>
.dashboard-card {
  background-color: #FBF6F2;
  height: 100%;
}

.header-section {
  background: linear-gradient(to right, #FBF6F2, #ffffff);
  border-bottom: 1px solid #D7B899;
  padding: 12px 16px 0;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title-wrapper {
  display: flex;
  align-items: center;
}

.panel-title {
  color: #2A1F1B;
}

.metrics-section {
  padding: 8px 12px;
}

.metric-column {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.metric-card {
  background: white;
  border-radius: 8px;
  padding: 12px;
  text-align: center;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  width: 100%;
  min-height: 80px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.metric-content {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.metric-label {
  font-size: 0.8em;
  color: #6B3E26;
  margin-bottom: 4px;
}

.metric-value {
  color: #2A1F1B;
  font-weight: 700;
  font-size: 1.1em;
}

.couver-card {
  border-left: 4px solid #B5854C;
}

.total-30d-card {
  border-left: 4px solid #8B7355;
}

.media-card {
  border-left: 4px solid #6B3E26;
}

.top-events-section {
  padding: 8px 16px 8px;
}

.top-events-title {
  text-align: center;
  font-size: 0.9em;
  color: #2A1F1B;
  margin-bottom: 12px;
  font-weight: 600;
}

.event-row {
  margin-bottom: 10px;
}

.event-row-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.event-name {
  font-weight: 700;
  color: #2A1F1B;
  min-width: 80px;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.event-total {
  color: #6B3E26;
  font-weight: 700;
  white-space: nowrap;
  margin-left: auto;
  text-align: right;
  flex-shrink: 0;
}

.progress-indicator {
  position: relative;
  background-color: #F5EDE6;
  border-radius: 100px;
  height: 14px;
  overflow: hidden;
  min-width: 60px;
  width: 80px;
  flex: 0 0 auto;
}

.progress-indicator-fill {
  height: 100%;
  background-color: #B5854C;
  border-radius: 100px;
}

.progress-indicator-label {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.65rem;
  font-weight: 600;
  color: #ffffff;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.35);
}

.action-section {
  padding: 4px 16px 10px;
  margin-top: auto;
}

.action-section .q-btn {
  color: #6B3E26;
}

.action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.error-section {
  padding: 12px 16px;
}

.error-banner {
  background-color: #fff4e6;
  color: #8a4f1f;
  border: 1px solid #f5c16a;
  border-radius: 8px;
  font-weight: 600;
  box-shadow: none;
  display: flex;
  align-items: center;
  justify-content: center;
}

.error-banner .q-icon {
  font-size: 1.15rem;
  margin-right: 8px;
}
</style>
