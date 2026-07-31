<template>
  <div>
    <q-input
      dense
      outlined
      stack-label
      :label="label"
      v-model="displayLabel"
      :disable="disabled"
      readonly
      @click="openDialog"
    >
      <template #append>
        <q-icon name="search" class="cursor-pointer" @click.stop="openDialog" v-if="!disabled" />
        <q-icon name="close" class="cursor-pointer" @click.stop="clearSelection" v-if="modelValue && !disabled" />
      </template>
    </q-input>

    <q-dialog v-model="dialog" maximized persistent>
      <q-card class="flex flex-col h-full">
        <q-bar class="bg-primary text-white">
          <div>{{ label }}</div>
          <q-space />
          <q-btn dense flat icon="close" @click="dialog = false" />
        </q-bar>

        <q-card-section class="q-pb-none bg-grey-1">
          <q-input
            v-model="search"
            dense
            debounce="300"
            placeholder="Pesquisar SKU, produto, código de barras..."
            hide-bottom-space
            @update:model-value="fetchOptions"
            autofocus
            clearable
          >
            <template #append>
              <q-icon name="search" />
            </template>
          </q-input>
        </q-card-section>

        <q-card-section class="q-pt-none scroll" style="height: calc(100% - 130px)">
          <q-table
            :rows="options"
            :columns="columns"
            row-key="value"
            :loading="loading"
            flat
            dense
            :rows-per-page-options="[10, 20, 50]"
            :pagination="{ rowsPerPage: 10 }"
            selection="single"
            v-model:selected="selected"
            @row-dblclick="confirmSelection"
          >
            <template #body-cell="props">
              <q-td :props="props">{{ props.row[props.col.field] }}</q-td>
            </template>
            <template #no-data>
              <div class="full-width text-center q-pa-md text-grey-6">
                <q-icon name="search_off" size="48px" class="q-mb-sm" />
                <div>Nenhum resultado</div>
              </div>
            </template>
          </q-table>
        </q-card-section>

        <q-card-actions align="right" class="bg-grey-1">
          <q-btn flat label="Cancelar" color="secondary" @click="dialog = false" />
          <q-btn flat label="Selecionar" color="primary" :disable="!selected.length" @click="confirmSelection" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
  
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useApiRequest } from '@/composables/useApiRequest'

const props = defineProps({
  modelValue: { default: null },
  produtoId: { type: Number, required: true },
  label: { type: String, default: 'SKU / Variação' },
  disabled: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue'])
const { apiRequest } = useApiRequest()

const dialog = ref(false)
const search = ref('')
const options = ref([])
const loading = ref(false)
const selected = ref([])
const displayLabel = ref('')

const columns = [
  { name: 'label', label: 'SKU', field: 'label', align: 'left' },
  { name: 'codigoBarras', label: 'Código de Barras', field: 'codigoBarras', align: 'left' },
  { name: 'preco', label: 'Preço', field: 'preco', align: 'right' },
  { name: 'estoque', label: 'Estoque', field: 'estoque', align: 'right' },
]

watch(() => props.modelValue, async (val) => {
  if (!val) { displayLabel.value = ''; selected.value = []; return }
  const opt = options.value.find(o => o.value === val)
  if (opt) { displayLabel.value = opt.label; selected.value = [opt]; return }
  await fetchSingle(val)
}, { immediate: true })

async function fetchSingle(id) {
  try {
    const resp = await apiRequest(`/api/skus/${id}`)
    const d = resp && resp.data ? resp.data : resp
    if (d && d.id) {
      const lbl = [d.sku, d.produto, d.variacao].filter(Boolean).join(' - ')
      const opt = { value: d.id, label: lbl, codigoBarras: d.codigoBarras || '', preco: d.precoVenda || null, estoque: d.estoque ?? null }
      options.value.unshift(opt)
      selected.value = [opt]
      displayLabel.value = lbl
    }
  } catch (e) {
    displayLabel.value = `ID: ${id}`
  }
}

async function fetchOptions() {
  if (!props.produtoId) return
  loading.value = true
  try {
    const url = `/api/skus/search-options?produtoId=${props.produtoId}&q=${encodeURIComponent(search.value || '')}`
    const resp = await apiRequest(url)
    const list = resp && resp.data ? resp.data : []
    options.value = list
  } catch (e) {
    options.value = []
  } finally {
    loading.value = false
  }
}

function openDialog() {
  if (props.disabled) return
  dialog.value = true
  search.value = ''
  fetchOptions()
}

function clearSelection() {
  emit('update:modelValue', null)
  displayLabel.value = ''
  selected.value = []
}

function confirmSelection() {
  if (!selected.value.length) return
  const item = selected.value[0]
  emit('update:modelValue', item.value)
  displayLabel.value = item.label
  dialog.value = false
}

onMounted(async () => {
  if (props.modelValue && !displayLabel.value) {
    await fetchSingle(props.modelValue)
  }
})
</script>

<style scoped>
.scroll { overflow-y: auto; }
</style>

