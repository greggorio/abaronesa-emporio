<template>
  <div class="filters-card q-mb-md">
    <div class="filters-content">
      <div class="row q-col-gutter-md items-end">
        <!-- Status -->
        <div class="col-12 col-sm-6 col-md-3">
          <div class="filter-label">Status</div>
          <q-select
            v-model="localFilters.status"
            :options="statusOptions"
            option-value="value"
            option-label="label"
            emit-value
            map-options
            outlined
            dense
            clearable
            @update:model-value="emitFilters"
          >
            <template v-slot:prepend>
              <q-icon name="filter_alt" style="color: #6B3E26" />
            </template>
          </q-select>
        </div>

        <!-- Fornecedor -->
        <div class="col-12 col-sm-6 col-md-3">
          <div class="filter-label">Fornecedor</div>
          <q-select
            v-model="localFilters.fornecedorId"
            :options="fornecedores"
            option-value="id"
            option-label="nomeExibicao"
            emit-value
            map-options
            outlined
            dense
            clearable
            use-input
            input-debounce="300"
            @filter="filterFornecedores"
            @update:model-value="emitFilters"
          >
            <template v-slot:prepend>
              <q-icon name="business" style="color: #6B3E26" />
            </template>
            <template v-slot:no-option>
              <q-item>
                <q-item-section class="text-grey">
                  Nenhum fornecedor encontrado
                </q-item-section>
              </q-item>
            </template>
          </q-select>
        </div>

        <!-- Período De -->
        <div class="col-12 col-sm-6 col-md-2">
          <div class="filter-label">Período De</div>
          <q-input
            v-model="localFilters.de"
            type="date"
            outlined
            dense
            clearable
            @update:model-value="emitFilters"
          >
            <template v-slot:prepend>
              <q-icon name="event" style="color: #6B3E26" />
            </template>
          </q-input>
        </div>

        <!-- Período Até -->
        <div class="col-12 col-sm-6 col-md-2">
          <div class="filter-label">Até</div>
          <q-input
            v-model="localFilters.ate"
            type="date"
            outlined
            dense
            clearable
            @update:model-value="emitFilters"
          >
            <template v-slot:prepend>
              <q-icon name="event" style="color: #6B3E26" />
            </template>
          </q-input>
        </div>

        <!-- Busca -->
        <div class="col-12 col-sm-6 col-md-2">
          <div class="filter-label">Buscar</div>
          <q-input
            v-model="localFilters.search"
            placeholder="Observação..."
            outlined
            dense
            clearable
            debounce="500"
            @update:model-value="emitFilters"
          >
            <template v-slot:prepend>
              <q-icon name="search" style="color: #6B3E26" />
            </template>
          </q-input>
        </div>
      </div>

      <!-- Ações -->
      <div class="row q-col-gutter-sm q-mt-sm">
        <div class="col-auto">
          <q-btn
            outline
            icon="clear_all"
            label="Limpar Filtros"
            @click="clearFilters"
            :disable="!hasActiveFilters"
            style="color: #6B3E26; border-color: #6B3E26"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useApiRequest } from '@/composables/useApiRequest'

const emit = defineEmits(['update:filters'])

const { apiRequest } = useApiRequest()

const statusOptions = [
  { label: 'Rascunho', value: 'RASCUNHO' },
  { label: 'Enviado', value: 'ENVIADO' },
  { label: 'Parcial', value: 'PARCIAL' },
  { label: 'Recebido', value: 'RECEBIDO' },
  { label: 'Cancelado', value: 'CANCELADO' }
]

const localFilters = reactive({
  status: null,
  fornecedorId: null,
  de: null,
  ate: null,
  search: null
})

const fornecedores = ref([])
const fornecedoresOriginal = ref([])

const hasActiveFilters = computed(() => {
  return Object.values(localFilters).some(v => v !== null && v !== '')
})

async function loadFornecedores() {
  try {
    const data = await apiRequest('/api/fornecedores')
    fornecedoresOriginal.value = Array.isArray(data) ? data.map(f => ({
      id: f.id,
      nomeExibicao: f.nomeFantasia || f.razaoSocial
    })) : []
    fornecedores.value = fornecedoresOriginal.value
  } catch (e) {
    console.error('Erro ao carregar fornecedores:', e)
    fornecedores.value = []
  }
}

function filterFornecedores(val, update) {
  update(() => {
    if (val === '') {
      fornecedores.value = fornecedoresOriginal.value
    } else {
      const needle = val.toLowerCase()
      fornecedores.value = fornecedoresOriginal.value.filter(
        f => f.nomeExibicao.toLowerCase().includes(needle)
      )
    }
  })
}

function emitFilters() {
  const filters = {}
  if (localFilters.status) filters.status = localFilters.status
  if (localFilters.fornecedorId) filters.fornecedorId = localFilters.fornecedorId
  if (localFilters.de) filters.de = localFilters.de
  if (localFilters.ate) filters.ate = localFilters.ate
  if (localFilters.search) filters.search = localFilters.search

  emit('update:filters', filters)
}

function clearFilters() {
  localFilters.status = null
  localFilters.fornecedorId = null
  localFilters.de = null
  localFilters.ate = null
  localFilters.search = null
  emitFilters()
}

onMounted(() => {
  loadFornecedores()
})
</script>

<style scoped>
.filters-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(107, 62, 38, 0.08);
  overflow: hidden;
}

.filters-content {
  padding: 20px 24px;
}

.filter-label {
  font-size: 0.75rem;
  color: #6B3E26;
  margin-bottom: 6px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}
</style>
