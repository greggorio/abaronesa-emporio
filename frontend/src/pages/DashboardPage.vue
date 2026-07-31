<template>
  <q-page class="q-pa-md" style="background-color: #f5f1ed; margin-top: -10px">
    <!-- Métricas rápidas -->
    <div class="row q-col-gutter-md q-mb-md">
      <div
        v-for="metric in metrics"
        :key="metric.id"
        class="col-lg-2 col-md-4 col-sm-6 col-xs-12"
      >
        <metric-card
          :label="metric.label"
          :value="metric.value"
          :subtitle="metric.subtitle"
          :icon="metric.icon"
          :color="metric.color"
        />
      </div>
    </div>

    <!-- Relatórios rápidos -->
    <div class="row q-col-gutter-md q-mb-md">
      <div class="col-12">
        <q-card flat bordered class="bg-white q-pa-sm">
          <div class="row items-center q-gutter-sm">
            <q-icon name="o_picture_as_pdf" size="20px" color="primary" />
            <span class="text-subtitle2 text-grey-8">Relatórios:</span>
            <q-btn
              unelevated
              size="sm"
              color="primary"
              icon="o_inventory_2"
              label="Vendas por Produto"
              @click="relatorioVendasProdutosDialog = true"
            />
          </div>
        </q-card>
      </div>
    </div>

    <!-- Painéis draggable -->
    <draggable
      v-model="paineis"
      item-key="id"
      class="row q-col-gutter-md"
      ghost-class="ghost"
      chosen-class="chosen"
      handle=".painel-handle"
      :animation="300"
      @start="isDragging = true"
      @end="onDragEnd"
    >
      <template #item="{ element }">
        <div :class="element.colClass">
          <div class="my-content relative-position">
            <div class="painel-handle" :class="{ 'cursor-move': !isDragging }">
              <q-icon name="drag_indicator" size="sm" class="absolute-top-right q-pa-xs" />
            </div>
            <component :is="element.component" v-bind="element.props" />
          </div>
        </div>
      </template>
    </draggable>

    <RelatorioVendasProdutosDialog v-model="relatorioVendasProdutosDialog" />
  </q-page>
</template>

<script setup>
import { ref, onMounted, computed, markRaw } from 'vue';
import { useApiRequest } from '@/composables/useApiRequest';
import { useUserStore } from '@/stores/userStore';
import draggable from 'vuedraggable';
import PainelMesaTop from '@/components/paineis/PainelMesaTop.vue';
import PainelTopMesas from '@/components/paineis/PainelTopMesas.vue';
import PainelVendasPeriodo from '@/components/paineis/PainelVendasPeriodo.vue';
import PainelMovimentoCaixa from '@/components/paineis/PainelMovimentoCaixa.vue';
import PainelProdutos from '@/components/paineis/PainelProdutos.vue';
import PainelFinanceiro from '@/components/paineis/PainelFinanceiro.vue';
import PainelPedidosLocal from '@/components/paineis/PainelPedidosLocal.vue';
import PainelPendencias from '@/components/paineis/PainelPendencias.vue';
import PainelVoucher from '@/components/paineis/PainelVoucher.vue';
import PainelProdutosPromocao from '@/components/paineis/PainelProdutosPromocao.vue';
import PainelEventos from '@/components/paineis/PainelEventos.vue';
import PainelVendasHistorico from '@/components/paineis/PainelVendasHistorico.vue';
import PainelValidade from '@/components/paineis/PainelValidade.vue';
import MetricCard from '@/components/MetricCard.vue';
import RelatorioVendasProdutosDialog from '@/components/dialogs/RelatorioVendasProdutosDialog.vue';
import eventBus from '@/eventBus';

const { apiRequest } = useApiRequest();
const userStore = useUserStore();
const isDragging = ref(false);

