<template>
  <div>
    <!-- Cards de Métricas -->
    <div class="row q-col-gutter-md q-mb-md">
      <div class="col-lg-3 col-md-6 col-xs-12">
        <div class="metric-card metric-card--primary">
          <div class="metric-card__content">
            <div class="metric-card__header">
              <span class="metric-card__label">Total de Sugestões</span>
              <q-icon name="lightbulb" size="24px" class="metric-card__icon" />
            </div>
            <div class="metric-card__value">{{ linhas.length }}</div>
            <div class="metric-card__subtitle">Itens sugeridos</div>
          </div>
        </div>
      </div>

      <div class="col-lg-3 col-md-6 col-xs-12">
        <div class="metric-card metric-card--info">
          <div class="metric-card__content">
            <div class="metric-card__header">
              <span class="metric-card__label">Itens Selecionados</span>
              <q-icon name="shopping_cart" size="24px" class="metric-card__icon" />
            </div>
            <div class="metric-card__value">{{ selecionados.length }}</div>
            <div class="metric-card__subtitle">Prontos para pedido</div>
          </div>
        </div>
      </div>

      <div class="col-lg-3 col-md-6 col-xs-12">
        <div class="metric-card metric-card--negative">
          <div class="metric-card__content">
            <div class="metric-card__header">
              <span class="metric-card__label">Itens Críticos</span>
              <q-icon name="warning" size="24px" class="metric-card__icon" />
            </div>
            <div class="metric-card__value">{{ itensCriticos }}</div>
            <div class="metric-card__subtitle">Abaixo de 30%</div>
          </div>
        </div>
      </div>

      <div class="col-lg-3 col-md-6 col-xs-12">
        <div class="metric-card metric-card--positive">
          <div class="metric-card__content">
            <div class="metric-card__header">
              <span class="metric-card__label">Valor Estimado</span>
              <q-icon name="payments" size="24px" class="metric-card__icon" />
            </div>
            <div class="metric-card__value">{{ formatCurrency(valorEstimado) }}</div>
            <div class="metric-card__subtitle">Total selecionado</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Card principal da tabela -->
    <div class="dashboard-card">
      <!-- Header do Card -->
      <div class="header-section">
        <div class="header-content">
          <div class="title-wrapper">
            <q-icon name="o_inventory" color="primary" size="xs" />
            <h5 class="panel-title q-my-none q-ml-sm">Sugestões de Compra</h5>
          </div>
          <div class="controls-wrapper row q-gutter-sm">
            <q-toggle
              v-model="somenteCriticos"
              color="warning"
              label="Somente críticos"
              @update:model-value="$emit('load-sugestoes', somenteCriticos)"
            />
            <q-btn
              color="primary"
              icon="refresh"
              label="Atualizar"
              @click="$emit('load-sugestoes', somenteCriticos)"
              :loading="loading"
              unelevated
            />
            <q-btn
              color="positive"
              icon="shopping_cart"
              label="Criar Pedido"
              @click="$emit('criar-pedido', selecionados)"
              :disable="selecionados.length === 0"
              :loading="criando"
              unelevated
            />
          </div>
        </div>
      </div>

      <!-- Tabela -->
      <div class="q-pt-none q-pa-md">
        <q-table
          flat
          :rows="linhas"
          :columns="colunas"
          :row-key="rowKey"
          selection="multiple"
          v-model:selected="selecionados"
          :loading="loading"
          :pagination="{ rowsPerPage: 20 }"
          :rows-per-page-options="[10, 20, 50, 100]"
          class="sugestoes-table"
        >
          <template v-slot:body-cell-insumo="props">
            <q-td :props="props">
              <q-badge
                :style="props.row.insumo ? 'background-color: #00897B; color: white' : 'background-color: #C67C48; color: white'"
                class="product-badge"
              >
                {{ props.row.insumo ? 'Insumo' : 'Vendável' }}
              </q-badge>
            </q-td>
          </template>

          <template v-slot:body-cell-produtoNome="props">
            <q-td :props="props">
              <div class="product-name-cell">
                {{ props.row.produtoNome }}
              </div>
            </q-td>
          </template>

          <template v-slot:body-cell-referencia="props">
            <q-td :props="props">
              <div v-if="props.row.insumo" class="reference-cell">
                <div class="text-weight-medium">{{ props.row.embalagemNome || '—' }}</div>
                <div v-if="props.row.fatorBase" class="text-caption text-grey-7">
                  Fator: {{ props.row.fatorBase }}
                </div>
              </div>
              <div v-else class="reference-cell">
                <div class="text-weight-medium">{{ props.row.sku }}</div>
              </div>
            </q-td>
          </template>

          <template v-slot:body-cell-estoque="props">
            <q-td :props="props">
              <div class="estoque-cell">
                <div class="estoque-header">
                  <span class="estoque-atual">{{ props.row.insumo ? (props.row.estoqueAtualBase ?? 0) : (props.row.estoqueAtualSku ?? 0) }}</span>
                  <span class="estoque-separator">/</span>
                  <span class="estoque-minimo">mín {{ props.row.insumo ? (props.row.estoqueMinimoBase ?? 0) : (props.row.estoqueMinimoSku ?? 0) }}</span>
                  <q-badge
                    v-if="isCritico(props.row.insumo ? props.row.estoqueAtualBase : props.row.estoqueAtualSku, props.row.insumo ? props.row.estoqueMinimoBase : props.row.estoqueMinimoSku)"
                    color="negative"
                    class="q-ml-sm critical-badge"
                  >
                    CRÍTICO
                  </q-badge>
                </div>
                <q-linear-progress
                  :value="getPercentualEstoque(
                    props.row.insumo ? props.row.estoqueAtualBase : props.row.estoqueAtualSku,
                    props.row.insumo ? props.row.estoqueMinimoBase : props.row.estoqueMinimoSku
                  )"
                  :color="getCorEstoque(
                    props.row.insumo ? props.row.estoqueAtualBase : props.row.estoqueAtualSku,
                    props.row.insumo ? props.row.estoqueMinimoBase : props.row.estoqueMinimoSku
                  )"
                  size="10px"
                  class="q-mt-xs progress-bar"
                  track-color="grey-5"
                  rounded
                />
              </div>
            </q-td>
          </template>

          <template v-slot:body-cell-quantidade="props">
            <q-td :props="props">
              <q-input
                dense
                type="number"
                min="0"
                v-model.number="props.row.quantidadeEdit"
                style="max-width: 120px"
                outlined
                class="input-quantidade"
              />
            </q-td>
          </template>

          <template v-slot:body-cell-custoUnitario="props">
            <q-td :props="props">
              <q-input
                dense
                type="number"
                min="0"
                step="0.01"
                v-model.number="props.row.custoUnitario"
                prefix="R$"
                style="max-width: 140px"
                outlined
                class="input-custo"
              />
            </q-td>
          </template>

          <template v-slot:body-cell-subtotal="props">
            <q-td :props="props" class="text-right">
              <strong class="subtotal-value">{{ calcularSubtotal(props.row) }}</strong>
            </q-td>
          </template>

          <template v-slot:no-data>
            <div class="full-width column items-center q-pa-xl no-data">
              <q-icon name="inventory" size="4em" color="grey-5" />
              <div class="text-h6 text-grey-7 q-mt-md">Nenhuma sugestão disponível</div>
              <div class="text-caption text-grey-6">
                Todos os produtos estão com estoque adequado
              </div>
            </div>
          </template>
        </q-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  linhas: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  criando: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['load-sugestoes', 'criar-pedido'])

