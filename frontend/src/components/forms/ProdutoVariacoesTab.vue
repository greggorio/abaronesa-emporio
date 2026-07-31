<template>
  <div class="variacoes-container">
    <!-- Mensagem contextual baseada no tipo de precificação -->
    <q-banner v-if="tipoPrecificacaoAtual === 'UNIFICADA'" class="q-mb-md bg-blue-1 text-blue-9" rounded>
      <template v-slot:avatar>
        <q-icon name="info" color="blue" />
      </template>
      <div class="text-subtitle2">Preço Único para Todas as Variações</div>
      <div class="text-caption">
        Os preços de custo e venda são definidos na aba <strong>Dados Gerais</strong> e aplicados automaticamente a todas as variações.
        Aqui você define apenas as variações disponíveis.
      </div>
    </q-banner>

    <q-banner v-else-if="tipoPrecificacaoAtual === 'INDIVIDUAL'" class="q-mb-md bg-orange-1 text-orange-9" rounded>
      <template v-slot:avatar>
        <q-icon name="attach_money" color="orange" />
      </template>
      <div class="text-subtitle2">Preços Individuais por Variação</div>
      <div class="text-caption">
        Cada variação pode ter seu próprio preço de custo e venda. Configure os preços diretamente nesta tabela.
      </div>
    </q-banner>

    <div class="q-mb-md">
      <q-btn color="primary" icon="add" label="Adicionar Variação" @click="adicionarVariacao" :disable="!podeAdicionarVariacao" />
      <span v-if="!podeAdicionarVariacao" class="q-ml-md text-caption text-grey">Informe o nome do produto antes</span>
    </div>

    <!-- Validação de quantidade mínima -->
    <q-banner v-if="variacoes.length === 0 && props.modelValue.nome" class="q-mb-md bg-amber-1 text-amber-9" rounded>
      <template v-slot:avatar>
        <q-icon name="warning" color="amber" />
      </template>
      Adicione pelo menos uma variação ao produto.
    </q-banner>

    <q-table :rows="variacoes" :columns="colunas" row-key="tempId" flat bordered :loading="loading" :pagination="{ rowsPerPage: 10 }">
      <template v-slot:body-cell-variacao="slotProps">
        <q-td :props="slotProps">
          <q-input v-model="slotProps.row.variacao" label="Variação" dense clearable />
        </q-td>
      </template>

      <template v-slot:body-cell-sku="slotProps">
        <q-td :props="slotProps">
          <div class="text-weight-medium">
            {{ slotProps.row.sku || gerarPreviewSKU(slotProps.row) }}
          </div>
          <div class="text-caption text-grey">
            {{ slotProps.row.id ? '' : 'SKU será gerado ao salvar' }}
          </div>
        </q-td>
      </template>

      <template v-slot:body-cell-codigoBarras="slotProps">
        <q-td :props="slotProps">
          <q-input v-model="slotProps.row.codigoBarras" label="Cód. Barras" dense clearable />
        </q-td>
      </template>

      <template v-slot:body-cell-embalagem="slotProps">
        <q-td :props="slotProps">
          <q-select
            v-if="slotProps.row.id"
            dense
            outlined
            emit-value
            map-options
            :options="embalagens.map(e => ({ label: `${e.nome} (${e.fatorBase})`, value: e.id }))"
            :model-value="slotProps.row.embalagemId || null"
            label="Embalagem"
            @update:model-value="val => setEmbalagemForSKU(slotProps.row, val)"
          />
          <div v-else class="text-caption text-grey-7">Será atribuída a embalagem principal ao salvar</div>
        </q-td>
      </template>

      <template v-slot:body-cell-estoqueAtual="slotProps">
        <q-td :props="slotProps">
          <div class="text-body2">{{ slotProps.row.estoqueAtual || 0 }}</div>
        </q-td>
      </template>

      <!-- Estoque mínimo por SKU (vendáveis) -->
      <template v-slot:body-cell-estoqueMinimo="slotProps">
        <q-td :props="slotProps">
          <q-input
            v-model.number="slotProps.row.estoqueMinimo"
            dense
            type="number"
            :min="0"
            :step="1"
            style="max-width: 120px"
            placeholder="0"
          />
        </q-td>
      </template>

      <template v-if="podeEditarPrecos" v-slot:body-cell-precoCusto="slotProps">
        <q-td :props="slotProps">
          <q-input v-model.number="slotProps.row.precoCusto" label="Custo" dense type="number" prefix="R$" :min="0" />
        </q-td>
      </template>

      <template v-if="podeEditarPrecos" v-slot:body-cell-precoVenda="slotProps">
        <q-td :props="slotProps">
          <q-input v-model.number="slotProps.row.precoVenda" label="Venda" dense type="number" prefix="R$" :min="0" />
        </q-td>
      </template>

      <template v-slot:body-cell-ativo="slotProps">
        <q-td :props="slotProps">
          <q-toggle v-model="slotProps.row.ativo" color="green" :label="slotProps.row.ativo ? 'Ativo' : 'Inativo'" />
        </q-td>
      </template>

      <template v-slot:body-cell-principal="slotProps">
        <q-td :props="slotProps">
          <q-toggle v-model="slotProps.row.principal" color="primary" label="Principal" />
        </q-td>
      </template>

      <template v-slot:body-cell-acoes="slotProps">
        <q-td :props="slotProps">
          <q-btn flat round dense icon="delete" color="negative" @click="removerVariacao(slotProps.rowIndex)">
            <q-tooltip>Remover variação</q-tooltip>
          </q-btn>
        </q-td>
      </template>

      <template v-slot:no-data>
        <div class="full-width row flex-center text-grey q-gutter-sm q-pa-lg">
          <q-icon size="2em" name="inventory_2" />
          <span>Nenhuma variação adicionada</span>
        </div>
      </template>
    </q-table>

    <!-- Informações úteis -->
    <q-banner class="q-mt-md bg-info text-white" rounded>
      <template v-slot:avatar>
        <q-icon name="lightbulb" />
      </template>
      <div class="text-subtitle2">Dicas:</div>
      <ul class="q-ma-none q-pl-md">
        <li>O código SKU será gerado automaticamente baseado no nome do produto e variação</li>
        <li>Você pode adicionar um código de barras opcional para cada variação</li>
        <li v-if="podeEditarPrecos">Os preços podem ser diferentes para cada variação</li>
        <li v-else>
          Os preços são definidos na aba <strong>Dados Gerais</strong> e aplicados a todas as variações
        </li>
        <li>Variações duplicadas não são permitidas</li>
      </ul>
    </q-banner>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useApiRequest } from '@/composables/useApiRequest'

