<template>
  <div class="q-pa-md">
    <div class="text-h6 text-secondary q-mb-md">
      <q-icon name="build" class="q-mr-sm" />
      Ajustes de Inventário
    </div>

    <div class="row q-col-gutter-md q-mb-md">
      <div class="col-12 col-md-4">
        <q-card class="bg-orange-1 text-center">
          <q-card-section>
            <div class="text-h3 text-orange">{{ ajustes.length }}</div>
            <div class="text-subtitle2">Produtos Ajustados</div>
          </q-card-section>
        </q-card>
      </div>

      <div class="col-12 col-md-4">
        <q-card class="bg-blue-1 text-center">
          <q-card-section>
            <div class="text-h3 text-primary">{{ totalAjustes }}</div>
            <div class="text-subtitle2">Qtde Total Ajustada</div>
          </q-card-section>
        </q-card>
      </div>

      <div class="col-12 col-md-4">
        <q-card class="bg-green-1 text-center">
          <q-card-section>
            <div class="text-h3" :class="impactoTotal >= 0 ? 'text-positive' : 'text-negative'">
              {{ formatCurrency(impactoTotal) }}
            </div>
            <div class="text-subtitle2">Impacto Total</div>
          </q-card-section>
        </q-card>
      </div>
    </div>

    <q-card flat bordered class="bg-white">
      <q-card-section class="q-pa-none">
        <q-table :rows="ajustes" :columns="colunas" row-key="id" separator="cell" :pagination="{ rowsPerPage: 10 }" dense color="secondary">
          <template v-slot:header-cell="props">
            <q-th :props="props" class="text-secondary">
              {{ props.col.label }}
            </q-th>
          </template>

          <template v-slot:body-cell-produto_codigo="props">
            <q-td :props="props">
              <q-chip square color="secondary" text-color="white" size="sm">
                {{ props.value }}
              </q-chip>
            </q-td>
          </template>

          <template v-slot:body-cell-produto="props">
            <q-td :props="props">
              <div class="row items-center no-wrap">
                <div>
                  <div class="text-weight-medium">{{ props.value }}</div>
                  <div class="text-caption text-grey">{{ props.row.categoria_nome }}</div>
                </div>
              </div>
            </q-td>
          </template>

          <template v-slot:body-cell-divergencia_percentual="props">
            <q-td :props="props">
              <q-badge :color="getDivergenciaColor(props.value)" class="full-width text-center q-py-xs">
                {{ props.value > 0 ? "+" : "" }}{{ props.value }}%
              </q-badge>
            </q-td>
          </template>

          <template v-slot:body-cell-quantidade_ajuste="props">
            <q-td :props="props" class="text-center">
              <div class="row items-center justify-center no-wrap">
                <q-icon
                  :name="props.value > 0 ? 'arrow_upward' : 'arrow_downward'"
                  :color="props.value > 0 ? 'positive' : 'negative'"
                  size="xs"
                  class="q-mr-xs"
                />
                <span :class="props.value > 0 ? 'text-positive' : 'text-negative'">{{ props.value > 0 ? "+" : "" }}{{ props.value }}</span>
              </div>
            </q-td>
          </template>

          <template v-slot:body-cell-preco_custo="props">
            <q-td :props="props" class="text-right">
              {{ formatCurrency(props.value) }}
            </q-td>
          </template>

          <template v-slot:body-cell-impacto_valor="props">
            <q-td :props="props" class="text-right">
              <span :class="props.value >= 0 ? 'text-positive' : 'text-negative'">
                {{ formatCurrency(props.value) }}
              </span>
            </q-td>
          </template>

          <template v-slot:body-cell-acoes="props">
            <q-td :props="props" class="text-center">
              <q-btn round flat size="sm" icon="visibility" color="secondary" @click="verDetalhes(props.row)">
                <q-tooltip>Ver detalhes</q-tooltip>
              </q-btn>
              <q-btn round flat size="sm" icon="print" color="secondary" @click="gerarRelatorio(props.row)">
                <q-tooltip>Imprimir ajuste</q-tooltip>
              </q-btn>
            </q-td>
          </template>

          <template v-slot:no-data>
            <div class="full-width row flex-center q-pa-lg">
              <q-icon name="build" size="2em" color="grey-6" class="q-mr-sm" />
              <span class="text-grey-6">Nenhum ajuste de inventário registrado</span>
            </div>
          </template>
        </q-table>
      </q-card-section>
    </q-card>

    <!-- Dialog de detalhes -->
    <q-dialog v-model="showDetalhes">
      <q-card style="min-width: 350px">
        <q-card-section class="row items-center">
          <div class="text-h6">Detalhes do Ajuste</div>
          <q-space />
          <q-btn icon="close" flat round dense v-close-popup />
        </q-card-section>

        <q-card-section v-if="ajusteSelecionado">
          <div class="q-mb-md">
            <div class="text-subtitle2">{{ ajusteSelecionado.produto_descricao }}</div>
            <q-chip dense color="secondary" text-color="white">
              {{ ajusteSelecionado.produto_codigo }}
            </q-chip>
          </div>

          <q-list bordered separator>
            <q-item>
              <q-item-section>
                <q-item-label caption>Categoria</q-item-label>
                <q-item-label>{{ ajusteSelecionado.categoria_nome }}</q-item-label>
              </q-item-section>
            </q-item>

            <q-item>
              <q-item-section>
                <q-item-label caption>Quantidade no Sistema</q-item-label>
                <q-item-label>{{ ajusteSelecionado.quantidade_esperada }}</q-item-label>
              </q-item-section>
            </q-item>

            <q-item>
              <q-item-section>
                <q-item-label caption>Quantidade Contada</q-item-label>
                <q-item-label>{{ ajusteSelecionado.qtde_contada }}</q-item-label>
              </q-item-section>
            </q-item>

            <q-item>
              <q-item-section>
                <q-item-label caption>Ajuste Aplicado</q-item-label>
                <q-item-label :class="ajusteSelecionado.quantidade_ajuste > 0 ? 'text-positive' : 'text-negative'">
                  {{ ajusteSelecionado.quantidade_ajuste > 0 ? "+" : "" }}{{ ajusteSelecionado.quantidade_ajuste }}
                </q-item-label>
              </q-item-section>
            </q-item>

            <q-item>
              <q-item-section>
                <q-item-label caption>Divergência</q-item-label>
                <q-item-label>
                  <q-badge :color="getDivergenciaColor(ajusteSelecionado.divergencia_percentual)">
                    {{ ajusteSelecionado.divergencia_percentual }}%
                  </q-badge>
                </q-item-label>
              </q-item-section>
            </q-item>

            <q-item>
              <q-item-section>
                <q-item-label caption>Preço de Custo</q-item-label>
                <q-item-label>{{ formatCurrency(ajusteSelecionado.preco_custo) }}</q-item-label>
              </q-item-section>
            </q-item>

            <q-item>
              <q-item-section>
                <q-item-label caption>Impacto Financeiro</q-item-label>
                <q-item-label :class="ajusteSelecionado.impacto_valor >= 0 ? 'text-positive' : 'text-negative'">
                  {{ formatCurrency(ajusteSelecionado.impacto_valor) }}
                </q-item-label>
              </q-item-section>
            </q-item>

            <q-item>
              <q-item-section>
                <q-item-label caption>Data do Ajuste</q-item-label>
                <q-item-label>
                  {{ ajusteSelecionado.data_ajuste_formatada || "28/04/2025 09:49" }}
                </q-item-label>
              </q-item-section>
            </q-item>
          </q-list>
        </q-card-section>

        <q-card-actions align="right">
          <q-btn flat color="secondary" label="Fechar" v-close-popup />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from "vue";

