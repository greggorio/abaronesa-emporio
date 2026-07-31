<template>
  <q-page class="q-pa-md validade-page">
    <div class="row q-col-gutter-md q-mb-md">
      <div
        v-for="card in metricCards"
        :key="card.status"
        class="col-lg-2 col-md-4 col-sm-6 col-xs-12"
      >
        <div
          class="metric-trigger"
          :class="{ 'metric-trigger--active': filtroStatus === card.status }"
          role="button"
          tabindex="0"
          @click="toggleStatus(card.status)"
          @keyup.enter="toggleStatus(card.status)"
          @keyup.space.prevent="toggleStatus(card.status)"
        >
          <MetricCard
            :label="card.title"
            :value="card.count"
            :subtitle="card.helper"
            :icon="card.icon"
            :color="card.color"
          />
        </div>
      </div>
    </div>

    <div class="row q-col-gutter-md">
      <div class="col-lg-4 col-md-6 col-sm-12">
        <div class="my-content relative-position">
        <div class="dashboard-card-container" style="height: 310px">
          <q-card flat class="full-width dashboard-card">
            <q-card-section class="header-section">
              <div class="header-content">
                <div class="title-wrapper">
                  <q-icon name="inventory_2" color="primary" size="xs" />
                  <h5 class="panel-title q-my-none q-ml-sm">Controle de Validade</h5>
                </div>
              </div>
            </q-card-section>

            <div v-if="isLoading" class="flex justify-center items-center" style="height: 200px">
              <q-spinner color="primary" size="3em" />
              <span class="q-ml-md text-grey">Carregando dados...</span>
            </div>

            <template v-else>
              <q-card-section class="metrics-section">
                <div class="row q-col-gutter-sm">
                  <div v-for="card in metricCards" :key="card.status" class="col-4 col-sm-4 col-xs-6">
                    <ValidadeCard
                      :title="card.title"
                      :count="card.count"
                      :status="card.status"
                      :icon="card.icon"
                      :color="card.iconColor"
                      @click="toggleStatus(card.status)"
                    />
                  </div>
                </div>
              </q-card-section>

              <q-card-section class="list-section">
                <div v-if="!rowsPreview.length" class="text-grey-6 q-py-sm">Sem dados.</div>
                <q-scroll-area v-else :thumb-style="thumbStyle" class="list-scroll list-scroll--compact">
                  <div class="list-row list-row--header">
                    <div class="list-cell list-cell--produto">Produto</div>
                    <div class="list-cell">SKU</div>
                    <div class="list-cell">Validade</div>
                    <div class="list-cell list-cell--right">Qtd</div>
                    <div class="list-cell list-cell--right">Dias</div>
                    <div class="list-cell">Status</div>
                  </div>
                  <div v-for="row in rowsPreview" :key="row.rowId" class="list-row">
                    <div class="list-cell list-cell--produto" :title="row.produtoNome || '-'">
                      {{ row.produtoNome || '-' }}
                    </div>
                    <div class="list-cell">{{ row.skuCodigo || '-' }}</div>
                    <div class="list-cell">
                      <span :class="getDiasClass(row.diasParaVencer)">{{ formatarData(row.dataValidade) }}</span>
                    </div>
                    <div class="list-cell list-cell--right">{{ row.quantidade ?? '-' }}</div>
                    <div class="list-cell list-cell--right">
                      <span :class="getDiasClass(row.diasParaVencer)">{{ formatarDias(row.diasParaVencer) }}</span>
                    </div>
                    <div class="list-cell">
                      <q-badge :color="getStatusColor(row.status)" class="text-white">
                        {{ getStatusLabel(row.status) }}
                      </q-badge>
                    </div>
                  </div>
                </q-scroll-area>
              </q-card-section>
            </template>
          </q-card>
        </div>
        </div>
      </div>

      <div class="col-lg-4 col-md-6 col-sm-12">
        <div class="my-content relative-position">
        <div class="dashboard-card-container" style="height: 310px">
          <q-card flat class="full-width dashboard-card">
            <q-card-section class="header-section">
              <div class="title-wrapper">
                <q-icon name="assignment" color="primary" size="xs" />
                <h5 class="panel-title q-my-none q-ml-sm">Tarefas de validade</h5>
              </div>
            </q-card-section>

            <q-card-section class="compact-metrics-section">
              <div class="summary-row summary-row--full">
                <div class="summary-chip">
                  <div class="summary-label">Em aberto</div>
                  <div class="summary-value">{{ totalTarefasEmAberto }}</div>
                </div>
                <div class="summary-chip">
                  <div class="summary-label">Finalizadas</div>
                  <div class="summary-value">{{ totalTarefasFinalizadas }}</div>
                </div>
                <div class="summary-chip">
                  <div class="summary-label">Com divergência</div>
                  <div class="summary-value">{{ totalTarefasComDivergencia }}</div>
                </div>
              </div>
            </q-card-section>


            <q-card-section class="list-section q-pt-none">
              <q-scroll-area :thumb-style="thumbStyle" class="list-scroll list-scroll--tasks">
                <div v-if="tarefasLoading" class="no-data-message no-data-message--small">
                  <div class="text-grey-7 text-subtitle2">Carregando tarefas...</div>
                </div>

                <div
                  v-if="tarefaEmAberto"
                  class="task-item task-item--open"
                  @click="abrirTarefa(tarefaEmAberto.id)"
                >
                  <div class="item-header">
                    <div class="item-name">Tarefa #{{ tarefaEmAberto.id }}</div>
                    <q-badge color="orange" text-color="white">Em aberto</q-badge>
                  </div>
                  <div class="item-sub">Criada em {{ formatarDataHora(tarefaEmAberto.criadoEm) }}</div>
                  <div class="item-sub">{{ itemCount(tarefaEmAberto) }} item(ns)</div>
                </div>

                <div
                  v-for="tarefa in tarefasRecentes"
                  :key="tarefa.id"
                  class="task-item"
                  @click="abrirTarefa(tarefa.id)"
                >
                  <div class="item-header">
                    <div class="item-name">Tarefa #{{ tarefa.id }}</div>
                    <q-badge :color="taskBadgeColor(tarefa)" text-color="white">{{ taskBadgeLabel(tarefa) }}</q-badge>
                  </div>
                  <div class="item-sub">{{ taskStatusDescription(tarefa) }}</div>
                  <div class="item-sub">{{ formatarDataHora(taskReferenceDate(tarefa)) }}</div>
                </div>

                <div v-if="!tarefasLoading && !tarefaEmAberto && !tarefasRecentes.length" class="no-data-message no-data-message--small">
                  <q-icon name="o_info" size="28px" color="grey-6" />
                  <div class="text-grey-7 text-subtitle2 q-mt-sm">Sem tarefas para exibir.</div>
                </div>
              </q-scroll-area>
            </q-card-section>
          </q-card>
        </div>
        </div>
      </div>

      <div class="col-lg-4 col-md-6 col-sm-12">
        <div class="my-content relative-position">
        <div class="dashboard-card-container" style="height: 310px">
          <q-card flat class="full-width dashboard-card">
            <q-card-section class="header-section">
              <div class="title-wrapper">
                <q-icon name="warning_amber" color="primary" size="xs" />
                <h5 class="panel-title q-my-none q-ml-sm">Pendências do módulo</h5>
              </div>
            </q-card-section>

            <q-card-section class="compact-metrics-section">
              <div class="summary-row summary-row--full">
                <div class="summary-chip">
                  <div class="summary-label">Lotes vencidos</div>
                  <div class="summary-value">{{ dashboard.vencido || 0 }}</div>
                </div>
                <div class="summary-chip">
                  <div class="summary-label">Sem vida útil</div>
                  <div class="summary-value">{{ dashboard.semVidaUtil || 0 }}</div>
                </div>
                <div class="summary-chip">
                  <div class="summary-label">Com divergência</div>
                  <div class="summary-value">{{ totalTarefasComDivergencia }}</div>
                </div>
              </div>
            </q-card-section>

            <q-card-section class="list-section q-pt-none">
              <q-scroll-area :thumb-style="thumbStyle" class="list-scroll list-scroll--pending q-mt-none">
                <div v-if="pendenciasModulo.length" class="q-pt-sm">
                  <div
                    v-for="pendencia in pendenciasModulo"
                    :key="pendencia.key"
                    class="task-item"
                  >
                    <div class="item-header">
                      <div class="item-name">{{ pendencia.title }}</div>
                      <q-badge :color="pendencia.color" text-color="white">{{ pendencia.count }}</q-badge>
                    </div>
                    <div class="item-sub">{{ pendencia.description }}</div>
                  </div>
                </div>
                <div v-else class="no-data-message no-data-message--small">
                  <q-icon name="check_circle" size="28px" color="positive" />
                  <div class="text-grey-7 text-subtitle2 q-mt-sm">Sem pendências relevantes.</div>
                </div>
              </q-scroll-area>
            </q-card-section>
          </q-card>
        </div>
        </div>
      </div>

      <div class="col-lg-4 col-md-6 col-sm-12">
        <div class="my-content relative-position">
        <div class="dashboard-card-container" style="height: 310px">
          <q-card flat class="full-width dashboard-card">
            <q-card-section class="header-section">
              <div class="header-content">
                <div class="title-wrapper">
                  <q-icon name="table_view" color="primary" size="xs" />
                  <h5 class="panel-title q-my-none q-ml-sm">Lotes monitorados</h5>
                </div>
                <q-btn-toggle
                  v-model="filtroStatus"
                  dense
                  size="sm"
                  toggle-color="primary"
                  :options="statusToggleOptions"
                  @update:model-value="loadRows"
                />
              </div>
            </q-card-section>

            <q-card-section class="panel-helper text-caption text-grey-7">
              {{ rowsFiltrados.length }} lote(s) encontrados no recorte atual.
            </q-card-section>

            <q-card-section class="filters-section filters-section--compact">
              <div class="row q-col-gutter-sm items-center">
                <div class="col-12 col-md-6">
                  <q-input
                    v-model="busca"
                    dense
                    outlined
                    clearable
                    placeholder="Buscar por produto, SKU ou lote"
                  >
                    <template #prepend>
                      <q-icon name="search" />
                    </template>
                  </q-input>
                </div>
                <div class="col-12 col-md-3">
                  <q-select
                    v-model="filtroStatus"
                    dense
                    outlined
                    emit-value
                    map-options
                    label="Status"
                    :options="statusOptions"
                    @update:model-value="loadRows"
                  />
                </div>
                <div class="col-12 col-md-3 flex justify-end">
                  <q-toggle
                    v-model="somenteComSaldo"
                    label="Somente com saldo"
                    color="primary"
                    @update:model-value="refreshTabela"
                  />
                </div>
              </div>
            </q-card-section>

            <q-card-section class="list-section q-pt-none">
              <div v-if="tableLoading" class="flex items-center q-py-sm">
                <q-spinner color="primary" size="2em" />
                <span class="q-ml-sm text-grey">Carregando lista...</span>
              </div>
              <div v-else-if="!rowsFiltrados.length" class="text-grey-6 q-py-sm">Sem dados.</div>
              <q-scroll-area v-else :thumb-style="thumbStyle" class="list-scroll list-scroll--monitored-compact">
                <div
                  v-for="row in rowsMonitoradosPreview"
                  :key="row.rowId"
                  class="task-item"
                >
                  <div class="item-header">
                    <div class="item-name">{{ row.produtoNome || '-' }}</div>
                    <q-badge :color="getStatusColor(row.status)" text-color="white">
                      {{ getStatusLabel(row.status) }}
                    </q-badge>
                  </div>
                  <div class="item-sub">SKU {{ row.skuCodigo || '-' }} | Lote {{ row.lote || 'SEM_LOTE' }}</div>
                  <div class="item-sub">
                    Validade {{ formatarData(row.dataValidade) }} | Qtd {{ row.quantidade ?? '-' }} | {{ formatarDias(row.diasParaVencer) }} dia(s)
                  </div>
                </div>
              </q-scroll-area>
            </q-card-section>
          </q-card>
        </div>
        </div>
      </div>

      <div class="col-lg-4 col-md-6 col-sm-12">
        <div class="my-content relative-position">
        <div class="dashboard-card-container" style="height: 310px">
          <q-card flat class="full-width dashboard-card">
            <q-card-section class="header-section">
              <div class="title-wrapper">
                <q-icon name="schedule" color="primary" size="xs" />
                <h5 class="panel-title q-my-none q-ml-sm">Faixa de vencimento</h5>
              </div>
            </q-card-section>

            <q-card-section class="compact-metrics-section">
              <div class="summary-row summary-row--full">
                <div class="summary-chip">
                  <div class="summary-label">Hoje / 7 dias</div>
                  <div class="summary-value">{{ faixaResumo.proximos7 }}</div>
                </div>
                <div class="summary-chip">
                  <div class="summary-label">8 a 30 dias</div>
                  <div class="summary-value">{{ faixaResumo.de8a30 }}</div>
                </div>
                <div class="summary-chip">
                  <div class="summary-label">31+ dias</div>
                  <div class="summary-value">{{ faixaResumo.acima30 }}</div>
                </div>
              </div>
            </q-card-section>

            <q-card-section class="list-section q-pt-none">
              <q-scroll-area :thumb-style="thumbStyle" class="list-scroll list-scroll--tasks q-mt-none">
                <div
                  v-for="faixa in faixasVencimento"
                  :key="faixa.key"
                  class="task-item"
                >
                  <div class="item-header">
                    <div class="item-name">{{ faixa.label }}</div>
                    <q-badge :color="faixa.color" text-color="white">{{ faixa.count }}</q-badge>
                  </div>
                  <div class="item-sub">{{ faixa.description }}</div>
                </div>
              </q-scroll-area>
            </q-card-section>
          </q-card>
        </div>
        </div>
      </div>

      <div class="col-lg-4 col-md-6 col-sm-12">
        <div class="my-content relative-position">
        <div class="dashboard-card-container" style="height: 310px">
          <q-card flat class="full-width dashboard-card">
            <q-card-section class="header-section">
              <div class="title-wrapper">
                <q-icon name="local_fire_department" color="primary" size="xs" />
                <h5 class="panel-title q-my-none q-ml-sm">Top produtos em risco</h5>
              </div>
            </q-card-section>

            <q-card-section class="compact-metrics-section">
              <div class="summary-row summary-row--full">
                <div class="summary-chip">
                  <div class="summary-label">Produtos em risco</div>
                  <div class="summary-value">{{ topProdutosResumo.total }}</div>
                </div>
                <div class="summary-chip">
                  <div class="summary-label">Críticos / vencidos</div>
                  <div class="summary-value">{{ topProdutosResumo.criticos }}</div>
                </div>
                <div class="summary-chip">
                  <div class="summary-label">Maior risco</div>
                  <div class="summary-value">{{ topProdutosResumo.maiorRisco }}</div>
                </div>
              </div>
            </q-card-section>

            <q-card-section class="list-section q-pt-none">
              <q-scroll-area :thumb-style="thumbStyle" class="list-scroll list-scroll--pending q-mt-none">
                <div v-if="topProdutosRisco.length" class="q-pt-sm">
                  <div
                    v-for="produto in topProdutosRisco"
                    :key="produto.key"
                    class="task-item"
                  >
                    <div class="item-header">
                      <div class="item-name">{{ produto.produtoNome }}</div>
                      <q-badge :color="getStatusColor(produto.statusPrincipal)" text-color="white">
                        {{ getStatusLabel(produto.statusPrincipal) }}
                      </q-badge>
                    </div>
                    <div class="item-sub">SKU {{ produto.skuCodigo || '-' }} | {{ produto.ocorrencias }} ocorrência(s)</div>
                    <div class="item-sub">Mais crítico em {{ produto.menorDiasTexto }} | score {{ produto.score }}</div>
                  </div>
                </div>
                <div v-else class="no-data-message no-data-message--small">
                  <q-icon name="check_circle" size="28px" color="positive" />
                  <div class="text-grey-7 text-subtitle2 q-mt-sm">Sem produtos em risco.</div>
                </div>
              </q-scroll-area>
            </q-card-section>
          </q-card>
        </div>
        </div>
      </div>
    </div>
  </q-page>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useApiRequest } from '@/composables/useApiRequest'
