// src/composables/useAIAssistant.js
import { ref } from "vue";
import { useAIService } from "@/services/aiService";
import { useAIAssistantStore } from "@/stores/aiAssistant";

export function useAIAssistant() {
  const { processPrompt } = useAIService();
  const store = useAIAssistantStore();

  const isProcessing = ref(false);

  /**
   * Processar comando do usuário via OpenAI
   */
  const processCommand = async (command) => {
    if (!command || isProcessing.value) return null;

    isProcessing.value = true;

    try {
      console.log("Processando comando:", command);

      const response = await processPrompt(command);

      if (response) {
        // Armazenar resposta no store
        store.setCurrentResponse(response);
        store.addToHistory(command, response);

        console.log("Resposta recebida:", response);
        return response;
      }

      return null;
    } catch (error) {
      console.error("Erro ao processar comando:", error);
      throw error;
    } finally {
      isProcessing.value = false;
    }
  };

  /**
   * Identificar tipo de comando baseado na resposta
   */
  const identifyCommandType = (response) => {
    if (!response) return "desconhecido";

    // Verificar se é erro
    if (response.tipo === "erro" || response.error) {
      return "erro";
    }

    // Verificar tipo explícito na resposta
    if (response.tipo) {
      return response.tipo.toLowerCase();
    }

    // Tentar identificar pelo conteúdo
    if (response.cpf || response.cnpj || response.nome) {
      return "cliente";
    }

    if (response.usuario || response.role) {
      return "usuario";
    }

    return "geral";
  };

  /**
   * Verificar se o tipo de comando requer diálogo
   */
  const requiresDialog = (type) => {
    const dialogTypes = ["cliente", "usuario", "produto", "pedido"];
    return dialogTypes.includes(type);
  };

  /**
   * Validar resposta antes de processar
   */
  const validateResponse = (response) => {
    const errors = [];

    if (!response) {
      errors.push("Resposta vazia");
      return { valid: false, errors };
    }

    const type = identifyCommandType(response);

    // Validações específicas por tipo
    if (type === "cliente") {
      if (!response.nome) {
        errors.push("Nome do cliente não informado");
      }

      // Deve ter CPF ou CNPJ
      if (!response.cpf && !response.cnpj) {
        errors.push("CPF ou CNPJ não informado");
      }
    }

    if (type === "usuario") {
      if (!response.nome) {
        errors.push("Nome do usuário não informado");
      }
      if (!response.email) {
        errors.push("Email não informado");
      }
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  };

  /**
   * Limpar estado
   */
  const clearState = () => {
    isProcessing.value = false;
    store.clearCurrentResponse();
  };

  return {
    // Estado
    isProcessing,

    // Métodos
    processCommand,
    identifyCommandType,
    requiresDialog,
    validateResponse,
    clearState,
  };
}
