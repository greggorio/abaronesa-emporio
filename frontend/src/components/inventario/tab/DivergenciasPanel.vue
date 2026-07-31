<template>
  <div class="row q-col-gutter-md">
    <!-- COLUNA DE PRODUTOS COM DIVERGÊNCIA -->
    <div class="col-12 col-md-7">
      <q-card flat class="rounded-card q-mb-md">
        <q-card-section :style="{ backgroundColor: '#f5f1ed' }" class="text-secondary q-pb-xs">
          <div class="row items-center">
            <q-icon name="warning" size="28px" class="q-mr-md" />
            <div>
              <div class="text-h6 q-mb-none">Produtos com Maior Divergência</div>
              <div class="text-caption">Mostrando os {{ produtosDivergentes.length }} itens com maior variação</div>
            </div>
          </div>
        </q-card-section>

        <div class="divergencia-list">
          <q-list padding>
            <q-item v-for="(produto, i) in produtosDivergentes" :key="i" class="produto-item q-py-md">
              <q-item-section side>
                <div class="divergencia-indicator" :class="getDivergenciaClass(produto.divergencia)">
                  {{ produto.divergencia < 0 ? "" : "+" }}{{ produto.divergencia }}%
                </div>
              </q-item-section>

              <q-item-section>
                <div class="text-weight-bold">{{ produto.descricao_produto }}</div>
                <div class="row items-center text-caption">
                  <q-chip size="sm" outline color="primary" class="q-py-xs">
                    {{ produto.codigo_produto }}
                  </q-chip>
                  <q-separator vertical inset color="grey-5" class="q-mx-sm" />
                  {{ produto.categoria_nome || "Sem categoria" }}
                </div>
              </q-item-section>

              <q-item-section side>
                <div class="row column items-end">
                  <div class="inventory-stat">
                    <span class="text-weight-bold">{{ produto.quantidade_esperada }}</span>
                    <span class="text-grey-7 q-ml-xs">Sistema</span>
                  </div>
                  <div class="inventory-stat" :class="getUltimaContagem(produto) < produto.quantidade_contada ? 'text-negative' : 'text-positive'">
                    <span class="text-weight-bold">{{ getUltimaContagem(produto) }}</span>
                    <span class="text-grey-7 q-ml-xs">Contado</span>
                  </div>
                </div>
              </q-item-section>
            </q-item>

            <q-separator v-if="produtosDivergentes.length === 0" />
            <q-item v-if="produtosDivergentes.length === 0" class="text-center">
              <q-item-section>
                <q-icon name="thumb_up" color="positive" size="48px" />
                <div class="text-subtitle1 q-mt-sm">Nenhuma divergência encontrada!</div>
                <div class="text-caption text-grey">O inventário está em conformidade</div>
              </q-item-section>
            </q-item>
          </q-list>
        </div>
      </q-card>
    </div>

    <!-- COLUNA DE ANÁLISE DE DIVERGÊNCIAS -->
    <div class="col-12 col-md-5">
      <q-card flat class="rounded-card">
        <q-card-section :style="{ backgroundColor: '#f5f1ed' }" class="text-secondary q-pb-xs">
          <div class="row items-center">
            <q-icon name="insights" size="28px" class="q-mr-md" />
            <div>
              <div class="text-h6 q-mb-none">Análise de Divergências</div>
              <div class="text-caption">Resumo do impacto no inventário</div>
            </div>
          </div>
        </q-card-section>

        <q-card-section class="q-pa-md">
          <div class="row q-col-gutter-md">
            <!-- Card de taxa de precisão -->
            <div class="col-12">
              <q-card flat bordered class="precision-card">
                <q-card-section class="q-pa-md">
                  <div class="row items-center">
                    <div class="col-auto">
                      <q-circular-progress
                        :value="taxaPrecisao"
                        size="80px"
                        :thickness="0.2"
                        :color="taxaPrecisao > 90 ? 'positive' : taxaPrecisao > 70 ? 'warning' : 'negative'"
                        track-color="grey-3"
                        show-value
                        font-size="16px"
                        class="q-mr-md"
                      >
                        {{ Math.round(taxaPrecisao) }}%
                      </q-circular-progress>
                    </div>
                    <div class="col">
                      <div class="text-h6">Precisão do Inventário</div>
                      <div class="text-caption">
                        <span v-if="taxaPrecisao > 90" class="text-positive">Excelente</span>
                        <span v-else-if="taxaPrecisao > 70" class="text-warning">Média</span>
                        <span v-else class="text-negative">Necessita atenção</span>
                      </div>
                      <div class="text-caption q-mt-xs">
                        Divergência média:
                        <span :class="divergenciaMedia < 0 ? 'text-negative' : 'text-positive'">
                          {{ divergenciaMedia > 0 ? "+" : "" }}{{ divergenciaMedia }}%
                        </span>
                      </div>
                    </div>
                  </div>
                </q-card-section>
              </q-card>
            </div>

            <!-- Cards de impacto -->
            <div class="col-6">
              <q-card flat class="impact-card negative-impact">
                <q-card-section class="text-center q-pa-sm">
                  <q-icon name="arrow_downward" color="white" size="24px" />
                  <div class="text-h6 text-white">{{ itensFaltantes.length }}</div>
                  <div class="text-subtitle2 text-white">Itens Faltantes</div>
                  <q-separator dark class="q-my-sm" />
                  <div class="text-h5 text-white">{{ formatCurrency(impactoFinanceiroFaltantes) }}</div>
                  <div class="text-caption text-white-8">Impacto financeiro</div>
                </q-card-section>
              </q-card>
            </div>

            <div class="col-6">
              <q-card flat class="impact-card positive-impact">
                <q-card-section class="text-center q-pa-sm">
                  <q-icon name="arrow_upward" color="white" size="24px" />
                  <div class="text-h6 text-white">{{ itensExcedentes.length }}</div>
                  <div class="text-subtitle2 text-white">Itens Excedentes</div>
                  <q-separator dark class="q-my-sm" />
                  <div class="text-h5 text-white">{{ formatCurrency(impactoFinanceiroExcedentes) }}</div>
                  <div class="text-caption text-white-8">Impacto financeiro</div>
                </q-card-section>
              </q-card>
            </div>
          </div>

          <!-- Gráfico de divergência por categoria -->
          <div class="q-mt-md">
            <div class="text-subtitle1 text-weight-medium q-mb-sm">Divergências por Categoria</div>

            <div class="categoria-chart q-pa-sm">
              <div v-for="(categoria, i) in categoriasDivergencia" :key="i" class="categoria-row q-mb-sm">
                <div class="categoria-label">{{ categoria.nome }}</div>

                <div class="categoria-bars">
                  <!-- Barra negativa -->
                  <div class="bar-container negative">
                    <div
                      v-if="categoria.faltante > 0"
                      class="bar negative-bar"
                      :style="{
                        width: `${Math.min(100, (categoria.faltante / maxDivergenciaCategoria) * 100)}%`,
                      }"
                    >
                      <span v-if="categoria.faltante >= 3" class="bar-value">{{ categoria.faltante }}</span>
                    </div>
                    <span v-if="categoria.faltante > 0 && categoria.faltante < 3" class="outside-value text-negative">
                      {{ categoria.faltante }}
                    </span>
                  </div>

                  <!-- Divisor central -->
                  <div class="bar-divider"></div>

                  <!-- Barra positiva -->
                  <div class="bar-container positive">
                    <div
                      v-if="categoria.excedente > 0"
                      class="bar positive-bar"
                      :style="{
                        width: `${Math.min(100, (categoria.excedente / maxDivergenciaCategoria) * 100)}%`,
                      }"
                    >
                      <span v-if="categoria.excedente >= 3" class="bar-value">{{ categoria.excedente }}</span>
                    </div>
                    <span v-if="categoria.excedente > 0 && categoria.excedente < 3" class="outside-value text-positive">
                      {{ categoria.excedente }}
                    </span>
                  </div>
                </div>
              </div>

              <!-- Legenda -->
              <div class="chart-legend q-mt-sm">
                <div class="legend-item">
                  <div class="legend-color negative-legend"></div>
                  <span class="text-caption">Faltantes</span>
                </div>
                <div class="legend-item">
                  <div class="legend-color positive-legend"></div>
                  <span class="text-caption">Excedentes</span>
                </div>
              </div>
            </div>
          </div>
        </q-card-section>
      </q-card>
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";

