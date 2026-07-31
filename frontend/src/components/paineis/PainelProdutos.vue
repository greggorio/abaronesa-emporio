<template>
  <div class="dashboard-card-container" style="height: 310px">
    <q-card flat class="full-width dashboard-card">
      <q-card-section class="header-section">
        <div class="header-content">
          <div class="title-wrapper">
            <q-icon name="o_inventory" color="primary" size="xs" />
            <h5 class="panel-title q-my-none q-ml-sm">Desempenho por Produto</h5>
          </div>
          <div class="controls-wrapper">
            <q-btn-toggle
              v-model="periodoSelecionado"
              dense
              size="sm"
              toggle-color="primary"
              :options="[
                { label: 'Hoje', value: 'hoje' },
                { label: '7d', value: '7d' },
                { label: '30d', value: '30d' }
              ]"
              @update:model-value="$emit('update:periodo', periodoSelecionado)"
              class="q-mr-sm"
            />
            <q-btn-toggle
              v-model="ordenacao"
              dense
              size="sm"
              toggle-color="primary"
              :options="[
                { label: 'Valor', value: 'valor' },
                { label: 'Qtd', value: 'quantidade' }
              ]"
              @update:model-value="$emit('update:ordenacao', ordenacao)"
            />
          </div>
        </div>
      </q-card-section>

      <div v-if="isLoading" class="flex justify-center items-center" style="height: 200px">
        <q-spinner color="primary" size="3em" />
        <span class="q-ml-md text-grey">Carregando dados...</span>
      </div>

      <q-card-section v-else class="product-list-section">
        <template v-if="produtos && produtos.length > 0">
          <q-scroll-area :thumb-style="thumbStyle" style="height: 220px">
            <div v-for="prod in produtos" :key="prod.name" class="product-item">
              <div class="product-header">
                <div class="product-name">
                  {{ formatDescription(prod.descricao) }}
                  <q-tooltip
                    v-if="prod.descricao.length > 45"
                    class="bg-secondary"
                    anchor="bottom middle"
                    self="top middle"
                    :offset="[0, 20]"
                  >
                    {{ prod.descricao }}
                  </q-tooltip>
                  <span class="product-sales-count">({{ prod.quantidade }} vendas)</span>
                </div>
                <div class="product-value">
                  {{ formatarMoeda(prod.valor) }}
                </div>
              </div>

              <q-linear-progress
                :value="calculateProgress(prod)"
                :color="calculateShade(calculateProgress(prod))"
                track-color="grey-3"
                size="8px"
                class="product-progress"
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
  produtos: {
    type: Array,
    default: () => [],
  },
  isLoading: {
    type: Boolean,
    default: false,
  },
  modelValue: {
    type: String,
    default: 'valor'
  },
  periodo: {
    type: String,
    default: '7d'
  }
});

const emit = defineEmits(['update:ordenacao', 'update:periodo']);

const ordenacao = ref(props.modelValue);
const periodoSelecionado = ref(props.periodo);

const thumbStyle = {
  right: '4px',
  width: '5px',
  backgroundColor: '#C67C48',
  opacity: 0.75,
  borderRadius: '4px',
};

function formatDescription(descricao) {
  return descricao.length > 45 ? descricao.substring(0, 45) + '...' : descricao;
}

const { formatarMoeda } = useFormatarMoeda();

// Calcular valor máximo baseado no critério de ordenação
const valorMaximo = computed(() => {
  if (!props.produtos || props.produtos.length === 0) return 1;

  if (ordenacao.value === 'quantidade') {
    return Math.max(...props.produtos.map(p => p.quantidade || 0));
  } else {
    return Math.max(...props.produtos.map(p => p.valor || 0));
  }
});

// Calcular progresso do produto baseado na ordenação
const calculateProgress = (produto) => {
  if (valorMaximo.value === 0) return 0;

  if (ordenacao.value === 'quantidade') {
    return (produto.quantidade || 0) / valorMaximo.value;
  } else {
    return (produto.valor || 0) / valorMaximo.value;
  }
};

// Calcular cor da barra baseado no percentual
const calculateShade = (percentage) => {
  if (percentage > 0.75) return 'primary';
  if (percentage > 0.5) return 'secondary';
  if (percentage > 0.25) return 'accent';
  return 'info';
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

.controls-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.product-list-section {
  padding: 8px 16px 12px;
}

.product-item {
  margin-bottom: 16px;
  padding: 8px 0;
  border-bottom: 1px solid rgba(107, 62, 38, 0.08);
}

.product-item:last-child {
  border-bottom: none;
  margin-bottom: 0;
}

.product-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.product-name {
  font-size: 0.85rem;
  font-weight: 500;
  color: #2A1F1B;
  line-height: 1.2;
  padding-right: 12px;
}

.product-sales-count {
  font-size: 0.75rem;
  color: #8B7355;
  font-weight: 400;
  margin-left: 4px;
}

.product-value {
  font-size: 0.9rem;
  font-weight: 600;
  color: #2A1F1B;
  white-space: nowrap;
}

.product-progress {
  border-radius: 4px;
  margin-top: 4px;
}

.no-data-message {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 220px;
  text-align: center;
  opacity: 0.8;
}
</style>
