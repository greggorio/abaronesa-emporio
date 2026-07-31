<template>
  <div class="dashboard-card-container" style="height: 310px">
    <q-card flat class="full-width dashboard-card">
      <q-card-section class="header-section">
        <div class="header-content">
          <div class="title-wrapper">
            <q-icon name="sell" color="primary" size="xs" />
            <h5 class="panel-title q-my-none q-ml-sm">Produtos em Promoção</h5>
          </div>
          <q-btn-toggle
            v-model="periodoSelecionado"
            dense
            size="sm"
            toggle-color="primary"
            :options="[
              { label: 'Hoje', value: 'hoje' },
              { label: '7D', value: '7d' }
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
          <!-- Produtos em Promoção -->
          <div class="col-4 metric-column">
            <div class="metric-card produtos-promo-card">
              <div class="metric-content">
                <div class="metric-label">Produtos em Promo</div>
                <div class="metric-value">{{ dadosPromocao.produtosEmPromocao }}</div>
              </div>
            </div>
          </div>

          <!-- Desconto Médio -->
          <div class="col-4 metric-column">
            <div class="metric-card desconto-card">
              <div class="metric-content">
                <div class="metric-label">Desconto Médio</div>
                <div class="metric-value">{{ dadosPromocao.descontoMedio }}%</div>
              </div>
            </div>
          </div>

          <!-- Impacto em Vendas -->
          <div class="col-4 metric-column">
            <div class="metric-card impacto-card">
              <div class="metric-content">
                <div class="metric-label">Impacto em Vendas</div>
                <div class="metric-value">{{ formatarMoeda(dadosPromocao.impactoVendas) }}</div>
              </div>
            </div>
          </div>
        </div>
      </q-card-section>

      <q-card-section class="progress-section">
        <div class="progress-label">Vendas no período</div>
        <div class="progress-container">
        <div
          v-for="(segment, index) in filteredVendasSegments"
          :key="index"
          :style="{
            width: segment.percentual + '%',
            backgroundColor: segment.color,
          }"
          class="progress-segment"
        >
          <span class="segment-label">{{ segment.percentualExibicao }}%</span>
        </div>
        </div>
        <div class="progress-legend">
          <div class="legend-item">
            <div class="legend-color-box" :style="{ backgroundColor: dadosPromocao.vendasPromocaoColor || '#B5854C' }"></div>
            <span>Promoção</span>
          </div>
          <span class="legend-separator">|</span>
          <div class="legend-item">
            <div class="legend-color-box" :style="{ backgroundColor: dadosPromocao.vendasNormaisColor || '#8B7355' }"></div>
            <span>Normal</span>
          </div>
        </div>
      </q-card-section>

      <q-card-section class="product-list-section">
        <div v-for="(produto, index) in produtosVisiveis" :key="index" class="product-row">
          <div class="product-row-line">
            <span class="product-name" :title="produto.nome">{{ produto.nome }}</span>
            <div class="price-info-inline">
              <span class="original-price">{{ formatarMoeda(produto.precoOriginal) }}</span>
              <span class="arrow">→</span>
              <span class="discounted-price">{{ formatarMoeda(produto.precoComDesconto) }}</span>
              <span class="discount-tag">{{ Math.abs(produto.desconto) }}%</span>
            </div>
            <div class="progress-indicator" role="progressbar" :aria-label="`Progresso ${produto.nome}`">
              <div
                class="progress-indicator-fill"
                :style="{ width: produto.progressWidth + '%' }"
              ></div>
              <span class="progress-indicator-label">{{ produto.progressWidth }}%</span>
            </div>
            <span class="product-total">{{ formatarMoeda(produto.total) }}</span>
          </div>
        </div>
      </q-card-section>

      <q-card-section v-if="produtosRestantes > 0" class="action-section">
        <div class="text-center full-width action-row">
          <q-btn
            :label="`Ver Mais (+${produtosRestantes})`"
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
  dadosPromocao: {
    type: Object,
    default: () => ({}),
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

const emit = defineEmits(['update:periodo', 'ver-detalhes']);

const periodoSelecionado = ref(props.periodo);

const { formatarMoeda } = useFormatarMoeda();

const vendasSegments = computed(() => {
  if (!props.dadosPromocao) return [];

  return [
    {
      percentual: props.dadosPromocao.vendasPromocao,
      percentualExibicao: props.dadosPromocao.vendasPromocao,
      key: 'Promoção',
      color: '#B5854C', // Bronze Dourado (da paleta do sistema)
      valor: props.dadosPromocao.vendasPromocao || 0,
    },
    {
      percentual: props.dadosPromocao.vendasNormais,
      percentualExibicao: props.dadosPromocao.vendasNormais,
      key: 'Normal',
      color: '#8B7355', // Marrom Avermelhado (da paleta do sistema)
      valor: props.dadosPromocao.vendasNormais || 0,
    },
  ];
});

// Filtrar segmentos com percentual > 0
const filteredVendasSegments = computed(() =>
  vendasSegments.value.filter((segment) => segment.percentual > 0)
);

const produtosEmPromocao = computed(() => props.dadosPromocao.produtosEmPromocaoLista || []);
const produtosVisiveis = computed(() => produtosEmPromocao.value.slice(0, 2));
const produtosRestantes = computed(() => Math.max(0, produtosEmPromocao.value.length - produtosVisiveis.value.length));

const onVerDetalhesClick = () => {
  emit('ver-detalhes');
};
</script>

<style scoped>
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

.progress-label {
  text-align: center;
  font-size: 0.8em;
  color: #2A1F1B;
  margin-bottom: 4px;
}

.progress-container {
  background-color: #F5EDE6;
  border-radius: 999px;
  overflow: hidden;
  height: 26px;
  display: flex;
}

.progress-segment {
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.01em;
}

.segment-label {
  color: #ffffff;
  text-shadow: 0 1px 1px rgba(0, 0, 0, 0.4);
}

.progress-legend {
  display: flex;
  justify-content: space-between;
  font-size: 0.75em;
  color: #2A1F1B;
  margin-top: 4px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.legend-color-box {
  width: 12px;
  height: 12px;
  border-radius: 2px;
}

.progress-legend .legend-item span:last-child {
  font-weight: 600;
}

.legend-separator {
  width: 20px;
  text-align: center;
}

.product-list-section {
  padding: 0 16px 8px;
}

.product-row {
  margin-bottom: 10px;
}

.product-row-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.product-name {
  font-weight: 700;
  color: #2A1F1B;
  min-width: 80px;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-total {
  color: #6B3E26;
  font-weight: 700;
  white-space: nowrap;
  margin-left: auto;
  text-align: right;
  flex-shrink: 0;
}

.price-info-inline {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: nowrap;
  font-size: 0.8rem;
}

.original-price {
  text-decoration: line-through;
  color: #9E9E9E;
  font-size: 0.75rem;
}

.arrow {
  color: #6B3E26;
}

.discounted-price {
  color: #4CAF50;
  font-weight: 600;
}

.discount-tag {
  background-color: rgba(255, 255, 255, 0.8);
  color: #B5854C;
  border-radius: 6px;
  padding: 0 6px;
  font-size: 0.75rem;
  font-weight: 600;
  white-space: nowrap;
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

.product-row-note {
  font-size: 0.75rem;
  color: #6B3E26;
  font-weight: 600;
  padding-top: 4px;
}

.action-section {
  padding: 4px 16px 10px;
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

.inline-note {
  padding-top: 0;
}
</style>
