<template>
  <q-page class="q-pa-md" style="background-color: #f5f1ed; min-height: 100vh">
    <q-card flat bordered class="q-mb-md">
      <q-card-section>
        <div class="text-h6">Producao Propria</div>
        <div class="text-caption text-grey-7">
          Produza uma unidade e movimente os insumos conforme a ficha tecnica.
        </div>
      </q-card-section>
    </q-card>

    <q-card flat bordered>
      <q-card-section class="row q-col-gutter-md">
        <div class="col-12 col-md-6">
          <LookupSelect
            v-model="produtoId"
            :field="produtoField"
            :disabled="produzindo"
          />
        </div>
        <div class="col-12 col-md-6">
          <SkuLookupSelect
            v-model="skuId"
            :produto-id="produtoId"
            :label="skuLabel"
            :disabled="produzindo || !skuObrigatorio"
          />
          <div v-if="skuObrigatorio && !skuId" class="text-caption text-negative q-mt-xs">
            Selecione o SKU para producao.
          </div>
        </div>
        <div class="col-12">
          <q-input
            v-model="observacao"
            dense
            outlined
            label="Observacao"
            :disable="produzindo"
          />
        </div>
      </q-card-section>
      <q-separator />
      <q-card-section class="row items-center q-gutter-sm">
        <q-btn
          color="primary"
          icon="o_precision_manufacturing"
          label="Produzir 1 unidade"
          :disable="!podeProduzir"
          :loading="produzindo"
          @click="produzir"
        />
        <q-chip v-if="produto" color="grey-2" text-color="grey-8" icon="inventory_2">
          {{ produto.nome }}
        </q-chip>
      </q-card-section>
    </q-card>

    <q-banner
      v-if="produto && !produto.producaoPropria"
      class="q-mt-md bg-amber-2 text-amber-10"
      rounded
    >
      Este produto nao esta configurado como producao propria.
    </q-banner>

    <q-banner
      v-if="produto && fichaCarregada && !temFichaTecnica"
      class="q-mt-md bg-red-2 text-red-10"
      rounded
    >
      Este produto nao possui ficha tecnica.
    </q-banner>

    <q-card v-if="ficha && ficha.itens && ficha.itens.length" class="q-mt-md" flat bordered>
      <q-card-section>
        <div class="text-subtitle1">Ficha tecnica</div>
        <div class="text-caption text-grey-7">Insumos que serao consumidos por unidade.</div>
      </q-card-section>
      <q-separator />
      <q-table
        :rows="ficha.itens"
        :columns="colunasFicha"
        row-key="id"
        flat
        dense
        :rows-per-page-options="[5, 10]"
      >
        <template #body-cell-quantidade="props">
          <q-td :props="props">
            {{ props.row.quantidade }}
            <span v-if="props.row.unidade" class="text-caption text-grey-7">{{ props.row.unidade }}</span>
          </q-td>
        </template>
      </q-table>
    </q-card>

    <q-card v-if="resultado" class="q-mt-md" flat bordered>
      <q-card-section>
        <div class="text-subtitle1">Resultado da producao</div>
        <div class="text-caption text-grey-7">Movimentacoes registradas nesta producao.</div>
      </q-card-section>
      <q-separator />
      <q-card-section>
        <q-banner class="bg-green-1 text-green-10" rounded>
          Entrada registrada para o SKU {{ resultado.skuId }}.
        </q-banner>
      </q-card-section>
      <q-separator />
      <q-card-section>
        <q-table
          :rows="resultado.movimentosInsumos || []"
          :columns="colunasMovimentos"
          row-key="id"
          flat
          dense
          :rows-per-page-options="[5, 10]"
          no-data-label="Nenhum movimento de insumo"
        />
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useQuasar } from 'quasar'
import { useApiRequest } from '@/composables/useApiRequest'
import LookupSelect from '@/components/forms/LookupSelect.vue'
import SkuLookupSelect from '@/components/forms/SkuLookupSelect.vue'

