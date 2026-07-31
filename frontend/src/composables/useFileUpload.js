// composables/useFileUpload.js
import { ref, reactive } from "vue";
import { useQuasar } from "quasar";
import { baseApiUrl } from "@/global";

export function useFileUpload() {
  const $q = useQuasar();

  const uploadStates = reactive({});
  const fileInputRefs = ref({});
  const uploadedFiles = ref({});
  const uploadedImages = ref({});
  const uploadedFileNames = ref({});

  // Inicializa o estado para um campo
  function initializeField(fieldName) {
    if (!uploadStates[fieldName]) {
      uploadStates[fieldName] = {
        uploading: false,
        progress: 0,
        error: null,
      };
    }
  }

  // Valida arquivo
  function validateFile(file, options = {}) {
    const {
      maxSize = 5242880, // 5MB
      allowedTypes = ["image/*"],
      allowedExtensions = [],
    } = options;

    if (maxSize && file.size > maxSize) {
      throw new Error(`Arquivo muito grande! Tamanho máximo: ${(maxSize / 1024 / 1024).toFixed(1)}MB`);
    }

    if (allowedTypes.length > 0) {
      const isValidType = allowedTypes.some((type) => {
        if (type.endsWith("/*")) {
          return file.type.startsWith(type.slice(0, -1));
        }
        return file.type === type;
      });

      if (!isValidType) {
        throw new Error(`Tipo de arquivo não permitido. Permitidos: ${allowedTypes.join(", ")}`);
      }
    }

    if (allowedExtensions.length > 0) {
      const extension = file.name.split(".").pop().toLowerCase();
      if (!allowedExtensions.includes(extension)) {
        throw new Error(`Extensão não permitida. Permitidas: ${allowedExtensions.join(", ")}`);
      }
    }

    return true;
  }

  // Cria preview da imagem
  function createImagePreview(file, fieldName) {
    const reader = new FileReader();
    reader.onload = (e) => {
      uploadedImages.value[fieldName] = e.target.result;
    };
    reader.readAsDataURL(file);
  }

  // Seleciona arquivo
  function selectFile(fieldName) {
    if (fileInputRefs.value[fieldName]) {
      fileInputRefs.value[fieldName].click();
    }
  }

  // Processa mudança de arquivo
  function handleFileChange(fieldName, event, options = {}) {
    const file = event.target.files[0];
    if (!file) return;

    initializeField(fieldName);

    try {
      validateFile(file, options);

      uploadedFiles.value[fieldName] = file;
      uploadedFileNames.value[fieldName] = file.name;

      // Cria preview se for imagem
      if (file.type.startsWith("image/")) {
        createImagePreview(file, fieldName);
      }

      $q.notify({
        type: "info",
        message: "Arquivo selecionado com sucesso!",
      });
    } catch (error) {
      $q.notify({
        type: "negative",
        message: error.message,
      });
    }
  }

  // Faz upload do arquivo
  async function uploadFile(endpoint, file, fieldName, options = {}) {
    initializeField(fieldName);
    uploadStates[fieldName].uploading = true;
    uploadStates[fieldName].error = null;

    try {
      const formData = new FormData();
      formData.append(options.fileFieldName || "arquivo", file);

      // Adiciona campos extras se fornecidos
      if (options.extraFields) {
        Object.entries(options.extraFields).forEach(([key, value]) => {
          formData.append(key, value);
        });
      }

      const token = localStorage.getItem("token") || sessionStorage.getItem("token");
      const headers = {};

      if (token) {
        headers["Authorization"] = `Bearer ${token}`;
      }

      let fullEndpoint = endpoint;
      if (!/^https?:\/\//.test(endpoint)) {
        if (endpoint.startsWith("/")) {
          fullEndpoint = `${baseApiUrl}${endpoint}`;
        } else {
          fullEndpoint = `${baseApiUrl}/${endpoint}`;
        }
      }

      const response = await fetch(fullEndpoint, {
        method: "POST",
        body: formData,
        headers,
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Erro no upload: ${response.status} - ${response.statusText}`);
      }

      const contentType = response.headers.get("content-type");
      const result = contentType && contentType.includes("application/json") ? await response.json() : await response.text();

      $q.notify({
        type: "positive",
        message: "Upload realizado com sucesso!",
      });

      return result;
    } catch (error) {
      uploadStates[fieldName].error = error.message;
      $q.notify({
        type: "negative",
        message: error.message,
      });
      throw error;
    } finally {
      uploadStates[fieldName].uploading = false;
    }
  }

  // Remove arquivo
  function removeFile(fieldName) {
    delete uploadedFiles.value[fieldName];
    delete uploadedFileNames.value[fieldName];
    delete uploadedImages.value[fieldName];

    if (fileInputRefs.value[fieldName]) {
      fileInputRefs.value[fieldName].value = "";
    }
  }

  // Limpa todos os arquivos
  function clearAllFiles() {
    uploadedFiles.value = {};
    uploadedFileNames.value = {};
    uploadedImages.value = {};
    Object.keys(uploadStates).forEach((key) => {
      uploadStates[key] = {
        uploading: false,
        progress: 0,
        error: null,
      };
    });
  }

  // Gera URL completa da imagem
  function getImageUrl(imagePath) {
    if (!imagePath) return "";

    // Corrige para aceitar objeto { url: ... } ou { path: ... }
    if (typeof imagePath === "object" && imagePath !== null) {
      if ("url" in imagePath) {
        imagePath = imagePath.url;
      } else if ("path" in imagePath) {
        imagePath = imagePath.path;
      } else {
        // Se não tem url/path, retorna string vazia
        return "";
      }
    }

    if (typeof imagePath !== "string") return "";

    if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
      return imagePath;
    }

    if (imagePath.startsWith("/media/")) {
      return `${baseApiUrl}${imagePath}`;
    }

    return `${baseApiUrl}/media/${imagePath}`;
  }

  // Faz upload de múltiplos arquivos com endpoints específicos
  async function uploadMultipleFiles(recordId, fieldEndpoints) {
    const uploadPromises = Object.entries(uploadedFiles.value).map(async ([fieldName, file]) => {
      const endpointTemplate = fieldEndpoints[fieldName];
      if (!endpointTemplate) {
        throw new Error(`Endpoint não encontrado para o campo: ${fieldName}`);
      }

      const endpoint = endpointTemplate.replace("{id}", recordId);
      return {
        fieldName,
        result: await uploadFile(endpoint, file, fieldName),
      };
    });

    const results = await Promise.allSettled(uploadPromises);

    const successful = results.filter((r) => r.status === "fulfilled").length;
    const failed = results.filter((r) => r.status === "rejected").length;

    if (failed > 0) {
      const failedFields = results
        .filter((r) => r.status === "rejected")
        .map((r) => r.reason?.fieldName || "desconhecido")
        .join(", ");
      throw new Error(`${failed} arquivo(s) falharam no upload nos campos: ${failedFields}`);
    }

    return results.map((r) => r.value);
  }

  // Faz upload de arquivo específico com endpoint customizado
  async function uploadFileWithEndpoint(recordId, fieldName, endpointTemplate, options = {}) {
    const file = uploadedFiles.value[fieldName];
    if (!file) {
      throw new Error(`Nenhum arquivo selecionado para o campo: ${fieldName}`);
    }

    const endpoint = endpointTemplate.replace("{id}", recordId);
    return uploadFile(endpoint, file, fieldName, options);
  }

  return {
    // Estados
    uploadStates,
    fileInputRefs,
    uploadedFiles,
    uploadedImages,
    uploadedFileNames,

    // Métodos
    selectFile,
    handleFileChange,
    uploadFile,
    uploadMultipleFiles,
    uploadFileWithEndpoint,
    removeFile,
    clearAllFiles,
    getImageUrl,
    validateFile,
    createImagePreview,
  };
}
