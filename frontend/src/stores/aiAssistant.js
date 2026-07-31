// src/stores/aiAssistant.js
import { defineStore } from "pinia";
import { ref } from "vue";

export const useAIAssistantStore = defineStore("aiAssistant", () => {
  // Estado
  const currentResponse = ref(null);
  const isDialogOpen = ref(false);
  const activeDialogType = ref(null);
  const commandHistory = ref([]);

  /**
   * Define a resposta atual
   */
  const setCurrentResponse = (response) => {
    currentResponse.value = response;
  };

  /**
   * Limpa a resposta atual
   */
  const clearCurrentResponse = () => {
    currentResponse.value = null;
  };

  /**
   * Abre um diálogo específico
   */
  const openDialog = (type) => {
    activeDialogType.value = type;
    isDialogOpen.value = true;
  };

  /**
   * Fecha o diálogo ativo
   */
  const closeDialog = () => {
    isDialogOpen.value = false;
    activeDialogType.value = null;
    // Limpar resposta após fechar diálogo
    setTimeout(() => {
      clearCurrentResponse();
    }, 300);
  };

  /**
   * Adiciona comando ao histórico
   */
  const addToHistory = (command, response) => {
    // Limitar histórico a últimos 20 comandos
    commandHistory.value.unshift({
      id: Date.now(),
      command,
      response,
      timestamp: new Date(),
    });

    if (commandHistory.value.length > 20) {
      commandHistory.value.pop();
    }
  };

  /**
   * Limpa o histórico de comandos
   */
  const clearHistory = () => {
    commandHistory.value = [];
  };

  /**
   * Verifica se há resposta
   */
  const hasResponse = () => currentResponse.value !== null;

  /**
   * Retorna o tipo da resposta atual
   */
  const responseType = () => currentResponse.value?.tipo || null;

  /**
   * Verifica se a resposta atual é um erro
   */
  const isError = () => currentResponse.value?.tipo === "erro";

  /**
   * Retorna o último comando do histórico
   */
  const getLastCommand = () => {
    return commandHistory.value.length > 0 ? commandHistory.value[0] : null;
  };

  return {
    // Estado
    currentResponse,
    isDialogOpen,
    activeDialogType,
    commandHistory,

    // Actions
    setCurrentResponse,
    clearCurrentResponse,
    openDialog,
    closeDialog,
    addToHistory,
    clearHistory,

    // Getters
    hasResponse,
    responseType,
    isError,
    getLastCommand,
  };
});