import MetricCard from '@/components/MetricCard.vue'
import ValidadeCard from '@/components/ValidadeCard.vue'

const router = useRouter()
const { apiRequest } = useApiRequest()

const thumbStyle = {
  right: '4px',
  width: '5px',
  backgroundColor: '#C67C48',
  opacity: 0.75,
  borderRadius: '4px',
}

const isLoading = ref(false)
const tableLoading = ref(false)
const tarefasLoading = ref(false)
const busca = ref('')
const filtroStatus = ref('TOTAL')
const somenteComSaldo = ref(true)
const dashboard = ref({
  vencido: 0,
  critico: 0,
  atencao: 0,
  semVidaUtil: 0,
  ok: 0,
  total: 0,
})
const rows = ref([])
const tarefas = ref([])
const divergencias = ref([])

const statusLabels = {
  TOTAL: 'Todos',
  VENCIDO: 'Vencido',
  CRITICO: 'Crítico',
  ATENCAO: 'Atenção',
  SEM_VIDA_UTIL: 'Sem Vida Útil',
  OK: 'OK',
}

const statusOptions = [
  { label: 'Todos', value: 'TOTAL' },
  { label: 'Vencido', value: 'VENCIDO' },
  { label: 'Crítico', value: 'CRITICO' },
  { label: 'Atenção', value: 'ATENCAO' },
  { label: 'Sem Vida Útil', value: 'SEM_VIDA_UTIL' },
  { label: 'OK', value: 'OK' },
]

