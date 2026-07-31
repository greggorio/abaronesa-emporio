<template>
  <q-dialog v-model="show" maximized transition-show="slide-up" transition-hide="slide-down">
    <q-card style="background: #f5f1ed;">
      <!-- Premium Header -->
      <div class="premium-header">
        <div class="header-content">
          <div class="header-left">
            <q-icon name="assignment" size="32px" style="color: #6B3E26;" />
            <div class="header-text">
              <div class="header-title">Pedido #{{ pedido?.id }}</div>
              <div class="header-subtitle">Detalhes do Pedido de Compra</div>
            </div>
          </div>
          <div class="header-right">
            <q-badge
              v-if="pedido?.status"
              :style="`background: ${statusColor(pedido.status)}; color: white; padding: 8px 16px; border-radius: 20px; font-weight: 500; font-size: 13px;`"
              class="q-mr-md"
            >
              {{ statusLabel(pedido.status) }}
            </q-badge>

            <!-- Botão Editar no Header -->
            <q-btn
              v-if="!modoEdicao && podeEditar"
              unelevated
              icon="edit"
              label="Editar"
              @click="ativarEdicao"
              class="q-mr-md"
              style="background: #6B3E26; color: white;"
            >
              <q-tooltip>Editar Pedido</q-tooltip>
            </q-btn>

            <!-- Botões Salvar/Cancelar no Header (modo edição) -->
            <template v-if="modoEdicao">
              <q-btn
                outline
                icon="close"
                label="Cancelar"
                @click="cancelarEdicao"
                class="q-mr-sm"
                style="color: #6B3E26; border-color: #6B3E26;"
              >
                <q-tooltip>Cancelar Edição</q-tooltip>
              </q-btn>
              <q-btn
                unelevated
                icon="save"
                label="Salvar"
                @click="salvarCabecalho"
                :loading="salvandoCabecalho"
                class="q-mr-md"
                style="background: #B5854C; color: white;"
              >
                <q-tooltip>Salvar Alterações</q-tooltip>
              </q-btn>
            </template>

            <q-btn
              flat
              round
              icon="close"
              v-close-popup
              style="color: #6B3E26;"
            >
              <q-tooltip>Fechar</q-tooltip>
            </q-btn>
          </div>
        </div>
      </div>

      <!-- Content -->
      <q-card-section class="q-pa-md">
        <div class="row q-col-gutter-md">
          <!-- Informações do Pedido -->
          <div class="col-12">
            <div class="section-card">
              <div class="section-header">
                <div class="section-title">Informações do Pedido</div>
              </div>

              <div class="row q-col-gutter-md q-mt-sm">
                <!-- Fornecedor -->
                <div class="col-12 col-sm-6 col-md-3">
                  <div class="info-card">
                    <div class="info-label">FORNECEDOR</div>
                    <q-select
                      v-if="modoEdicao"
                      v-model="formCabecalho.fornecedorId"
                      :options="fornecedores"
                      option-value="id"
                      option-label="nomeExibicao"
                      emit-value
                      map-options
                      outlined
                      dense
                      clearable
                      :rules="[val => !!val || 'Campo obrigatório']"
                      use-input
                      input-debounce="300"
                      @filter="filterFornecedores"
                      style="margin-top: 4px;"
                    >
                      <template v-slot:no-option>
                        <q-item>
                          <q-item-section class="text-grey">
                            Nenhum fornecedor encontrado
                          </q-item-section>
                        </q-item>
                      </template>
                    </q-select>
                    <div v-else class="info-value">{{ pedido?.fornecedorNome || '—' }}</div>
                  </div>
                </div>

                <!-- Data Prevista -->
                <div class="col-12 col-sm-6 col-md-3">
                  <div class="info-card">
                    <div class="info-label">DATA PREVISTA</div>
                    <q-input
                      v-if="modoEdicao"
                      v-model="formCabecalho.dataPrevista"
                      type="date"
                      outlined
                      dense
                      style="margin-top: 4px;"
                    />
                    <div v-else class="info-value">{{ formatDate(pedido?.dataPrevista) }}</div>
                  </div>
                </div>

                <!-- Status -->
                <div class="col-12 col-sm-6 col-md-3">
                  <div class="info-card">
                    <div class="info-label">STATUS</div>
                    <q-select
                      v-if="modoEdicao"
                      v-model="formCabecalho.status"
                      :options="statusOptionsDisponiveis"
                      option-value="value"
                      option-label="label"
                      emit-value
                      map-options
                      outlined
                      dense
                      style="margin-top: 4px;"
                    />
                    <div v-else>
                      <q-badge
                        :style="`background: ${statusColor(pedido?.status)}; color: white; padding: 6px 12px; border-radius: 16px; font-weight: 500;`"
                      >
                        {{ statusLabel(pedido?.status) }}
                      </q-badge>
                    </div>
                  </div>
                </div>

                <!-- Valor Total -->
                <div class="col-12 col-sm-6 col-md-3">
                  <div class="metric-card" style="border-left-color: #B5854C;">
                    <div class="info-label">VALOR TOTAL</div>
                    <div class="metric-value" style="color: #B5854C;">{{ formatCurrency(pedido?.valorTotal) }}</div>
                  </div>
                </div>

                <!-- Criado Em -->
                <div class="col-12 col-sm-6 col-md-3">
                  <div class="info-card">
                    <div class="info-label">CRIADO EM</div>
                    <div class="info-value-small">{{ formatDateTime(pedido?.criadoEm) }}</div>
                  </div>
                </div>

                <!-- Atualizado Em -->
                <div class="col-12 col-sm-6 col-md-3">
                  <div class="info-card">
                    <div class="info-label">ATUALIZADO EM</div>
                    <div class="info-value-small">{{ formatDateTime(pedido?.atualizadoEm) }}</div>
                  </div>
                </div>

                <!-- Observação -->
                <div class="col-12">
                  <div class="info-card">
                    <div class="info-label">OBSERVAÇÃO</div>
                    <q-input
                      v-if="modoEdicao"
                      v-model="formCabecalho.observacao"
                      type="textarea"
                      outlined
                      dense
                      rows="2"
                      style="margin-top: 4px;"
                      placeholder="Adicione observações sobre o pedido..."
                    />
                    <div v-else class="info-value-small">{{ pedido?.observacao || '—' }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Itens do Pedido -->
          <div class="col-12">
            <div class="section-card">
              <div class="section-header">
                <div class="section-title">
                  Itens do Pedido
                  <q-badge
                    v-if="pedido?.itens?.length"
                    :label="pedido.itens.length"
                    color="grey-6"
                    class="q-ml-sm"
                  />
                </div>
                <q-btn
                  v-if="podeEditarItens"
                  unelevated
                  icon="add"
                  label="Adicionar Item"
                  @click="dialogAdicionarItem = true"
                  style="background: #6B3E26; color: white;"
                >
                  <q-tooltip>Adicionar novo item ao pedido</q-tooltip>
                </q-btn>
              </div>

              <div class="q-mt-md">
                <q-table
                  flat
                  :rows="pedido?.itens || []"
                  :columns="colunasItens"
                  row-key="id"
                  :rows-per-page-options="[10, 20, 50]"
                  :pagination="{ rowsPerPage: 10 }"
                  class="dashboard-table"
                >
                  <template v-slot:body-cell-produtoNome="props">
                    <q-td :props="props">
                      <div class="text-body2" style="font-weight: 500; color: #333;">
                        {{ props.row.produtoNome }}
                      </div>
                      <div class="text-caption" style="color: #666;">
                        SKU: {{ props.row.skuNome || '—' }} | Emb: {{ props.row.embalagemNome || '—' }}
                      </div>
                    </q-td>
                  </template>

                  <template v-slot:body-cell-quantidade="props">
                    <q-td :props="props">
                      <q-input
                        v-if="itemEmEdicao === props.row.id"
                        v-model.number="formItem.quantidade"
                        type="number"
                        dense
                        outlined
                        min="0"
                        step="0.001"
                        style="max-width: 120px"
                        :rules="[val => val > 0 || 'Deve ser maior que zero']"
                      />
                      <span v-else style="font-weight: 500;">{{ props.row.quantidade }}</span>
                    </q-td>
                  </template>

                  <template v-slot:body-cell-custoUnitario="props">
                    <q-td :props="props">
                      <q-input
                        v-if="itemEmEdicao === props.row.id"
                        v-model.number="formItem.custoUnitario"
                        type="number"
                        dense
                        outlined
                        min="0"
                        step="0.01"
                        prefix="R$"
                        style="max-width: 140px"
                      />
                      <span v-else style="font-weight: 500;">{{ formatCurrency(props.row.custoUnitario) }}</span>
                    </q-td>
                  </template>

                  <template v-slot:body-cell-subtotal="props">
                    <q-td :props="props" class="text-right">
                      <strong v-if="itemEmEdicao === props.row.id" style="color: #B5854C; font-size: 15px;">
                        {{ formatCurrency(subtotalItemEditando) }}
                      </strong>
                      <strong v-else style="color: #B5854C; font-size: 15px;">
                        {{ formatCurrency(props.row.subtotal) }}
                      </strong>
                    </q-td>
                  </template>

                  <template v-slot:body-cell-status="props">
                    <q-td :props="props">
                      <q-badge
                        :style="`background: ${statusItemColor(props.row.status)}; color: white; padding: 6px 12px; border-radius: 16px; font-weight: 500;`"
                      >
                        {{ statusItemLabel(props.row.status) }}
                      </q-badge>
                    </q-td>
                  </template>

                  <template v-slot:body-cell-acoes="props">
                    <q-td :props="props">
                      <div v-if="podeEditarItens" class="row q-gutter-xs no-wrap">
                        <template v-if="itemEmEdicao === props.row.id">
                          <q-btn
                            dense
                            flat
                            round
                            size="sm"
                            icon="check"
                            @click="salvarItem"
                            :loading="salvandoItem"
                            style="color: #B5854C;"
                          >
                            <q-tooltip>Salvar</q-tooltip>
                          </q-btn>
                          <q-btn
                            dense
                            flat
                            round
                            size="sm"
                            icon="close"
                            @click="cancelarEdicaoItem"
                            style="color: #666;"
                          >
                            <q-tooltip>Cancelar</q-tooltip>
                          </q-btn>
                        </template>
                        <template v-else>
                          <q-btn
                            dense
                            flat
                            round
                            size="sm"
                            icon="edit"
                            @click="editarItem(props.row)"
                            style="color: #6B3E26;"
                          >
                            <q-tooltip>Editar</q-tooltip>
                          </q-btn>
                          <q-btn
                            dense
                            flat
                            round
                            size="sm"
                            icon="delete"
                            @click="confirmarRemocaoItem(props.row)"
                            style="color: #D65A31;"
                          >
                            <q-tooltip>Remover</q-tooltip>
                          </q-btn>
                        </template>
                      </div>
                      <span v-else style="color: #999;">—</span>
                    </q-td>
                  </template>

                  <template v-slot:no-data>
                    <div class="full-width row flex-center q-pa-lg" style="color: #999;">
                      <q-icon name="inventory_2" size="2em" class="q-mr-sm" style="color: #C67C48;" />
                      <span>Nenhum item no pedido</span>
                    </div>
                  </template>
                </q-table>
              </div>
            </div>
          </div>
        </div>
      </q-card-section>

      <!-- Footer Actions -->
      <q-separator style="background: #e0e0e0;" />
      <q-card-actions align="right" class="q-pa-md" style="background: white;">
        <q-btn
          outline
          icon="picture_as_pdf"
          label="Exportar PDF"
          @click="handleExportarPDF"
          style="color: #D65A31; border-color: #D65A31"
          :loading="exportando"
        />
        <q-btn
          outline
          icon="table_chart"
          label="Exportar CSV"
          @click="handleExportarCSV"
          style="color: #B5854C; border-color: #B5854C"
          :loading="exportando"
        />
        <q-btn
          outline
          label="Fechar"
          v-close-popup
          style="color: #666; border-color: #ddd;"
        />
      </q-card-actions>
    </q-card>

    <!-- Sub-dialog: Adicionar Item -->
    <adicionar-item-dialog
      v-model="dialogAdicionarItem"
      :pedido-id="pedido?.id"
      @item-adicionado="onItemAdicionado"
    />
  </q-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useQuasar } from 'quasar'
