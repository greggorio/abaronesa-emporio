import axios from "axios";
import { baseApiUrl } from "@/global";
import { useUserStore } from "src/stores/userStore";

const formatDateForBackend = (dateString) => {
  if (!dateString) return null;

  // Se já tem milissegundos, retorna como está
  if (dateString.includes(".")) return dateString;

  // Adiciona os milissegundos
  return `${dateString}.000`;
};

// Configure o interceptor antes de criar a instância
axios.interceptors.request.use(
  (config) => {
    if (config.data && typeof config.data === "object") {
      // Função recursiva para tratar objetos aninhados
      const processData = (obj) => {
        Object.keys(obj).forEach((key) => {
          if (obj[key] !== null && typeof obj[key] === "object" && !Array.isArray(obj[key])) {
            // Recursão para objetos aninhados
            processData(obj[key]);
          } else if (key.toLowerCase().includes("data") && typeof obj[key] === "string" && obj[key].match(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/)) {
            obj[key] = formatDateForBackend(obj[key]);
          }
        });
      };

      processData(config.data);
    }
    return config;
  },
  (error) => {
    // Tratamento de erro do interceptor
    return Promise.reject(error);
  }
);

export function useApiRequest() {
  const apiRequest = async (url, methodOrOptions = "GET", data = null, options = {}) => {
    let method = methodOrOptions;
    let payload = data;
    let extraOptions = options;

    if (typeof methodOrOptions === "object" && methodOrOptions !== null) {
      extraOptions = methodOrOptions;
      method = methodOrOptions.method || "GET";
      payload = methodOrOptions.data ?? methodOrOptions.payload ?? methodOrOptions.body ?? payload;
    }

    extraOptions = extraOptions || {};

    try {
      const config = {
        method,
        url: `${baseApiUrl}${url}`,
        headers: {
          Authorization: `Bearer ${sessionStorage.getItem("token")}`,
          // Adiciona o ID do usuário como cabeçalho HTTP personalizado
          "X-User-ID": sessionStorage.getItem("user_id") || "",
        },
        ...extraOptions, // Mescla opções adicionais como responseType
      };

      // Só definir Content-Type como application/json se não for FormData
      if (!(payload instanceof FormData)) {
        config.headers["Content-Type"] = "application/json";
      }

      if (method === "POST" || method === "PUT" || method === "PATCH") {
        config.data = payload;
      }

      const response = await axios(config);

      // Para responseType 'arraybuffer', retornar response.data diretamente
      if (extraOptions.responseType === "arraybuffer") {
        return response.data;
      }

      return response.data;
    } catch (error) {
      console.error("Erro ao acessar a rota protegida ou ao carregar dados:", error);

      const userStore = useUserStore();
      //userStore.logout();
      throw error.response?.data || error;
    }
  };

  return { apiRequest };
}