const statusToggleOptions = [
  { label: 'Todos', value: 'TOTAL' },
  { label: 'Vencido', value: 'VENCIDO' },
  { label: 'Crítico', value: 'CRITICO' },
  { label: 'Atenção', value: 'ATENCAO' },
]

const metricCards = computed(() => [
  { title: 'Vencidos', status: 'VENCIDO', count: dashboard.value.vencido || 0, icon: 'report_problem', color: 'negative', iconColor: 'red', helper: 'Lotes já expirados' },
  { title: 'Críticos', status: 'CRITICO', count: dashboard.value.critico || 0, icon: 'warning', color: 'negative', iconColor: 'red', helper: 'Janela de ação imediata' },
  { title: 'Atenção', status: 'ATENCAO', count: dashboard.value.atencao || 0, icon: 'priority_high', color: 'warning', iconColor: 'orange', helper: 'Acompanhar próximos vencimentos' },
  { title: 'Sem vida útil', status: 'SEM_VIDA_UTIL', count: dashboard.value.semVidaUtil || 0, icon: 'block', color: 'info', iconColor: 'grey', helper: 'Itens sem parâmetro configurado' },
  { title: 'OK', status: 'OK', count: dashboard.value.ok || 0, icon: 'check_circle', color: 'positive', iconColor: 'green', helper: 'Lotes dentro da janela segura' },
  { title: 'Total monitorado', status: 'TOTAL', count: dashboard.value.total || 0, icon: 'inventory_2', color: 'primary', iconColor: 'blue', helper: 'Lotes retornados pelo monitoramento' },
])

