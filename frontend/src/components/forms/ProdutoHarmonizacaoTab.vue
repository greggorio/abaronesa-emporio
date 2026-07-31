<template>
  <q-card-section class="q-pa-none">
    <div class="row items-center justify-between q-pa-md bg-grey-1">
      <div class="text-subtitle1 text-weight-medium">Produtos Harmonizados</div>
      <q-btn
        color="primary"
        icon="add"
        label="Adicionar Harmonização"
        dense
        unelevated
        @click="openDialog()"
        :disable="!recordId"
      />
    </div>

    <!-- Lista Vazia -->
    <div v-if="harmonizacoes.length === 0" class="text-center q-pa-xl text-grey-6">
      <q-icon name="restaurant_menu" size="4em" class="q-mb-sm" />
      <div v-if="!recordId">Salve o produto primeiro para adicionar harmonizações.</div>
      <div v-else>Nenhuma harmonização cadastrada.</div>
    </div>

    <!-- Lista de Cards com Drag & Drop -->
    <div v-else class="q-pa-md">
      <draggable
        v-model="harmonizacoes"
        item-key="id"
        handle=".drag-handle"
        @end="onDragEnd"
        animation="200"
        ghost-class="ghost-card"
      >
        <template #item="{ element }">
          <q-card bordered flat class="q-mb-sm harmonizacao-card">
            <q-item>
              <q-item-section avatar class="cursor-move drag-handle">
                <q-icon name="drag_indicator" color="grey-6" />
              </q-item-section>

              <q-item-section avatar>
                <q-avatar rounded size="50px" color="grey-2">
                  <img
                    v-if="element.produtoHarmonizado.imagemPrincipal"
                    :src="getImageUrl(element.produtoHarmonizado.imagemPrincipal)"
                    style="object-fit: cover"
                  />
                  <q-icon v-else name="image_not_supported" color="grey-5" />
                </q-avatar>
              </q-item-section>

              <q-item-section>
                <q-item-label class="text-weight-bold">
                  {{ element.produtoHarmonizado.nome }}
                  <span v-if="element.produtoHarmonizado.skuVariacao" class="text-caption text-grey-7">
                     - {{ element.produtoHarmonizado.skuVariacao }}
                  </span>
                </q-item-label>
                <q-item-label caption lines="2" v-if="element.descricao">
                  {{ element.descricao }}
                </q-item-label>
                <div class="q-mt-xs">
                  <q-chip
                    v-if="element.tipo"
                    dense
                    size="sm"
                    color="secondary"
                    text-color="white"
                    icon="local_offer"
                  >
                    {{ element.tipo }}
                  </q-chip>
                </div>
              </q-item-section>
              
              <q-item-section side top>
                 <q-item-label caption class="text-weight-bold text-primary">
                    R$ {{ formatCurrency(element.produtoHarmonizado.preco) }}
                 </q-item-label>
              </q-item-section>

              <q-item-section side>
                <div class="row q-gutter-xs">
                  <q-btn
                    flat
                    round
                    dense
                    color="negative"
                    icon="delete"
                    @click="confirmDelete(element)"
                  >
                    <q-tooltip>Remover</q-tooltip>
                  </q-btn>
                </div>
              </q-item-section>
            </q-item>
          </q-card>
        </template>
      </draggable>
    </div>

    <!-- Dialog de Adição -->
    <q-dialog v-model="dialogOpen" persistent>
      <q-card style="min-width: 500px">
        <q-card-section class="row items-center q-pb-none">
          <div class="text-h6">Adicionar Harmonização</div>
          <q-space />
          <q-btn icon="close" flat round dense v-close-popup />
        </q-card-section>

        <q-card-section class="q-pt-md q-gutter-y-md">
          <!-- Lookup de Produto -->
          <q-select
            v-model="form.produtoHarmonizado"
            label="Buscar Produto"
            outlined
            use-input
            fill-input
            hide-selected
            input-debounce="300"
            :options="produtoOptions"
            @filter="filterProdutos"
            option-label="label"
            option-value="id"
            :loading="loadingSearch"
            hint="Digite o nome ou código do produto"
            @update:model-value="onProdutoSelected"
          >
            <template v-slot:no-option>
              <q-item>
                <q-item-section class="text-grey">Nenhum produto encontrado</q-item-section>
              </q-item>
            </template>
            <template v-slot:option="scope">
              <q-item v-bind="scope.itemProps">
                <q-item-section avatar v-if="scope.opt.imagem">
                   <img :src="getImageUrl(scope.opt.imagem)" style="width: 30px; height: 30px; object-fit: cover; border-radius: 4px;" />
                </q-item-section>
                <q-item-section>
                  <q-item-label>{{ scope.opt.label }}</q-item-label>
                  <q-item-label caption>R$ {{ formatCurrency(scope.opt.preco) }}</q-item-label>
                </q-item-section>
              </q-item>
            </template>
          </q-select>

          <!-- Seleção de SKU/Variação -->
          <q-select
            v-if="form.produtoHarmonizado"
            v-model="form.skuSelecionado"
            :options="skuOptions"
            label="Variação / SKU"
            outlined
            option-label="label"
            option-value="id"
            :loading="loadingSkus"
            :disable="loadingSkus || skuOptions.length === 0"
            hint="Selecione a variação específica se necessário"
          >
             <template v-slot:option="scope">
              <q-item v-bind="scope.itemProps">
                <q-item-section>
                  <q-item-label>{{ scope.opt.label }}</q-item-label>
                  <q-item-label caption>R$ {{ formatCurrency(scope.opt.preco) }}</q-item-label>
                </q-item-section>
              </q-item>
            </template>
          </q-select>

          <!-- Tipo de Harmonização -->
          <q-select
            v-model="form.tipo"
            :options="tiposHarmonizacao"
            label="Tipo de Harmonização"
            outlined
            emit-value
            map-options
            clearable
          />

          <!-- Descrição -->
          <q-input
            v-model="form.descricao"
            label="Descrição (Opcional)"
            outlined
            type="textarea"
            rows="3"
            hint="Ex: 'Este vinho realça o sabor...'"
          />
        </q-card-section>

        <q-card-actions align="right" class="text-primary q-pa-md">
          <q-btn flat label="Cancelar" v-close-popup />
          <q-btn
            unelevated
            color="primary"
            label="Salvar"
            @click="saveHarmonizacao"
            :loading="saving"
            :disable="!form.produtoHarmonizado"
          />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-card-section>
