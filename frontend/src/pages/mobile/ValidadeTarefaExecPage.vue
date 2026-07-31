<template>
  <q-page class="q-pa-md bg-grey-2 exec-page">
    <div class="page-shell">
      <div class="page-header q-mb-md">
        <div>
          <div class="text-h6 q-mb-xs">Execução de Validade</div>
          <div class="text-subtitle2 text-grey-7">{{ stateDescription }}</div>
        </div>
        <div class="row items-center q-gutter-sm">
          <q-btn
            v-if="canManageObservacao"
            flat
            color="primary"
            :icon="observacaoActionIcon"
            :label="observacaoActionLabel"
            @click="abrirDialogObservacao"
          />
          <q-btn
            v-if="tarefa && tarefa.status !== 'RASCUNHO'"
            flat
            color="primary"
            icon="arrow_back"
            label="Voltar para operação"
            @click="voltarOperacao"
          />
        </div>
      </div>

      <q-banner v-if="produtoContextoId" dense rounded class="bg-blue-1 text-blue-9 q-mb-md">
        <template #avatar>
          <q-icon name="inventory_2" color="blue" />
        </template>
        Contexto do produto: {{ produtoContextoNome || `#${produtoContextoId}` }}
      </q-banner>

      <q-card v-if="tarefa" flat bordered class="q-mb-md summary-card">
        <q-card-section class="summary-section">
          <div class="summary-main">
            <div class="text-overline text-grey-7">Tarefa atual</div>
            <div class="summary-title-row">
              <div>
                <div class="summary-title">Tarefa #{{ tarefa.id }}</div>
                <div class="text-caption text-grey-7">{{ statusMeta.helper }}</div>
              </div>
              <div class="summary-inline-kpis">
                <div class="summary-kpi summary-kpi--compact">
                  <div class="summary-kpi__label">Criada em</div>
                  <div class="summary-kpi__value">{{ formatDate(tarefa.criadoEm) }}</div>
                </div>
                <div class="summary-kpi summary-kpi--compact">
                  <div class="summary-kpi__label">Itens lançados</div>
                  <div class="summary-kpi__value">{{ itemCount }}</div>
                </div>
              </div>
            </div>
          </div>
          <div class="summary-meta">
            <q-chip :color="statusMeta.color" :text-color="statusMeta.textColor" :icon="statusMeta.icon" size="sm">
              {{ statusMeta.label }}
            </q-chip>
          </div>
        </q-card-section>
        <q-separator v-if="observacao" />
        <q-card-section v-if="observacao" class="q-pt-sm">
          <div class="summary-kpi">
            <div class="summary-kpi__label">Observação atual</div>
            <div class="summary-kpi__value summary-kpi__value--text">{{ observacao }}</div>
          </div>
        </q-card-section>
      </q-card>

      <q-card v-else flat bordered class="q-mb-md">
        <q-card-section class="text-grey-7">
          Carregando tarefa...
        </q-card-section>
      </q-card>

      <q-banner
        v-if="tarefa && tarefa.status === 'FINALIZADA' && temDivergencias"
        dense
        rounded
        class="bg-orange-1 text-orange-10 q-mb-md"
      >
        <template #avatar>
          <q-icon name="warning" color="orange-8" />
        </template>
        <span>A tarefa foi finalizada com divergências. Revise o resultado antes de sair.</span>
      </q-banner>

      <q-banner
        v-if="tarefa && tarefa.status === 'CANCELADA'"
        dense
        rounded
        class="bg-red-1 text-red-10 q-mb-md"
      >
        <template #avatar>
          <q-icon name="cancel" color="red-8" />
        </template>
        Esta tarefa foi cancelada e não pode mais ser editada.
      </q-banner>

      <q-card v-if="tarefa && tarefa.status === 'RASCUNHO'" flat bordered class="q-mb-md launch-shell">
        <q-card-section class="section-header">
          <div>
            <div class="text-subtitle1 text-weight-medium">Lançamento da contagem</div>
            <div class="text-caption text-grey-7">Use esta área para registrar os itens e revisar rapidamente o que já foi lançado.</div>
          </div>
        </q-card-section>
        <q-separator />
        <q-card-section>
          <TarefaEditorMobile
            :tarefa="tarefa"
            :loading="loadingItens"
            :loading-finalizar="loadingFinalizar"
            :show-finalizar="false"
            :show-header="false"
            :initial-produto-id="produtoContextoId"
            :initial-produto-name="produtoContextoNome"
            @add-item="adicionarItem"
            @remove-item="removerItem"
            @sku-change="setSkuSelecionado"
          />
        </q-card-section>
      </q-card>

      <q-banner
        v-if="tarefa && tarefa.status === 'RASCUNHO' && !itemCount"
        dense
        rounded
        class="bg-orange-1 text-orange-10 q-mb-md"
      >
        <template #avatar>
          <q-icon name="info" color="orange-8" />
        </template>
        Lance pelo menos um item para habilitar a finalização da tarefa.
      </q-banner>

      <q-card v-if="tarefa && tarefa.status === 'FINALIZADA' && temDivergencias" flat bordered class="q-mb-md">
        <q-card-section class="section-header">
          <div>
            <div class="text-subtitle1 text-weight-medium">Revisão de divergências</div>
            <div class="text-caption text-grey-7">Trate as diferenças encontradas antes de encerrar o fluxo.</div>
          </div>
        </q-card-section>
        <q-separator />
        <q-card-section>
          <DivergenciaListMobile
            :divergencias="divergencias"
            :loading="loadingDivergencias"
            @refresh="carregarDivergencias"
            @treated="aposTratarDivergencia"
          />
        </q-card-section>
      </q-card>

      <q-card v-if="tarefa && tarefa.status !== 'RASCUNHO' && itemCount" flat bordered class="q-mb-md">
        <q-card-section class="section-header">
          <div>
            <div class="text-subtitle1 text-weight-medium">Itens registrados</div>
            <div class="text-caption text-grey-7">Consulta dos itens lançados nesta tarefa.</div>
          </div>
        </q-card-section>
        <q-separator />
        <q-list separator>
          <q-item
            v-for="item in orderedItems"
            :key="item.id"
            class="history-item-row"
          >
            <q-item-section>
              <div class="row items-center justify-between q-col-gutter-sm">
                <div class="col">
                  <div class="text-body1 text-weight-medium">{{ item.produtoNome || `SKU ${item.skuId}` }}</div>
                </div>
                <div class="col-auto">
                  <q-chip dense square color="grey-2" text-color="grey-8">{{ actionLabel(item.acao) }}</q-chip>
                </div>
              </div>
              <div class="text-caption text-grey-7 q-mt-xs">
                Lote: {{ item.lote || 'SEM_LOTE' }} | Validade: {{ formatItemDate(item.dataValidade) }}
              </div>
              <div class="text-caption text-grey-7">Quantidade: {{ item.quantidade }}</div>
            </q-item-section>
          </q-item>
        </q-list>
      </q-card>

      <q-card v-if="tarefa && tarefa.status === 'FINALIZADA' && !temDivergencias" flat bordered class="q-mb-md status-card">
        <q-card-section class="row items-center justify-between q-col-gutter-md">
          <div class="col">
            <div class="text-subtitle1 text-weight-medium">Conciliação concluída</div>
            <div class="text-caption text-grey-7">Tarefa finalizada sem divergências pendentes.</div>
          </div>
          <div class="col-auto">
            <q-btn color="primary" icon="arrow_back" label="Voltar para operação" @click="voltarOperacao" />
          </div>
        </q-card-section>
      </q-card>

      <q-dialog v-model="dialogLotes" persistent maximized transition-show="slide-up" transition-hide="slide-down">
        <q-card class="bg-white">
          <q-card-section class="row items-center justify-between">
            <div class="text-subtitle1">Lotes do SKU {{ skuSelecionado || '-' }}</div>
            <q-btn flat round icon="close" v-close-popup />
          </q-card-section>
          <q-separator />
          <q-card-section>
            <SkuLotesViewer
              ref="lotesViewerRef"
              :sku-id="skuSelecionado"
              :tarefa-id="tarefa?.id"
              @refresh-tarefa="refetchTarefa"
            />
          </q-card-section>
        </q-card>
      </q-dialog>

      <q-dialog v-model="dialogObservacao">
        <q-card class="observacao-dialog">
          <q-card-section class="row items-center q-pb-none">
            <div class="text-h6">Observação da tarefa</div>
            <q-space />
            <q-btn icon="close" flat round dense v-close-popup />
          </q-card-section>

          <q-card-section class="q-gutter-md">
            <q-input
              v-model="observacaoDialogValue"
              outlined
              type="textarea"
              label="Observação"
              autogrow
              :disable="loadingObservacao"
            />
          </q-card-section>

          <q-card-actions align="right">
            <q-btn flat label="Cancelar" v-close-popup />
            <q-btn color="primary" label="Salvar" :loading="loadingObservacao" @click="salvarObservacaoDialog" />
          </q-card-actions>
        </q-card>
      </q-dialog>

      <div v-if="tarefa && tarefa.status === 'RASCUNHO'" class="sticky-actions">
        <q-btn
          color="positive"
          icon="check_circle"
          label="Finalizar tarefa"
          class="full-width q-mb-sm"
          :disable="!canFinalizeTask"
          :loading="loadingFinalizar"
          @click="finalizarTarefa"
        />
        <q-btn
          color="negative"
          icon="cancel"
          label="Cancelar tarefa"
          class="full-width"
          :disable="loadingItens || loadingFinalizar || loadingCancelar"
          :loading="loadingCancelar"
          @click="cancelarTarefa"
        />
      </div>
    </div>
  </q-page>