// Estados de carregamento para cada seção
const isTopMesaLoading = ref(true);
const isTopListLoading = ref(true);
const isVendasPeriodoLoading = ref(false);
const isMovimentoCaixaLoading = ref(false);
const isProdutosLoading = ref(false);
const isFinanceiroLoading = ref(false);
const isPedidosLocalLoading = ref(false);
const isPendenciasLoading = ref(true);
const isVoucherLoading = ref(false);
const isPromocaoLoading = ref(false);
const isEventosLoading = ref(false);
const isVendasHistoricoLoading = ref(false);

// Dados dos painéis
const topMesa = ref(null);
const topList = ref([]);
const ordenacaoProdutos = ref('valor');
const periodoProdutos = ref('7d');
const periodoVendas = ref('7d');
const periodoVendasHistorico = ref('30d');
const periodoPromocao = ref('7d');
const periodoEventos = ref('30d');
const dadosPromocao = ref({
  produtosEmPromocao: 0,
  descontoMedio: 0,
  impactoVendas: 0,
  vendasPromocao: 0,
  vendasNormais: 0,
  produtosEmPromocaoLista: []
});
const dadosEventos = ref({
  totalCouverAtual: 0,
  totalFaturamento30d: 0,
  mediaFaturamento: 0,
  eventosLista: []
});
const mensagemEventosErro = ref('');

// Vendas no Período
const vendasPeriodo = ref({
  faturamento: 0,
  quantidade_vendas: 0,
  ticket_medio: 0,
  dinheiro: 0,
  pix: 0,
  cartaoCredito: 0,
  cartaoDebito: 0,
  voucher: 0,
});

// Movimento de Caixa
const movimentoCaixa = ref({
  entradas: 0,
  saidas: 0,
  saldo: 0,
  detalhesPorTipo: {},
});

// Top Produtos
const topProdutos = ref([]);

// Pedidos por local
const pedidosPorLocal = ref([]);
const diasPedidosLocal = ref(30);
const consumoVoucher = ref([]);
const vendasHistorico = ref([]);

// Dados financeiros
const dadosFinanceiros = ref({
  recebimentos: 0,
  total_recebido: 0,
  pagamentos: 0,
  total_pago: 0,
  pendencias_recebimento: 0,
  pendencias_pagamento: 0,
});
const pendencias = ref(null);
const relatorioVendasProdutosDialog = ref(false);

// Métricas rápidas (dados que serão carregados da API)
const metrics = ref([
  {
    id: 1,
    label: 'Pedidos no Bar',
    value: 0,
    subtitle: '0 aguardando preparo',
    icon: 'local_bar',
    color: 'primary',
  },
  {
    id: 2,
    label: 'Pedidos na Cozinha',
    value: 0,
    subtitle: '0 aguardando preparo',
    icon: 'restaurant',
    color: 'warning',
  },
  {
    id: 3,
    label: 'Pedidos Prontos',
    value: 0,
    subtitle: 'Aguardando entrega',
    icon: 'check_circle',
    color: 'positive',
  },
  {
    id: 4,
    label: 'Mesas Ocupadas',
    value: '0/0',
    subtitle: '0% ocupação',
    icon: 'table_restaurant',
    color: 'secondary',
  },
  {
    id: 5,
    label: 'Total de Vendas',
    value: 'R$ 0,00',
    subtitle: 'Hoje',
    icon: 'payments',
    color: 'positive',
  },
  {
    id: 6,
    label: 'Ticket Médio',
    value: 'R$ 0,00',
    subtitle: 'Sem dados',
    icon: 'trending_up',
    color: 'positive',
  },
]);

// Computed properties para dados processados
const paidCentavos = computed(() => {
  if (!topMesa.value) return 0;
  return Math.max(0, Number(topMesa.value.pagoCentavos || 0));
});

const paidPercent = computed(() => {
  if (!topMesa.value) return 0;
  const subtotal = Number(topMesa.value.devidoCentavos || 0) + Number(topMesa.value.pagoCentavos || 0);
  if (subtotal <= 0) return 0;
  return Math.min(paidCentavos.value / subtotal, 1);
});