// Props
const props = defineProps({
  ajustes: {
    type: Array,
    default: () => [],
  },
});

// Estado
const showDetalhes = ref(false);
const ajusteSelecionado = ref(null);

// Colunas da tabela
const colunas = [
  {
    name: "produto_codigo",
    label: "Código",
    field: "produto_codigo",
    align: "left",
    sortable: true,
  },
  {
    name: "produto",
    label: "Produto",
    field: "produto_descricao",
    align: "left",
    sortable: true,
  },
  {
    name: "quantidade_esperada",
    label: "Qtde Sistema",
    field: "quantidade_esperada",
    align: "center",
    sortable: true,
  },
  {
    name: "qtde_contada",
    label: "Qtde Contada",
    field: "qtde_contada",
    align: "center",
    sortable: true,
  },
  {
    name: "divergencia_percentual",
    label: "Divergência",
    field: "divergencia_percentual",
    align: "center",
    sortable: true,
  },
  {
    name: "quantidade_ajuste",
    label: "Ajuste",
    field: "quantidade_ajuste",
    align: "center",
    sortable: true,
  },
  {
    name: "preco_custo",
    label: "Preço Custo",
    field: "preco_custo",
    align: "right",
    sortable: true,
  },
  {
    name: "impacto_valor",
    label: "Impacto (R$)",
    field: "impacto_valor",
    align: "right",
    sortable: true,
  },
  {
    name: "acoes",
    label: "Ações",
    field: "id",
    align: "center",
    sortable: false,
  },
];

// Computados
const totalAjustes = computed(() => {
  return props.ajustes.reduce((total, ajuste) => total + Math.abs(ajuste.quantidade_ajuste), 0);
});

const impactoTotal = computed(() => {
  return props.ajustes.reduce((total, ajuste) => total + ajuste.impacto_valor, 0);
});

// Métodos
const formatCurrency = (value) => {
  const numberValue = Number(value) || 0;
  return (
    "R$ " +
    numberValue
      .toFixed(2)
      .replace(".", ",")
      .replace(/(\d)(?=(\d{3})+(?!\d))/g, "$1.")
  );
};

const getDivergenciaColor = (value) => {
  if (value === null || value === undefined) return "grey";
  if (value === 0) return "positive";
  if (value < 0) return value <= -10 ? "negative" : "warning";
  return value >= 10 ? "negative" : "warning";
};

const verDetalhes = (ajuste) => {
  ajusteSelecionado.value = ajuste;
  showDetalhes.value = true;
};

const gerarRelatorio = (ajuste) => {
  console.log("Gerando relatório para o ajuste:", ajuste.id);
  // Implementar lógica de geração de relatório
};
</script>

<style scoped>
.q-table__top,
.q-table__bottom,
thead tr:first-child th {
  background-color: #f5f1ed;
}
</style>