</template>

<script setup>
import { ref, onMounted, nextTick, computed } from 'vue'
import { useQuasar } from 'quasar'
import { useRoute, useRouter } from 'vue-router'
import { useApiRequest } from '@/composables/useApiRequest'
import TarefaEditorMobile from '@/components/validade/mobile/TarefaEditorMobile.vue'
import SkuLotesViewer from '@/components/validade/mobile/SkuLotesViewer.vue'
import DivergenciaListMobile from '@/components/validade/mobile/DivergenciaListMobile.vue'

const $q = useQuasar()
const route = useRoute()
const router = useRouter()
const { apiRequest } = useApiRequest()

const tarefa = ref(null)
const divergencias = ref([])
const skuSelecionado = ref(null)
const observacao = ref('')
const observacaoSalva = ref('')
const observacaoDialogValue = ref('')
const dialogObservacao = ref(false)
const dialogLotes = ref(false)
const lotesViewerRef = ref(null)

const loadingCriar = ref(false)
const loadingObservacao = ref(false)
const loadingItens = ref(false)
const loadingFinalizar = ref(false)
const loadingDivergencias = ref(false)
const loadingCancelar = ref(false)

const isNova = computed(() => route.params.id === 'nova')
const itemCount = computed(() => tarefa.value?.itens?.length || 0)
const temDivergencias = computed(() => divergencias.value.some(divergencia => divergencia?.acaoTomada === 'PENDENTE'))
const orderedItems = computed(() => [...(tarefa.value?.itens || [])].sort((a, b) => Number(b?.id || 0) - Number(a?.id || 0)))
const canFinalizeTask = computed(() => tarefa.value?.status === 'RASCUNHO' && itemCount.value > 0 && !loadingItens.value && !loadingFinalizar.value && !loadingCancelar.value)
const canOpenLotes = computed(() => !!skuSelecionado.value && tarefa.value?.status === 'RASCUNHO')
const canManageObservacao = computed(() => !!tarefa.value && (tarefa.value.status === 'RASCUNHO' || !!observacao.value))
const observacaoActionLabel = computed(() => tarefa.value?.status === 'RASCUNHO' ? 'Observação' : 'Ver observação')
const observacaoActionIcon = computed(() => tarefa.value?.status === 'RASCUNHO' ? 'edit_note' : 'visibility')
const statusMeta = computed(() => {
  switch (tarefa.value?.status) {
    case 'FINALIZADA':
      return {
        label: 'Finalizada',
        color: 'green-2',
        textColor: 'green-10',
        icon: 'check_circle',
        helper: temDivergencias.value ? 'Aguardando revisão de divergências' : 'Fluxo concluído sem pendências'
      }
    case 'CANCELADA':
      return {
        label: 'Cancelada',
        color: 'red-2',
        textColor: 'red-10',
        icon: 'cancel',
        helper: 'Esta tarefa foi encerrada sem possibilidade de edição'
      }
    default:
      return {
        label: 'Em aberto',
        color: 'orange-2',
        textColor: 'orange-10',
        icon: 'edit_note',
        helper: 'Lance os itens e finalize quando a contagem estiver concluída'
      }
  }
})
const stateDescription = computed(() => {
  switch (tarefa.value?.status) {
    case 'FINALIZADA':
      return temDivergencias.value
        ? 'Revise as divergências geradas pela finalização antes de encerrar o fluxo.'
        : 'A tarefa já foi concluída e não possui divergências pendentes.'
    case 'CANCELADA':
      return 'Esta tarefa foi cancelada e mantida apenas para consulta.'
    default:
      return 'Lance os itens da contagem e revise o resultado antes de sair.'
  }
})
const produtoContextoId = computed(() => {
  const value = route.query.produtoId
  if (!value) return null
  const parsed = Number(value)
  return Number.isNaN(parsed) ? null : parsed
})
const produtoContextoNome = computed(() => {
  const value = route.query.produtoNome
  return typeof value === 'string' && value.trim() ? value.trim() : null
})