// Definição de todos os painéis disponíveis
const paineisDefinicao = [
  {
    id: 1,
    component: markRaw(PainelVendasPeriodo),
    colClass: 'col-lg-4 col-md-6 col-xs-12',
    props: computed(() => ({
      vendas: vendasPeriodo.value,
      isLoading: isVendasPeriodoLoading.value,
      periodo: periodoVendas.value,
      'onUpdate:periodo': handlePeriodoVendasChange
    })),
  },
  {
    id: 12,
    component: markRaw(PainelVendasHistorico),
    colClass: 'col-lg-4 col-md-6 col-xs-12',
    props: computed(() => ({
      dados: vendasHistorico.value,
      isLoading: isVendasHistoricoLoading.value,
    })),
  },
  {
    id: 2,
    component: markRaw(PainelMovimentoCaixa),
    colClass: 'col-lg-4 col-md-6 col-xs-12',
    props: computed(() => ({
      movimentoCaixa: movimentoCaixa.value,
      isLoading: isMovimentoCaixaLoading.value,
    })),
  },
  {
    id: 3,
    component: markRaw(PainelFinanceiro),
    colClass: 'col-lg-4 col-md-6 col-xs-12',
    props: computed(() => ({
      financeiro: dadosFinanceiros.value,
      isLoading: isFinanceiroLoading.value,
    })),
  },
  {
    id: 4,
    component: markRaw(PainelProdutos),
    colClass: 'col-lg-4 col-md-6 col-xs-12',
    props: computed(() => ({
      produtos: topProdutos.value,
      isLoading: isProdutosLoading.value,
      modelValue: ordenacaoProdutos.value,
      periodo: periodoProdutos.value,
      'onUpdate:ordenacao': handleOrdenacaoProdutosChange,
      'onUpdate:periodo': handlePeriodoProdutosChange
    })),
  },
  {
    id: 5,
    component: markRaw(PainelMesaTop),
    colClass: 'col-lg-4 col-md-6 col-xs-12',
    props: computed(() => ({
      topMesa: topMesa.value,
      paidPercent: paidPercent.value,
      paidCentavos: paidCentavos.value,
      isLoading: isTopMesaLoading.value,
    })),
  },
  {
    id: 6,
    component: markRaw(PainelTopMesas),
    colClass: 'col-lg-4 col-md-6 col-xs-12',
    props: computed(() => ({
      topList: topList.value,
      isLoading: isTopListLoading.value,
    })),
  },
  {
    id: 7,
    component: markRaw(PainelPedidosLocal),
    colClass: 'col-lg-4 col-md-6 col-xs-12',
    props: computed(() => ({
      pedidosDiarios: pedidosPorLocal.value,
      isLoading: isPedidosLocalLoading.value,
    })),
  },
  {
    id: 8,
    component: markRaw(PainelPendencias),
    colClass: 'col-lg-4 col-md-6 col-xs-12',
    props: computed(() => ({
      pendencias: pendencias.value,
      isLoading: isPendenciasLoading.value,
    })),
  },
  {
    id: 9,
    component: markRaw(PainelVoucher),
    colClass: 'col-lg-4 col-md-6 col-xs-12',
    props: computed(() => ({
      data: consumoVoucher.value,
      isLoading: isVoucherLoading.value,
    })),
  },
  {
    id: 10,
    component: markRaw(PainelProdutosPromocao),
    colClass: 'col-lg-4 col-md-6 col-xs-12',
    props: computed(() => ({
      dadosPromocao: dadosPromocao.value,
      isLoading: isPromocaoLoading.value,
      periodo: periodoPromocao.value,
      'onUpdate:periodo': handlePeriodoPromocaoChange,
      'onVer-detalhes': handleVerDetalhesPromocao
    })),
  },
  {
    id: 11,
      component: markRaw(PainelEventos),
      colClass: 'col-lg-4 col-md-6 col-xs-12',
      props: computed(() => ({
        dadosEventos: dadosEventos.value,
        isLoading: isEventosLoading.value,
        periodo: periodoEventos.value,
        mensagemErro: mensagemEventosErro.value,
        'onUpdate:periodo': handlePeriodoEventosChange,
        'onVer-detalhes': handleVerDetalhesEventos
      })),
  },
  {
    id: 13,
    component: markRaw(PainelValidade),
    colClass: 'col-lg-4 col-md-6 col-xs-12',
    props: computed(() => ({})),
  },
];