const rowsComId = computed(() =>
  rows.value.map((row, index) => ({
    ...row,
    rowId: row.estoqueLoteId ?? row.id ?? `${row.skuCodigo ?? 'sku'}-${index}`,
  }))
)

const rowsFiltrados = computed(() => {
  const termo = busca.value.trim().toLowerCase()

  return rowsComId.value.filter((row) => {
    const matchBusca = !termo || [row.produtoNome, row.skuCodigo, row.lote]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(termo)

    const matchStatus = filtroStatus.value === 'TOTAL' || row.status === filtroStatus.value
    return matchBusca && matchStatus
  })
})

const rowsPreview = computed(() => rowsComId.value.slice(0, 5))
const rowsMonitoradosPreview = computed(() => rowsFiltrados.value.slice(0, 6))

const statusPriority = {
  VENCIDO: 5,
  CRITICO: 4,
  ATENCAO: 3,
}

const topProdutosRisco = computed(() => {
  const grouped = new Map()

  rowsComId.value.forEach((row) => {
    if (!['VENCIDO', 'CRITICO', 'ATENCAO'].includes(row?.status)) {
      return
    }

    const key = `${row.produtoNome || 'Produto'}::${row.skuCodigo || 'SKU'}`
    const dias = Number(row?.diasParaVencer)
    const prioridade = statusPriority[row?.status] || 0
    const scoreBase = prioridade * 100
    const scoreDias = Number.isNaN(dias)
      ? 0
      : dias < 0
        ? Math.min(Math.abs(dias), 365)
        : Math.max(0, 15 - dias)
    const score = scoreBase + scoreDias

    if (!grouped.has(key)) {
      grouped.set(key, {
        key,
        produtoNome: row.produtoNome || 'Produto',
        skuCodigo: row.skuCodigo || '-',
        ocorrencias: 0,
        statusPrincipal: row.status || 'OK',
        statusPriority: prioridade,
        menorDias: Number.isNaN(dias) ? null : dias,
        score: 0,
      })
    }

    const current = grouped.get(key)
    current.ocorrencias += 1
    current.score += score

    if (prioridade > current.statusPriority) {
      current.statusPrincipal = row.status || 'OK'
      current.statusPriority = prioridade
    }

    if (current.menorDias === null || (!Number.isNaN(dias) && dias < current.menorDias)) {
      current.menorDias = Number.isNaN(dias) ? current.menorDias : dias
    }
  })

  return [...grouped.values()]
    .map((item) => ({
      ...item,
      menorDiasTexto: item.menorDias === null ? 'sem data' : `${item.menorDias} dia(s)`,
    }))
    .sort((a, b) => {
      if (b.score !== a.score) return b.score - a.score
      if (b.statusPriority !== a.statusPriority) return b.statusPriority - a.statusPriority
      return a.produtoNome.localeCompare(b.produtoNome)
    })
    .slice(0, 5)
})