const extractErrorMsg = (e, fallback) => e?.error?.message || e?.message || fallback || 'Erro'
const notifyError = (msg) => $q.notify({ type: 'negative', message: msg || 'Erro' })
const notifyOk = (msg) => $q.notify({ type: 'positive', message: msg })
const formatDate = (value) => value ? new Date(value).toLocaleString('pt-BR') : '-'
const formatItemDate = (value) => {
  if (!value) return 'SEM_VALIDADE'
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return value
  const [, year, month, day] = match
  return `${day}/${month}/${year}`
}

function actionLabel(action) {
  switch (action) {
    case 'ADD': return 'Adicionar'
    case 'REMOVE': return 'Remover'
    default: return 'Definir'
  }
}

async function carregarTarefa() {
  if (isNova.value) {
    await iniciarTarefaAuto()
    return
  }
  try {
    const resp = await apiRequest(`/api/validade/tarefas/${route.params.id}`)
    tarefa.value = resp
    observacao.value = resp?.observacao || ''
    observacaoSalva.value = observacao.value
    observacaoDialogValue.value = observacao.value
    if (resp?.status === 'FINALIZADA') {
      await carregarDivergencias()
    }
  } catch (e) {
    notifyError(extractErrorMsg(e, 'Falha ao carregar tarefa'))
  }
}

