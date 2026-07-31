<template>
  <div>
    <!-- Filtros -->
    <pedido-filters @update:filters="onFiltersChange" class="q-mb-md" />

    <!-- Cards de métricas -->
    <div class="row q-col-gutter-md q-mb-md">
      <div class="col-12 col-sm-6 col-md-3">
        <div class="metric-card" style="border-left-color: #6B3E26">
          <div class="metric-icon" style="color: #6B3E26">
            <q-icon name="assignment" size="28px" />
          </div>
          <div class="metric-content">
            <div class="metric-label">Total de Pedidos</div>
            <div class="metric-value" style="color: #6B3E26">{{ pagination.totalElements || 0 }}</div>
          </div>
        </div>
      </div>

      <div class="col-12 col-sm-6 col-md-3">
        <div class="metric-card" style="border-left-color: #6c757d">
          <div class="metric-icon" style="color: #6c757d">
            <q-icon name="edit_note" size="28px" />
          </div>
          <div class="metric-content">
            <div class="metric-label">Rascunhos</div>
            <div class="metric-value" style="color: #6c757d">{{ metricas.rascunhos }}</div>
          </div>
        </div>
      </div>

      <div class="col-12 col-sm-6 col-md-3">
        <div class="metric-card" style="border-left-color: #0288D1">
          <div class="metric-icon" style="color: #0288D1">
            <q-icon name="send" size="28px" />
          </div>
          <div class="metric-content">
            <div class="metric-label">Enviados</div>
            <div class="metric-value" style="color: #0288D1">{{ metricas.enviados }}</div>
          </div>
        </div>
      </div>

      <div class="col-12 col-sm-6 col-md-3">
        <div class="metric-card" style="border-left-color: #B5854C">
          <div class="metric-icon" style="color: #B5854C">
            <q-icon name="attach_money" size="28px" />
          </div>
          <div class="metric-content">
            <div class="metric-label">Valor Total</div>
            <div class="metric-value" style="color: #B5854C">{{ formatCurrency(metricas.valorTotal) }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Tabela -->
    <div class="dashboard-card">
      <div class="card-header">
        <div class="header-content">
          <q-icon name="assignment" size="24px" class="q-mr-sm" style="color: #6B3E26" />
          <div>
            <div class="header-title">Lista de Pedidos</div>
            <div class="header-subtitle">Gerencie todos os seus pedidos de compra</div>
          </div>
        </div>
        <q-btn
          flat
          dense
          round
          icon="refresh"
          @click="loadData"
          :loading="loading"
          style="color: #6B3E26"
        >
          <q-tooltip>Atualizar</q-tooltip>
        </q-btn>
      </div>

      <q-table
        flat
        :rows="pedidos"
        :columns="columns"
        row-key="id"
        :loading="loading"
        :pagination="pagination"
        @request="onRequest"
        binary-state-sort
        class="custom-table"
      >
        <template v-slot:body-cell-status="props">
          <q-td :props="props">
            <q-badge :color="statusColor(props.row.status)" :label="statusLabel(props.row.status)" />
          </q-td>
        </template>

        <template v-slot:body-cell-fornecedorNome="props">
          <q-td :props="props">
            <div style="font-weight: 500; color: #333">
              {{ props.row.fornecedorNome || '—' }}
            </div>
          </q-td>
        </template>

        <template v-slot:body-cell-criadoEm="props">
          <q-td :props="props">
            <div style="color: #666; font-size: 0.9em">
              {{ formatDateTime(props.row.criadoEm) }}
            </div>
          </q-td>
        </template>

        <template v-slot:body-cell-dataPrevista="props">
          <q-td :props="props">
            <div style="color: #666">
              {{ formatDate(props.row.dataPrevista) }}
            </div>
          </q-td>
        </template>

        <template v-slot:body-cell-itens="props">
          <q-td :props="props">
            <q-chip size="sm" style="background-color: #f5f1ed; color: #6B3E26; font-weight: 500">
              {{ props.row.itens?.length || 0 }}
            </q-chip>
          </q-td>
        </template>

        <template v-slot:body-cell-valorTotal="props">
          <q-td :props="props" class="text-right">
            <strong style="color: #B5854C; font-size: 1.05em">
              {{ formatCurrency(props.row.valorTotal) }}
            </strong>
          </q-td>
        </template>

        <template v-slot:body-cell-acoes="props">
          <q-td :props="props">
            <div class="row q-gutter-xs no-wrap">
              <q-btn
                dense
                flat
                round
                size="sm"
                icon="visibility"
                style="color: #6B3E26"
                @click="$emit('ver-detalhes', props.row)"
              >
                <q-tooltip>Ver Detalhes</q-tooltip>
              </q-btn>

              <q-btn
                v-if="props.row.status === 'RASCUNHO'"
                dense
                flat
                round
                size="sm"
                icon="edit"
                style="color: #0288D1"
                @click="$emit('editar', props.row)"
              >
                <q-tooltip>Editar</q-tooltip>
              </q-btn>

              <q-btn
                v-if="['RASCUNHO', 'ENVIADO'].includes(props.row.status)"
                dense
                flat
                round
                size="sm"
                icon="cancel"
                style="color: #D65A31"
                @click="$emit('cancelar', props.row)"
              >
                <q-tooltip>Cancelar Pedido</q-tooltip>
              </q-btn>

              <q-btn-dropdown
                dense
                flat
                round
                size="sm"
                icon="more_vert"
                style="color: #6c757d"
              >
                <q-list>
                  <q-item clickable v-close-popup @click="$emit('duplicar', props.row)">
                    <q-item-section avatar>
                      <q-icon name="content_copy" style="color: #6B3E26" />
                    </q-item-section>
                    <q-item-section>Duplicar</q-item-section>
                  </q-item>

                  <q-separator />

                  <q-item clickable v-close-popup @click="$emit('exportar-pdf', props.row)">
                    <q-item-section avatar>
                      <q-icon name="picture_as_pdf" style="color: #D65A31" />
                    </q-item-section>
                    <q-item-section>Exportar PDF</q-item-section>
                  </q-item>

                  <q-item clickable v-close-popup @click="$emit('exportar-csv', props.row)">
                    <q-item-section avatar>
                      <q-icon name="table_chart" style="color: #B5854C" />
                    </q-item-section>
                    <q-item-section>Exportar CSV</q-item-section>
                  </q-item>

                  <q-separator v-if="props.row.status === 'RASCUNHO'" />

                  <q-item
                    v-if="props.row.status === 'RASCUNHO'"
                    clickable
                    v-close-popup
                    @click="$emit('deletar', props.row)"
                  >
                    <q-item-section avatar>
                      <q-icon name="delete" style="color: #D65A31" />
                    </q-item-section>
                    <q-item-section>
                      <q-item-label style="color: #D65A31; font-weight: 500">Deletar</q-item-label>
                    </q-item-section>
                  </q-item>
                </q-list>
              </q-btn-dropdown>
            </div>
          </q-td>
        </template>

        <template v-slot:no-data>
          <div class="full-width row flex-center q-pa-xl text-grey">
            <q-icon name="assignment" size="4em" class="q-mr-md" style="color: #C67C48; opacity: 0.5" />
            <div>
              <div class="text-h6" style="color: #6B3E26">Nenhum pedido encontrado</div>
              <div class="text-caption" style="color: #999">Crie um pedido a partir das sugestões</div>
            </div>
          </div>
        </template>
      </q-table>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useQuasar } from 'quasar'