import { useApiRequest } from '@/composables/useApiRequest'
import { useExportacaoPedido } from '@/composables/useExportacaoPedido'
import AdicionarItemDialog from './AdicionarItemDialog.vue'

const props = defineProps({
  modelValue: Boolean,
  pedidoId: Number
})

const emit = defineEmits(['update:modelValue', 'pedido-atualizado', 'exportar-pdf', 'exportar-csv'])

const $q = useQuasar()
const { apiRequest } = useApiRequest()
const { exportarPDF, exportarCSV } = useExportacaoPedido()

const show = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const pedido = ref(null)
const exportando = ref(false)
const modoEdicao = ref(false)
const salvandoCabecalho = ref(false)
const itemEmEdicao = ref(null)
const salvandoItem = ref(false)
const dialogAdicionarItem = ref(false)
const fornecedores = ref([])
const fornecedoresFiltrados = ref([])

const formCabecalho = reactive({
  fornecedorId: null,
  dataPrevista: null,
  observacao: null,
  status: null
})

const formItem = reactive({
  quantidade: null,
  custoUnitario: null
})

// Computed: Pode editar o pedido (RASCUNHO ou ENVIADO)
const podeEditar = computed(() => {
  return pedido.value?.status === 'RASCUNHO' || pedido.value?.status === 'ENVIADO'
})

