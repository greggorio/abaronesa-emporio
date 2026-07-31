<template>
  <q-card-section>
    <div class="row items-center q-mb-sm">
      <div class="text-subtitle2">Produtos do Recebimento</div>
      <q-space />
      <q-btn color="primary" icon="add" label="Adicionar Item" dense @click="addRow" />
    </div>

    <q-table
      :rows="rows"
      :columns="columns"
      row-key="__id"
      flat
      bordered
      dense
      :pagination="{ rowsPerPage: 0 }"
    >
      <template #body-cell-produto="props">
        <q-td :props="props">
          <LookupSelect
            v-model="props.row.produtoId"
            :field="produtoLookupField"
            :disabled="saving"
            @update:model-value="() => onProdutoSelected(props.row)"
          />
        </q-td>
      </template>

      <template #body-cell-sku="props">
        <q-td :props="props">
          <SkuLookupSelect
            v-if="!props.row.isInsumo"
            v-model="props.row.skuId"
            :produto-id="props.row.produtoId"
            :disabled="!props.row.produtoId || saving"
            @update:model-value="() => onSkuSelected(props.row)"
          />
          <div v-else class="text-grey-6">—</div>
        </q-td>
      </template>

      <template #body-cell-embalagem="props">
        <q-td :props="props">
          <q-select
            v-if="props.row.isInsumo"
            v-model="props.row.embalagemId"
            :options="props.row.embalagensOptions"
            option-label="label"
            option-value="value"
            emit-value
            map-options
            dense
            outlined
            :disable="!props.row.produtoId || saving || props.row.loadingEmbalagens"
            :loading="props.row.loadingEmbalagens"
            placeholder="Selecione a embalagem"
            @update:model-value="() => onEmbalagemSelected(props.row)"
          />
          <div v-else class="text-grey-6">—</div>
        </q-td>
      </template>

      <template #body-cell-quantidade="props">
        <q-td :props="props">
          <q-input
            v-model.number="props.row.quantidade"
            type="number"
            min="1"
            step="1"
            dense
            outlined
            @update:model-value="() => recalcRow(props.row)"
          />
        </q-td>
      </template>

      <template #body-cell-custoUnitario="props">
        <q-td :props="props" class="text-right">
          <q-input
            v-model.number="props.row.custoUnitario"
            type="number"
            step="0.0001"
            min="0"
            dense
            outlined
            prefix="R$"
            @update:model-value="() => recalcRow(props.row)"
          />
        </q-td>
      </template>

      <template #body-cell-valorTotal="props">
        <q-td :props="props" class="text-right">
          {{ formatCurrency(props.row.valorTotal) }}
        </q-td>
      </template>

      <template #body-cell-lote="props">
        <q-td :props="props">
          <q-input v-model="props.row.lote" dense outlined placeholder="Opcional" />
        </q-td>
      </template>

      <template #body-cell-dataValidade="props">
        <q-td :props="props">
          <DateInput v-model="props.row.dataValidade" :label="''" dense @update:model-value="emitRows" />
        </q-td>
      </template>

      <template #body-cell-actions="props">
        <q-td :props="props" class="text-center">
          <q-btn flat round dense icon="delete" color="negative" @click="removeRow(props.row.__id)" />
        </q-td>
      </template>
    </q-table>

    <div class="row q-mt-md">
      <div class="col-6 text-subtitle2">Total de Itens: {{ rows.length }}</div>
      <div class="col-6 text-right text-subtitle2">Valor Total: {{ formatCurrency(totalGeral) }}</div>
    </div>
  </q-card-section>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useApiRequest } from '@/composables/useApiRequest'
import LookupSelect from './LookupSelect.vue'
import SkuLookupSelect from './SkuLookupSelect.vue'
import DateInput from './date/DateInput.vue'

const props = defineProps({
  modelValue: { type: Object, required: true }, // formData
  recordId: { type: Number, default: null },
})
const emit = defineEmits(['update:modelValue'])

const { apiRequest } = useApiRequest()
const saving = ref(false)

const columns = [
  { name: 'produto', label: 'Produto', field: 'produtoId', align: 'left' },
  { name: 'sku', label: 'SKU / Variação', field: 'skuId', align: 'left' },
  { name: 'embalagem', label: 'Embalagem', field: 'embalagemId', align: 'left' },
  { name: 'quantidade', label: 'Quantidade', field: 'quantidade', align: 'center' },
  { name: 'custoUnitario', label: 'Custo Unit.', field: 'custoUnitario', align: 'right' },
  { name: 'valorTotal', label: 'Total', field: 'valorTotal', align: 'right' },
  { name: 'lote', label: 'Lote', field: 'lote', align: 'center' },
  { name: 'dataValidade', label: 'Validade', field: 'dataValidade', align: 'center' },
  { name: 'actions', label: '', field: 'actions', align: 'center' },
]