import { useApiRequest } from '@/composables/useApiRequest'
import PedidoFilters from './PedidoFilters.vue'

const emit = defineEmits(['ver-detalhes', 'editar', 'cancelar', 'duplicar', 'exportar-pdf', 'exportar-csv', 'deletar'])

const $q = useQuasar()
const { apiRequest } = useApiRequest()

const loading = ref(false)
const pedidos = ref([])
const filters = ref({})

const pagination = ref({
  sortBy: 'criadoEm',
  descending: true,
  page: 1,
  rowsPerPage: 20,
  rowsNumber: 0,
  totalElements: 0
})

const columns = [
  { name: 'id', label: 'ID', field: 'id', align: 'left', sortable: true },
  { name: 'status', label: 'Status', field: 'status', align: 'left', sortable: true },
  { name: 'fornecedorNome', label: 'Fornecedor', field: 'fornecedorNome', align: 'left', sortable: false },
  { name: 'criadoEm', label: 'Criado Em', field: 'criadoEm', align: 'left', sortable: true },
  { name: 'dataPrevista', label: 'Data Prevista', field: 'dataPrevista', align: 'left', sortable: true },
  { name: 'itens', label: 'Itens', field: 'itens', align: 'center', sortable: false },
  { name: 'valorTotal', label: 'Valor Total', field: 'valorTotal', align: 'right', sortable: true },
  { name: 'acoes', label: 'Ações', field: 'acoes', align: 'center', sortable: false }
]