// Declara que o componente espera uma prop chamada "produtos"
const props = defineProps({
  produtos: {
    type: Array,
    required: true,
    default: () => [],
  },
});

// Funções para contagens
const getContagens = (row) => {
  if (row.contagens && Array.isArray(row.contagens) && row.contagens.length > 0) {
    const total = row.contagens.length;
    return row.contagens.map((contagem, index) => {
      return {
        number: total - index,
        value: contagem.quantidade_contada || 0,
      };
    });
  }

  // Se não tiver contagens reais, retorna um array vazio
  return [];
};

const getUltimaContagem = (row) => {
  const contagens = getContagens(row);
  return contagens.length > 0 ? contagens[0].value : "-";
};

// Funções de formatação e estilo
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

// Computed properties para análise de divergência
const produtosDivergentes = computed(() => {
  if (!Array.isArray(props.produtos)) return [];

  return [...props.produtos]
    .filter((produto) => produto.divergencia !== 0 && produto.divergencia !== null)
    .sort((a, b) => Math.abs(b.divergencia) - Math.abs(a.divergencia))
    .slice(0, 20);
});

// Itens com divergência negativa e positiva
const itensFaltantes = computed(() => {
  return props.produtos.filter((p) => p.divergencia < 0);
});

const itensExcedentes = computed(() => {
  return props.produtos.filter((p) => p.divergencia > 0);
});

// Impacto financeiro
const impactoFinanceiroFaltantes = computed(() => {
  return Math.abs(itensFaltantes.value.reduce((sum, p) => sum + (p.custo_divergencia || 0), 0));
});

const impactoFinanceiroExcedentes = computed(() => {
  return itensExcedentes.value.reduce((sum, p) => sum + (p.custo_divergencia || 0), 0);
});