// Painéis filtrados que estarão visíveis
const paineisVisiveis = computed(() => {
  return paineisDefinicao.filter((painel) => {
    if (painel.visivel !== undefined) {
      return painel.visivel.value;
    }
    return true;
  });
});

// Painéis ordenáveis
const paineis = ref([]);

// Chave para localStorage baseada no usuário
const getLocalStorageKey = () => {
  const userId = userStore.getUserGroup || 'default';
  return `dashboard_paineis_ordem_${userId}`;
};

// Salvar ordem no localStorage
const saveOrder = () => {
  const order = paineis.value.map(p => p.id);
  localStorage.setItem(getLocalStorageKey(), JSON.stringify(order));
};

// Carregar ordem salva do localStorage
const loadSavedOrder = () => {
  const savedOrder = localStorage.getItem(getLocalStorageKey());
  if (!savedOrder) return null;

  const orderIds = JSON.parse(savedOrder);
  const visiveis = paineisVisiveis.value;
  const ordered = [];

  // Reordena conforme IDs salvos
  orderIds.forEach(id => {
    const painel = visiveis.find(p => p.id === id);
    if (painel) ordered.push(painel);
  });

  // Adiciona painéis novos que não estavam salvos
  visiveis.forEach(painel => {
    if (!ordered.find(p => p.id === painel.id)) {
      ordered.push(painel);
    }
  });

  return ordered;
};

// Função para resetar a ordem dos painéis
const resetOrder = () => {
  const savedOrder = loadSavedOrder();
  paineis.value = savedOrder || [...paineisVisiveis.value];
};

// Handler do evento de fim de drag
const onDragEnd = () => {
  isDragging.value = false;
  saveOrder();
};

/**
 * Carrega todos os dados do dashboard
 */
const loadDashBoard = async () => {
  try {
    // Carregar métricas de pedidos em preparação
    await loadPedidosPreparacao();

    // Carregar métricas de vendas
    await loadVendas();

    // Carregar vendas do período
    await loadVendasPeriodo();
    await loadVendasHistorico();

    // Carregar top produtos
    await loadTopProdutos();

    // Carregar movimento de caixa
    await loadMovimentoCaixa();

    // Carregar dados financeiros
    await loadFinanceiro();

    // Carregar pendências de produtos
    await loadPendencias();

    // Carregar consumo de voucher (funcionários/admin)
    await loadVoucherConsumo();

    // Carregar mesa com maior consumo
    await loadTopMesa();

    // Carregar top 5 mesas
    await loadTopList();

    // Carregar pedidos por local
    await loadPedidosPorLocal();

    // Carregar dados de promoções
    await loadPromocoes();

    // Carregar dados de eventos
    await loadEventos();
  } catch (error) {
    console.error('Erro ao carregar dashboard:', error);
  }
};

/**
 * Handler para quando a ordenação de produtos mudar
 */
const handleOrdenacaoProdutosChange = async (novaOrdenacao) => {
  ordenacaoProdutos.value = novaOrdenacao;
  await loadTopProdutos();
};

/**
 * Handler para quando o período de vendas mudar
 */
const handlePeriodoVendasChange = async (novoPeriodo) => {
  periodoVendas.value = novoPeriodo;
  await loadVendasPeriodo();
};

/**
 * Handler para quando o período de produtos mudar
 */
const handlePeriodoProdutosChange = async (novoPeriodo) => {
  periodoProdutos.value = novoPeriodo;
  await loadTopProdutos();
};

/**
 * Handler para quando o período de promoções mudar
 */
