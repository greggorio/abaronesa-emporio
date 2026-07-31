<template>
  <div class="produto-validade-tab q-pa-md">
    <q-banner v-if="!recordId" class="bg-warning text-white" rounded>
      <template #avatar>
        <q-icon name="warning" />
      </template>
      Salve o produto primeiro para gerenciar validades.
    </q-banner>

    <q-banner
      v-else-if="produtoControlaValidade === false"
      class="bg-blue-1 text-blue-9"
      rounded
    >
      <template #avatar>
        <q-icon name="info" color="blue" />
      </template>
      Este produto não está configurado para controle de validade. A aba permanece visível para contexto operacional.
    </q-banner>

    <div v-else>
      <div class="row q-col-gutter-md q-mb-md">
        <div class="col-12 col-sm-6 col-md-2" v-for="card in summaryCards" :key="card.key">
          <q-card flat bordered>
            <q-card-section>
              <div class="text-caption text-grey-7">{{ card.label }}</div>
              <div class="text-h6">{{ card.value }}</div>
            </q-card-section>
          </q-card>
        </div>
      </div>

      <q-card flat bordered class="q-mb-md">
        <q-card-section class="row q-col-gutter-md items-end">
          <div class="col-12 col-md-3">
            <q-select
              v-model="filtros.skuId"
              dense
              outlined
              clearable
              emit-value
              map-options
              label="SKU"
              :options="skuOptions"
            />
          </div>
          <div class="col-12 col-md-3">
            <q-select
              v-model="filtros.status"
              dense
              outlined
              clearable
              emit-value
              map-options
              label="Status"
              :options="statusOptions"
            />
          </div>
          <div class="col-12 col-md-3">
            <q-input
              v-model="filtros.buscaLote"
              dense
              outlined
              clearable
              label="Buscar lote"
            />
          </div>
          <div class="col-12 col-md-3 row q-col-gutter-sm">
            <div class="col-6">
              <q-toggle v-model="filtros.somenteComSaldo" label="Somente saldo" />
            </div>
            <div class="col-6">
              <q-toggle v-model="filtros.incluirSemLote" label="Incluir SEM_LOTE" />
            </div>
          </div>
        </q-card-section>
        <q-card-actions align="right">
          <q-btn
            flat
            icon="add"
            label="Criar lote"
            color="primary"
            @click="abrirDialogCriacao"
            :disable="!podeOperar"
          />
          <q-btn
            flat
            icon="assignment"
            :label="temTarefaEmAberto ? 'Retomar tarefa em aberto' : 'Abrir tarefa'"
            color="secondary"
            @click="abrirTarefaValidade"
            :disable="!recordId"
            :loading="loadingAbrirTarefa"
          />
          <q-btn flat icon="refresh" label="Atualizar" color="primary" @click="carregar" :loading="loading" />
        </q-card-actions>
      </q-card>

      <q-card flat bordered>
        <q-card-section class="row items-center justify-between">
          <div class="text-h6">Lotes por SKU</div>
          <q-badge v-if="hasDivergencia" color="orange">Há divergências entre lotes e agregado</q-badge>
        </q-card-section>

        <q-separator />

        <q-card-section v-if="loading" class="flex flex-center q-py-xl">
          <q-spinner color="primary" size="40px" />
        </q-card-section>

        <q-card-section v-else-if="erro" class="text-negative">
          {{ erro }}
        </q-card-section>

        <q-card-section v-else-if="!skus.length">
          <div class="text-grey-7">Nenhum SKU encontrado para este produto.</div>
        </q-card-section>

        <q-card-section v-else>
          <div v-for="sku in skus" :key="sku.skuId" class="q-mb-lg">
            <div class="row items-center justify-between q-mb-sm">
              <div>
                <div class="text-subtitle1 text-weight-medium">{{ sku.skuDescricao }}</div>
                <div class="text-caption text-grey-7">
                  {{ sku.skuCodigo }} | Agregado: {{ formatQuantidade(sku.estoqueAgregado) }} | Soma lotes:
                  {{ formatQuantidade(sku.somaLotes) }}
                </div>
              </div>
              <q-badge v-if="sku.possuiDivergencia" color="orange">Divergência</q-badge>
            </div>

            <q-list bordered separator class="rounded-borders">
              <q-item v-for="lote in sku.lotes" :key="lote.estoqueLoteId">
                <q-item-section>
                  <q-item-label>
                    {{ lote.lote || 'SEM_LOTE' }}
                  </q-item-label>
                  <q-item-label caption>
                    Validade: {{ lote.dataValidade || 'SEM_DATA_VALIDADE' }} | Quantidade:
                    {{ formatQuantidade(lote.quantidade) }}
                  </q-item-label>
                </q-item-section>
                <q-item-section side>
                  <div class="column items-end">
                    <q-badge :color="statusColor(lote.status)">{{ lote.status }}</q-badge>
                    <span class="text-caption text-grey-7 q-mt-xs">{{ lote.rastreabilidade }}</span>
                    <div class="row q-gutter-xs q-mt-sm">
                      <q-btn
                        flat
                        dense
                        size="sm"
                        icon="history"
                        label="Histórico"
                        color="secondary"
                        @click="abrirDialogHistorico(sku, lote)"
                        :disable="!recordId"
                      />
                      <q-btn
                        flat
                        dense
                        size="sm"
                        icon="edit"
                        label="Ajustar"
                        color="primary"
                        @click="abrirDialogAjuste(sku, lote)"
                        :disable="!podeOperar"
                      />
                      <q-btn
                        flat
                        dense
                        size="sm"
                        icon="delete_sweep"
                        label="Zerar"
                        color="negative"
                        @click="abrirDialogZero(sku, lote)"
                        :disable="!podeOperar"
                      />
                    </div>
                  </div>
                </q-item-section>
              </q-item>

              <q-item v-if="!sku.lotes?.length">
                <q-item-section class="text-grey-7">
                  Nenhum lote encontrado para este SKU com os filtros atuais.
                </q-item-section>
              </q-item>
            </q-list>
          </div>
        </q-card-section>
      </q-card>
    </div>

    <q-dialog v-model="dialogCriacao.aberto" persistent>
      <q-card style="min-width: 520px">
        <q-card-section class="row items-center q-pb-none">
          <div class="text-h6">Criar lote</div>
          <q-space />
          <q-btn icon="close" flat round dense v-close-popup />
        </q-card-section>

        <q-card-section class="q-gutter-md">
          <q-select
            v-model="dialogCriacao.form.skuId"
            outlined
            emit-value
            map-options
            :options="skuOptions"
            label="SKU"
          />
          <q-input v-model="dialogCriacao.form.lote" outlined label="Lote" />
          <q-input v-model="dialogCriacao.form.dataValidade" outlined label="Data de validade" type="date" />
          <q-input v-model.number="dialogCriacao.form.quantidade" outlined label="Quantidade" type="number" min="0" step="0.001" />
          <q-input v-model="dialogCriacao.form.observacao" outlined type="textarea" label="Observação" autogrow />
        </q-card-section>

        <q-card-actions align="right">
          <q-btn flat label="Cancelar" v-close-popup />
          <q-btn color="primary" label="Salvar" @click="criarLote" :loading="dialogCriacao.salvando" />
        </q-card-actions>
      </q-card>
    </q-dialog>

    <q-dialog v-model="dialogAjuste.aberto" persistent>
      <q-card style="min-width: 520px">
        <q-card-section class="row items-center q-pb-none">
          <div class="text-h6">Ajustar lote</div>
          <q-space />
          <q-btn icon="close" flat round dense v-close-popup />
        </q-card-section>

        <q-card-section class="q-gutter-md">
          <div class="text-caption text-grey-7">
            {{ dialogAjuste.contexto }}
          </div>
          <q-select
            v-model="dialogAjuste.form.acao"
            outlined
            emit-value
            map-options
            :options="acoesAjuste"
            label="Ação"
          />
          <q-input v-model.number="dialogAjuste.form.quantidade" outlined label="Quantidade" type="number" min="0" step="0.001" />
          <q-input v-model="dialogAjuste.form.observacao" outlined type="textarea" label="Observação" autogrow />
        </q-card-section>

        <q-card-actions align="right">
          <q-btn flat label="Cancelar" v-close-popup />
          <q-btn color="primary" label="Aplicar" @click="ajustarLote" :loading="dialogAjuste.salvando" />
        </q-card-actions>
      </q-card>
    </q-dialog>

    <q-dialog v-model="dialogZero.aberto" persistent>
      <q-card style="min-width: 480px">
        <q-card-section class="row items-center q-pb-none">
          <div class="text-h6">Zerar lote</div>
          <q-space />
          <q-btn icon="close" flat round dense v-close-popup />
        </q-card-section>

        <q-card-section class="q-gutter-md">
          <div>
            Deseja zerar o lote <strong>{{ dialogZero.loteLabel }}</strong>?
          </div>
          <q-input v-model="dialogZero.form.observacao" outlined type="textarea" label="Observação" autogrow />
        </q-card-section>

        <q-card-actions align="right">
          <q-btn flat label="Cancelar" v-close-popup />
          <q-btn color="negative" label="Zerar" @click="zerarLote" :loading="dialogZero.salvando" />
        </q-card-actions>
      </q-card>
    </q-dialog>

    <q-dialog v-model="dialogHistorico.aberto">
      <q-card style="min-width: 720px; max-width: 90vw">
        <q-card-section class="row items-center q-pb-none">
          <div class="text-h6">Histórico do lote</div>
          <q-space />
          <q-btn icon="close" flat round dense v-close-popup />
        </q-card-section>

        <q-card-section>
          <div class="text-caption text-grey-7 q-mb-md">
            {{ dialogHistorico.contexto }}
          </div>

          <div v-if="dialogHistorico.loading" class="flex flex-center q-py-lg">
            <q-spinner color="primary" size="32px" />
          </div>

          <div v-else-if="dialogHistorico.erro" class="text-negative">
            {{ dialogHistorico.erro }}
          </div>

          <q-list v-else bordered separator class="rounded-borders">
            <q-item v-for="movimento in dialogHistorico.movimentos" :key="movimento.id">
              <q-item-section>
                <q-item-label>
                  {{ formatDataHora(movimento.dataMovimento) }} | {{ movimento.tipoMovimentoDescricao }}
                </q-item-label>
                <q-item-label caption>
                  {{ movimento.observacao || 'Sem observação' }}
                </q-item-label>
                <q-item-label caption v-if="movimento.documentoReferencia || movimento.usuarioNome">
                  {{ movimento.documentoReferencia || 'Sem documento' }}<span v-if="movimento.usuarioNome"> | {{ movimento.usuarioNome }}</span>
                </q-item-label>
              </q-item-section>
              <q-item-section side>
                <q-badge :color="movimento.deltaQuantidade >= 0 ? 'positive' : 'negative'">
                  {{ formatDelta(movimento.deltaQuantidade) }}
                </q-badge>
              </q-item-section>
            </q-item>

            <q-item v-if="!dialogHistorico.movimentos.length">
              <q-item-section class="text-grey-7">
                Nenhum movimento encontrado para este lote.
              </q-item-section>
            </q-item>
          </q-list>
        </q-card-section>
      </q-card>
    </q-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useQuasar } from 'quasar'
