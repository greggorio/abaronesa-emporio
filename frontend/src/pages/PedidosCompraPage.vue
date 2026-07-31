<template>
  <q-page class="q-pa-md" style="background-color: #f5f1ed; min-height: 100vh">

    <!-- Tabs -->
    <q-tabs
      v-model="tab"
      dense
      class="text-primary q-mb-md"
      active-color="primary"
      indicator-color="primary"
    >
      <q-tab name="sugestoes" label="Sugestões" icon="lightbulb" />
      <q-tab name="pedidos" label="Pedidos" icon="assignment" />
    </q-tabs>

    <q-separator class="q-mb-md" />

    <!-- Tab Panels -->
    <q-tab-panels v-model="tab" animated style="background: transparent">
      <!-- Sugestões -->
      <q-tab-panel name="sugestoes" class="q-pa-none">
        <sugestoes-tab
          :linhas="sugestoes"
          :loading="loadingSugestoes"
          :criando="criandoPedido"
          @load-sugestoes="loadSugestoes"
          @criar-pedido="criarPedido"
        />
      </q-tab-panel>

      <!-- Pedidos -->
      <q-tab-panel name="pedidos" class="q-pa-none">
        <pedidos-tab
          ref="pedidosTabRef"
          @ver-detalhes="verDetalhesPedido"
          @editar="editarPedido"
          @cancelar="cancelarPedido"
          @duplicar="duplicarPedido"
          @exportar-pdf="exportarPDF"
          @exportar-csv="exportarCSV"
          @deletar="deletarPedido"
        />
      </q-tab-panel>
    </q-tab-panels>

    <!-- Dialog: Detalhes do Pedido -->
    <pedido-detalhes-dialog
      v-model="dialogDetalhes"
      :pedido-id="pedidoSelecionado"
      @pedido-atualizado="onPedidoAtualizado"
      @exportar-pdf="exportarPDF"
      @exportar-csv="exportarCSV"
    />
  </q-page>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useQuasar } from 'quasar'
import { useApiRequest } from '@/composables/useApiRequest'
import { useExportacaoPedido } from '@/composables/useExportacaoPedido'
import SugestoesTab from '@/components/pedidos-compra/SugestoesTab.vue'
import PedidosTab from '@/components/pedidos-compra/PedidosTab.vue'
import PedidoDetalhesDialog from '@/components/pedidos-compra/PedidoDetalhesDialog.vue'

const $q = useQuasar()
const { apiRequest } = useApiRequest()
const { exportarPDF: exportPDF, exportarCSV: exportCSV } = useExportacaoPedido()

const tab = ref('sugestoes')
const loadingSugestoes = ref(false)
const criandoPedido = ref(false)
const sugestoes = ref([])
const dialogDetalhes = ref(false)
const pedidoSelecionado = ref(null)
const pedidosTabRef = ref(null)

async function loadSugestoes(somenteCriticos = true) {
  loadingSugestoes.value = true
  try {
    const data = await apiRequest(
      `/api/pedidos-compra/sugestoes?somenteCriticos=${somenteCriticos ? 'true' : 'false'}`
    )
    sugestoes.value = (Array.isArray(data) ? data : []).map(item => ({
      ...item,
      quantidadeEdit: Number(item.quantidadeSugerida || 0),
      custoUnitario: item.custoUnitario || null
    }))
  } catch (e) {
    $q.notify({
      type: 'negative',
      message: `Erro ao carregar sugestões: ${e?.message || e}`
    })
  } finally {
    loadingSugestoes.value = false
  }
}

async function criarPedido(selecionados) {
  if (!selecionados || selecionados.length === 0) {
    $q.notify({ type: 'warning', message: 'Selecione ao menos um item' })
    return
  }

  criandoPedido.value = true
  try {
    const itens = selecionados
      .map(row => {
        const quantidade = Number(row.quantidadeEdit || 0)
        return {
          produtoId: row.produtoId,
          skuId: row.insumo ? null : row.skuId,
          embalagemId: row.insumo ? (row.embalagemId || null) : null,
          quantidade,
          custoUnitario:
            row.custoUnitario != null && row.custoUnitario !== ''
              ? Number(row.custoUnitario)
              : null
        }
      })
      .filter(it => (it.quantidade || 0) > 0)

    if (itens.length === 0) {
      $q.notify({
        type: 'warning',
        message: 'Defina quantidades maiores que zero para os itens selecionados'
      })
      criandoPedido.value = false
      return
    }

    const payload = {
      observacao: 'Pedido criado a partir de sugestões de compra',
      itens
    }

    const resp = await apiRequest('/api/pedidos-compra', 'POST', payload)
    $q.notify({
      type: 'positive',
      message: `Pedido #${resp?.id} criado com sucesso!`,
      actions: [
        {
          label: 'Ver Pedido',
          color: 'white',
          handler: () => {
            tab.value = 'pedidos'
          }
        }
      ]
    })

    await loadSugestoes(true)
    if (pedidosTabRef.value) {
      pedidosTabRef.value.loadData()
    }
  } catch (e) {
    $q.notify({
      type: 'negative',
      message: `Erro ao criar pedido: ${e?.message || e}`
    })
  } finally {
    criandoPedido.value = false
  }
}