const handlePeriodoPromocaoChange = async (novoPeriodo) => {
  periodoPromocao.value = novoPeriodo;
  await loadPromocoes();
};

/**
 * Handler para ver detalhes de promoções
 */
const handleVerDetalhesPromocao = () => {
  // Implementar lógica para ver detalhes
  console.log('Ver detalhes de promoções');
};

/**
 * Handler para quando o período de eventos mudar
 */
const handlePeriodoEventosChange = async (novoPeriodo) => {
  periodoEventos.value = novoPeriodo;
  await loadEventos();
};

/**
 * Handler para ver detalhes de eventos
 */
const handleVerDetalhesEventos = () => {
  // Implementar lógica para ver detalhes
  console.log('Ver detalhes de eventos');
};

/**
 * Carregar dados de promoções
 */
const loadPromocoes = async () => {
  isPromocaoLoading.value = true;
  try {
    const response = await apiRequest(`/api/dashboard/promocoes?periodo=${periodoPromocao.value}`);

    if (response) {
      dadosPromocao.value = {
        produtosEmPromocao: response.produtosEmPromocao || 0,
        descontoMedio: response.descontoMedio || 0,
        impactoVendas: response.impactoVendas || 0,
        vendasPromocao: response.vendasPromocao || 0,
        vendasNormais: response.vendasNormais || 0,
        produtosEmPromocaoLista: Array.isArray(response.produtosEmPromocaoLista)
          ? response.produtosEmPromocaoLista
          : []
      };
    } else {
      dadosPromocao.value = {
        produtosEmPromocao: 0,
        descontoMedio: 0,
        impactoVendas: 0,
        vendasPromocao: 0,
        vendasNormais: 0,
        produtosEmPromocaoLista: []
      };
    }
  } catch (error) {
    console.error('Erro ao carregar promoções:', error);
    dadosPromocao.value = {
      produtosEmPromocao: 0,
      descontoMedio: 0,
      impactoVendas: 0,
      vendasPromocao: 0,
      vendasNormais: 0,
      produtosEmPromocaoLista: []
    };
  } finally {
    isPromocaoLoading.value = false;
  }
};

/**
 * Carregar mesa com maior consumo
 */
const loadTopMesa = async () => {
  isTopMesaLoading.value = true;
  try {
    const response = await apiRequest('/api/admin/mesas/sessoes?status=open');
    const sessoes = response?.sessoes || [];

    if (Array.isArray(sessoes) && sessoes.length > 0) {
      const sorted = [...sessoes].sort((a, b) => {
        const subtotalA = Number(a.devidoCentavos || 0) + Number(a.pagoCentavos || 0);
        const subtotalB = Number(b.devidoCentavos || 0) + Number(b.pagoCentavos || 0);
        if (subtotalB !== subtotalA) return subtotalB - subtotalA;
        const ad = Number(a.devidoCentavos || 0);
        const bd = Number(b.devidoCentavos || 0);
        return bd - ad;
      });

      const m = sorted[0];
      topMesa.value = {
        mesaSlug: m.mesaSlug,
        mesaRotulo: m.mesaRotulo,
        totalMesaCentavos: Number(m.devidoCentavos || 0) + Number(m.pagoCentavos || 0),
        devidoCentavos: m.devidoCentavos,
        pessoas: m.pessoas,
        pagoCentavos: m.pagoCentavos,
      };
    } else {
      topMesa.value = null;
    }
  } catch (error) {
    console.error('Erro ao carregar mesa top:', error);
    topMesa.value = null;
  } finally {
    isTopMesaLoading.value = false;
  }
};

/**
 * Carregar top 5 mesas
 */