const metricas = computed(() => {
  const rascunhos = pedidos.value.filter(p => p.status === 'RASCUNHO').length
  const enviados = pedidos.value.filter(p => p.status === 'ENVIADO').length
  const valorTotal = pedidos.value.reduce((sum, p) => sum + (p.valorTotal || 0), 0)

  return { rascunhos, enviados, valorTotal }
})

async function onRequest(props) {
  const { page, rowsPerPage, sortBy, descending } = props.pagination

  loading.value = true
  try {
    const params = new URLSearchParams({
      page: (page - 1).toString(),
      size: rowsPerPage.toString()
    })

    if (filters.value.status) params.append('status', filters.value.status)
    if (filters.value.fornecedorId) params.append('fornecedorId', filters.value.fornecedorId)
    if (filters.value.de) params.append('de', filters.value.de)
    if (filters.value.ate) params.append('ate', filters.value.ate)
    if (filters.value.search) params.append('search', filters.value.search)

    const data = await apiRequest(`/api/pedidos-compra?${params.toString()}`)

    pedidos.value = data.content || []
    pagination.value.page = page
    pagination.value.rowsPerPage = rowsPerPage
    pagination.value.sortBy = sortBy
    pagination.value.descending = descending
    pagination.value.rowsNumber = data.totalElements || 0
    pagination.value.totalElements = data.totalElements || 0
  } catch (e) {
    $q.notify({ type: 'negative', message: `Erro ao carregar pedidos: ${e?.message || e}` })
  } finally {
    loading.value = false
  }
}

function onFiltersChange(newFilters) {
  filters.value = newFilters
  pagination.value.page = 1
  loadData()
}

function loadData() {
  onRequest({ pagination: pagination.value })
}

function statusColor(status) {
  const colors = {
    RASCUNHO: 'grey',
    ENVIADO: 'info',
    PARCIAL: 'orange',
    RECEBIDO: 'positive',
    CANCELADO: 'negative'
  }
  return colors[status] || 'grey'
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

defineExpose({ loadData })

onMounted(() => {
  loadData()
})
</script>

<style scoped>
/* Metric Cards */
.metric-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(107, 62, 38, 0.08);
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: all 0.3s ease;
  border-left: 4px solid;
  height: 100%;
  min-height: 110px;
}

.metric-card:hover {
  box-shadow: 0 4px 16px rgba(107, 62, 38, 0.15);
  transform: translateY(-2px);
}

.metric-icon {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(107, 62, 38, 0.08);
  border-radius: 10px;
}

.metric-content {
  flex: 1;
  min-width: 0;
}

.metric-label {
  font-size: 0.85rem;
  color: #666;
  margin-bottom: 4px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.metric-value {
  font-size: 1.75rem;
  font-weight: 700;
  line-height: 1.2;
}

/* Dashboard Card */
.dashboard-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(107, 62, 38, 0.08);
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid #e0e0e0;
  background-color: #fafafa;
}

.header-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-title {
  font-size: 1.15rem;
  font-weight: 600;
  color: #333;
  line-height: 1.3;
}

.header-subtitle {
  font-size: 0.85rem;
  color: #666;
  margin-top: 2px;
}

/* Custom Table */
.custom-table :deep(thead tr th) {
  background-color: #f5f1ed;
  color: #6B3E26;
  font-weight: 600;
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 14px 12px;
  border: none;
}

.custom-table :deep(tbody tr) {
  transition: background-color 0.2s ease;
}

.custom-table :deep(tbody tr:hover) {
  background-color: #fffbf7;
}

.custom-table :deep(tbody td) {
  padding: 14px 12px;
  border-bottom: 1px solid #f0f0f0;
  color: #333;
}

.custom-table :deep(.q-table__bottom) {
  border-top: 1px solid #e0e0e0;
  background-color: #fafafa;
  padding: 12px 16px;
}
</style>
