<template>
  <q-dialog v-model="show" persistent>
    <q-card style="min-width: 600px; max-width: 800px">
      <q-card-section class="row items-center q-pb-none">
        <div class="text-h6">Adicionar Item ao Pedido</div>
        <q-space />
        <q-btn icon="close" flat round dense v-close-popup />
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="q-gutter-md">
          <!-- Produto -->
          <q-select
            v-model="form.produtoId"
            :options="produtosFiltrados"
            option-value="id"
            option-label="nome"
            emit-value
            map-options
            label="Produto *"
            outlined
            use-input
            input-debounce="300"
            @filter="filterProdutos"
            @update:model-value="onProdutoChange"
            :loading="loadingProdutos"
            :error="!!errors.produtoId"
            :error-message="errors.produtoId"
          >
            <template v-slot:no-option>
              <q-item>
                <q-item-section class="text-grey">
                  Nenhum produto encontrado
                </q-item-section>
              </q-item>
            </template>

            <template v-slot:option="scope">
              <q-item v-bind="scope.itemProps">
                <q-item-section>
                  <q-item-label>{{ scope.opt.nome }}</q-item-label>
                  <q-item-label caption>
                    <q-badge :color="scope.opt.insumo ? 'teal' : 'blue'" size="xs">
                      {{ scope.opt.insumo ? 'Insumo' : 'Vendável' }}
                    </q-badge>
                  </q-item-label>
                </q-item-section>
              </q-item>
            </template>
          </q-select>

          <!-- SKU (para vendáveis) -->
          <q-select
            v-if="produtoSelecionado && !produtoSelecionado.insumo"
            v-model="form.skuId"
            :options="skus"
            option-value="id"
            option-label="sku"
            emit-value
            map-options
            label="SKU"
            outlined
            :loading="loadingSkus"
          >
            <template v-slot:option="scope">
              <q-item v-bind="scope.itemProps">
                <q-item-section>
                  <q-item-label>{{ scope.opt.sku }}</q-item-label>
                  <q-item-label caption>Estoque: {{ scope.opt.estoqueAtual || 0 }}</q-item-label>
                </q-item-section>
              </q-item>
            </template>
          </q-select>

          <!-- Embalagem (para insumos) -->
          <q-select
            v-if="produtoSelecionado && produtoSelecionado.insumo"
            v-model="form.embalagemId"
            :options="embalagens"
            option-value="id"
            option-label="nome"
            emit-value
            map-options
            label="Embalagem"
            outlined
            :loading="loadingEmbalagens"
          >
            <template v-slot:option="scope">
              <q-item v-bind="scope.itemProps">
                <q-item-section>
                  <q-item-label>{{ scope.opt.nome }}</q-item-label>
                  <q-item-label caption>Fator: {{ scope.opt.fatorBase || 1 }}</q-item-label>
                </q-item-section>
              </q-item>
            </template>
          </q-select>

          <!-- Quantidade -->
          <q-input
            v-model.number="form.quantidade"
            type="number"
            label="Quantidade *"
            outlined
            min="0"
            step="0.001"
            :error="!!errors.quantidade"
            :error-message="errors.quantidade"
          />

          <!-- Custo Unitário -->
          <q-input
            v-model.number="form.custoUnitario"
            type="number"
            label="Custo Unitário"
            outlined
            min="0"
            step="0.01"
            prefix="R$"
            :error="!!errors.custoUnitario"
            :error-message="errors.custoUnitario"
          />

          <!-- Subtotal (calculado) -->
          <div v-if="subtotalCalculado != null" class="q-pa-sm bg-grey-2 rounded-borders">
            <div class="text-caption text-grey-7">Subtotal estimado</div>
            <div class="text-h6 text-primary">{{ formatCurrency(subtotalCalculado) }}</div>
          </div>
        </div>
      </q-card-section>

      <q-separator />

      <q-card-actions align="right">
        <q-btn flat label="Cancelar" color="grey-7" v-close-popup />
        <q-btn
          label="Adicionar"
          color="primary"
          @click="submit"
          :loading="submitting"
          :disable="!isValid"
        />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useQuasar } from 'quasar'
import { useApiRequest } from '@/composables/useApiRequest'

const props = defineProps({
  modelValue: Boolean,
  pedidoId: Number
})

const emit = defineEmits(['update:modelValue', 'item-adicionado'])