const loadTopList = async () => {
  isTopListLoading.value = true;
  try {
    const response = await apiRequest('/api/admin/mesas/sessoes?status=open');
    const sessoes = response?.sessoes || [];

    const sorted = [...sessoes]
      .sort((a, b) => {
        const subtotalA = Number(a.devidoCentavos || 0) + Number(a.pagoCentavos || 0);
        const subtotalB = Number(b.devidoCentavos || 0) + Number(b.pagoCentavos || 0);
        return subtotalB - subtotalA;
      })
      .slice(0, 5);

    topList.value = sorted.map((m) => ({
      mesaSlug: m.mesaSlug,
      mesaRotulo: m.mesaRotulo,
      totalMesaCentavos: Number(m.devidoCentavos || 0) + Number(m.pagoCentavos || 0),
      devidoCentavos: m.devidoCentavos,
      pagoCentavos: m.pagoCentavos,
    }));
  } catch (error) {
    console.error('Erro ao carregar top 5 mesas:', error);
    topList.value = [];
  } finally {
    isTopListLoading.value = false;
  }
};

/**
 * Carregar métricas de pedidos em preparação
 */
const loadPedidosPreparacao = async () => {
  try {
    const response = await apiRequest('/api/dashboard/pedidos-preparacao');

    if (response) {
      // Atualizar métricas de pedidos no bar
      metrics.value[0].value = response.pedidosNoBar || 0;
      metrics.value[0].subtitle = `${response.aguardandoPreparoBar || 0} aguardando preparo`;

      // Atualizar métricas de pedidos na cozinha
      metrics.value[1].value = response.pedidosNaCozinha || 0;
      metrics.value[1].subtitle = `${response.emPreparoCozinha || 0} aguardando preparo`;

      // Atualizar métricas de pedidos prontos
      metrics.value[2].value = response.pedidosEmEspera || 0;
      metrics.value[2].subtitle = `${response.pedidosEmEspera || 0} prontos para entrega`;
    }
  } catch (error) {
    console.error('Erro ao carregar pedidos em preparação:', error);
  }
};

/**
 * Carregar métricas de vendas
 */
