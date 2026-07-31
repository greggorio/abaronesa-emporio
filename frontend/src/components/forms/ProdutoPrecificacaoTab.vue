<template>
  <div class="precificacao-container">
    <!-- Banner informativo baseado no tipo de precificação -->
    <q-banner v-if="tipoPrecificacaoAtual === 'SIMPLES'" class="q-mb-md bg-blue-1 text-blue-9" rounded>
      <template v-slot:avatar>
        <q-icon name="info" color="blue" />
      </template>
      <div class="text-subtitle2">Produto Sem Variações</div>
      <div class="text-caption">
        Os preços definidos aqui serão aplicados diretamente ao produto único.
      </div>
    </q-banner>

    <q-banner v-else-if="tipoPrecificacaoAtual === 'UNIFICADA'" class="q-mb-md bg-orange-1 text-orange-9" rounded>
      <template v-slot:avatar>
        <q-icon name="attach_money" color="orange" />
      </template>
      <div class="text-subtitle2">Preço Único para Todas as Variações</div>
      <div class="text-caption">
        Os preços definidos aqui serão aplicados automaticamente a todas as variações do produto.
        Use os botões abaixo para sincronizar os preços.
      </div>
    </q-banner>

    <!-- Tipo de Cálculo de Margem -->
    <div class="row q-col-gutter-md q-mb-md">
      <div class="col-12">
        <q-select
          v-model="formData.tipoCalculoMargem"
          :options="tiposCalculoMargem"
          label="Tipo de Cálculo de Margem *"
          emit-value
          map-options
          outlined
          dense
          hint="Selecione como a margem será calculada"
          :rules="[(val) => !!val || 'Campo obrigatório']"
        />
      </div>
    </div>

    <!-- Preços -->
    <div class="row q-col-gutter-md q-mb-md">
      <div class="col-4">
        <q-input
          v-model.number="formData.precoCusto"
          label="Preço de Custo"
          type="number"
          prefix="R$"
          outlined
          dense
          :min="0"
          :step="0.01"
          hint="Custo do produto"
          @update:model-value="calcularPrecoVenda"
        />
      </div>
      <div class="col-4">
        <q-input
          v-model.number="formData.precoVenda"
          label="Preço de Venda"
          type="number"
          prefix="R$"
          outlined
          dense
          :min="0"
          :step="0.01"
          hint="Preço final para o cliente"
          @update:model-value="calcularMargem"
        />
      </div>
      <div class="col-4">
        <q-input
          v-model.number="formData.margemLucro"
          label="Margem de Lucro"
          type="number"
          :suffix="formData.tipoCalculoMargem === 'VALOR_FIXO' ? 'R$' : '%'"
          outlined
          dense
          :min="0"
          :step="0.01"
          hint="Margem calculada ou fixa"
          @update:model-value="aplicarMargem"
        />
      </div>
    </div>

    <!-- Validações e Limites -->
    <q-separator class="q-my-md" />
    <div class="text-subtitle2 q-mb-md text-warning">Validações e Limites</div>

    <div class="row q-col-gutter-md q-mb-md">
      <div class="col-6">
        <q-input
          v-model.number="formData.precoMinimoVenda"
          label="Preço Mínimo de Venda"
          type="number"
          prefix="R$"
          outlined
          dense
          :min="0"
          :step="0.01"
        />
      </div>
      <div class="col-6">
        <q-input
          v-model.number="formData.margemMinima"
          label="Margem Mínima"
          type="number"
          suffix="%"
          outlined
          dense
          :min="0"
          :step="0.01"
        />
      </div>
    </div>

    <!-- Aplicar Preços às Variações (apenas para UNIFICADA) -->
    <template v-if="tipoPrecificacaoAtual === 'UNIFICADA'">
      <q-separator class="q-my-md" />
      <div class="text-subtitle2 q-mb-md text-primary">Aplicar Preços às Variações</div>

      <div class="row q-col-gutter-md q-mb-md">
        <div class="col-6">
          <q-btn
            color="primary"
            icon="sync"
            label="Aplicar a Todas"
            outline
            class="full-width"
            @click="aplicarPrecosTodasVariacoes"
            :disable="!podeAplicarPrecos"
          >
            <q-tooltip v-if="!podeAplicarPrecos">Defina os preços antes de aplicar</q-tooltip>
          </q-btn>
        </div>
        <div class="col-6">
          <q-btn
            color="secondary"
            icon="filter_list"
            label="Aplicar às Vazias"
            outline
            class="full-width"
            @click="aplicarPrecosVariacoesVazias"
            :disable="!podeAplicarPrecos"
          >
            <q-tooltip v-if="!podeAplicarPrecos">Defina os preços antes de aplicar</q-tooltip>
          </q-btn>
        </div>
      </div>
    </template>

    <!-- Informações Calculadas -->
    <q-separator class="q-my-md" />
    <div class="text-subtitle2 q-mb-md text-info">Informações Calculadas</div>

    <div class="row q-col-gutter-md">
      <div class="col-3">
        <q-input
          :model-value="lucroUnitario"
          label="Lucro Unitário"
          prefix="R$"
          readonly
          outlined
          dense
          color="positive"
        />
      </div>
      <div class="col-3">
        <q-input
          :model-value="percentualLucro"
          label="Percentual de Lucro"
          suffix="%"
          readonly
          outlined
          dense
          color="positive"
        />
      </div>
      <div class="col-3">
        <q-input
          :model-value="formData.precoVenda || 0"
          label="Preço Atual"
          prefix="R$"
          readonly
          outlined
          dense
          color="primary"
        />
      </div>
      <div class="col-3">
        <q-input
          :model-value="markup"
          label="Markup"
          suffix="x"
          readonly
          outlined
          dense
          color="info"
        />
      </div>
    </div>

    <!-- Dicas -->
    <q-banner class="q-mt-md bg-info text-white" rounded>
      <template v-slot:avatar>
        <q-icon name="lightbulb" />
      </template>
      <div class="text-subtitle2">Dicas:</div>
      <ul class="q-ma-none q-pl-md">
        <li v-if="formData.tipoCalculoMargem === 'SOBRE_CUSTO'">
          A margem sobre custo calcula: Preço Venda = Custo × (1 + Margem/100)
        </li>
        <li v-else-if="formData.tipoCalculoMargem === 'SOBRE_VENDA'">
          A margem sobre venda calcula: Preço Venda = Custo ÷ (1 - Margem/100)
        </li>
        <li v-else-if="formData.tipoCalculoMargem === 'VALOR_FIXO'">
          O valor fixo soma: Preço Venda = Custo + Margem
        </li>
        <li v-if="tipoPrecificacaoAtual === 'UNIFICADA'">
          Use os botões acima para aplicar os preços às variações já criadas
        </li>
      </ul>
    </q-banner>
  </div>
