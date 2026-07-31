// Configuração centralizada da URL da API
// Prioridade: 1) RuntimeConfig (para Docker), 2) ERRO se não configurado
// Para Docker: window.RuntimeConfig DEVE estar configurado

// Debug: log dos valores disponíveis
console.log("🔍 Debug API URL Config:", {
  viteEnv: import.meta.env.VITE_BASE_API_URL,
  runtimeConfig: window.RuntimeConfig?.apiBaseUrl,
  windowRuntimeConfig: window.RuntimeConfig
});

// Lógica: RuntimeConfig (runtime) é OBRIGATÓRIO em produção
// Em desenvolvimento, usa VITE_BASE_API_URL se RuntimeConfig não estiver configurado
let apiUrl = window.RuntimeConfig?.apiBaseUrl;

// Se RuntimeConfig contém o placeholder, usar import.meta.env em DEV ou ERRO em produção
if (!apiUrl || apiUrl.includes('__VITE_BASE_API_URL__')) {
  if (import.meta.env.DEV) {
    // Em desenvolvimento, usar variável de ambiente do Vite
    apiUrl = import.meta.env.VITE_BASE_API_URL;
    console.log("🔧 Modo desenvolvimento: usando VITE_BASE_API_URL");
  } else {
    // Em produção, RuntimeConfig DEVE estar configurado
    console.error("❌ ERRO CRÍTICO: API URL não configurada corretamente!");
    console.error("RuntimeConfig:", window.RuntimeConfig);
    throw new Error("API URL não configurada! Configure VITE_BASE_API_URL no docker-compose.");
  }
}

export const baseApiUrl = apiUrl;

console.log("✅ baseApiUrl selecionada:", baseApiUrl);
