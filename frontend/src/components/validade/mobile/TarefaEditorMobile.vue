<template>
  <q-card flat bordered class="q-mb-md">
    <q-card-section v-if="showHeader" class="row items-center justify-between">
      <div class="text-subtitle1">Tarefa #{{ tarefa?.id }} ({{ tarefa?.status || '...' }})</div>
      <q-chip :color="statusColor(tarefa?.status)" text-color="white" size="sm">{{ tarefa?.status }}</q-chip>
    </q-card-section>
    <q-separator v-if="showHeader" />
    <q-card-section class="column q-gutter-md editor-section">
      <div class="editor-header">
        <div class="text-subtitle2">Adicionar item à contagem</div>
      </div>

      <q-input
        v-if="!skuSelecionado"
        ref="buscaInputRef"
        v-model="busca"
        outlined
        dense
        label="Buscar produto ou SKU"
        placeholder="Código de barras, código interno ou descrição"
        :loading="loadingBusca"
        :disable="!canEdit"
        @keyup.enter="resolverBuscaRapida"
      >
        <template #prepend>
          <q-icon name="barcode_reader" />
        </template>
        <template #append>
          <q-icon
            v-if="busca"
            name="close"
            class="cursor-pointer"
            @click="limparBusca"
          />
          <q-btn
            flat
            round
            dense
            icon="search"
            :disable="!canEdit || !busca.trim()"
            @click="resolverBuscaRapida"
          />
        </template>
      </q-input>

      <q-banner
        v-if="produtoContextoAtivo"
        dense
        rounded
        class="bg-blue-1 text-blue-9"
      >
        <template #avatar>
          <q-icon name="inventory_2" color="blue-8" />
        </template>
        <div class="row items-center justify-between q-gutter-sm">
          <div>
            <div class="text-body2 text-weight-medium">Contexto do produto</div>
            <div class="text-caption">{{ initialProdutoName || `Produto #${initialProdutoId}` }}</div>
          </div>
          <q-btn
            flat
            dense
            color="primary"
            icon="list"
            label="Escolher SKU"
            :disable="!canEdit"
            @click="abrirDialogContextual"
          />
        </div>
      </q-banner>

      <q-banner
        v-if="erroBusca"
        dense
        rounded
        class="bg-red-1 text-red-9"
      >
        <template #avatar>
          <q-icon name="warning" color="negative" />
        </template>
        {{ erroBusca }}
      </q-banner>

      <q-card v-if="skuSelecionado" flat bordered class="selection-card">
        <q-card-section class="row items-start justify-between q-col-gutter-md">
          <div class="col">
            <div class="text-overline text-grey-7">SKU selecionado</div>
            <div class="text-subtitle1 text-weight-medium">{{ skuSelecionado.produtoNome }}</div>
            <div class="row items-center q-col-gutter-sm q-mt-xs">
              <div class="col-auto">
                <q-chip dense square color="brown-1" text-color="brown-9" icon="sell">{{ skuSelecionado.skuCodigo }}</q-chip>
              </div>
              <div class="col-auto" v-if="skuSelecionado.variacao">
                <q-chip dense square color="grey-2" text-color="grey-8" icon="tune">{{ skuSelecionado.variacao }}</q-chip>
              </div>
            </div>
            <div class="text-caption text-grey-7 q-mt-xs">
              <span v-if="skuSelecionado.codigoBarras">CB: {{ skuSelecionado.codigoBarras }}</span>
              <span v-if="skuSelecionado.codigoBarras && skuSelecionado.estoque !== null"> | </span>
              <span v-if="skuSelecionado.estoque !== null">Estoque: {{ skuSelecionado.estoque }}</span>
            </div>
          </div>
          <div class="col-auto">
            <q-btn
              flat
              dense
              color="primary"
              icon="restart_alt"
              label="Trocar"
              :disable="!canEdit || !skuSelecionado"
              @click="resetSelecaoSku"
            />
          </div>
        </q-card-section>
      </q-card>

      <template v-if="skuSelecionado">
        <div class="row q-col-gutter-sm compact-grid">
          <div class="col-12 col-sm-6">
            <q-input v-model="lote" label="Lote (opcional)" dense outlined :disable="!canEdit" />
          </div>
          <div class="col-12 col-sm-6">
            <q-input
              v-model="dataValidadeDisplay"
              label="Data de validade (opcional)"
              mask="##/##/####"
              dense
              outlined
              placeholder="dd/mm/yyyy"
              :disable="!canEdit"
            >
              <template #append>
                <q-icon name="event" class="cursor-pointer">
                  <q-popup-proxy cover transition-show="scale" transition-hide="scale">
                    <q-date v-model="dataValidadeDisplay" mask="DD/MM/YYYY">
                      <div class="row items-center justify-end">
                        <q-btn v-close-popup label="Fechar" color="primary" flat />
                      </div>
                    </q-date>
                  </q-popup-proxy>
                </q-icon>
              </template>
            </q-input>
          </div>
          <div class="col-12 col-sm-6">
            <q-input v-model.number="quantidade" label="Quantidade" type="number" dense outlined :disable="!canEdit" />
          </div>
          <div class="col-12 col-sm-6">
            <q-select v-model="acao" :options="acaoOptions" label="Ação" dense outlined emit-value map-options :disable="!canEdit" />
          </div>
        </div>

        <div class="row q-col-gutter-sm">
          <div class="col-12 col-sm-6">
            <q-btn
              color="primary"
              icon="playlist_add"
              class="full-width"
              label="Adicionar item"
              :disable="!canAdicionarItem"
              :loading="loading"
              @click="adicionar"
            />
          </div>
          <div v-if="showFinalizar" class="col-12 col-sm-6">
            <q-btn
              color="positive"
              icon="check_circle"
              class="full-width"
              label="Finalizar tarefa"
              :disable="tarefa?.status !== 'RASCUNHO'"
              :loading="loadingFinalizar"
              @click="$emit('finalizar', tarefa?.id)"
            />
          </div>
        </div>
      </template>
    </q-card-section>
    <q-separator />
    <q-card-section>
      <div class="text-subtitle2 q-mb-sm">Itens lançados</div>
      <q-list bordered class="rounded-borders item-list">
        <q-item v-for="it in orderedItems" :key="it.id" dense class="item-row" :class="{ 'item-highlight': it.id === ultimoItemId }">
          <q-item-section>
            <div class="row items-center justify-between q-col-gutter-sm">
              <div class="col">
                <div class="text-body2 text-weight-medium">{{ it.produtoNome || ('SKU ' + it.skuId) }}</div>
              </div>
              <div class="col-auto row items-center q-gutter-xs">
                <q-chip dense square color="grey-2" text-color="grey-8">{{ actionLabel(it.acao) }}</q-chip>
                <q-btn
                  v-if="canEdit"
                  flat
                  round
                  dense
                  size="sm"
                  color="negative"
                  icon="delete"
                  @click.stop="$emit('remove-item', it)"
                />
              </div>
            </div>
            <div class="text-caption text-grey-7 q-mt-xs">
              Lote: {{ it.lote || 'SEM_LOTE' }} | Validade: {{ formatItemDate(it.dataValidade) }}
            </div>
            <div class="text-caption text-grey-7">Quantidade: {{ it.quantidade }}</div>
          </q-item-section>
          <q-item-section v-if="it.id === ultimoItemId" side>
            <q-badge color="primary">Último</q-badge>
          </q-item-section>
        </q-item>
        <q-item v-if="!tarefa?.itens || !tarefa.itens.length">
          <q-item-section class="text-caption text-grey-6">Nenhum item lançado até agora.</q-item-section>
        </q-item>
      </q-list>
    </q-card-section>

    <q-dialog v-model="dialogBuscaSku" maximized persistent>
      <q-card class="flex flex-col h-full">
        <q-bar class="bg-primary text-white">
          <div>Selecionar SKU</div>
          <q-space />
          <q-btn dense flat icon="close" @click="dialogBuscaSku = false" />
        </q-bar>

        <q-card-section class="q-pb-none bg-grey-1">
          <q-banner
            v-if="produtoContextoAtivo"
            dense
            rounded
            class="bg-blue-1 text-blue-9 q-mb-sm"
          >
            <template #avatar>
              <q-icon name="inventory_2" color="blue-8" />
            </template>
            Exibindo SKUs de {{ initialProdutoName || `Produto #${initialProdutoId}` }}.
          </q-banner>
          <q-input
            v-model="buscaDialog"
            dense
            outlined
            debounce="250"
            placeholder="Pesquisar SKU, código de barras ou descrição"
            :loading="loadingBusca"
            @update:model-value="buscarResultadosDialog"
          >
            <template #append>
              <q-icon name="search" />
            </template>
          </q-input>
        </q-card-section>

        <q-card-section class="q-pt-sm scroll dialog-results">
          <q-list separator>
            <q-item
              v-for="option in skuDialogOptions"
              :key="option.value"
              clickable
              v-ripple
              @click="selecionarSku(option)"
            >
              <q-item-section>
                <div class="text-body1 text-weight-medium">{{ option.produtoNome }}</div>
                <div class="text-body2 text-grey-8">{{ option.skuCodigo }}</div>
                <div v-if="option.variacao" class="text-caption text-grey-8">{{ option.variacao }}</div>
                <div class="text-caption text-grey-7">
                  <span v-if="option.codigoBarras">CB: {{ option.codigoBarras }}</span>
                  <span v-if="option.codigoBarras && option.estoque !== null"> | </span>
                  <span v-if="option.estoque !== null">Estoque: {{ option.estoque }}</span>
                </div>
              </q-item-section>
            </q-item>
            <q-item v-if="!loadingBusca && !skuDialogOptions.length">
              <q-item-section class="text-center text-grey-6">
                Nenhum SKU encontrado para a busca informada.
              </q-item-section>
            </q-item>
          </q-list>
        </q-card-section>
      </q-card>
    </q-dialog>
  </q-card>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useApiRequest } from '@/composables/useApiRequest'