// Computed: Pode editar itens (RASCUNHO ou ENVIADO)
const podeEditarItens = computed(() => {
  return pedido.value?.status === 'RASCUNHO' || pedido.value?.status === 'ENVIADO'
})

// Computed: Opções de status disponíveis baseado no status atual
const statusOptionsDisponiveis = computed(() => {
  if (!pedido.value) return []

  const statusAtual = pedido.value.status

  // Se for RASCUNHO, pode mudar para ENVIADO ou CANCELADO
  if (statusAtual === 'RASCUNHO') {
    return [
      { label: 'Rascunho', value: 'RASCUNHO' },
      { label: 'Enviado', value: 'ENVIADO' },
      { label: 'Cancelado', value: 'CANCELADO' }
    ]
  }

  // Se for ENVIADO, pode manter ENVIADO ou CANCELAR
  if (statusAtual === 'ENVIADO') {
    return [
      { label: 'Enviado', value: 'ENVIADO' },
      { label: 'Cancelado', value: 'CANCELADO' }
    ]
  }

  // Para outros status, não permite mudança
  return [
    { label: statusLabel(statusAtual), value: statusAtual }
  ]
})

// Computed: Subtotal do item em edição
const subtotalItemEditando = computed(() => {
  if (formItem.quantidade && formItem.custoUnitario) {
    return formItem.quantidade * formItem.custoUnitario
  }
  return 0
})

