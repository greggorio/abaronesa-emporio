<template>
  <div class="q-mb-md">
    <div class="row q-col-gutter-md items-center">
      <div class="col-12 col-md-4">
        <q-input
          v-model="searchText"
          outlined
          dense
          bg-color="white"
          placeholder="Buscar produtos..."
          class="full-width"
          @update:model-value="emitSearch"
        >
          <template v-slot:prepend>
            <q-icon name="search" color="secondary" />
          </template>
          <template v-slot:append>
            <q-icon name="qr_code_scanner" color="secondary" class="cursor-pointer" @click="openScanner" />
          </template>
        </q-input>
      </div>
      <div class="col-12 col-md-7 text-right dense">
        <div class="row q-col-gutter-sm">
          <div class="col-6">
            <q-select
              v-model="categoriaFiltro"
              outlined
              dense
              bg-color="white"
              label="Categoria"
              :options="categoriaOptions"
              class="full-width"
              @update:model-value="emitSearch"
            />
          </div>
          <div class="col-6">
            <q-select
              v-model="statusFiltro"
              outlined
              dense
              bg-color="white"
              label="Status"
              :options="statusOptions"
              class="full-width"
              @update:model-value="emitSearch"
            />
          </div>
        </div>
      </div>
      <div class="col-12 col-md-1 text-right">
        <q-btn flat round color="secondary" icon="filter_list" class="q-ml-sm">
          <q-menu anchor="bottom right" self="top right">
            <q-list style="min-width: 200px">
              <q-item-label header>Filtros Avançados</q-item-label>
              <q-item tag="label">
                <q-item-section>
                  <q-checkbox v-model="filters.showDivergencias" label="Somente divergências" />
                </q-item-section>
              </q-item>
              <q-item tag="label">
                <q-item-section>
                  <q-checkbox v-model="filters.showNaoContados" label="Não contados" />
                </q-item-section>
              </q-item>
              <q-separator />
              <q-item>
                <q-item-section>
                  <q-slider v-model="filters.minDivergencia" :min="0" :max="100" label :label-value="filters.minDivergencia + '%'" />
                  <div class="text-caption">Divergência mínima: {{ filters.minDivergencia }}%</div>
                </q-item-section>
              </q-item>
              <q-separator />
              <q-item>
                <q-item-section>
                  <div class="row items-center justify-end">
                    <q-btn color="secondary" label="Aplicar" size="sm" @click="applyFilters" />
                  </div>
                </q-item-section>
              </q-item>

              <q-item>
                <q-item-section>
                  <div class="row items-center justify-end">
                    <q-btn flat round color="secondary" icon="refresh" size="sm" @click="refreshData" />
                  </div>
                </q-item-section>
              </q-item>
            </q-list>
          </q-menu>
        </q-btn>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, defineEmits, defineProps, watch, computed } from "vue";

const props = defineProps({
  produtos: {
    type: Array,
    default: () => [],
  },
});

const emit = defineEmits(["search", "view-change"]);

const searchText = ref("");
const categoriaFiltro = ref("Todas");
const statusFiltro = ref("Todos");
const viewMode = ref("list");

const filters = ref({
  showDivergencias: false,
  showNaoContados: false,
  minDivergencia: 0, // Alterado para 0 em vez de 10
});

// Extrair categorias únicas dos produtos
const categoriaOptions = computed(() => {
  const categorias = new Set(["Todas"]);
  props.produtos.forEach((produto) => {
    if (produto.categoria_nome) {
      categorias.add(produto.categoria_nome);
    }
  });
  return Array.from(categorias);
});

const statusOptions = ["Todos", "Divergência", "OK", "Não contado"];

const emitSearch = () => {
  emit("search", {
    text: searchText.value,
    categoria: categoriaFiltro.value,
    status: statusFiltro.value,
    ...filters.value,
  });
};

const setView = (view) => {
  viewMode.value = view;
  emit("view-change", view);
};

const applyFilters = () => {
  // Log para depuração
  console.log("Aplicando filtros:", {
    showDivergencias: filters.value.showDivergencias,
    showNaoContados: filters.value.showNaoContados,
    minDivergencia: filters.value.minDivergencia,
  });

  // Emitir o evento com todos os filtros atualizados
  emit("search", {
    text: searchText.value,
    categoria: categoriaFiltro.value,
    status: statusFiltro.value,
    showDivergencias: filters.value.showDivergencias,
    showNaoContados: filters.value.showNaoContados,
    minDivergencia: filters.value.minDivergencia,
  });
};

const refreshData = () => {
  // Reset filters
  searchText.value = "";
  categoriaFiltro.value = "Todas";
  statusFiltro.value = "Todos";
  filters.value = {
    showDivergencias: false,
    showNaoContados: false,
    minDivergencia: 0, // Também alterado aqui
  };

  // Emitir busca com filtros resetados
  emitSearch();

  // Notificar para atualizar os dados
  emit("refresh");
};

const openScanner = () => {
  console.log("Abrir scanner");
  // Implementar lógica
};

// Emitir busca inicial quando o componente for montado
emitSearch();
</script>