import { useRouter } from 'vue-router'
import { useApiRequest } from '@/composables/useApiRequest'

const props = defineProps({
  modelValue: {
    type: Object,
    required: true,
  },
  recordId: {
    type: Number,
    default: null,
  },
})

const $q = useQuasar()
const router = useRouter()
const { apiRequest } = useApiRequest()

const loading = ref(false)
const loadingAbrirTarefa = ref(false)
const erro = ref('')
const produtoValidade = ref(null)
const tarefasValidade = ref([])
const filtros = reactive({
  skuId: null,
  status: null,
  somenteComSaldo: true,
  incluirSemLote: true,
  buscaLote: '',
})

const skus = computed(() => produtoValidade.value?.skus || [])
const resumo = computed(() => produtoValidade.value?.resumo || {})
const produtoControlaValidade = computed(() => {
  if (produtoValidade.value?.controlaValidade !== undefined && produtoValidade.value?.controlaValidade !== null) {
    return produtoValidade.value.controlaValidade
  }

  return props.modelValue?.controlaValidade
})
const podeOperar = computed(() => !!props.recordId && produtoControlaValidade.value === true)
const hasDivergencia = computed(() => skus.value.some((sku) => sku.possuiDivergencia))
const tarefaEmAberto = computed(() => {
  return [...tarefasValidade.value]
    .filter((tarefa) => tarefa?.status === 'RASCUNHO')
    .sort((a, b) => new Date(b?.criadoEm || 0).getTime() - new Date(a?.criadoEm || 0).getTime())[0] || null
})
const temTarefaEmAberto = computed(() => !!tarefaEmAberto.value)
const acoesAjuste = [
  { label: 'Definir valor final', value: 'SET' },
  { label: 'Adicionar saldo', value: 'ADD' },
  { label: 'Remover saldo', value: 'REMOVE' },
]