const somenteCriticos = ref(true)
const selecionados = ref([])

// Selecionar todos os itens automaticamente quando as linhas mudarem
watch(() => props.linhas, (newLinhas) => {
  if (newLinhas && newLinhas.length > 0) {
    selecionados.value = [...newLinhas]
  }
}, { immediate: true })

const colunas = [
  { name: 'insumo', label: 'Tipo', field: 'insumo', align: 'center', style: 'width: 100px' },
  { name: 'produtoNome', label: 'Produto', field: 'produtoNome', align: 'left', sortable: true },
  { name: 'referencia', label: 'SKU/Embalagem', field: 'referencia', align: 'left' },
  { name: 'estoque', label: 'Estoque Atual / Mínimo', field: 'estoque', align: 'left', style: 'min-width: 220px' },
  { name: 'quantidade', label: 'Qtd. Pedido', field: 'quantidade', align: 'right' },
  { name: 'custoUnitario', label: 'Custo Unitário', field: 'custoUnitario', align: 'right' },
  { name: 'subtotal', label: 'Subtotal', field: 'subtotal', align: 'right' }
]

const itensCriticos = computed(() => {
  return props.linhas.filter(row => {
    if (row.insumo) {
      return isCritico(row.estoqueAtualBase, row.estoqueMinimoBase)
    } else {
      return isCritico(row.estoqueAtualSku, row.estoqueMinimoSku)
    }
  }).length
})

const valorEstimado = computed(() => {
  return selecionados.value.reduce((sum, row) => {
    const qtd = Number(row.quantidadeEdit || 0)
    const custo = Number(row.custoUnitario || 0)
    return sum + (qtd * custo)
  }, 0)
})

function rowKey(row) {
  return row.insumo ? `P${row.produtoId}-E${row.embalagemId || 0}` : `S${row.skuId}`
}

