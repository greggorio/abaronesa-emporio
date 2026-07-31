import { ref, computed, onMounted, onBeforeUnmount } from "vue";
import { useApiRequest } from "@/composables/useApiRequest";

export function useDynamicTable(endpoint) {
  const { apiRequest } = useApiRequest();

  const response = ref(null);
  const loading = ref(false);
  const selected = ref(null);
  const filter = ref("");
  const activeFilters = ref({});

  const lastEndpoint = ref(endpoint.value);

  // NOVA: Altura de linha calculada dinamicamente
  const dynamicRowsPerPage = ref(15);

  const pagination = ref({
    page: 1,
    rowsPerPage: dynamicRowsPerPage.value,
    sortBy: null,
    descending: false,
    totalPages: 1,
    totalItems: 0,
  });

  const ProgramIcon = computed(() => response.value?.program_icon || "");
  const tableData = computed(() => response.value?.table_data || response.value?.objeto || []);
  const tableOrder = computed(() => response.value?.table_order || "id");
  const programName = computed(() => response.value?.program_name || "");

  const formDefinitions = computed(() => {
    if (response.value?.form_definitions_new) {
      return response.value.form_definitions_new;
    } else if (response.value?.form_definitions) {
      return response.value.form_definitions;
    }
    return [];
  });

  const tableDefinitions = computed(() => response.value?.table_definitions?.columns ?? []);

  const actions = computed(() => response.value?.form_botoes || []);

  // NOVA FUNÇÃO: Calcula rows per page baseado na altura da janela
  function calculateRowsPerPage() {
    const availableHeight = window.innerHeight * 0.8;
    const headerHeight = 50;
    const paginationHeight = 40;
    const extraMargin = 10;
    const rowHeight = 33; // Altura padrão de linha

    const calculatedRows = Math.max(1, Math.floor((availableHeight - headerHeight - paginationHeight - extraMargin) / rowHeight));

    dynamicRowsPerPage.value = calculatedRows;

    // Atualiza a paginação com o novo valor
    pagination.value.rowsPerPage = calculatedRows;

    return calculatedRows;
  }

  // NOVA FUNÇÃO: Handler para resize
  function handleResize() {
    const oldRowsPerPage = pagination.value.rowsPerPage;
    calculateRowsPerPage();

    // Se mudou a quantidade de linhas, recarrega os dados
    if (oldRowsPerPage !== pagination.value.rowsPerPage) {
      pagination.value.page = 1; // Volta para primeira página
      loadData();
    }
  }

  // Métodos
  async function loadData(advancedFilters = null) {
    loading.value = true;

    if (lastEndpoint.value !== endpoint.value) {
      pagination.value.page = 1;
      pagination.value.sortBy = null;
      pagination.value.descending = false;
      lastEndpoint.value = endpoint.value;
    }

    let url = `/api/${endpoint.value}/form-config`;
    const queryParams = [];

    // Paginação com valor dinâmico
    const pageParam = pagination.value.page > 0 ? pagination.value.page - 1 : 0;
    queryParams.push(`pagina=${pageParam}`);
    queryParams.push(`tamanho=${pagination.value.rowsPerPage}`); // Usa valor calculado

    // Ordenação
    if (pagination.value.sortBy) {
      queryParams.push(`ordenacao=${pagination.value.sortBy}`);
      queryParams.push(`direcao=${pagination.value.descending ? "desc" : "asc"}`);
    }

    // Filtros
    const filters = advancedFilters || activeFilters.value;
    if (filters && Object.keys(filters).length > 0) {
      console.log("[useDynamicTable] Aplicando filtros:", JSON.stringify(filters, null, 2));
      queryParams.push(`filter=${encodeURIComponent(JSON.stringify(filters))}`);
    }

    if (queryParams.length > 0) {
      url += `?${queryParams.join("&")}`;
    }

    console.log("[useDynamicTable] URL final:", url);

    try {
      const responseFromApi = await apiRequest(url);
      response.value = responseFromApi;

      // Inicializar ordenação do backend
      if (!pagination.value.sortBy && responseFromApi.table_order) {
        const parts = responseFromApi.table_order.trim().split(/\s+/);
        if (parts.length > 0) {
          pagination.value.sortBy = parts[0];
          pagination.value.descending = parts[1]?.toLowerCase() === "desc";
        }
      }

      // Atualizar paginação mantendo rowsPerPage calculado
      if (responseFromApi.totalPaginas !== undefined) {
        pagination.value = {
          ...pagination.value,
          rowsPerPage: dynamicRowsPerPage.value, // Mantém valor calculado
          totalPages: responseFromApi.totalPaginas, // Usar valor do backend
          totalItems: responseFromApi.totalElementos || 0,
        };
      }

      return responseFromApi;
    } catch (error) {
      throw error;
    } finally {
      loading.value = false;
    }
  }

  async function loadFullData(advancedFilters = null) {
    let url = `/api/${endpoint.value}?semPaginacao=true`;

    // Aplicar filtros se fornecidos
    const filters = advancedFilters || activeFilters.value;
    if (filters && Object.keys(filters).length > 0) {
      console.log("[useDynamicTable] loadFullData - Aplicando filtros:", JSON.stringify(filters, null, 2));
      url += `&filter=${encodeURIComponent(JSON.stringify(filters))}`;
    }

    console.log("[useDynamicTable] loadFullData - URL final:", url);
    const response = await apiRequest(url);
    return response.table_data || response.objeto || [];
  }

  function updatePagination(newPagination) {
    // Preserva o rowsPerPage calculado ao atualizar paginação
    pagination.value = {
      ...newPagination,
      rowsPerPage: dynamicRowsPerPage.value,
    };
    loadData();
  }

  function handlePageChange(newPage) {
    pagination.value.page = newPage;
    loadData();
  }

  function selectRow(row) {
    selected.value = selected.value && selected.value.id === row.id ? null : row;
  }

  function applyFilters(filters) {
    activeFilters.value = filters;
    pagination.value.page = 1;
    loadData(filters);
  }

  // NOVA: Inicializar cálculo e listener
  function initializeDynamicPagination() {
    calculateRowsPerPage();
    window.addEventListener("resize", handleResize);
  }

  // NOVA: Cleanup do listener
  function cleanupDynamicPagination() {
    window.removeEventListener("resize", handleResize);
  }

  return {
    // Estado
    response,
    loading,
    selected,
    filter,
    pagination,
    activeFilters,
    dynamicRowsPerPage, // NOVA: Expor valor calculado

    // Computed
    tableData,
    tableOrder,
    programName,
    formDefinitions,
    tableDefinitions,
    actions,
    ProgramIcon,

    // Métodos
    loadData,
    loadFullData,
    updatePagination,
    handlePageChange,
    selectRow,
    applyFilters,

    // NOVOS métodos
    calculateRowsPerPage,
    initializeDynamicPagination,
    cleanupDynamicPagination,
  };
}
