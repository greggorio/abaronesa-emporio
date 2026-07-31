<template>
  <div class="dashboard-card-container" style="height: 310px">
    <q-card flat class="full-width dashboard-card">
      <q-card-section class="header-section">
        <div class="header-content">
          <div class="title-wrapper">
            <q-icon name="warning_amber" color="negative" size="sm" />
            <h5 class="panel-title q-my-none q-ml-sm">Pendências</h5>
            <q-badge v-if="total > 0" color="negative" class="q-ml-sm" :label="total" />
            <q-badge v-else color="positive" class="q-ml-sm" label="OK" />
          </div>
          <div>
            <q-btn dense flat icon="visibility" label="Ver detalhes" @click="dialogVisivel = true" />
          </div>
        </div>
      </q-card-section>

      <div v-if="isLoading" class="flex justify-center items-center" style="height: 200px">
        <q-spinner color="primary" size="3em" />
        <span class="q-ml-md text-grey">Carregando pendências...</span>
      </div>

      <q-card-section v-else class="metrics-section">
        <div class="row q-col-gutter-md">
          <div class="col-6">
            <div class="metric-card critico">
              <div class="metric-icon critico-bg">
                <q-icon name="price_change" size="xs" />
              </div>
              <div class="metric-content">
                <div class="metric-label">Produtos sem preço</div>
                <div class="metric-value text-negative">{{ produtosSemPreco }}</div>
              </div>
            </div>
          </div>
          <div class="col-6">
            <div class="metric-card medio">
              <div class="metric-icon medio-bg">
                <q-icon name="inventory_2" size="xs" />
              </div>
              <div class="metric-content">
                <div class="metric-label">Sem estoque</div>
                <div class="metric-value text-warning">{{ produtosSemEstoque }}</div>
              </div>
            </div>
          </div>
        </div>
      </q-card-section>

      <q-card-section class="footer-section">
        <div class="text-caption text-grey-7">Atualizado em: {{ formatarData(geradoEm) }}</div>
      </q-card-section>
    </q-card>

    <q-dialog v-model="dialogVisivel">
      <q-card style="min-width: 380px">
        <q-card-section class="row items-center q-pb-none">
          <div class="text-h6">Pendências</div>
          <q-space />
          <q-btn icon="close" flat round dense v-close-popup />
        </q-card-section>
        <q-card-section>
          <q-tabs v-model="abaAtiva" dense align="justify" active-color="primary" indicator-color="primary">
            <q-tab name="sem-preco" icon="price_change" :label="`Sem preço (${produtosSemPreco})`" />
            <q-tab name="sem-estoque" icon="inventory_2" :label="`Sem estoque (${produtosSemEstoque})`" />
          </q-tabs>
          <div class="row q-col-gutter-sm items-center q-mt-sm">
            <div class="col-12 col-md-6">
              <q-select
                v-model="ordenacao"
                :options="opcoesOrdenacao"
                label="Ordenar por"
                dense
                outlined
                emit-value
                map-options
              />
            </div>
            <div class="col-12 col-md-6">
              <q-select
                v-model="direcao"
                :options="opcoesDirecao"
                label="Direção"
                dense
                outlined
                emit-value
                map-options
              />
            </div>
          </div>
          <q-separator />
          <q-tab-panels v-model="abaAtiva" animated>
            <q-tab-panel name="sem-preco" class="q-pa-sm">
              <div class="text-subtitle2 q-mb-sm">Itens sem preço</div>
              <div v-if="carregandoSemPreco" class="q-pa-md flex items-center">
                <q-spinner color="primary" size="2em" />
                <span class="q-ml-sm text-grey">Buscando itens...</span>
              </div>
              <div v-else-if="erroSemPreco" class="q-pa-sm text-negative">
                {{ erroSemPreco }}
              </div>
              <div v-else-if="!itensSemPreco.length" class="q-pa-sm text-grey">Nenhum item encontrado.</div>
              <div v-else class="q-gutter-y-sm">
                <q-card v-for="item in itensSemPreco" :key="item.id" flat bordered>
                  <q-card-section class="row no-wrap items-start q-gutter-sm">
                    <div class="col">
                      <div class="text-body1 text-weight-medium">{{ item.nome }}</div>
                      <div class="text-caption text-grey-7">SKU: {{ item.sku }}</div>
                      <div class="text-caption text-grey-7">Categoria: {{ item.categoria }}</div>
                    </div>
                    <div class="col-auto text-right">
                      <div class="text-caption text-grey-7">Custo</div>
                      <div class="text-weight-bold">{{ item.custo ?? '-' }}</div>
                      <div class="text-caption text-grey-7">Atualizado: {{ formatarData(item.atualizadoEm) }}</div>
                    </div>
                  </q-card-section>
                </q-card>
              </div>
              <div v-if="totalPaginasSemPreco > 1" class="q-pt-sm flex justify-end">
                <q-pagination
                  v-model="paginaSemPreco"
                  :max="totalPaginasSemPreco"
                  max-pages="5"
                  direction-links
                  boundary-links
                  size="sm"
                />
              </div>
            </q-tab-panel>
            <q-tab-panel name="sem-estoque" class="q-pa-sm">
              <div class="text-subtitle2 q-mb-sm">Itens sem estoque</div>
              <div v-if="carregandoSemEstoque" class="q-pa-md flex items-center">
                <q-spinner color="primary" size="2em" />
                <span class="q-ml-sm text-grey">Buscando itens...</span>
              </div>
              <div v-else-if="erroSemEstoque" class="q-pa-sm text-negative">
                {{ erroSemEstoque }}
              </div>
              <div v-else-if="!itensSemEstoque.length" class="q-pa-sm text-grey">Nenhum item encontrado.</div>
              <div v-else class="q-gutter-y-sm">
                <q-card v-for="item in itensSemEstoque" :key="item.id" flat bordered>
                  <q-card-section class="row no-wrap items-start q-gutter-sm">
                    <div class="col">
                      <div class="text-body1 text-weight-medium">{{ item.nome }}</div>
                      <div class="text-caption text-grey-7">SKU: {{ item.sku }}</div>
                      <div class="text-caption text-grey-7">Categoria: {{ item.categoria }}</div>
                    </div>
                    <div class="col-auto text-right">
                      <div class="text-caption text-grey-7">Última atualização</div>
                      <div class="text-weight-bold">{{ formatarData(item.ultimaVenda || item.atualizadoEm) }}</div>
                    </div>
                  </q-card-section>
                </q-card>
              </div>
              <div v-if="totalPaginasSemEstoque > 1" class="q-pt-sm flex justify-end">
                <q-pagination
                  v-model="paginaSemEstoque"
                  :max="totalPaginasSemEstoque"
                  max-pages="5"
                  direction-links
                  boundary-links
                  size="sm"
                />
              </div>
            </q-tab-panel>
          </q-tab-panels>
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="Fechar" v-close-popup />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { useApiRequest } from '@/composables/useApiRequest';

