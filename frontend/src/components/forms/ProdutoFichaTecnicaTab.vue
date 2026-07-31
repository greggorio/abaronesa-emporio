<template>
  <div class="ficha-tecnica-container">
    <!-- Banner informativo -->
    <q-banner v-if="!props.modelValue.vendavel" class="q-mb-md bg-grey-3" rounded>
      <template v-slot:avatar>
        <q-icon name="info" color="grey-7" />
      </template>
      <div class="text-caption">
        Ficha técnica disponível apenas para produtos vendáveis.
      </div>
    </q-banner>

    <q-banner v-else-if="!possuiItens && props.modelValue.id" class="q-mb-md bg-blue-1 text-blue-9" rounded>
      <template v-slot:avatar>
        <q-icon name="o_restaurant_menu" color="blue" />
      </template>
      <div class="text-subtitle2">Ficha Técnica</div>
      <div class="text-caption">
        Adicione os ingredientes (insumos) que compõem este produto.
        O custo será calculado automaticamente e a baixa de estoque ocorrerá quando o pedido for aceito.
      </div>
    </q-banner>

    <!-- Resumo do custo -->
    <div v-if="possuiItens" class="row q-mb-md q-col-gutter-sm">
      <div class="col-6">
        <q-card flat bordered>
          <q-card-section class="q-pa-md">
            <div class="text-caption text-grey-7">Custo Total</div>
            <div class="text-h5 text-primary">{{ formatarMoeda(custoTotal) }}</div>
          </q-card-section>
        </q-card>
      </div>
      <div class="col-6">
        <q-card flat bordered>
          <q-card-section class="q-pa-md">
            <div class="text-caption text-grey-7">Ingredientes</div>
            <div class="text-h5">{{ itens.length }}</div>
          </q-card-section>
        </q-card>
      </div>
    </div>

    <!-- Botões de ação -->
    <div class="q-mb-md row q-gutter-sm action-buttons">
      <q-btn
        color="primary"
        icon="add"
        label="Adicionar Ingrediente"
        @click="adicionarIngrediente"
        :disable="!podeAdicionarIngrediente"
        no-caps
      />
      <div v-if="!podeAdicionarIngrediente" class="full-width q-mt-sm">
        <q-banner dense class="bg-grey-3 text-grey-8">
          <template v-slot:avatar>
            <q-icon name="info" color="grey-7" />
          </template>
          Salve o produto antes de adicionar ingredientes
        </q-banner>
      </div>
    </div>

    <!-- Tabela de ingredientes -->
    <div class="table-wrapper">
      <q-table
        v-if="possuiItens"
        :rows="itens"
        :columns="colunas"
        row-key="tempId"
        flat
        bordered
        :loading="loading"
        :pagination="{ rowsPerPage: 10 }"
        no-data-label="Nenhum ingrediente adicionado"
      >
      <!-- Coluna Ingrediente/SKU -->
      <template v-slot:body-cell-insumo="slotProps">
        <q-td :props="slotProps">
          <!-- Item NOVO: mostrar select para escolher -->
          <q-select
            v-if="!slotProps.row.id"
            v-model="slotProps.row.insumoSkuId"
            :options="skuOptions"
            option-value="value"
            option-label="label"
            emit-value
            map-options
            use-input
            input-debounce="300"
            @filter="filterSkus"
            @update:model-value="val => onSkuChange(slotProps.row, val)"
            label="Buscar produto/ingrediente"
            dense
            outlined
            :loading="loadingSkus"
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
                  <q-item-label>{{ scope.opt.label }}</q-item-label>
                  <q-item-label caption v-if="scope.opt.estoqueAtual !== undefined">
                    Estoque: {{ scope.opt.estoqueAtual }}
                  </q-item-label>
                </q-item-section>
              </q-item>
            </template>
          </q-select>

          <!-- Item SALVO: mostrar apenas texto -->
          <div v-else class="ingredient-display">
            <div class="text-body2 text-weight-medium">
              {{ slotProps.row.insumoProdutoNome }}
            </div>
            <div v-if="slotProps.row.embalagemNome" class="text-caption text-grey-7">
              {{ slotProps.row.embalagemNome }}
            </div>
          </div>
        </q-td>
      </template>

      <!-- Coluna Quantidade -->
      <template v-slot:body-cell-quantidade="slotProps">
        <q-td :props="slotProps">
          <q-input
            v-model.number="slotProps.row.quantidade"
            type="number"
            dense
            outlined
            :min="0.001"
            step="0.001"
            @update:model-value="calcularCusto"
            @blur="salvarFichaTecnica"
            style="max-width: 80px"
          >
            <template v-slot:append v-if="slotProps.row.unidade">
              <span class="text-caption">{{ slotProps.row.unidade }}</span>
            </template>
          </q-input>
        </q-td>
      </template>

      <!-- Coluna Custo Unitário -->
      <template v-slot:body-cell-custoUnitario="slotProps">
        <q-td :props="slotProps">
          <div class="text-body2">{{ formatarMoeda(slotProps.row.custoUnitario) }}</div>
        </q-td>
      </template>

      <!-- Coluna Custo Total -->
      <template v-slot:body-cell-custoTotalItem="slotProps">
        <q-td :props="slotProps">
          <div class="text-weight-medium">{{ formatarMoeda(slotProps.row.custoTotal) }}</div>
        </q-td>
      </template>

      <!-- Coluna Estoque -->
      <template v-slot:body-cell-estoque="slotProps">
        <q-td :props="slotProps">
          <q-chip
            :color="slotProps.row.estoqueDisponivel > 0 ? 'positive' : 'negative'"
            text-color="white"
            dense
            size="sm"
          >
            {{ slotProps.row.estoqueDisponivel || 0 }}
          </q-chip>
        </q-td>
      </template>

      <!-- Coluna Ações -->
      <template v-slot:body-cell-acoes="slotProps">
        <q-td :props="slotProps">
          <q-btn
            flat
            round
            dense
            color="negative"
            icon="delete"
            @click="removerIngrediente(slotProps.row)"
          >
            <q-tooltip>Remover</q-tooltip>
          </q-btn>
        </q-td>
      </template>
    </q-table>
    </div>

    <q-inner-loading :showing="salvando">
      <q-spinner-gears size="50px" color="primary" />
    </q-inner-loading>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";