const $q = useQuasar()
const { apiRequest } = useApiRequest()

const show = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const form = reactive({
  produtoId: null,
  skuId: null,
  embalagemId: null,
  quantidade: null,
  custoUnitario: null
})

const errors = reactive({
  produtoId: null,
  quantidade: null,
  custoUnitario: null
})

const loadingProdutos = ref(false)
const loadingSkus = ref(false)
const loadingEmbalagens = ref(false)
const submitting = ref(false)

const produtos = ref([])
const produtosFiltrados = ref([])
const skus = ref([])
const embalagens = ref([])

const produtoSelecionado = computed(() => {
  return produtos.value.find(p => p.id === form.produtoId)
})

const subtotalCalculado = computed(() => {
  if (form.quantidade && form.custoUnitario) {
    return form.quantidade * form.custoUnitario
  }
  return null
})

const isValid = computed(() => {
  return form.produtoId && form.quantidade && form.quantidade > 0
})

async function loadProdutos() {
  loadingProdutos.value = true
  try {
    const data = await apiRequest('/api/produtos')
    produtos.value = Array.isArray(data) ? data : []
    produtosFiltrados.value = produtos.value
  } catch (e) {
    $q.notify({ type: 'negative', message: `Erro ao carregar produtos: ${e?.message || e}` })
  } finally {
    loadingProdutos.value = false
  }
}

function filterProdutos(val, update) {
  update(() => {
    if (val === '') {
      produtosFiltrados.value = produtos.value
    } else {
      const needle = val.toLowerCase()
      produtosFiltrados.value = produtos.value.filter(
        p => p.nome.toLowerCase().includes(needle)
      )
    }
  })
}

async function onProdutoChange(produtoId) {
  form.skuId = null
  form.embalagemId = null
  skus.value = []
  embalagens.value = []

  if (!produtoId) return

  const produto = produtos.value.find(p => p.id === produtoId)
  if (!produto) return

  if (produto.insumo) {
    await loadEmbalagens(produtoId)
  } else {
    await loadSkus(produtoId)
  }
}

async function loadSkus(produtoId) {
  loadingSkus.value = true
  try {
    const data = await apiRequest(`/api/produtos/${produtoId}/skus`)
    skus.value = Array.isArray(data) ? data : []
  } catch (e) {
    $q.notify({ type: 'negative', message: `Erro ao carregar SKUs: ${e?.message || e}` })
  } finally {
    loadingSkus.value = false
  }
}

async function loadEmbalagens(produtoId) {
  loadingEmbalagens.value = true
  try {
    const data = await apiRequest(`/api/produtos/${produtoId}/embalagens`)
    embalagens.value = Array.isArray(data) ? data : []
  } catch (e) {
    $q.notify({ type: 'negative', message: `Erro ao carregar embalagens: ${e?.message || e}` })
  } finally {
    loadingEmbalagens.value = false
  }
}

function validate() {
  errors.produtoId = null
  errors.quantidade = null
  errors.custoUnitario = null

  let valid = true

  if (!form.produtoId) {
    errors.produtoId = 'Selecione um produto'
    valid = false
  }

  if (!form.quantidade || form.quantidade <= 0) {
    errors.quantidade = 'Quantidade deve ser maior que zero'
    valid = false
  }

  return valid
}

async function submit() {
  if (!validate()) return

  submitting.value = true
  try {
    const payload = {
      produtoId: form.produtoId,
      skuId: form.skuId,
      embalagemId: form.embalagemId,
      quantidade: form.quantidade,
      custoUnitario: form.custoUnitario
    }

    await apiRequest(`/api/pedidos-compra/${props.pedidoId}/itens`, 'POST', payload)

    $q.notify({ type: 'positive', message: 'Item adicionado com sucesso' })
    emit('item-adicionado')
    resetForm()
    show.value = false
  } catch (e) {
    $q.notify({ type: 'negative', message: `Erro ao adicionar item: ${e?.message || e}` })
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  form.produtoId = null
  form.skuId = null
  form.embalagemId = null
  form.quantidade = null
  form.custoUnitario = null
  Object.keys(errors).forEach(k => errors[k] = null)
}

function formatCurrency(value) {
  if (value == null) return '—'
  const num = Number(value)
  if (Number.isNaN(num)) return '—'
  return num.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    loadProdutos()
  }
})
</script>