const loadVendas = async () => {
  try {
    const response = await apiRequest('/api/dashboard/vendas');

    if (response) {
      // Atualizar métricas de mesas ocupadas
      metrics.value[3].value = `${response.mesasOcupadas || 0}/${response.totalMesas || 0}`;
      metrics.value[3].subtitle = `${Math.round(response.percentualOcupacao || 0)}% ocupação`;

      // Atualizar métricas de total de vendas
      const totalVendas = (response.totalVendasCentavos || 0) / 100;
      metrics.value[4].value = `R$ ${totalVendas.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
      metrics.value[4].subtitle = `${response.quantidadeVendas || 0} vendas hoje`;

      // Atualizar métricas de ticket médio
      const ticketMedio = (response.ticketMedioCentavos || 0) / 100;
      metrics.value[5].value = `R$ ${ticketMedio.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
      metrics.value[5].subtitle = (response.quantidadeVendas && response.quantidadeVendas > 0) ? 'Por venda' : 'Sem dados';
    }
  } catch (error) {
    console.error('Erro ao carregar métricas de vendas:', error);
  }
};

/**
 * Carregar top produtos
 */
const loadTopProdutos = async () => {
  isProdutosLoading.value = true;
  try {
    const response = await apiRequest(`/api/dashboard/top-produtos?ordenarPor=${ordenacaoProdutos.value}&periodo=${periodoProdutos.value}`);

    if (response && Array.isArray(response)) {
      topProdutos.value = response.map(produto => ({
        descricao: produto.nome,
        quantidade: produto.quantidade,
        valor: produto.valor // valor vem em reais (BigDecimal)
      }));
    } else {
      topProdutos.value = [];
    }
  } catch (error) {
    console.error('Erro ao carregar top produtos:', error);
    topProdutos.value = [];
  } finally {
    isProdutosLoading.value = false;
  }
};

/**
 * Carregar movimento de caixa do dia
 */
const loadMovimentoCaixa = async () => {
  isMovimentoCaixaLoading.value = true;
  try {
    const response = await apiRequest('/api/dashboard/movimento-caixa');

    if (response) {
      movimentoCaixa.value = {
        entradas: response.entradas || 0,
        saidas: response.saidas || 0,
        saldo: response.saldo || 0,
        detalhesPorTipo: response.detalhesPorTipo || {}
      };
    }
  } catch (error) {
    console.error('Erro ao carregar movimento de caixa:', error);
    movimentoCaixa.value = {
      entradas: 0,
      saidas: 0,
      saldo: 0,
      detalhesPorTipo: {}
    };
  } finally {
    isMovimentoCaixaLoading.value = false;
  }
};

/**
 * Carregar vendas do período
 */
const loadVendasPeriodo = async () => {
  isVendasPeriodoLoading.value = true;
  try {
    const response = await apiRequest(`/api/dashboard/vendas-periodo?periodo=${periodoVendas.value}`);

    if (response) {
      vendasPeriodo.value = {
        faturamento: response.faturamento || 0,
        quantidade_vendas: response.quantidadeVendas || 0,
        ticket_medio: response.ticketMedio || 0,
        dinheiro: response.dinheiro || 0,
        pix: response.pix || 0,
        cartaoCredito: response.cartaoCredito || 0,
        cartaoDebito: response.cartaoDebito || 0,
        voucher: response.voucher || 0,
      };
    }
  } catch (error) {
    console.error('Erro ao carregar vendas do período:', error);
    vendasPeriodo.value = {
      faturamento: 0,
      quantidade_vendas: 0,
      ticket_medio: 0,
      dinheiro: 0,
      pix: 0,
      cartao: 0,
    };
  } finally {
    isVendasPeriodoLoading.value = false;
  }
};

/**
 * Carregar histórico de vendas (valor/quantidade por dia)
 */
const loadVendasHistorico = async () => {
  isVendasHistoricoLoading.value = true;
  try {
    const response = await apiRequest(`/api/dashboard/vendas-historico?periodo=${periodoVendasHistorico.value}`);
    if (Array.isArray(response)) {
      vendasHistorico.value = response.map((item) => ({
        data: item.data,
        valor: Number(item.valor || 0),
        quantidade: Number(item.quantidade || 0),
      }));
    } else {
      vendasHistorico.value = [];
    }
  } catch (error) {
    console.error('Erro ao carregar histórico de vendas:', error);
    vendasHistorico.value = [];
  } finally {
    isVendasHistoricoLoading.value = false;
  }
};

/**
 * Carregar pedidos por local de preparação
 */
const loadPedidosPorLocal = async () => {
  isPedidosLocalLoading.value = true;
  try {
    const response = await apiRequest(`/api/dashboard/pedidos-local?diasRetroativos=${diasPedidosLocal.value}`);

    if (response && response.pedidosDiarios) {
      pedidosPorLocal.value = response.pedidosDiarios;
    } else {
      pedidosPorLocal.value = [];
    }
  } catch (error) {
    console.error('Erro ao carregar pedidos por local:', error);
    pedidosPorLocal.value = [];
  } finally {
    isPedidosLocalLoading.value = false;
  }
};

/**
 * Carregar dados financeiros do dia
 */
const loadFinanceiro = async () => {
  isFinanceiroLoading.value = true;
  try {
    const response = await apiRequest('/api/dashboard/financeiro');

    if (response) {
      dadosFinanceiros.value = {
        recebimentos: response.recebimentos || 0,
        total_recebido: response.total_recebido || 0,
        pagamentos: response.pagamentos || 0,
        total_pago: response.total_pago || 0,
        pendencias_recebimento: response.pendencias_recebimento || 0,
        pendencias_pagamento: response.pendencias_pagamento || 0,
      };
    }
  } catch (error) {
    console.error('Erro ao carregar dados financeiros:', error);
    dadosFinanceiros.value = {
      recebimentos: 0,
      total_recebido: 0,
      pagamentos: 0,
      total_pago: 0,
      pendencias_recebimento: 0,
      pendencias_pagamento: 0,
    };
  } finally {
    isFinanceiroLoading.value = false;
  }
};

/**
 * Carregar pendências de produtos
 */
const loadPendencias = async () => {
  isPendenciasLoading.value = true;
  try {
    const response = await apiRequest('/api/dashboard/pendencias?somenteAtivos=true');

    if (response) {
      pendencias.value = {
        total: response.total || 0,
        produtosSemPreco: response.produtosSemPreco || 0,
        produtosSemEstoque: response.produtosSemEstoque || 0,
        geradoEm: response.geradoEm || null,
      };
    } else {
      pendencias.value = null;
    }
  } catch (error) {
    console.error('Erro ao carregar pendências:', error);
    pendencias.value = null;
  } finally {
    isPendenciasLoading.value = false;
  }
};

/**
 * Carregar consumo de voucher de funcionários/admin no mês
 */
const loadVoucherConsumo = async () => {
  isVoucherLoading.value = true;
  try {
    const response = await apiRequest('/api/admin/dashboard/voucher-consumo/mes-atual');
    if (response && Array.isArray(response.data)) {
      consumoVoucher.value = response.data;
    } else {
      consumoVoucher.value = [];
    }
  } catch (error) {
    console.error('Erro ao carregar consumo de voucher:', error);
    consumoVoucher.value = [];
  } finally {
    isVoucherLoading.value = false;
  }
};

/**
 * Carregar dados de eventos
 */
const loadEventos = async () => {
  isEventosLoading.value = true;
  mensagemEventosErro.value = '';
  try {
    // Buscar dados reais da API
    const response = await apiRequest(`/api/dashboard/eventos?periodo=${periodoEventos.value}`);

    if (response) {
      dadosEventos.value = {
        totalCouverAtual: response.totalCouverAtual || 0,
        totalFaturamento30d: response.totalFaturamento30d || 0,
        mediaFaturamento: response.mediaFaturamento || 0,
        eventosLista: Array.isArray(response.eventosLista) ? response.eventosLista : []
      };
      mensagemEventosErro.value = '';
    } else {
      dadosEventos.value = {
        totalCouverAtual: 0,
        totalFaturamento30d: 0,
        mediaFaturamento: 0,
        eventosLista: []
      };
    }
  } catch (error) {
    console.error('Erro ao carregar dados de eventos:', error);
    const mensagem = typeof error === 'string'
      ? error
      : (error?.message || error?.mensagem || 'Não foi possível carregar os eventos no momento.');
    mensagemEventosErro.value = mensagem;
    dadosEventos.value = {
      totalCouverAtual: 0,
      totalFaturamento30d: 0,
      mediaFaturamento: 0,
      eventosLista: []
    };
  } finally {
    isEventosLoading.value = false;
  }
};

onMounted(() => {
  // Inicializa os painéis
  resetOrder();

  // Carregar dados do dashboard
  loadDashBoard();

  // Configurar header
  eventBus.emit('update:header', {
    breadcrumbs: [
      { icon: 'home', label: 'Home', to: '/home' },
      { label: 'Dashboard', class: 'secondary' },
    ],
    title: 'Dashboard',
    icon: 'o_dashboard',
  });
});
</script>

<style scoped>
.panel-header {
  font-size: 1.25rem;
  font-weight: 500;
}

.panel-title {
  font-size: 18px;
  font-weight: bold;
  font-family: Arial, sans-serif;
  color: #6B3E26;
}

.painel-handle {
  position: absolute;
  top: 0;
  right: 0;
  z-index: 10;
  opacity: 0.3;
  transition: opacity 0.3s;
}

.painel-handle:hover {
  opacity: 1;
}

.ghost {
  opacity: 0.5;
  background: #e8dfd6;
  border: 1px dashed #8B7355;
}

.chosen {
  box-shadow: 0 0 10px 0 rgba(0, 0, 0, 0.3);
}

.my-content {
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 5px rgba(0, 0, 0, 0.2);
  padding: 16px;
  height: 100%;
}
</style>