const pendingDivergenciasByTask = computed(() => {
  const map = new Map()
  divergencias.value.forEach((divergencia) => {
    if (divergencia?.tarefaId && divergencia?.acaoTomada === 'PENDENTE') {
      map.set(divergencia.tarefaId, (map.get(divergencia.tarefaId) || 0) + 1)
    }
  })
  return map
})

const tarefasOrdenadas = computed(() =>
  [...tarefas.value].sort((a, b) => new Date(taskReferenceDate(b) || b?.criadoEm || 0).getTime() - new Date(taskReferenceDate(a) || a?.criadoEm || 0).getTime())
)

const tarefaEmAberto = computed(() => tarefasOrdenadas.value.find((tarefa) => tarefa?.status === 'RASCUNHO') || null)
const tarefasRecentes = computed(() => tarefasOrdenadas.value.filter((tarefa) => tarefa?.status !== 'RASCUNHO').slice(0, 5))
const totalTarefasEmAberto = computed(() => tarefas.value.filter((tarefa) => tarefa?.status === 'RASCUNHO').length)
const totalTarefasFinalizadas = computed(() => tarefas.value.filter((tarefa) => tarefa?.status === 'FINALIZADA').length)
const totalTarefasComDivergencia = computed(() => tarefas.value.filter((tarefa) => pendingDivergenciasByTask.value.has(tarefa?.id)).length)