const produtoLookupField = {
  label: 'Produto',
  component: 'LookupSelect',
  type: 'LOOKUP',
  lookupEndpoint: '/api/produtos/lookup/search',
  displayColumns: ['codigo', 'descricao', 'estoqueAtual', 'preco'],
  props: { 'option-label': 'label', 'option-value': 'id', dense: true },
}

const rows = ref([])

function initializeFromForm() {
  const itens = Array.isArray(props.modelValue?.itens) ? props.modelValue.itens : []
  rows.value = itens.map((it, idx) => ({
    __id: idx + 1,
    produtoId: it.produtoId || null,
    isInsumo: false,
    skuId: it.skuId || null,
    embalagemId: it.embalagemId || null,
    quantidade: it.quantidade != null ? Number(it.quantidade) : 1,
    custoUnitario: it.custoUnitario != null ? Number(it.custoUnitario) : 0,
    valorTotal: it.valorTotal != null ? Number(it.valorTotal) : 0,
    lote: it.lote || '',
    dataValidade: it.dataValidade || null,
    embalagensOptions: [],
    loadingEmbalagens: false,
  }))

  // Carregar embalagens para linhas com produto já definido
  rows.value.forEach(async (r) => {
    if (r.produtoId) await resolveProdutoTipo(r)
    recalcRow(r)
  })
}

watch(
  () => props.modelValue,
  () => initializeFromForm(),
  { immediate: true, deep: true }
)

function addRow() {
  const id = rows.value.length > 0 ? Math.max(...rows.value.map((r) => r.__id)) + 1 : 1
  rows.value.push({
    __id: id,
    produtoId: null,
    isInsumo: false,
    skuId: null,
    embalagemId: null,
    quantidade: 1,
    custoUnitario: 0,
    valorTotal: 0,
    lote: '',
    dataValidade: null,
    embalagensOptions: [],
    loadingEmbalagens: false,
  })
  emitRows()
}

function removeRow(id) {
  rows.value = rows.value.filter((r) => r.__id !== id)
  emitRows()
}

async function onProdutoSelected(row) {
  // Reset embalagem ao trocar produto
  row.embalagemId = null
  row.skuId = null
  row.embalagensOptions = []
  row.skuOptions = []
  await resolveProdutoTipo(row)
  recalcRow(row)
  emitRows()
}

async function resolveProdutoTipo(row) {
  if (!row.produtoId) return
  try {
    const detail = await apiRequest(`/api/produtos/lookup/search/${row.produtoId}`)
    row.isInsumo = !!(detail && detail.insumo)
    if (row.isInsumo) {
      await loadEmbalagens(row)
  } else {
    // SKU é selecionado via diálogo; não é necessário pré-carregar aqui
  }
  } catch (e) {
    row.isInsumo = false
  }
}

async function loadEmbalagens(row) {
  if (!row.produtoId) return
  row.loadingEmbalagens = true
  try {
    const data = await apiRequest(`/api/embalagens?produtoId=${row.produtoId}`)
    const opts = (Array.isArray(data) ? data : []).map((e) => ({
      label: `${e.nome}${e.fatorBase ? ' (' + e.fatorBase + ')' : ''}`,
      value: e.id,
      fatorBase: e.fatorBase || 1,
    }))
    row.embalagensOptions = opts
  } catch (e) {
    row.embalagensOptions = []
  } finally {
    row.loadingEmbalagens = false
  }
}

function onEmbalagemSelected(row) {
  recalcRow(row)
  emitRows()
}

function onSkuSelected(row) {
  recalcRow(row)
  emitRows()
}

// SKU options handled by SkuLookupSelect

function recalcRow(row) {
  const q = Number(row.quantidade || 0)
  const c = Number(row.custoUnitario || 0)
  row.valorTotal = Number.isFinite(q * c) ? q * c : 0
}

const totalGeral = computed(() => rows.value.reduce((acc, r) => acc + (Number(r.valorTotal) || 0), 0))

function emitRows() {
  const itens = rows.value.map((r) => ({
    produtoId: r.produtoId,
    skuId: r.isInsumo ? null : r.skuId,
    embalagemId: r.isInsumo ? r.embalagemId : null,
    quantidade: r.quantidade,
    custoUnitario: r.custoUnitario,
    valorTotal: r.valorTotal,
    lote: r.lote,
    dataValidade: r.dataValidade,
  }))
  emit('update:modelValue', { ...props.modelValue, itens })
}

function formatCurrency(value) {
  const n = Number(value || 0)
  return 'R$ ' + n.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
</script>

<style scoped>
</style>