async function iniciarTarefaAuto() {
  if (loadingCriar.value) return
  loadingCriar.value = true
  try {
    const body = { observacao: observacao.value || null }
    const resp = await apiRequest('/api/validade/tarefas', { method: 'POST', body })
    tarefa.value = resp
    observacaoDialogValue.value = resp?.observacao || ''
    await router.replace({
      path: `/mobile/validade/tarefa/${resp.id}`,
      query: route.query,
    })
    notifyOk('Tarefa criada')
  } catch (e) {
    notifyError(extractErrorMsg(e, 'Falha ao criar tarefa'))
  } finally {
    loadingCriar.value = false
  }
}

async function salvarObservacao() {
  if (!tarefa.value) return
  if (observacao.value === observacaoSalva.value) return
  loadingObservacao.value = true
  try {
    const resp = await apiRequest(`/api/validade/tarefas/${tarefa.value.id}/observacao`, {
      method: 'PUT',
      body: { observacao: observacao.value || null }
    })
    tarefa.value = resp
    observacaoSalva.value = observacao.value
    observacaoDialogValue.value = observacao.value
    notifyOk('Observação salva')
  } catch (e) {
    notifyError(extractErrorMsg(e, 'Falha ao salvar observação'))
  } finally {
    loadingObservacao.value = false
  }
}