function verDetalhesPedido(pedido) {
  pedidoSelecionado.value = pedido.id
  dialogDetalhes.value = true
}

function editarPedido(pedido) {
  pedidoSelecionado.value = pedido.id
  dialogDetalhes.value = true
}

function cancelarPedido(pedido) {
  $q.dialog({
    title: 'Cancelar Pedido',
    message: `Deseja realmente cancelar o pedido #${pedido.id}?`,
    cancel: true,
    persistent: true
  }).onOk(async () => {
    try {
      await apiRequest(`/api/pedidos-compra/${pedido.id}`, 'PATCH', {
        status: 'CANCELADO'
      })
      $q.notify({ type: 'positive', message: 'Pedido cancelado com sucesso' })
      if (pedidosTabRef.value) {
        pedidosTabRef.value.loadData()
      }
    } catch (e) {
      $q.notify({
        type: 'negative',
        message: `Erro ao cancelar pedido: ${e?.message || e}`
      })
    }
  })
}

async function duplicarPedido(pedido) {
  try {
    const original = await apiRequest(`/api/pedidos-compra/${pedido.id}`)

    const payload = {
      observacao: `[DUPLICADO] ${original.observacao || ''}`,
      itens: (original.itens || []).map(item => ({
        produtoId: item.produtoId,
        skuId: item.skuId,
        embalagemId: item.embalagemId,
        quantidade: item.quantidade,
        custoUnitario: item.custoUnitario
      }))
    }

    const resp = await apiRequest('/api/pedidos-compra', 'POST', payload)
    $q.notify({
      type: 'positive',
      message: `Pedido duplicado! Novo pedido: #${resp?.id}`
    })

    if (pedidosTabRef.value) {
      pedidosTabRef.value.loadData()
    }
  } catch (e) {
    $q.notify({
      type: 'negative',
      message: `Erro ao duplicar pedido: ${e?.message || e}`
    })
  }
}

async function exportarPDF(pedido) {
  try {
    // Buscar dados completos do pedido
    const pedidoCompleto = await apiRequest(`/api/pedidos-compra/${pedido.id}`)
    exportPDF(pedidoCompleto)
    $q.notify({
      type: 'positive',
      message: 'PDF gerado com sucesso!'
    })
  } catch (e) {
    $q.notify({
      type: 'negative',
      message: `Erro ao gerar PDF: ${e?.message || e}`
    })
  }
}

async function exportarCSV(pedido) {
  try {
    // Buscar dados completos do pedido
    const pedidoCompleto = await apiRequest(`/api/pedidos-compra/${pedido.id}`)
    exportCSV(pedidoCompleto)
    $q.notify({
      type: 'positive',
      message: 'CSV gerado com sucesso!'
    })
  } catch (e) {
    $q.notify({
      type: 'negative',
      message: `Erro ao gerar CSV: ${e?.message || e}`
    })
  }
}

async function deletarPedido(pedido) {
  $q.dialog({
    title: 'Deletar Pedido',
    message: `Deseja realmente deletar o pedido #${pedido.id}? Esta ação não pode ser desfeita.`,
    cancel: true,
    persistent: true,
    ok: {
      label: 'Deletar',
      color: 'negative'
    }
  }).onOk(async () => {
    try {
      await apiRequest(`/api/pedidos-compra/${pedido.id}`, 'DELETE')
      $q.notify({ type: 'positive', message: 'Pedido deletado com sucesso' })
      if (pedidosTabRef.value) {
        pedidosTabRef.value.loadData()
      }
    } catch (e) {
      $q.notify({
        type: 'negative',
        message: `Erro ao deletar pedido: ${e?.message || e}`
      })
    }
  })
}

function onPedidoAtualizado() {
  if (pedidosTabRef.value) {
    pedidosTabRef.value.loadData()
  }
}

onMounted(async () => {
  await loadSugestoes(true)
})
</script>

<style scoped>
.q-tab-panel {
  padding: 0;
}
</style>