</template>

<script setup>
import { ref, computed, watch, toRefs } from 'vue'
import { useQuasar } from 'quasar'

const props = defineProps({
  modelValue: {
    type: Object,
    required: true
  },
  tipoPrecificacao: {
    type: String,
    default: 'SIMPLES'
  }
})

const emit = defineEmits(['update:modelValue'])

const $q = useQuasar()

// Refs locais
const formData = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const tipoPrecificacaoAtual = computed(() => props.tipoPrecificacao || formData.value.tipoPrecificacao || 'SIMPLES')

// Opções de tipo de cálculo
const tiposCalculoMargem = [
  { label: 'Margem sobre Custo (%)', value: 'SOBRE_CUSTO' },
  { label: 'Margem sobre Venda (%)', value: 'SOBRE_VENDA' },
  { label: 'Valor Fixo de Lucro (R$)', value: 'VALOR_FIXO' }
]

// Computed para informações calculadas
const lucroUnitario = computed(() => {
  const custo = parseFloat(formData.value.precoCusto) || 0
  const venda = parseFloat(formData.value.precoVenda) || 0
  return (venda - custo).toFixed(2)
})

const percentualLucro = computed(() => {
  const custo = parseFloat(formData.value.precoCusto) || 0
  const venda = parseFloat(formData.value.precoVenda) || 0
  if (custo === 0) return '0.00'
  return (((venda - custo) / custo) * 100).toFixed(2)
})