const props = defineProps({
  modelValue: {
    type: Object,
    required: true,
  },
  recordId: {
    type: [Number, String],
    default: null,
  },
});

const emit = defineEmits(["update:model-value"]);

const $q = useQuasar();
const { apiRequest } = useApiRequest();

const loading = ref(false);
const salvando = ref(false);
const loadingSkus = ref(false);
const skuOptions = ref([]);
const itens = ref([]);
const custoTotal = ref(0);

const podeAdicionarIngrediente = computed(() => {
  return props.modelValue.id && props.modelValue.vendavel;
});

const possuiItens = computed(() => {
  return itens.value && itens.value.length > 0;
});

const colunas = [
  {
    name: "insumo",
    label: "Ingrediente/Insumo",
    align: "left",
    field: "insumoProdutoNome",
    sortable: false,
    style: "width: 35%; min-width: 150px",
  },
  {
    name: "quantidade",
    label: "Qtd.",
    align: "left",
    field: "quantidade",
    sortable: false,
    style: "width: 100px",
  },
  {
    name: "custoUnitario",
    label: "Custo Unit.",
    align: "right",
    field: "custoUnitario",
    sortable: false,
  },
  {
    name: "custoTotalItem",
    label: "Custo Total",
    align: "right",
    field: "custoTotal",
    sortable: false,
  },
  {
    name: "estoque",
    label: "Estoque",
    align: "center",
    field: "estoqueDisponivel",
    sortable: false,
  },
  {
    name: "acoes",
    label: "Ações",
    align: "center",
    sortable: false,
    style: "width: 80px",
  },
];

let tempIdCounter = 0;

onMounted(() => {
  if (props.recordId) {
    carregarFichaTecnica();
  }
});

watch(
  () => props.recordId,
  (newId) => {
    if (newId) {
      carregarFichaTecnica();
    }
  }
);