const $q = useQuasar()
const { apiRequest } = useApiRequest()

const produtoId = ref(null)
const skuId = ref(null)
const produto = ref(null)
const ficha = ref(null)
const observacao = ref('')
const resultado = ref(null)
const carregandoProduto = ref(false)
const carregandoFicha = ref(false)
const produzindo = ref(false)

const produtoField = {
  label: 'Produto',
  placeholder: 'Pesquisar produto por nome ou codigo',
  component: 'LookupSelect',
  lookupEndpoint: '/api/produtos/lookup/search',
  displayColumns: [
    { name: 'label', label: 'Produto', field: 'label', align: 'left' },
    { name: 'estoqueAtual', label: 'Estoque', field: 'estoqueAtual', align: 'right' },
    { name: 'preco', label: 'Preco', field: 'preco', align: 'right' }
  ],
  props: {
    'option-label': 'label',
    'option-value': 'value'
  }
}

const colunasFicha = [
  { name: 'insumo', label: 'Insumo', field: 'insumoProdutoNome', align: 'left' },
  { name: 'quantidade', label: 'Quantidade', field: 'quantidade', align: 'left' }
]

const colunasMovimentos = [
  { name: 'produto', label: 'Produto', field: 'produtoNome', align: 'left' },
  { name: 'sku', label: 'SKU', field: 'skuCodigo', align: 'left' },
  { name: 'quantidade', label: 'Quantidade', field: 'quantidade', align: 'right' },
  { name: 'tipo', label: 'Tipo', field: 'tipoMovimento', align: 'left' }
]

const skuObrigatorio = computed(() => {
  return produto.value && produto.value.skus && produto.value.skus.length > 1
})

const fichaCarregada = computed(() => ficha.value !== null)

const temFichaTecnica = computed(() => {
  return ficha.value && Array.isArray(ficha.value.itens) && ficha.value.itens.length > 0
})

const skuLabel = computed(() => {
  if (!produto.value) return 'SKU / Variacao'
  if (skuObrigatorio.value) return 'SKU / Variacao (obrigatorio)'
  return 'SKU / Variacao'
})

const podeProduzir = computed(() => {
  if (!produto.value) return false
  if (!produto.value.producaoPropria || !temFichaTecnica.value) return false
  if (produto.value.controlaEstoque === false) return false
  if (skuObrigatorio.value && !skuId.value) return false
  return !produzindo.value
})

watch(produtoId, async (newId) => {
  produto.value = null
  ficha.value = null
  skuId.value = null
  resultado.value = null

  if (!newId) return

  await Promise.all([carregarProduto(newId), carregarFicha(newId)])
})

async function carregarProduto(id) {
  carregandoProduto.value = true
  try {
    const data = await apiRequest(`/api/produtos/${id}`)
    produto.value = data
    if (produto.value?.skus?.length === 1) {
      skuId.value = produto.value.skus[0].id
    }
  } catch (e) {
    produto.value = null
    $q.notify({ type: 'negative', message: `Erro ao carregar produto: ${e?.message || e}` })
  } finally {
    carregandoProduto.value = false
  }
}

async function carregarFicha(id) {
  carregandoFicha.value = true
  try {
    ficha.value = await apiRequest(`/api/ficha-tecnica/produto/${id}`)
  } catch (e) {
    ficha.value = null
  } finally {
    carregandoFicha.value = false
  }
}

async function produzir() {
  if (!podeProduzir.value) return
  produzindo.value = true
  try {
    const payload = {
      produtoId: produtoId.value,
      skuId: skuId.value,
      observacao: observacao.value || null
    }
    const resp = await apiRequest('/api/producao', 'POST', payload)
    resultado.value = resp && resp.data ? resp.data : resp
    $q.notify({ type: 'positive', message: 'Producao registrada com sucesso' })
    await carregarProduto(produtoId.value)
  } catch (e) {
    $q.notify({ type: 'negative', message: `Erro ao registrar producao: ${e?.message || e}` })
  } finally {
    produzindo.value = false
  }
}
</script>