function abrirDialogObservacao() {
  observacaoDialogValue.value = observacao.value
  dialogObservacao.value = true
}

async function salvarObservacaoDialog() {
  if (tarefa.value?.status !== 'RASCUNHO') {
    dialogObservacao.value = false
    return
  }
  observacao.value = observacaoDialogValue.value
  await salvarObservacao()
  if (!loadingObservacao.value) {
    dialogObservacao.value = false
  }
}

function setSkuSelecionado(id) {
  skuSelecionado.value = id
}

async function adicionarItem(payload) {
  if (!tarefa.value) {
    notifyError('Tarefa não iniciada')
    return
  }
  loadingItens.value = true
  try {
    const resp = await apiRequest(`/api/validade/tarefas/${tarefa.value.id}/itens`, {
      method: 'POST',
      body: [payload]
    })
    tarefa.value = resp
    notifyOk('Item adicionado')
  } catch (e) {
    notifyError(extractErrorMsg(e, 'Falha ao adicionar item'))
  } finally {
    loadingItens.value = false
  }
}

async function removerItem(item) {
  if (!tarefa.value || !item?.id) {
    notifyError('Item inválido para remoção')
    return
  }

  const shouldRemove = await confirmarAcao({
    title: 'Remover item',
    message: `Deseja remover o item "${item.produtoNome || `SKU ${item.skuId}`}" da tarefa #${tarefa.value.id}?`,
    okLabel: 'Remover',
    okColor: 'negative',
  })

  if (!shouldRemove) return

  loadingItens.value = true
  try {
    const resp = await apiRequest(`/api/validade/tarefas/${tarefa.value.id}/itens/${item.id}`, 'DELETE')
    tarefa.value = resp
    notifyOk('Item removido')
  } catch (e) {
    notifyError(extractErrorMsg(e, 'Falha ao remover item'))
  } finally {
    loadingItens.value = false
  }
}

async function finalizarTarefa() {
  if (!tarefa.value) return
  const shouldFinalize = await confirmarAcao({
    title: 'Finalizar tarefa',
    message: `Deseja finalizar a tarefa #${tarefa.value.id} com ${itemCount.value} item(ns) lançado(s)?`,
    okLabel: 'Finalizar',
    okColor: 'positive',
  })
  if (!shouldFinalize) return
  loadingFinalizar.value = true
  try {
    const resp = await apiRequest(`/api/validade/tarefas/${tarefa.value.id}/finalizar`, { method: 'POST' })
    tarefa.value = resp
    await carregarDivergencias()
    if (temDivergencias.value) {
      notifyOk('Tarefa finalizada. Revise as divergências antes de sair.')
      return
    }
    notifyOk('Tarefa finalizada com sucesso')
  } catch (e) {
    notifyError(extractErrorMsg(e, 'Falha ao finalizar tarefa'))
  } finally {
    loadingFinalizar.value = false
  }
}