const props = defineProps({
  tarefa: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  loadingFinalizar: { type: Boolean, default: false },
  showFinalizar: { type: Boolean, default: true },
  showHeader: { type: Boolean, default: false },
  initialProdutoId: { type: Number, default: null },
  initialProdutoName: { type: String, default: '' }
})
const emit = defineEmits(['add-item', 'finalizar', 'sku-change', 'remove-item'])
const { apiRequest } = useApiRequest()

const buscaInputRef = ref(null)
const produtoId = ref(null)
const skuId = ref(null)
const skuSelecionado = ref(null)
const busca = ref('')
const buscaDialog = ref('')
const erroBusca = ref('')
const dialogBuscaSku = ref(false)
const skuDialogOptions = ref([])
const loadingBusca = ref(false)
const carregandoContextoProduto = ref(false)
const lote = ref('')
const dataValidadeDisplay = ref('')
const quantidade = ref(null)
const acao = ref('SET')
const ultimoItemId = ref(null)

const canEdit = computed(() => props.tarefa?.status === 'RASCUNHO')
const canAdicionarItem = computed(() => canEdit.value && !!skuId.value && !!quantidade.value)
const produtoContextoAtivo = computed(() => !!props.initialProdutoId)
const orderedItems = computed(() => {
  return [...(props.tarefa?.itens || [])].sort((a, b) => {
    const left = Number(a?.id || 0)
    const right = Number(b?.id || 0)
    return right - left
  })
})