const dialogCriacao = reactive({
  aberto: false,
  salvando: false,
  form: {
    skuId: null,
    lote: '',
    dataValidade: '',
    quantidade: null,
    observacao: '',
  },
})

const dialogAjuste = reactive({
  aberto: false,
  salvando: false,
  estoqueLoteId: null,
  contexto: '',
  form: {
    acao: 'SET',
    quantidade: null,
    observacao: '',
  },
})

const dialogZero = reactive({
  aberto: false,
  salvando: false,
  estoqueLoteId: null,
  loteLabel: '',
  form: {
    observacao: '',
  },
})

const dialogHistorico = reactive({
  aberto: false,
  loading: false,
  erro: '',
  estoqueLoteId: null,
  contexto: '',
  movimentos: [],
})

const skuOptions = computed(() =>
  skus.value.map((sku) => ({
    label: sku.skuDescricao,
    value: sku.skuId,
  }))
)

const statusOptions = [
  { label: 'Vencido', value: 'VENCIDO' },
  { label: 'Crítico', value: 'CRITICO' },
  { label: 'Atenção', value: 'ATENCAO' },
  { label: 'OK', value: 'OK' },
  { label: 'Sem vida útil', value: 'SEM_VIDA_UTIL' },
  { label: 'Sem data', value: 'SEM_DATA_VALIDADE' },
]