// Taxa de precisão do inventário
const taxaPrecisao = computed(() => {
  const totalProdutos = props.produtos.length;
  const produtosCorretos = props.produtos.filter((p) => p.divergencia === 0).length;
  return totalProdutos > 0 ? (produtosCorretos / totalProdutos) * 100 : 0;
});

// Divergência média
const divergenciaMedia = computed(() => {
  const produtosComDivergencia = props.produtos.filter((p) => p.divergencia !== null);
  const total = produtosComDivergencia.length;
  if (total === 0) return 0;

  const soma = produtosComDivergencia.reduce((sum, p) => sum + p.divergencia, 0);
  return Math.round((soma / total) * 10) / 10; // Arredondar para 1 casa decimal
});

// Análise por categoria
const categoriasDivergencia = computed(() => {
  const categorias = {};

  props.produtos.forEach((produto) => {
    const categoriaNome = produto.categoria_nome || "Sem categoria";

    if (!categorias[categoriaNome]) {
      categorias[categoriaNome] = {
        nome: categoriaNome,
        faltante: 0,
        excedente: 0,
        qtdProdutos: 0,
        valorTotal: 0,
      };
    }

    categorias[categoriaNome].qtdProdutos++;
    categorias[categoriaNome].valorTotal += produto.valor_estoque || 0;

    if (produto.divergencia < 0) {
      categorias[categoriaNome].faltante++;
    } else if (produto.divergencia > 0) {
      categorias[categoriaNome].excedente++;
    }
  });

  return Object.values(categorias)
    .filter((c) => c.faltante > 0 || c.excedente > 0)
    .sort((a, b) => b.faltante + b.excedente - (a.faltante + a.excedente));
});

// Valor máximo para escalar os gráficos
const maxDivergenciaCategoria = computed(() => {
  if (categoriasDivergencia.value.length === 0) return 1;

  return Math.max(...categoriasDivergencia.value.map((c) => Math.max(c.faltante, c.excedente)));
});

// Função para classes de divergência
const getDivergenciaClass = (divergencia) => {
  if (divergencia === null || divergencia === 0) return "divergencia-neutro";
  if (divergencia < 0) return divergencia <= -20 ? "divergencia-alto-neg" : "divergencia-medio-neg";
  return divergencia >= 20 ? "divergencia-alto-pos" : "divergencia-medio-pos";
};
</script>

<style scoped>
.rounded-card {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.divergencia-list {
  max-height: 530px;
  overflow-y: auto;
  scrollbar-width: thin;
}

.produto-item {
  transition: all 0.2s ease;
  border-bottom: 1px solid #eee;
}

.produto-item:hover {
  background-color: #f5f8ff;
}

.divergencia-indicator {
  width: 70px;
  height: 70px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 16px;
  color: white;
}

.divergencia-alto-neg {
  background-color: #ef5350;
}

.divergencia-medio-neg {
  background-color: #ff9800;
}

.divergencia-neutro {
  background-color: #4caf50;
}

.divergencia-medio-pos {
  background-color: #ff9800;
}

.divergencia-alto-pos {
  background-color: #ef5350;
}

.inventory-stat {
  margin-top: 5px;
  text-align: right;
}

.precision-card {
  border-left: 4px solid #1976d2;
  background-color: #f5f8ff;
}

.impact-card {
  border-radius: 8px;
  color: white;
  height: 100%;
}

.negative-impact {
  background: linear-gradient(135deg, #f44336, #e57373);
}

.positive-impact {
  background: linear-gradient(135deg, #4caf50, #81c784);
}

.categoria-chart {
  background-color: #f5f8ff;
  border-radius: 8px;
}

.categoria-row {
  display: flex;
  align-items: center;
}

.categoria-label {
  width: 100px;
  font-size: 13px;
  font-weight: 500;
  text-align: right;
  padding-right: 10px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.categoria-bars {
  flex: 1;
  display: flex;
  align-items: center;
  height: 36px;
}

.bar-container {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
}

.bar-container.negative {
  width: 45%;
  justify-content: flex-end;
}

.bar-container.positive {
  width: 45%;
  justify-content: flex-start;
}

.bar-divider {
  width: 10%;
  height: 60%;
  border-left: 1px dashed #ccc;
  border-right: 1px dashed #ccc;
}

.bar {
  height: 22px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  transition: width 0.5s ease;
  min-width: 20px;
}

.negative-bar {
  background-color: #f44336;
  justify-content: flex-end;
}

.positive-bar {
  background-color: #4caf50;
  justify-content: flex-start;
}

.bar-value {
  color: white;
  font-size: 12px;
  padding: 0 8px;
  font-weight: 500;
}

.outside-value {
  font-size: 12px;
  font-weight: 500;
  margin: 0 4px;
}

.chart-legend {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 10px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 5px;
}

.legend-color {
  width: 12px;
  height: 12px;
  border-radius: 3px;
}

.negative-legend {
  background-color: #f44336;
}

.positive-legend {
  background-color: #4caf50;
}
</style>