// Carregar ficha técnica do produto
async function carregarFichaTecnica() {
  if (!props.recordId) return;

  loading.value = true;
  try {
    const response = await apiRequest(`/api/ficha-tecnica/produto/${props.recordId}`);

    if (response.itens && response.itens.length > 0) {
      itens.value = response.itens.map(item => {
        // Criar label formatado para o SKU
        const label = formatarLabelIngrediente(item);

        // Adicionar às opções do select se não existir
        if (!skuOptions.value.find(opt => opt.value === item.insumoSkuId)) {
          skuOptions.value.push({
            value: item.insumoSkuId,
            label: label,
            descricao: item.insumoProdutoNome,
            variacao: item.insumoVariacao,
            precoCusto: item.custoUnitario,
            estoqueAtual: item.estoqueDisponivel,
            unidade: item.unidade
          });
        }

        return {
          ...item,
          tempId: tempIdCounter++,
          custoTotal: (item.quantidade || 0) * (item.custoUnitario || 0),
        };
      });
      custoTotal.value = response.custoTotal || 0;
    }
  } catch (error) {
    console.error("Erro ao carregar ficha técnica:", error);
  } finally {
    loading.value = false;
  }
}

// Formatar label do ingrediente
function formatarLabelIngrediente(item) {
  let label = item.insumoProdutoNome;

  if (item.insumoVariacao) {
    label += ` - ${item.insumoVariacao}`;
  }

  if (item.embalagemNome) {
    label += ` (${item.embalagemNome})`;
  }

  return label;
}

// Filtrar SKUs para o autocomplete
async function filterSkus(val, update) {
  if (!val || val.length < 2) {
    update(() => {
      skuOptions.value = [];
    });
    return;
  }

  loadingSkus.value = true;
  try {
    const response = await apiRequest(`/api/ficha-tecnica/buscar-insumos?search=${encodeURIComponent(val)}`);

    update(() => {
      skuOptions.value = response || [];
    });
  } catch (error) {
    console.error("Erro ao buscar insumos:", error);
    update(() => {
      skuOptions.value = [];
    });
  } finally {
    loadingSkus.value = false;
  }
}

// Quando seleciona um SKU
async function onSkuChange(item, skuId) {
  if (!skuId) return;

  const skuSelecionado = skuOptions.value.find(opt => opt.value === skuId);
  if (skuSelecionado) {
    item.insumoSkuId = skuId;
    item.insumoProdutoNome = skuSelecionado.descricao || skuSelecionado.label;
    item.insumoVariacao = skuSelecionado.variacao;
    item.custoUnitario = skuSelecionado.precoCusto || 0;
    item.estoqueDisponivel = skuSelecionado.estoqueAtual || 0;
    item.unidade = skuSelecionado.unidade;
    item.embalagemNome = skuSelecionado.embalagem;

    if (!item.quantidade) {
      item.quantidade = 1;
    }

    calcularCusto();

    // Auto-salvar quando seleciona ingrediente em item novo
    if (!item.id) {
      await salvarFichaTecnica();
    }
  }
}

// Adicionar novo ingrediente
function adicionarIngrediente() {
  itens.value.push({
    tempId: tempIdCounter++,
    insumoSkuId: null,
    quantidade: 1,
    custoUnitario: 0,
    custoTotal: 0,
    estoqueDisponivel: 0,
    ordem: itens.value.length,
  });
}

// Remover ingrediente
function removerIngrediente(item) {
  $q.dialog({
    title: "Confirmar remoção",
    message: "Deseja realmente remover este ingrediente?",
    cancel: true,
    persistent: true,
  }).onOk(async () => {
    const index = itens.value.findIndex(i => i.tempId === item.tempId);
    if (index > -1) {
      itens.value.splice(index, 1);
      calcularCusto();
      await salvarFichaTecnica();
    }
  });
}

// Calcular custo total
function calcularCusto() {
  itens.value.forEach(item => {
    item.custoTotal = (item.quantidade || 0) * (item.custoUnitario || 0);
  });

  custoTotal.value = itens.value.reduce((total, item) => total + (item.custoTotal || 0), 0);
}