const markup = computed(() => {
  const custo = parseFloat(formData.value.precoCusto) || 0
  const venda = parseFloat(formData.value.precoVenda) || 0
  if (custo === 0) return '0.00'
  return (venda / custo).toFixed(2)
})

const podeAplicarPrecos = computed(() => {
  return formData.value.precoCusto > 0 && formData.value.precoVenda > 0
})

// Funções de cálculo
function calcularPrecoVenda() {
  const custo = parseFloat(formData.value.precoCusto) || 0
  const margem = parseFloat(formData.value.margemLucro) || 0
  const tipo = formData.value.tipoCalculoMargem

  if (custo === 0 || margem === 0) return

  let precoVenda = 0

  switch (tipo) {
    case 'SOBRE_CUSTO':
      precoVenda = custo * (1 + margem / 100)
      break
    case 'SOBRE_VENDA':
      precoVenda = custo / (1 - margem / 100)
      break
    case 'VALOR_FIXO':
      precoVenda = custo + margem
      break
  }

  formData.value.precoVenda = parseFloat(precoVenda.toFixed(2))
}

function calcularMargem() {
  const custo = parseFloat(formData.value.precoCusto) || 0
  const venda = parseFloat(formData.value.precoVenda) || 0
  const tipo = formData.value.tipoCalculoMargem

  if (custo === 0 || venda === 0) return

  let margem = 0

  switch (tipo) {
    case 'SOBRE_CUSTO':
      margem = ((venda - custo) / custo) * 100
      break
    case 'SOBRE_VENDA':
      margem = ((venda - custo) / venda) * 100
      break
    case 'VALOR_FIXO':
      margem = venda - custo
      break
  }

  formData.value.margemLucro = parseFloat(margem.toFixed(2))
}

function aplicarMargem() {
  calcularPrecoVenda()
}

function aplicarPrecosTodasVariacoes() {
  if (!formData.value.skus || formData.value.skus.length === 0) {
    $q.notify({
      type: 'warning',
      message: 'Não há variações para aplicar os preços',
      position: 'top'
    })
    return
  }

  const precoCusto = formData.value.precoCusto
  const precoVenda = formData.value.precoVenda

  formData.value.skus = formData.value.skus.map(sku => ({
    ...sku,
    precoCusto,
    precoVenda
  }))

  $q.notify({
    type: 'positive',
    message: `Preços aplicados a ${formData.value.skus.length} variações`,
    position: 'top'
  })
}

function aplicarPrecosVariacoesVazias() {
  if (!formData.value.skus || formData.value.skus.length === 0) {
    $q.notify({
      type: 'warning',
      message: 'Não há variações para aplicar os preços',
      position: 'top'
    })
    return
  }

  const precoCusto = formData.value.precoCusto
  const precoVenda = formData.value.precoVenda
  let count = 0

  formData.value.skus = formData.value.skus.map(sku => {
    if (!sku.precoCusto || !sku.precoVenda) {
      count++
      return { ...sku, precoCusto, precoVenda }
    }
    return sku
  })

  if (count === 0) {
    $q.notify({
      type: 'info',
      message: 'Todas as variações já possuem preços definidos',
      position: 'top'
    })
  } else {
    $q.notify({
      type: 'positive',
      message: `Preços aplicados a ${count} variações vazias`,
      position: 'top'
    })
  }
}

// Inicializar tipo de cálculo se não existir
if (!formData.value.tipoCalculoMargem) {
  formData.value.tipoCalculoMargem = 'SOBRE_CUSTO'
}
</script>

<style scoped>
.precificacao-container {
  padding: 16px;
}
</style>