const faixaResumo = computed(() => {
  const resumo = {
    proximos7: 0,
    de8a30: 0,
    acima30: 0,
  }

  rowsComId.value.forEach((row) => {
    const dias = Number(row?.diasParaVencer)
    if (Number.isNaN(dias) || dias < 0) return
    if (dias <= 7) {
      resumo.proximos7 += 1
    } else if (dias <= 30) {
      resumo.de8a30 += 1
    } else {
      resumo.acima30 += 1
    }
  })

  return resumo
})

const faixasVencimento = computed(() => [
  {
    key: 'vencidos',
    label: 'Vencidos',
    count: dashboard.value.vencido || 0,
    description: 'Lotes já expirados.',
    color: 'negative',
  },
  {
    key: 'proximos7',
    label: 'Hoje a 7 dias',
    count: faixaResumo.value.proximos7,
    description: 'Itens na janela imediata de acompanhamento.',
    color: 'orange',
  },
  {
    key: 'de8a30',
    label: '8 a 30 dias',
    count: faixaResumo.value.de8a30,
    description: 'Itens em atenção para o curto prazo.',
    color: 'warning',
  },
  {
    key: 'acima30',
    label: '31+ dias',
    count: faixaResumo.value.acima30,
    description: 'Itens fora da janela de risco imediato.',
    color: 'positive',
  },
  {
    key: 'semVidaUtil',
    label: 'Sem vida útil',
    count: dashboard.value.semVidaUtil || 0,
    description: 'Cadastros sem parâmetro configurado.',
    color: 'grey',
  },
])

const topProdutosResumo = computed(() => {
  const total = topProdutosRisco.value.length
  const criticos = topProdutosRisco.value.filter((item) => ['VENCIDO', 'CRITICO'].includes(item.statusPrincipal)).length
  const maiorRisco = topProdutosRisco.value[0]?.produtoNome || 'Nenhum'

  return {
    total,
    criticos,
    maiorRisco,
  }
})

const nomeFiltroAtual = computed(() => statusLabels[filtroStatus.value] || 'Todos')
const resumoMaisUrgente = computed(() => {
  const row = [...rowsFiltrados.value].sort((a, b) => Number(a?.diasParaVencer ?? 99999) - Number(b?.diasParaVencer ?? 99999))[0]
  if (!row) return 'Sem itens'
  return `${row.produtoNome || row.skuCodigo || 'Item'} • ${formatarDias(row.diasParaVencer)}`
})

const pendenciasModulo = computed(() => {
  const items = []

  if ((dashboard.value.vencido || 0) > 0) {
    items.push({
      key: 'vencidos',
      title: 'Lotes vencidos',
      description: 'Itens já expirados e que exigem ação imediata.',
      count: dashboard.value.vencido || 0,
      color: 'negative',
    })
  }

  if ((dashboard.value.semVidaUtil || 0) > 0) {
    items.push({
      key: 'sem-vida-util',
      title: 'Produtos sem vida útil configurada',
      description: 'Cadastros sem parâmetro de monitoramento por validade.',
      count: dashboard.value.semVidaUtil || 0,
      color: 'grey',
    })
  }

  if (totalTarefasComDivergencia.value > 0) {
    items.push({
      key: 'tarefas-divergencia',
      title: 'Tarefas com divergência pendente',
      description: 'Contagens finalizadas que ainda exigem revisão ou ajuste.',
      count: totalTarefasComDivergencia.value,
      color: 'orange',
    })
  }

  return items
})

