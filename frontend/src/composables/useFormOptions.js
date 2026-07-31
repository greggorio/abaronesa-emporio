// composables/useFormOptions.js
import { ref, computed } from "vue";
import { useApiRequest } from "@/composables/useApiRequest";

export function useFormOptions() {
  const { apiRequest } = useApiRequest();
  const optionsCache = ref(new Map());
  const loadingOptions = ref(new Set());

  // Carrega opções de um endpoint específico
  async function loadOptions(endpoint, cacheKey = endpoint) {
    if (optionsCache.value.has(cacheKey)) {
      return optionsCache.value.get(cacheKey);
    }

    if (loadingOptions.value.has(cacheKey)) {
      // Aguarda o carregamento em andamento
      return new Promise((resolve) => {
        const checkLoading = () => {
          if (!loadingOptions.value.has(cacheKey)) {
            resolve(optionsCache.value.get(cacheKey) || []);
          } else {
            setTimeout(checkLoading, 100);
          }
        };
        checkLoading();
      });
    }

    loadingOptions.value.add(cacheKey);

    try {
      const response = await apiRequest(endpoint);
      const options = response.data || [];
      optionsCache.value.set(cacheKey, options);
      return options;
    } catch (error) {
      console.error(`Erro ao carregar opções de ${endpoint}:`, error);
      return [];
    } finally {
      loadingOptions.value.delete(cacheKey);
    }
  }

  // Processa definições de campo com options
  function processFieldOptions(fieldDefinitions) {
    return fieldDefinitions.map((field) => {
      const processedField = { ...field };

      // Se o campo tem um endpoint para options
      if (field.optionsEndpoint) {
        processedField.options = computed(() => optionsCache.value.get(field.optionsEndpoint) || []);

        processedField.loading = computed(() => loadingOptions.value.has(field.optionsEndpoint));
      }

      return processedField;
    });
  }

  // Carrega todas as opções necessárias para um formulário
  async function loadFormOptions(fieldDefinitions) {
    if (!Array.isArray(fieldDefinitions)) return;
    const promises = fieldDefinitions.filter((field) => field.optionsEndpoint).map((field) => loadOptions(field.optionsEndpoint));

    await Promise.all(promises);
  }

  // Limpa o cache de opções
  function clearOptionsCache(cacheKey) {
    if (cacheKey) {
      optionsCache.value.delete(cacheKey);
    } else {
      optionsCache.value.clear();
    }
  }

  return {
    loadOptions,
    processFieldOptions,
    loadFormOptions,
    clearOptionsCache,
    optionsCache: computed(() => optionsCache.value),
    loadingOptions: computed(() => loadingOptions.value),
  };
}