const summaryCards = computed(() => [
  { key: 'skus', label: 'SKUs', value: resumo.value.totalSkus ?? 0 },
  { key: 'lotes', label: 'Lotes', value: resumo.value.totalLotes ?? 0 },
  { key: 'vencidos', label: 'Vencidos', value: resumo.value.vencidos ?? 0 },
  { key: 'criticos', label: 'Críticos', value: resumo.value.criticos ?? 0 },
  { key: 'atencao', label: 'Atenção', value: resumo.value.atencao ?? 0 },
  { key: 'semLote', label: 'SEM_LOTE', value: resumo.value.semLote ?? 0 },
])

async function carregar() {
  if (!props.recordId) {
    produtoValidade.value = null
    tarefasValidade.value = []
    return
  }

  loading.value = true
  erro.value = ''

  try {
    const params = new URLSearchParams()
    if (filtros.skuId) params.set('skuId', filtros.skuId)
    if (filtros.status) params.set('status', filtros.status)
    params.set('somenteComSaldo', String(filtros.somenteComSaldo))
    params.set('incluirSemLote', String(filtros.incluirSemLote))
    if (filtros.buscaLote) params.set('buscaLote', filtros.buscaLote)

    const query = params.toString()
    produtoValidade.value = await apiRequest(`/api/validade/produtos/${props.recordId}/lotes${query ? `?${query}` : ''}`)
    await carregarTarefasValidade()
  } catch (e) {
    erro.value = e?.message || 'Falha ao carregar validades do produto.'
    produtoValidade.value = null
    tarefasValidade.value = []
  } finally {
    loading.value = false
  }
}

async function carregarTarefasValidade() {
  try {
    const response = await apiRequest('/api/validade/tarefas')
    tarefasValidade.value = Array.isArray(response) ? response : []
  } catch (_error) {
    tarefasValidade.value = []
  }
}

function formatQuantidade(value) {
  if (value == null) return '0'
  return String(value)
}

function statusColor(status) {
  switch (status) {
    case 'VENCIDO':
      return 'negative'
    case 'CRITICO':
      return 'deep-orange'
    case 'ATENCAO':
      return 'warning'
    case 'OK':
      return 'positive'
    default:
      return 'grey'
  }
}