async function loadDashboard() {
  isLoading.value = true
  try {
    const data = await apiRequest(`/api/validade/dashboard?somenteComSaldo=${somenteComSaldo.value}`)
    dashboard.value = {
      vencido: data?.vencido || 0,
      critico: data?.critico || 0,
      atencao: data?.atencao || 0,
      semVidaUtil: data?.semVidaUtil || 0,
      ok: data?.ok || 0,
      total: data?.total || 0,
    }
  } finally {
    isLoading.value = false
  }
}

async function loadRows() {
  tableLoading.value = true
  try {
    const params = new URLSearchParams({
      somenteComSaldo: String(somenteComSaldo.value),
      limit: '300',
      offset: '0',
    })

    if (filtroStatus.value && filtroStatus.value !== 'TOTAL') {
      params.append('status', filtroStatus.value)
    }

    const data = await apiRequest(`/api/validade/alertas?${params.toString()}`)
    rows.value = Array.isArray(data) ? data : []
  } catch {
    rows.value = []
  } finally {
    tableLoading.value = false
  }
}

async function loadTarefas() {
  tarefasLoading.value = true
  try {
    const [tarefasResp, divergenciasResp] = await Promise.all([
      apiRequest('/api/validade/tarefas'),
      apiRequest('/api/validade/divergencias'),
    ])
    tarefas.value = Array.isArray(tarefasResp) ? tarefasResp : []
    divergencias.value = Array.isArray(divergenciasResp) ? divergenciasResp : []
  } catch {
    tarefas.value = []
    divergencias.value = []
  } finally {
    tarefasLoading.value = false
  }
}

async function refreshTabela() {
  await Promise.all([loadDashboard(), loadRows()])
}

async function refreshAll() {
  await Promise.all([refreshTabela(), loadTarefas()])
}

function toggleStatus(status) {
  filtroStatus.value = filtroStatus.value === status ? 'TOTAL' : status
  loadRows()
}

function voltarDashboard() {
  router.push('/home')
}

function abrirTarefa(tarefaId) {
  if (!tarefaId) return
  router.push(`/mobile/validade/tarefa/${tarefaId}`)
}

function itemCount(tarefa) {
  return Array.isArray(tarefa?.itens) ? tarefa.itens.length : 0
}

function taskHasPendingDivergencia(tarefa) {
  return pendingDivergenciasByTask.value.has(tarefa?.id)
}

function taskReferenceDate(tarefa) {
  return tarefa?.finalizadoEm || tarefa?.updatedAt || tarefa?.criadoEm || null
}

function taskBadgeLabel(tarefa) {
  if (taskHasPendingDivergencia(tarefa)) return 'Com divergência'
  if (tarefa?.status === 'CANCELADA') return 'Cancelada'
  if (tarefa?.status === 'FINALIZADA') return 'Finalizada'
  return 'Em aberto'
}

function taskBadgeColor(tarefa) {
  if (taskHasPendingDivergencia(tarefa)) return 'orange'
  if (tarefa?.status === 'CANCELADA') return 'negative'
  if (tarefa?.status === 'FINALIZADA') return 'positive'
  return 'orange'
}

function taskStatusDescription(tarefa) {
  if (taskHasPendingDivergencia(tarefa)) return 'Finalizada com divergência'
  if (tarefa?.status === 'CANCELADA') return 'Cancelada'
  return 'Finalizada'
}

function getStatusLabel(status) {
  return statusLabels[status] || status || '-'
}

function getStatusColor(status) {
  switch (status) {
    case 'VENCIDO':
    case 'CRITICO':
      return 'red'
    case 'ATENCAO':
      return 'orange'
    case 'SEM_VIDA_UTIL':
      return 'grey'
    case 'OK':
      return 'green'
    default:
      return 'blue'
  }
}

function formatarData(value) {
  if (!value) return '-'
  try {
    if (Array.isArray(value)) {
      const [year, month, day] = value
      const dd = String(day).padStart(2, '0')
      const mm = String(month).padStart(2, '0')
      const yy = String(year).slice(-2)
      return `${dd}/${mm}/${yy}`
    }
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return '-'
    return date.toLocaleDateString('pt-BR')
  } catch {
    return '-'
  }
}

function formatarDataHora(value) {
  if (!value) return '-'
  try {
    return new Date(value).toLocaleString('pt-BR')
  } catch {
    return '-'
  }
}