const colunasItens = [
  { name: 'produtoNome', label: 'Produto', field: 'produtoNome', align: 'left' },
  { name: 'quantidade', label: 'Quantidade', field: 'quantidade', align: 'right' },
  { name: 'custoUnitario', label: 'Custo Unitário', field: 'custoUnitario', align: 'right' },
  { name: 'subtotal', label: 'Subtotal', field: 'subtotal', align: 'right' },
  { name: 'status', label: 'Status', field: 'status', align: 'center' },
  { name: 'acoes', label: 'Ações', field: 'acoes', align: 'center' }
]

// ================== FUNÇÕES DE CARREGAMENTO ==================

async function loadPedido() {
  if (!props.pedidoId) return

  try {
    const data = await apiRequest(`/api/pedidos-compra/${props.pedidoId}`)
    pedido.value = data
  } catch (e) {
    $q.notify({
      type: 'negative',
      message: `Erro ao carregar pedido: ${e?.message || e}`,
      position: 'top'
    })
  }
}

async function loadFornecedores() {
  try {
    const data = await apiRequest('/api/fornecedores')
    fornecedores.value = Array.isArray(data) ? data.map(f => ({
      id: f.id,
      nomeExibicao: f.nomeFantasia || f.razaoSocial
    })) : []
    fornecedoresFiltrados.value = fornecedores.value
  } catch (e) {
    console.error('Erro ao carregar fornecedores:', e)
  }
}

function filterFornecedores(val, update) {
  update(() => {
    if (val === '') {
      fornecedoresFiltrados.value = fornecedores.value
    } else {
      const needle = val.toLowerCase()
      fornecedoresFiltrados.value = fornecedores.value.filter(
        f => f.nomeExibicao.toLowerCase().includes(needle)
      )
    }
  })
}

// ================== EDIÇÃO DE CABEÇALHO ==================

function ativarEdicao() {
  formCabecalho.fornecedorId = pedido.value.fornecedorId
  formCabecalho.dataPrevista = pedido.value.dataPrevista
  formCabecalho.observacao = pedido.value.observacao
  formCabecalho.status = pedido.value.status
  modoEdicao.value = true
}