const props = defineProps({
  modelValue: { type: Object, required: true },
  recordId: { type: Number, default: null },
  tipoPrecificacao: { type: String, default: 'SIMPLES' }, // SIMPLES, UNIFICADA, INDIVIDUAL
})

const emit = defineEmits(['update:modelValue'])

const loading = ref(false)
const variacoes = ref([])
let nextTempId = 1
const embalagens = ref([])
const { apiRequest } = useApiRequest()

// Tipo de precificação atual (da prop ou do modelValue)
const tipoPrecificacaoAtual = computed(() => {
  return props.tipoPrecificacao || props.modelValue?.tipoPrecificacao || 'SIMPLES'
})

// Permite editar preços apenas quando é INDIVIDUAL
const podeEditarPrecos = computed(() => {
  return tipoPrecificacaoAtual.value === 'INDIVIDUAL'
})

const colunas = computed(() => {
    const cols = [
      { name: 'variacao', label: 'Variação', field: 'variacao', align: 'left', style: 'min-width: 150px' },
    { name: 'sku', label: 'SKU', field: 'sku', align: 'left' },
    { name: 'codigoBarras', label: 'Cód. Barras', field: 'codigoBarras', align: 'left' },
    { name: 'embalagem', label: 'Embalagem', field: 'embalagem', align: 'left' },
    { name: 'estoqueAtual', label: 'Estoque', field: 'estoqueAtual', align: 'right' },
  ]
  // Estoque mínimo por SKU apenas para vendáveis (não-insumo)
  if (!props.modelValue?.insumo) {
    cols.push({ name: 'estoqueMinimo', label: 'Estoq. mín.', field: 'estoqueMinimo', align: 'right' })
  }
  if (podeEditarPrecos.value) {
    cols.push(
      { name: 'precoCusto', label: 'Preço Custo', field: 'precoCusto', align: 'right' },
      { name: 'precoVenda', label: 'Preço Venda', field: 'precoVenda', align: 'right' },
    )
  }
  cols.push(
    { name: 'ativo', label: 'Status', field: 'ativo', align: 'center' },
    { name: 'principal', label: 'Principal', field: 'principal', align: 'center' },
    { name: 'acoes', label: 'Ações', align: 'center' },
  )
  return cols
})