function formatDataHora(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function formatDelta(value) {
  const numero = Number(value || 0)
  if (Number.isNaN(numero)) return String(value)
  return `${numero >= 0 ? '+' : ''}${numero}`
}

function abrirDialogCriacao() {
  dialogCriacao.form.skuId = filtros.skuId || skus.value[0]?.skuId || null
  dialogCriacao.form.lote = ''
  dialogCriacao.form.dataValidade = ''
  dialogCriacao.form.quantidade = null
  dialogCriacao.form.observacao = ''
  dialogCriacao.aberto = true
}

function abrirDialogAjuste(sku, lote) {
  dialogAjuste.estoqueLoteId = lote.estoqueLoteId
  dialogAjuste.contexto = `${sku.skuDescricao} | ${lote.lote || 'SEM_LOTE'} | Atual: ${formatQuantidade(lote.quantidade)}`
  dialogAjuste.form.acao = 'SET'
  dialogAjuste.form.quantidade = Number(lote.quantidade || 0)
  dialogAjuste.form.observacao = ''
  dialogAjuste.aberto = true
}

function abrirDialogZero(_sku, lote) {
  dialogZero.estoqueLoteId = lote.estoqueLoteId
  dialogZero.loteLabel = lote.lote || 'SEM_LOTE'
  dialogZero.form.observacao = ''
  dialogZero.aberto = true
}

async function abrirDialogHistorico(sku, lote) {
  dialogHistorico.aberto = true
  dialogHistorico.loading = true
  dialogHistorico.erro = ''
  dialogHistorico.estoqueLoteId = lote.estoqueLoteId
  dialogHistorico.contexto = `${sku.skuDescricao} | ${lote.lote || 'SEM_LOTE'}`
  dialogHistorico.movimentos = []

  try {
    dialogHistorico.movimentos = await apiRequest(`/api/validade/lotes/${lote.estoqueLoteId}/movimentos`)
  } catch (e) {
    dialogHistorico.erro = extractErrorMessage(e, 'Falha ao carregar histórico do lote.')
  } finally {
    dialogHistorico.loading = false
  }
}

async function criarLote() {
  if (!dialogCriacao.form.skuId || !dialogCriacao.form.quantidade || Number(dialogCriacao.form.quantidade) <= 0) {
    $q.notify({ type: 'warning', message: 'Informe SKU e quantidade válida.' })
    return
  }

  dialogCriacao.salvando = true
  try {
    await apiRequest(`/api/validade/produtos/${props.recordId}/lotes`, 'POST', {
      skuId: dialogCriacao.form.skuId,
      lote: dialogCriacao.form.lote || null,
      dataValidade: dialogCriacao.form.dataValidade || null,
      quantidade: dialogCriacao.form.quantidade,
      observacao: dialogCriacao.form.observacao || null,
    })
    dialogCriacao.aberto = false
    $q.notify({ type: 'positive', message: 'Lote criado com sucesso.' })
    await carregar()
  } catch (e) {
    $q.notify({ type: 'negative', message: extractErrorMessage(e, 'Falha ao criar lote.') })
  } finally {
    dialogCriacao.salvando = false
  }
}

async function ajustarLote() {
  if (!dialogAjuste.estoqueLoteId || !dialogAjuste.form.quantidade || Number(dialogAjuste.form.quantidade) <= 0) {
    $q.notify({ type: 'warning', message: 'Informe uma quantidade válida para o ajuste.' })
    return
  }

  dialogAjuste.salvando = true
  try {
    await apiRequest(`/api/validade/lotes/${dialogAjuste.estoqueLoteId}/ajustar`, 'POST', {
      acao: dialogAjuste.form.acao,
      quantidade: dialogAjuste.form.quantidade,
      observacao: dialogAjuste.form.observacao || null,
    })
    dialogAjuste.aberto = false
    $q.notify({ type: 'positive', message: 'Lote ajustado com sucesso.' })
    await carregar()
  } catch (e) {
    $q.notify({ type: 'negative', message: extractErrorMessage(e, 'Falha ao ajustar lote.') })
  } finally {
    dialogAjuste.salvando = false
  }
}

async function zerarLote() {
  if (!dialogZero.estoqueLoteId) {
    return
  }

  dialogZero.salvando = true
  try {
    await apiRequest(`/api/validade/lotes/${dialogZero.estoqueLoteId}/zerar`, 'POST', {
      observacao: dialogZero.form.observacao || null,
    })
    dialogZero.aberto = false
    $q.notify({ type: 'positive', message: 'Lote zerado com sucesso.' })
    await carregar()
  } catch (e) {
    $q.notify({ type: 'negative', message: extractErrorMessage(e, 'Falha ao zerar lote.') })
  } finally {
    dialogZero.salvando = false
  }
}

function extractErrorMessage(error, fallback) {
  if (typeof error === 'string') return error
  if (error?.message) return error.message
  if (error?.error) return error.error
  return fallback
}

async function abrirTarefaValidade() {
  if (!props.recordId) {
    return
  }

  loadingAbrirTarefa.value = true
  try {
    await carregarTarefasValidade()
    const destino = tarefaEmAberto.value
      ? `/mobile/validade/tarefa/${tarefaEmAberto.value.id}`
      : '/mobile/validade/tarefa/nova'

    router.push({
      path: destino,
      query: {
        produtoId: String(props.recordId),
        produtoNome: props.modelValue?.nome || produtoValidade.value?.produtoNome || '',
      },
    })
  } finally {
    loadingAbrirTarefa.value = false
  }
}

watch(() => props.recordId, carregar, { immediate: true })

watch(
  () => props.modelValue?.controlaValidade,
  () => {
    if (props.recordId) {
      carregar()
    }
  }
)

onMounted(() => {
  if (props.recordId) {
    carregar()
  }
})
</script>

<style scoped>
.produto-validade-tab {
  padding: 16px;
}

.rounded-borders {
  border-radius: 8px;
}
</style>