function cancelarEdicao() {
  modoEdicao.value = false
  // Resetar form
  formCabecalho.fornecedorId = null
  formCabecalho.dataPrevista = null
  formCabecalho.observacao = null
  formCabecalho.status = null
}

async function salvarCabecalho() {
  // Validações
  if (!formCabecalho.fornecedorId) {
    $q.notify({
      type: 'warning',
      message: 'Selecione um fornecedor',
      position: 'top'
    })
    return
  }

  salvandoCabecalho.value = true
  try {
    await apiRequest(`/api/pedidos-compra/${props.pedidoId}`, 'PATCH', {
      fornecedorId: formCabecalho.fornecedorId,
      dataPrevista: formCabecalho.dataPrevista,
      observacao: formCabecalho.observacao,
      status: formCabecalho.status
    })

    $q.notify({
      type: 'positive',
      message: 'Pedido atualizado com sucesso',
      position: 'top',
      icon: 'check_circle'
    })

    modoEdicao.value = false
    await loadPedido()
    emit('pedido-atualizado')
  } catch (e) {
    $q.notify({
      type: 'negative',
      message: `Erro ao atualizar pedido: ${e?.message || e}`,
      position: 'top'
    })
  } finally {
    salvandoCabecalho.value = false
  }
}

// ================== EDIÇÃO DE ITEM ==================

function editarItem(item) {
  itemEmEdicao.value = item.id
  formItem.quantidade = item.quantidade
  formItem.custoUnitario = item.custoUnitario
}

function cancelarEdicaoItem() {
  itemEmEdicao.value = null
  formItem.quantidade = null
  formItem.custoUnitario = null
}

async function salvarItem() {
  // Validações
  if (!formItem.quantidade || formItem.quantidade <= 0) {
    $q.notify({
      type: 'warning',
      message: 'Quantidade deve ser maior que zero',
      position: 'top'
    })
    return
  }

  salvandoItem.value = true
  try {
    await apiRequest(
      `/api/pedidos-compra/${props.pedidoId}/itens/${itemEmEdicao.value}`,
      'PATCH',
      {
        quantidade: formItem.quantidade,
        custoUnitario: formItem.custoUnitario
      }
    )

    $q.notify({
      type: 'positive',
      message: 'Item atualizado com sucesso',
      position: 'top',
      icon: 'check_circle'
    })

    cancelarEdicaoItem()
    await loadPedido()
    emit('pedido-atualizado')
  } catch (e) {
    $q.notify({
      type: 'negative',
      message: `Erro ao atualizar item: ${e?.message || e}`,
      position: 'top'
    })
  } finally {
    salvandoItem.value = false
  }
}

// ================== REMOÇÃO DE ITEM ==================

function confirmarRemocaoItem(item) {
  $q.dialog({
    title: 'Confirmar Remoção',
    message: `Deseja realmente remover o item "${item.produtoNome}" do pedido?`,
    cancel: {
      label: 'Cancelar',
      flat: true,
      color: 'grey-7'
    },
    ok: {
      label: 'Remover',
      color: 'negative',
      unelevated: true
    },
    persistent: true
  }).onOk(async () => {
    await removerItem(item.id)
  })
}

async function removerItem(itemId) {
  try {
    await apiRequest(`/api/pedidos-compra/${props.pedidoId}/itens/${itemId}`, 'DELETE')

    $q.notify({
      type: 'positive',
      message: 'Item removido com sucesso',
      position: 'top',
      icon: 'check_circle'
    })

    await loadPedido()
    emit('pedido-atualizado')
  } catch (e) {
    $q.notify({
      type: 'negative',
      message: `Erro ao remover item: ${e?.message || e}`,
      position: 'top'
    })
  }
}

// ================== ADICIONAR ITEM ==================

async function onItemAdicionado() {
  await loadPedido()
  emit('pedido-atualizado')
}

// ================== FUNÇÕES DE FORMATAÇÃO ==================

function statusColor(status) {
  const colors = {
    RASCUNHO: '#999',
    ENVIADO: '#6B3E26',
    PARCIAL: '#C67C48',
    RECEBIDO: '#B5854C',
    CANCELADO: '#D65A31'
  }
  return colors[status] || '#999'
}

function statusLabel(status) {
  const labels = {
    RASCUNHO: 'Rascunho',
    ENVIADO: 'Enviado',
    PARCIAL: 'Parcial',
    RECEBIDO: 'Recebido',
    CANCELADO: 'Cancelado'
  }
  return labels[status] || status
}