const PAGE_SIZE = 5;

const props = defineProps({
  pendencias: {
    type: Object,
    default: () => null
  },
  isLoading: {
    type: Boolean,
    default: true
  }
});

const dialogVisivel = ref(false);
const abaAtiva = ref('sem-preco');
const { apiRequest } = useApiRequest();

const total = computed(() => Number(props.pendencias?.total || 0));
const produtosSemPreco = computed(() => Number(props.pendencias?.produtosSemPreco || 0));
const produtosSemEstoque = computed(() => Number(props.pendencias?.produtosSemEstoque || 0));
const geradoEm = computed(() => props.pendencias?.geradoEm || null);

const itensSemPreco = ref([]);
const itensSemEstoque = ref([]);
const carregandoSemPreco = ref(false);
const carregandoSemEstoque = ref(false);
const erroSemPreco = ref(null);
const erroSemEstoque = ref(null);
const jaCarregouSemPreco = ref(false);
const jaCarregouSemEstoque = ref(false);
const paginaSemPreco = ref(1);
const paginaSemEstoque = ref(1);
const totalPaginasSemPreco = ref(0);
const totalPaginasSemEstoque = ref(0);
const ordenacao = ref('dataAtualizacao');
const direcao = ref('desc');

const opcoesOrdenacao = [
  { label: 'Última atualização', value: 'dataAtualizacao' },
  { label: 'Nome', value: 'nome' },
];

const opcoesDirecao = [
  { label: 'Desc', value: 'desc' },
  { label: 'Asc', value: 'asc' },
];

const fetchPendencias = async (tipo) => {
  const isPreco = tipo === 'sem-preco';
  const loadingRef = isPreco ? carregandoSemPreco : carregandoSemEstoque;
  const erroRef = isPreco ? erroSemPreco : erroSemEstoque;
  const cacheFlag = isPreco ? jaCarregouSemPreco : jaCarregouSemEstoque;
  const pagina = isPreco ? paginaSemPreco.value : paginaSemEstoque.value;
  const totalPaginasRef = isPreco ? totalPaginasSemPreco : totalPaginasSemEstoque;

  if (cacheFlag.value || loadingRef.value) return;

  loadingRef.value = true;
  erroRef.value = null;
  try {
    const response = await apiRequest(
      `/api/produtos/pendencias?tipo=${tipo}&pagina=${pagina - 1}&tamanho=${PAGE_SIZE}&apenasAtivos=true&ordenacao=${ordenacao.value}&direcao=${direcao.value}`
    );
    const lista = Array.isArray(response?.items) ? response.items : [];
    if (isPreco) {
      itensSemPreco.value = lista.map(normalizarItemPreco);
    } else {
      itensSemEstoque.value = lista.map(normalizarItemEstoque);
    }
    const totalPaginas = Number(response?.totalPaginas || 0);
    totalPaginasRef.value = totalPaginas;
    cacheFlag.value = true;
  } catch (e) {
    erroRef.value = 'Não foi possível carregar os itens.';
    console.error('Erro ao carregar pendências', tipo, e);
  } finally {
    loadingRef.value = false;
  }
};