watch(skuId, (val) => emit('sku-change', val))
watch(() => props.initialProdutoId, (val) => {
  produtoId.value = val || null
  resetSelecaoSku()
  if (val) {
    prepararContextoProduto(val)
    return
  }
  busca.value = ''
}, { immediate: true })
watch(() => props.tarefa?.itens, (items) => {
  ultimoItemId.value = Array.isArray(items) && items.length ? items[items.length - 1]?.id || null : null
}, { immediate: true, deep: true })

const acaoOptions = [
  { label: 'Definir (SET)', value: 'SET' },
  { label: 'Adicionar (ADD)', value: 'ADD' },
  { label: 'Remover (REMOVE)', value: 'REMOVE' },
]

function actionLabel(action) {
  switch (action) {
    case 'ADD': return 'Adicionar'
    case 'REMOVE': return 'Remover'
    default: return 'Definir'
  }
}

function statusColor(status) {
  switch (status) {
    case 'FINALIZADA': return 'positive'
    case 'CANCELADA': return 'negative'
    default: return 'info'
  }
}

function adicionar() {
  emit('add-item', {
    skuId: skuId.value,
    lote: lote.value || null,
    dataValidade: toIsoDate(dataValidadeDisplay.value),
    quantidade: quantidade.value,
    acao: acao.value || 'SET'
  })
  lote.value = ''
  dataValidadeDisplay.value = ''
  quantidade.value = null
  erroBusca.value = ''
  busca.value = ''
  resetSelecaoSku()
  nextTick(() => buscaInputRef.value?.focus?.())
}