function statusItemColor(status) {
  const colors = {
    PENDENTE: '#999',
    PARCIAL: '#C67C48',
    RECEBIDO: '#B5854C',
    CANCELADO: '#D65A31'
  }
  return colors[status] || '#999'
}

function statusItemLabel(status) {
  const labels = {
    PENDENTE: 'Pendente',
    PARCIAL: 'Parcial',
    RECEBIDO: 'Recebido',
    CANCELADO: 'Cancelado'
  }
  return labels[status] || status
}

function formatCurrency(value) {
  if (value == null) return '—'
  const num = Number(value)
  if (Number.isNaN(num)) return '—'
  return num.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatDate(dateString) {
  if (!dateString) return '—'
  const date = new Date(dateString)
  return date.toLocaleDateString('pt-BR')
}

function formatDateTime(dateTimeString) {
  if (!dateTimeString) return '—'
  const date = new Date(dateTimeString)
  return date.toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// ================== EXPORTAÇÃO ==================

async function handleExportarPDF() {
  if (!pedido.value) return
  exportando.value = true
  try {
    exportarPDF(pedido.value)
    $q.notify({
      type: 'positive',
      message: 'PDF gerado com sucesso!',
      position: 'top'
    })
  } catch (e) {
    $q.notify({
      type: 'negative',
      message: `Erro ao gerar PDF: ${e?.message || e}`,
      position: 'top'
    })
  } finally {
    exportando.value = false
  }
}

async function handleExportarCSV() {
  if (!pedido.value) return
  exportando.value = true
  try {
    exportarCSV(pedido.value)
    $q.notify({
      type: 'positive',
      message: 'CSV gerado com sucesso!',
      position: 'top'
    })
  } catch (e) {
    $q.notify({
      type: 'negative',
      message: `Erro ao gerar CSV: ${e?.message || e}`,
      position: 'top'
    })
  } finally {
    exportando.value = false
  }
}

// ================== WATCHERS ==================

watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    loadPedido()
    loadFornecedores()
  } else {
    modoEdicao.value = false
    cancelarEdicaoItem()
  }
})
</script>

<style scoped>
/* Premium Header */
.premium-header {
  background: linear-gradient(135deg, #fafafa 0%, #ffffff 100%);
  border-bottom: 1px solid #e0e0e0;
  padding: 24px 32px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.header-title {
  font-size: 24px;
  font-weight: 600;
  color: #6B3E26;
  letter-spacing: -0.5px;
}

.header-subtitle {
  font-size: 14px;
  font-weight: 500;
  color: #999;
  letter-spacing: 0.2px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* Section Card */
.section-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(107, 62, 38, 0.08);
  padding: 24px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16px;
  border-bottom: 1px solid #e0e0e0;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: #6B3E26;
  letter-spacing: -0.3px;
  display: flex;
  align-items: center;
}

/* Info Card */
.info-card {
  background: white;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
}

.info-label {
  font-size: 11px;
  font-weight: 600;
  color: #999;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  margin-bottom: 8px;
}

.info-value {
  font-size: 16px;
  font-weight: 500;
  color: #333;
}

.info-value-small {
  font-size: 14px;
  font-weight: 400;
  color: #666;
}

/* Metric Card */
.metric-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(107, 62, 38, 0.08);
  border-left: 4px solid;
  padding: 12px;
  transition: transform 0.2s, box-shadow 0.2s;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(107, 62, 38, 0.12);
}

.metric-value {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.5px;
}

/* Dashboard Table */
.dashboard-table {
  border-radius: 8px;
  overflow: hidden;
}

.dashboard-table :deep(thead) {
  background: #f5f1ed;
}

.dashboard-table :deep(thead tr th) {
  font-size: 11px;
  font-weight: 600;
  color: #6B3E26;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  padding: 12px 16px;
}

.dashboard-table :deep(tbody tr) {
  transition: background-color 0.2s;
}

.dashboard-table :deep(tbody tr:hover) {
  background: #fffbf7;
}

.dashboard-table :deep(tbody tr td) {
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}

/* Responsividade */
@media (max-width: 768px) {
  .premium-header {
    padding: 16px;
  }

  .header-content {
    flex-direction: column;
    gap: 16px;
  }

  .header-left, .header-right {
    width: 100%;
    justify-content: space-between;
  }

  .section-card {
    padding: 16px;
  }
}
</style>