// Salvar ficha técnica
async function salvarFichaTecnica() {
  if (!props.recordId) {
    $q.notify({
      type: "warning",
      message: "Salve o produto antes de adicionar ingredientes",
    });
    return;
  }

  salvando.value = true;
  try {
    const payload = {
      produtoId: props.recordId,
      rendimento: 1,
      itens: itens.value
        .filter(item => item.insumoSkuId)
        .map((item, index) => ({
          id: item.id || null,
          insumoSkuId: item.insumoSkuId,
          quantidade: item.quantidade || 0,
          ordem: index,
        })),
    };

    const response = await apiRequest("/api/ficha-tecnica", "POST", payload);

    custoTotal.value = response.custoTotal || 0;

    $q.notify({
      type: "positive",
      message: "Ficha técnica salva com sucesso!",
    });

    // Recarregar para pegar IDs gerados
    await carregarFichaTecnica();
  } catch (error) {
    console.error("Erro ao salvar ficha técnica:", error);
    $q.notify({
      type: "negative",
      message: "Erro ao salvar ficha técnica: " + error.message,
    });
  } finally {
    salvando.value = false;
  }
}

// Formatar moeda
function formatarMoeda(valor) {
  if (!valor && valor !== 0) return "R$ 0,00";
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL",
  }).format(valor);
}

// Não usar auto-save no watch para evitar chamadas duplicadas
// O save é feito manualmente em onSkuChange, removerIngrediente e ao alterar quantidade
</script>

<style scoped>
.ficha-tecnica-container {
  padding: 16px;
}

/* Wrapper da tabela com scroll horizontal */
.table-wrapper {
  overflow-x: auto;
  width: 100%;
  -webkit-overflow-scrolling: touch;
}

.table-wrapper .q-table {
  width: 100%;
  table-layout: auto;
}

.table-wrapper .q-table thead tr,
.table-wrapper .q-table tbody tr {
  display: table-row;
}

.table-wrapper .q-table th,
.table-wrapper .q-table td {
  display: table-cell;
}

/* Exibição compacta de ingredientes salvos */
.ingredient-display {
  padding: 4px 0;
  line-height: 1.3;
}

.ingredient-display .text-body2 {
  margin-bottom: 2px;
  color: #2a1f1b;
}

.ingredient-display .text-caption {
  line-height: 1.2;
}

/* Responsividade Mobile First */
@media (max-width: 1023px) {
  .ficha-tecnica-container {
    padding: 12px;
  }
}

@media (max-width: 768px) {
  .ficha-tecnica-container {
    padding: 8px;
  }

  /* Botões em coluna no mobile */
  .action-buttons {
    flex-direction: column;
    align-items: stretch;
  }

  .action-buttons .q-btn {
    width: 100%;
    justify-content: center;
  }

  /* Tabela mais compacta */
  .q-table {
    font-size: 0.9rem;
  }

  /* Wrapper com scroll horizontal */
  .table-wrapper {
    position: relative;
  }

  /* Primeira coluna pode quebrar linha */
  .q-table tbody td:first-child {
    white-space: normal;
    word-break: break-word;
  }

  /* Demais células sem quebra */
  .q-table td:not(:first-child),
  .q-table th {
    white-space: nowrap;
  }
}

@media (max-width: 599px) {
  .ficha-tecnica-container {
    padding: 4px;
  }

  /* Tabela ainda mais compacta */
  .q-table {
    font-size: 0.85rem;
  }

  .q-table td,
  .q-table th {
    padding: 6px 4px;
  }

  /* Input de quantidade menor */
  .q-table td q-input {
    max-width: 70px !important;
  }

  /* Texto de ingredientes menor */
  .ingredient-display .text-body2 {
    font-size: 0.85rem;
  }

  .ingredient-display .text-caption {
    font-size: 0.7rem;
  }

  /* Cards de resumo mais compactos */
  .q-card .q-card-section {
    padding: 12px !important;
  }

  .q-card .text-h5 {
    font-size: 1.2rem;
  }
}

/* Dark mode support */
.body--dark .ingredient-display .text-body2 {
  color: #d7b899;
}

.body--dark .table-wrapper {
  box-shadow: inset -4px 0 8px -4px rgba(255, 255, 255, 0.1);
}
</style>