function limparBusca() {
  busca.value = ''
  erroBusca.value = ''
}

function resetSelecaoSku() {
  skuId.value = null
  skuSelecionado.value = null
}

function formatItemDate(value) {
  if (!value) return 'SEM_VALIDADE'
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return value
  const [, year, month, day] = match
  return `${day}/${month}/${year}`
}

function toIsoDate(value) {
  if (!value) return null
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value)
  if (!match) return null
  const [, day, month, year] = match
  return `${year}-${month}-${day}`
}

function parseSkuOption(option) {
  const label = option?.label || ''
  const [skuCodigoPart, ...rest] = label.split(' - ')
  const skuCodigo = skuCodigoPart || 'SKU'
  let produtoNome = rest.join(' - ') || label
  produtoNome = produtoNome.replace(/\s*\[Estoque:.*\]\s*$/u, '').trim()

  return {
    value: option?.value || null,
    label,
    skuCodigo,
    produtoNome,
    variacao: extrairVariacao(label),
    codigoBarras: option?.codigoBarras || '',
    estoque: option?.estoque ?? null,
  }
}

function extrairVariacao(label) {
  const match = /\(([^)]+)\)/.exec(label || '')
  return match?.[1]?.trim() || ''
}

function isCodigoSearch(term) {
  return /^[0-9A-Za-z._-]+$/u.test(term) && !/\s/.test(term)
}

async function buscarSkus(term, extraParams = {}) {
  const params = new URLSearchParams({
    q: term,
    size: '20',
    ...Object.fromEntries(Object.entries(extraParams).filter(([, value]) => value !== null && value !== undefined && value !== ''))
  })
  const resp = await apiRequest(`/api/skus/search-options?${params.toString()}`)
  const list = resp?.data || resp || []
  return Array.isArray(list) ? list.map(parseSkuOption).filter(item => item.value) : []
}

async function buscarProdutos(term) {
  const resp = await apiRequest(`/api/produtos/lookup/search?search=${encodeURIComponent(term)}`)
  return Array.isArray(resp) ? resp : []
}