function formatarDias(dias) {
  if (dias === null || dias === undefined || Number.isNaN(Number(dias))) return '-'
  return `${Number(dias)}`
}

function getDiasClass(dias) {
  if (dias === null || dias === undefined || Number.isNaN(Number(dias))) return 'text-grey'
  const numero = Number(dias)
  if (numero < 0) return 'text-negative'
  if (numero <= 7) return 'text-warning'
  return 'text-positive'
}

onMounted(() => {
  refreshAll()
})
</script>

<style scoped>
.validade-page {
  background-color: #f5f1ed;
  margin-top: -10px;
}

.my-content {
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 5px rgba(0, 0, 0, 0.2);
  padding: 16px;
  height: 100%;
}

.metric-trigger {
  border-radius: 12px;
}

.metric-trigger :deep(.metric-card) {
  height: 100%;
}

.metric-trigger--active :deep(.metric-card) {
  box-shadow: 0 0 0 2px rgba(107, 62, 38, 0.2), 0 4px 12px rgba(107, 62, 38, 0.12);
  background: #fffdfb;
}

.dashboard-card {
  background-color: #FBF6F2;
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
  gap: 12px;
}

.title-wrapper {
  display: flex;
  align-items: center;
}

.panel-title {
  color: #2A1F1B;
}

.metrics-section {
  padding: 6px 12px 2px;
}

.compact-metrics-section {
  padding: 10px 12px 6px;
}

.panel-helper {
  padding: 0 16px 4px;
}

.filters-section {
  padding: 8px 16px 8px;
}

.filters-section--compact {
  border-top: 1px solid rgba(215, 184, 153, 0.2);
  padding-top: 6px;
  padding-bottom: 4px;
}

.list-section {
  padding: 0 12px 8px;
}

.list-scroll {
  margin-top: 10px;
}

.list-scroll--compact {
  height: 120px;
}

.list-scroll--tasks {
  height: 180px;
}

.list-scroll--monitored-compact {
  height: 190px;
}

.list-scroll--pending {
  height: 170px;
}

.list-scroll--monitored {
  height: 150px;
}

.list-row {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 0.6fr 0.6fr 0.9fr;
  gap: 8px;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid rgba(215, 184, 153, 0.35);
  font-size: 0.72rem;
  color: #2A1F1B;
}

.list-row--header {
  font-weight: 600;
  font-size: 0.65rem;
  text-transform: uppercase;
  color: #6B3E26;
  border-bottom: 1px solid rgba(215, 184, 153, 0.6);
  padding-top: 0;
}

.list-cell {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.list-cell--produto {
  max-width: 100%;
}

.list-cell--right {
  text-align: right;
}

.summary-row {
  display: inline-flex;
  gap: 12px;
  min-width: 480px;
  width: max-content;
}

.summary-row--full {
  min-width: 100%;
  width: 100%;
}

.summary-row--stacked {
  min-width: 100%;
  width: 100%;
  flex-direction: column;
  gap: 10px;
}

.summary-chip {
  background: #fff;
  border: 1px solid rgba(107, 62, 38, 0.12);
  border-radius: 10px;
  padding: 8px 12px;
  min-width: 0;
  flex: 1 1 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.summary-chip--wide {
  min-width: 100%;
  flex: 1 1 auto;
}

.summary-scroll {
  height: 72px;
  max-width: 100%;
  border-radius: 8px;
}

.summary-label {
  font-size: 0.78rem;
  color: #8B7355;
}

.summary-value {
  font-weight: 700;
  color: #2A1F1B;
  font-size: 0.95rem;
}

.task-item {
  margin-bottom: 8px;
  padding: 6px 0;
  border-bottom: 1px solid rgba(107, 62, 38, 0.08);
  cursor: pointer;
}

.task-item:last-child {
  border-bottom: none;
  margin-bottom: 0;
}

.task-item--open {
  border-left: 3px solid #C67C48;
  padding-left: 10px;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}

.item-name {
  font-size: 0.84rem;
  font-weight: 600;
  color: #2A1F1B;
  line-height: 1.2;
}

.item-sub {
  display: block;
  font-size: 0.72rem;
  color: #8B7355;
  font-weight: 400;
  margin-top: 2px;
}

.no-data-message {
  display: flex;
  min-height: 120px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
}

.no-data-message--small {
  min-height: 100px;
}

@media (max-width: 1024px) {
  .header-content {
    flex-direction: column;
    align-items: stretch;
  }

  .summary-row {
    min-width: 100%;
  }
}
</style>