</template>

<script setup>
import { ref, watch } from "vue";
import { useApiRequest } from "@/composables/useApiRequest";
import { useQuasar } from "quasar";
import draggable from "vuedraggable";
import { baseApiUrl } from "@/global";

const props = defineProps({
  recordId: {
    type: Number,
    default: null,
  },
});

const $q = useQuasar();
const { apiRequest } = useApiRequest();

const harmonizacoes = ref([]);
const dialogOpen = ref(false);
const saving = ref(false);
const loadingSearch = ref(false);
const produtoOptions = ref([]);

// SKU Refs
const skuOptions = ref([]);
const loadingSkus = ref(false);

const tiposHarmonizacao = [
  { label: "Complementar", value: "COMPLEMENTAR" },
  { label: "Contraste", value: "CONTRASTE" },
  { label: "Semelhança", value: "SEMELHANCA" },
  { label: "Regional", value: "REGIONAL" },
];

const form = ref({
  produtoHarmonizado: null,
  skuSelecionado: null,
  tipo: null,
  descricao: "",
});

function getImageUrl(url) {
  if (!url) return "";
  let urlString = url;
  if (typeof url === "object" && url !== null) {
    urlString = url.url || url.path || url.src || "";
  }
  urlString = String(urlString);
  if (!urlString) return "";
  if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
    return urlString;
  }
  const cleanUrl = urlString.startsWith("/") ? urlString.substring(1) : urlString;
  const finalUrl = `${baseApiUrl}/${cleanUrl}`;
  return finalUrl;
}

const loadHarmonizacoes = async () => {
  if (!props.recordId) return;
  try {
    const data = await apiRequest(`/api/produtos/${props.recordId}/harmonizacoes`);
    harmonizacoes.value = (data || []).sort((a, b) => (a.ordem || 0) - (b.ordem || 0));
  } catch (error) {
    console.error("Erro ao carregar harmonizações:", error);
  }
};

const openDialog = () => {
  form.value = { produtoHarmonizado: null, skuSelecionado: null, tipo: null, descricao: "" };
  produtoOptions.value = [];
  skuOptions.value = [];
  dialogOpen.value = true;
};

