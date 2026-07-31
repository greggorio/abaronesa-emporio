import { ref } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";

export function useCrudOperations(endpoint, options = {}) {
  const $q = useQuasar();
  const { apiRequest } = useApiRequest();

  const dialogVisible = ref(false);
  const selectedRecord = ref({});
  const isEditing = ref(false);

  // Callback para recarregar dados após operações
  const { onDataChanged } = options;

  async function createRecord() {
    selectedRecord.value = {};
    isEditing.value = false;
    dialogVisible.value = true;
  }

  async function editRecord(record) {
    try {
      const response = await apiRequest(`/api/${endpoint.value}/${record.id}`);
      selectedRecord.value = response;
      isEditing.value = true;
      dialogVisible.value = true;
    } catch (error) {
      $q.notify({
        type: "negative",
        message: `Erro ao carregar dados: ${error.message}`,
      });
    }
  }

  async function deleteRecord(id) {
    try {
      await apiRequest(`/api/${endpoint.value}/${id}`, "DELETE");
      $q.notify({
        type: "positive",
        message: "Registro excluído com sucesso!",
      });
      return true;
    } catch (error) {
      $q.notify({
        type: "negative",
        message: `Erro ao excluir: ${error.message}`,
      });
      return false;
    }
  }

  async function saveRecord(data, callback) {
    try {
      const method = data.id ? "PUT" : "POST";
      const url = data.id ? `/api/${endpoint.value}/${data.id}` : `/api/${endpoint.value}`;

      const response = await apiRequest(url, method, data);

      if (callback) {
        callback(response.data || response);
      }

      dialogVisible.value = false;

      $q.notify({
        type: "positive",
        message: data.id ? "Registro atualizado com sucesso!" : "Registro criado com sucesso!",
      });

      // Chama o callback para recarregar os dados
      if (onDataChanged) {
        await onDataChanged();
      }

      return response;
    } catch (error) {
      console.log("Estrutura do erro:", error); // Debug

      const backendError = error?.error ?? (error?.data?.error ?? null);
      const userMessage = backendError?.message ?? error?.message;
      const errorCode = backendError?.code ?? null;

      // Tratar erros de validação com campos específicos
      const validationPayload = backendError?.errors ?? error?.errors;
      if (validationPayload && typeof validationPayload === "object") {
        const fieldErrors = Object.entries(validationPayload)
          .map(([field, message]) => `• ${field}: ${message}`)
          .join("\n");

        $q.notify({
          type: "negative",
          message: "Erro de validação",
          caption: fieldErrors,
          multiLine: true,
          timeout: 8000,
          actions: [
            {
              label: "Fechar",
              color: "white",
              handler: () => {},
            },
          ],
        });
      } else {
        // Erro genérico
        $q.notify({
          type: "negative",
          message: `Erro ao salvar: ${userMessage || "ocorreu um erro inesperado"}`,
          caption: errorCode ? `Código: ${errorCode}` : undefined,
        });
      }
      throw error;
    }
  }

  function closeDialog() {
    dialogVisible.value = false;
    selectedRecord.value = {};
  }

  return {
    // Estado
    dialogVisible,
    selectedRecord,
    isEditing,

    // Métodos
    createRecord,
    editRecord,
    deleteRecord,
    saveRecord,
    closeDialog,
  };
}