const normalizarItemPreco = (item) => ({
  id: item.id,
  nome: item.nome || '-',
  sku: item.sku || item.codigoInterno || '-',
  categoria: item.categoriaNome || item.subcategoriaNome || '-',
  custo: item.custo,
  atualizadoEm: item.atualizadoEm || item.dataAtualizacao,
});

const normalizarItemEstoque = (item) => ({
  id: item.id,
  nome: item.nome || '-',
  sku: item.sku || item.codigoInterno || '-',
  categoria: item.categoriaNome || item.subcategoriaNome || '-',
  diasSemEstoque: item.diasSemEstoque || null,
  ultimaVenda: item.ultimaVenda || item.atualizadoEm || item.dataAtualizacao,
});

watch(dialogVisivel, (visivel) => {
  if (visivel) {
    fetchPendencias(abaAtiva.value);
  } else {
    // Recarregar dados em uma próxima abertura
    jaCarregouSemPreco.value = false;
    jaCarregouSemEstoque.value = false;
  }
});

watch(abaAtiva, (nova) => {
  if (dialogVisivel.value) {
    // reset cache para forçar nova carga ao mudar de aba
    if (nova === 'sem-preco') {
      jaCarregouSemPreco.value = false;
      paginaSemPreco.value = 1;
    } else {
      jaCarregouSemEstoque.value = false;
      paginaSemEstoque.value = 1;
    }
    fetchPendencias(nova);
  }
});

watch(paginaSemPreco, () => {
  if (dialogVisivel.value) {
    jaCarregouSemPreco.value = false;
    fetchPendencias('sem-preco');
  }
});

watch(paginaSemEstoque, () => {
  if (dialogVisivel.value) {
    jaCarregouSemEstoque.value = false;
    fetchPendencias('sem-estoque');
  }
});

watch([ordenacao, direcao], () => {
  if (dialogVisivel.value) {
    jaCarregouSemPreco.value = false;
    jaCarregouSemEstoque.value = false;
    paginaSemPreco.value = 1;
    paginaSemEstoque.value = 1;
    fetchPendencias(abaAtiva.value);
  }
});

function formatarData(value) {
  if (!value) return '-';
  try {
    // Se for array [ano, mes, dia, hora, minuto, segundo, milissegundo]
    if (Array.isArray(value)) {
      const [year, month, day, hour = 0, minute = 0] = value;
      const dd = String(day).padStart(2, '0');
      const mm = String(month).padStart(2, '0');
      const hh = String(hour).padStart(2, '0');
      const mi = String(minute).padStart(2, '0');
      return `${dd}/${mm}/${year} ${hh}:${mi}`;
    }
    // Se for string, parsear normalmente
    const d = new Date(value);
    if (isNaN(d.getTime())) return '-';
    const dd = String(d.getDate()).padStart(2, '0');
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const yyyy = d.getFullYear();
    const hh = String(d.getHours()).padStart(2, '0');
    const mi = String(d.getMinutes()).padStart(2, '0');
    return `${dd}/${mm}/${yyyy} ${hh}:${mi}`;
  } catch (e) {
    return '-';
  }
}
</script>

<style scoped>
.dashboard-card {
  border-radius: 16px;
  box-shadow: 0 6px 25px rgba(0, 0, 0, 0.07);
  transition: all 0.3s ease;
  height: 100%;
  overflow: hidden;
  background-color: #fbf6f2;
}

.header-section {
  padding: 12px 16px 0;
  background: linear-gradient(to right, #fbf6f2, #ffffff);
  border-bottom: 1px solid #d7b899;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title-wrapper {
  display: flex;
  align-items: center;
}

.panel-title {
  color: #6b3e26;
}

.metrics-section {
  padding: 8px 12px;
}

.metric-card {
  padding: 10px 12px;
  border-radius: 10px;
  height: 100%;
  display: flex;
  align-items: center;
  background-color: #fff9f4;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
  transition: all 0.2s ease;
  gap: 8px;
  border-bottom: 3px solid transparent;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}

.metric-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  flex-shrink: 0;
}

.critico { border-bottom-color: #e74c3c; }
.critico-bg { background-color: #fee2e2; color: #e74c3c; }
.medio { border-bottom-color: #f39c12; }
.medio-bg { background-color: #fff3cd; color: #f39c12; }

.metric-content { flex: 1; }
.metric-label { font-size: 0.7rem; font-weight: 500; color: #7f8c8d; margin-bottom: 4px; text-transform: uppercase; letter-spacing: 0.3px; }
.metric-value { font-size: 0.95rem; font-weight: 600; }

.footer-section { padding: 0 16px 12px; }
</style>