const saveHarmonizacao = async () => {
  if (!props.recordId || !form.value.produtoHarmonizado) return;

  saving.value = true;
  try {
    const nextOrder = harmonizacoes.value.length > 0
      ? Math.max(...harmonizacoes.value.map(h => h.ordem || 0)) + 1
      : 0;

    const payload = {
      produtoHarmonizadoId: form.value.produtoHarmonizado.id,
      skuHarmonizadoId: form.value.skuSelecionado ? form.value.skuSelecionado.id : null,
      tipo: form.value.tipo,
      descricao: form.value.descricao,
      ordem: nextOrder
    };

    await apiRequest(`/api/produtos/${props.recordId}/harmonizacoes`, "POST", payload);

    $q.notify({ type: "positive", message: "Harmonização adicionada!" });
    dialogOpen.value = false;
    await loadHarmonizacoes();
  } catch (error) {
    console.error("Erro ao salvar:", error);
  } finally {
    saving.value = false;
  }
};

const confirmDelete = (item) => {
  $q.dialog({
    title: "Confirmar Remoção",
    message: `Deseja remover a harmonização com "${item.produtoHarmonizado.nome}"?`,
    cancel: true,
    persistent: true,
  }).onOk(async () => {
    try {
      await apiRequest(`/api/produtos/${props.recordId}/harmonizacoes/${item.id}`, "DELETE");
      $q.notify({ type: "positive", message: "Removido com sucesso!" });
      await loadHarmonizacoes();
    } catch (error) {
      console.error("Erro ao remover:", error);
    }
  });
};

const onDragEnd = async () => {
  // Implementação futura de reordenação
};

const filterProdutos = async (val, update, abort) => {
  if (val.length < 2) {
    abort();
    return;
  }

  loadingSearch.value = true;
  try {
    const data = await apiRequest(`/api/produtos/lookup/search?search=${encodeURIComponent(val)}`);
    update(() => {
      produtoOptions.value = data.map((p) => ({
        label: p.descricao || p.nome,
        id: p.id,
        preco: p.preco,
        imagem: p.imagemPrincipal
      })).filter(p => p.id !== props.recordId);
    });
  } catch (e) {
    console.error(e);
    update(() => { produtoOptions.value = []; });
  } finally {
    loadingSearch.value = false;
  }
};

// --- Logic to Fetch SKUs on Product Selection ---
const onProdutoSelected = async (produto) => {
  form.value.skuSelecionado = null;
  skuOptions.value = [];
  
  if (!produto || !produto.id) return;

  loadingSkus.value = true;
  try {
    // Fetch full product details to get SKUs
    const fullProduto = await apiRequest(`/api/produtos/${produto.id}`);
    
    if (fullProduto && fullProduto.skus && fullProduto.skus.length > 0) {
      skuOptions.value = fullProduto.skus
        .filter(sku => sku.ativo) // Filter active SKUs
        .map(sku => ({
            id: sku.id,
            label: sku.variacao || 'Padrão',
            preco: sku.precoVenda,
            principal: sku.principal
        }));
        
        // Optional: Auto-select principal SKU if desired
        const principal = skuOptions.value.find(s => s.principal);
        if (principal) {
            form.value.skuSelecionado = principal;
        } else if (skuOptions.value.length === 1) {
            form.value.skuSelecionado = skuOptions.value[0];
        }
    }
  } catch (error) {
    console.error("Erro ao buscar SKUs do produto:", error);
    $q.notify({ type: 'warning', message: 'Não foi possível carregar as variações deste produto.' });
  } finally {
    loadingSkus.value = false;
  }
};

const formatCurrency = (val) => {
    if(val === undefined || val === null) return "0,00";
    return val.toLocaleString('pt-BR', { minimumFractionDigits: 2 });
}

watch(() => props.recordId, async (newId) => {
  if (newId) {
    await loadHarmonizacoes();
  } else {
    harmonizacoes.value = [];
  }
}, { immediate: true });

</script>

<style scoped>
.harmonizacao-card {
  transition: box-shadow 0.3s;
}
.harmonizacao-card:hover {
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}
.ghost-card {
  opacity: 0.5;
  background: #f0f0f0;
}
.drag-handle {
    cursor: grab;
}
.drag-handle:active {
    cursor: grabbing;
}
</style>