const podeAdicionarVariacao = computed(() => props.modelValue?.nome && props.modelValue.nome.trim() !== '')

function gerarPreviewSKU(v) {
  if (!props.modelValue?.nome || !v?.variacao) return '---'
  const prefixo = props.modelValue.nome.substring(0, 3).toUpperCase().replace(/[^A-Z0-9]/g, '')
  const varClean = String(v.variacao).substring(0, 6).toUpperCase().replace(/[^A-Z0-9]/g, '')
  return `${prefixo}-${varClean}`
}

function adicionarVariacao() {
  const nova = {
    tempId: nextTempId++,
    id: null,
    variacao: '',
    sku: '',
    codigoBarras: '',
    precoCusto: podeEditarPrecos.value ? (props.modelValue.precoCusto || 0) : null,
    precoVenda: podeEditarPrecos.value ? (props.modelValue.precoVenda || 0) : null,
    estoqueAtual: 0,
    estoqueMinimo: props.modelValue?.insumo ? null : 0,
    ativo: true,
    principal: false,
  }
  variacoes.value.push(nova)
  emitirAlteracao()
}

function removerVariacao(index) {
  variacoes.value.splice(index, 1)
  emitirAlteracao()
}

function validarDuplicada(variacao, index) {
  return !variacoes.value.some((v, i) => i !== index && v.variacao === variacao)
}

function emitirAlteracao() {
  const skusParaEnviar = variacoes.value.map(v => ({
    id: v.id,
    variacao: v.variacao,
    sku: v.sku,
    codigoBarras: v.codigoBarras,
    precoCusto: podeEditarPrecos.value ? v.precoCusto : null,
    precoVenda: podeEditarPrecos.value ? v.precoVenda : null,
    estoqueAtual: v.estoqueAtual,
    estoqueMinimo: props.modelValue?.insumo ? null : (v.estoqueMinimo ?? null),
    ativo: v.ativo,
    principal: v.principal,
  }))
  emit('update:modelValue', { ...props.modelValue, skus: skusParaEnviar })
}

watch(variacoes, (novas) => {
  novas.forEach((v, i) => {
    if (v.variacao && !validarDuplicada(v.variacao, i)) {
      v.variacao = ''
    }
  })
  emitirAlteracao()
}, { deep: true })

onMounted(() => {
  if (props.modelValue?.skus && Array.isArray(props.modelValue.skus)) {
    variacoes.value = props.modelValue.skus.map(s => ({
      ...s,
      tempId: s.id || nextTempId++,
      estoqueMinimo: props.modelValue?.insumo ? null : (s.estoqueMinimo ?? 0),
    }))
  }
  loadEmbalagens()
})

watch(() => props.recordId, async (id) => {
  if (id) {
    await loadEmbalagens()
  } else {
    embalagens.value = []
  }
})

async function loadEmbalagens() {
  if (!props.recordId) return
  try {
    const data = await apiRequest(`/api/embalagens?produtoId=${props.recordId}`)
    embalagens.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.warn('Falha ao carregar embalagens:', e?.message || e)
  }
}

async function setEmbalagemForSKU(row, embalagemId) {
  if (!row.id) return // SKU ainda não salvo; backend vai assumir principal
  try {
    await apiRequest(`/api/skus/${row.id}/embalagem`, 'PUT', { embalagemId })
    row.embalagemId = embalagemId
  } catch (e) {
    console.warn('Falha ao definir embalagem do SKU:', e?.message || e)
  }
}
</script>

<style scoped>
.variacoes-container { padding: 16px; }
.q-table { max-height: 600px; }
</style>