async function resolverBuscaRapida() {
  const term = busca.value.trim()
  if (!canEdit.value || !term) return

  erroBusca.value = ''
  loadingBusca.value = true
  try {
    const skuOptions = await buscarSkus(term)
    const exatos = skuOptions.filter(option =>
      option.skuCodigo?.toLowerCase() === term.toLowerCase() ||
      option.codigoBarras === term
    )

    if (exatos.length === 1) {
      selecionarSku(exatos[0])
      return
    }

    if (skuOptions.length === 1 && isCodigoSearch(term)) {
      selecionarSku(skuOptions[0])
      return
    }

    if (skuOptions.length > 1) {
      abrirDialogComResultados(term, skuOptions)
      return
    }

    const produtos = await buscarProdutos(term)

    if (produtos.length === 1) {
      const produto = produtos[0]
      const produtoSkus = await buscarSkus('', { produtoId: produto.value })

      if (produtoSkus.length === 1) {
        produtoId.value = produto.value
        selecionarSku(produtoSkus[0])
        return
      }

      if (produtoSkus.length > 1) {
        produtoId.value = produto.value
        abrirDialogComResultados(term, produtoSkus)
        return
      }
    }

    erroBusca.value = 'Nenhum SKU encontrado para o termo informado.'
  } catch (error) {
    erroBusca.value = error?.message || 'Falha ao buscar SKU.'
  } finally {
    loadingBusca.value = false
  }
}

async function buscarResultadosDialog() {
  const term = buscaDialog.value.trim()

  loadingBusca.value = true
  try {
    let options = await buscarSkus(term, { produtoId: produtoContextoAtivo.value ? props.initialProdutoId : null })

    if (!options.length && term) {
      const produtos = await buscarProdutos(term)
      if (produtos.length === 1) {
        options = await buscarSkus('', { produtoId: produtos[0].value })
      }
    }

    skuDialogOptions.value = options
  } catch (error) {
    skuDialogOptions.value = []
    erroBusca.value = error?.message || 'Falha ao buscar SKU.'
  } finally {
    loadingBusca.value = false
  }
}

function abrirDialogComResultados(term, options) {
  buscaDialog.value = term
  skuDialogOptions.value = options
  dialogBuscaSku.value = true
}

async function abrirDialogContextual() {
  if (!canEdit.value) return
  buscaDialog.value = ''
  dialogBuscaSku.value = true
  loadingBusca.value = true
  try {
    skuDialogOptions.value = await buscarSkus('', { produtoId: produtoContextoAtivo.value ? props.initialProdutoId : null })
  } catch (error) {
    skuDialogOptions.value = []
    erroBusca.value = error?.message || 'Falha ao carregar SKUs do produto.'
  } finally {
    loadingBusca.value = false
  }
}

async function prepararContextoProduto(produtoIdInicial) {
  if (!produtoIdInicial || !canEdit.value || carregandoContextoProduto.value) return

  carregandoContextoProduto.value = true
  try {
    const options = await buscarSkus('', { produtoId: produtoIdInicial })
    if (options.length === 1) {
      selecionarSku(options[0])
      return
    }

    busca.value = props.initialProdutoName || ''
    skuDialogOptions.value = options
  } catch (error) {
    erroBusca.value = error?.message || 'Falha ao preparar contexto do produto.'
  } finally {
    carregandoContextoProduto.value = false
  }
}

function selecionarSku(option) {
  produtoId.value = props.initialProdutoId || produtoId.value || null
  skuId.value = option.value
  skuSelecionado.value = option
  busca.value = `${option.produtoNome} (${option.skuCodigo})`
  erroBusca.value = ''
  dialogBuscaSku.value = false
}
</script>

<style scoped>
.rounded-borders { border-radius: 8px; }

.item-highlight {
  background: rgba(25, 118, 210, 0.06);
  border-left: 3px solid rgba(25, 118, 210, 0.5);
}

.selection-card {
  background: #faf7f3;
}

.dialog-results {
  height: calc(100% - 120px);
  overflow-y: auto;
}

.editor-section {
  padding-top: 12px;
}

.editor-header {
  margin-bottom: -2px;
}

.compact-grid :deep(.q-field) {
  margin-bottom: 0;
}

.item-list {
  overflow: hidden;
}

.item-row {
  align-items: flex-start;
}
</style>