function isCritico(atual, minimo) {
  if (minimo === 0) return false
  return atual <= (minimo * 0.3)
}

function getPercentualEstoque(atual, minimo) {
  if (minimo === 0) return 1
  return Math.min(atual / minimo, 1)
}

function getCorEstoque(atual, minimo) {
  const percentual = getPercentualEstoque(atual, minimo)
  if (percentual <= 0.3) return 'negative'
  if (percentual <= 0.5) return 'warning'
  if (percentual <= 0.8) return 'secondary'
  return 'positive'
}

function calcularSubtotal(row) {
  const qtd = Number(row.quantidadeEdit || 0)
  const custo = Number(row.custoUnitario || 0)
  if (qtd > 0 && custo > 0) {
    return formatCurrency(qtd * custo)
  }
  return '—'
}

function formatCurrency(value) {
  if (value == null) return '—'
  const num = Number(value)
  if (Number.isNaN(num)) return '—'
  return num.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

defineExpose({ selecionados })
</script>

<style scoped>
/* Metric Cards - Estilo do Dashboard */
.metric-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(107, 62, 38, 0.08);
  padding: 16px 20px;
  transition: all 0.3s ease;
  border-left: 4px solid;
  height: 100%;
  min-height: 110px;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(107, 62, 38, 0.12);
}

.metric-card--primary {
  border-left-color: #6B3E26;
}

.metric-card--info {
  border-left-color: #8B7355;
}

.metric-card--negative {
  border-left-color: #D65A31;
}

.metric-card--positive {
  border-left-color: #B5854C;
}

.metric-card__content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.metric-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.metric-card__label {
  font-size: 0.875rem;
  font-weight: 500;
  color: #8B7355;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.metric-card__icon {
  color: #C67C48;
  opacity: 0.8;
}

.metric-card__value {
  font-size: 1.75rem;
  font-weight: 700;
  color: #6B3E26;
  line-height: 1.2;
}

.metric-card__subtitle {
  font-size: 0.813rem;
  color: #8B7355;
  font-weight: 400;
}

/* Dashboard Card - Estilo do painel */
.dashboard-card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 5px rgba(0, 0, 0, 0.2);
}

.header-section {
  border-bottom: 1px solid #e0e0e0;
  padding: 16px 20px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.title-wrapper {
  display: flex;
  align-items: center;
}

.panel-title {
  font-size: 18px;
  font-weight: bold;
  font-family: Arial, sans-serif;
  color: #6B3E26;
}

.controls-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* Tabela customizada */
.sugestoes-table :deep(.q-table__top) {
  padding: 0;
}

.sugestoes-table :deep(.q-table thead tr) {
  background-color: #f5f1ed;
}

.sugestoes-table :deep(.q-table thead th) {
  font-weight: 600;
  color: #6B3E26;
  text-transform: uppercase;
  font-size: 0.75rem;
  letter-spacing: 0.5px;
}

.sugestoes-table :deep(.q-table tbody tr:hover) {
  background-color: #faf8f6;
}

.product-badge {
  font-size: 0.7rem;
  font-weight: 600;
  padding: 4px 10px;
}

.product-name-cell {
  font-weight: 500;
  color: #6B3E26;
}

.reference-cell {
  line-height: 1.4;
}

/* Célula de Estoque */
.estoque-cell {
  min-width: 200px;
}

.estoque-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.estoque-atual {
  font-weight: 700;
  font-size: 1rem;
  color: #6B3E26;
}

.estoque-separator {
  color: #8B7355;
}

.estoque-minimo {
  font-size: 0.875rem;
  color: #8B7355;
}

.critical-badge {
  font-size: 0.65rem;
  padding: 2px 6px;
}

/* Inputs */
.input-quantidade,
.input-custo {
  margin-left: auto;
}

.input-quantidade :deep(.q-field__control),
.input-custo :deep(.q-field__control) {
  border-radius: 6px;
  border-color: #d4c5b9;
}

.input-quantidade :deep(.q-field__control:hover),
.input-custo :deep(.q-field__control:hover) {
  border-color: #C67C48;
}

.input-quantidade :deep(.q-field__native),
.input-custo :deep(.q-field__native) {
  font-weight: 500;
  color: #6B3E26;
  text-align: right;
}

.subtotal-value {
  font-size: 1rem;
  color: #B5854C;
  font-weight: 700;
}

/* No data state */
.no-data {
  min-height: 300px;
  display: flex;
  justify-content: center;
}

/* Progress bar */
.progress-bar :deep(.q-linear-progress__track) {
  opacity: 1;
  border: 1px solid #d0d0d0;
}
</style>