async function cancelarTarefa() {
  if (!tarefa.value) return
  const shouldCancel = await confirmarAcao({
    title: 'Cancelar tarefa',
    message: `Deseja cancelar a tarefa #${tarefa.value.id}? Esta ação encerra a edição da contagem.`,
    okLabel: 'Cancelar tarefa',
    okColor: 'negative',
  })
  if (!shouldCancel) return
  loadingCancelar.value = true
  try {
    const resp = await apiRequest(`/api/validade/tarefas/${tarefa.value.id}/cancelar`, { method: 'POST' })
    tarefa.value = resp
    notifyOk('Tarefa cancelada')
    router.push('/validade/operacao')
  } catch (e) {
    notifyError(extractErrorMsg(e, 'Falha ao cancelar tarefa'))
  } finally {
    loadingCancelar.value = false
  }
}

async function carregarDivergencias() {
  if (!tarefa.value) return
  loadingDivergencias.value = true
  try {
    const resp = await apiRequest(`/api/validade/divergencias?tarefaId=${tarefa.value.id}`)
    divergencias.value = resp || []
  } catch (e) {
    notifyError(extractErrorMsg(e, 'Falha ao carregar divergências'))
  } finally {
    loadingDivergencias.value = false
  }
}

async function aposTratarDivergencia() {
  await carregarDivergencias()
}

function confirmarAcao({ title, message, okLabel, okColor }) {
  return new Promise((resolve) => {
    $q.dialog({
      title,
      message,
      cancel: true,
      persistent: true,
      ok: {
        label: okLabel,
        color: okColor,
        unelevated: true,
      },
    }).onOk(() => resolve(true))
      .onCancel(() => resolve(false))
      .onDismiss(() => resolve(false))
  })
}

function abrirLotes() {
  if (!skuSelecionado.value) return
  dialogLotes.value = true
  nextTick(() => {
    if (lotesViewerRef.value?.carregar) {
      lotesViewerRef.value.carregar()
    }
  })
}

async function refetchTarefa() {
  if (!tarefa.value) return
  try {
    const resp = await apiRequest(`/api/validade/tarefas/${tarefa.value.id}`)
    tarefa.value = resp
  } catch (e) {
    notifyError(extractErrorMsg(e, 'Falha ao atualizar tarefa'))
  }
}

function voltarOperacao() {
  router.push('/validade/operacao')
}

onMounted(() => {
  carregarTarefa()
})
</script>

<style scoped>
.bg-grey-2 { background: #f7f7f9; }
.exec-page { padding-bottom: 88px; }
.page-shell {
  max-width: 980px;
  margin: 0 auto;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.summary-card {
  border-radius: 12px;
  overflow: hidden;
}
.summary-section {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.summary-main {
  min-width: 0;
  flex: 1;
}
.summary-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-top: 2px;
}
.summary-title {
  font-size: 1.35rem;
  font-weight: 700;
  color: #2c221d;
}
.summary-inline-kpis {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}
.summary-meta {
  display: flex;
  align-items: flex-start;
}
.summary-kpis {
  align-items: stretch;
}
.summary-kpi {
  height: 100%;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fbf8f5;
  border: 1px solid rgba(198, 124, 72, 0.12);
}
.summary-kpi__label {
  font-size: 0.72rem;
  text-transform: uppercase;
  color: #8a776a;
  margin-bottom: 6px;
}
.summary-kpi__value {
  font-size: 0.92rem;
  color: #2c221d;
  font-weight: 600;
}
.summary-kpi__value--text {
  font-weight: 500;
  line-height: 1.35;
}
.summary-kpi--compact {
  min-width: 164px;
  padding: 8px 10px;
}
.launch-shell {
  border-radius: 12px;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.completion-card {
  border-radius: 12px;
}
.status-card {
  border-radius: 12px;
}
.history-item-row {
  align-items: flex-start;
}
.observacao-dialog {
  width: min(560px, 92vw);
}
.sticky-actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12px 16px;
  background: #ffffff;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
}
@media (max-width: 640px) {
  .page-header,
  .section-header,
  .summary-section {
    flex-direction: column;
    align-items: stretch;
  }

  .summary-title-row,
  .summary-inline-kpis {
    flex-direction: column;
  }

  .summary-kpi--compact {
    min-width: 0;
  }
}
</style>
