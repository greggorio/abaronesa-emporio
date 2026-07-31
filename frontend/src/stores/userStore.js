import { defineStore } from "pinia";
import { ref, computed } from "vue";
import axios from "axios";
import { baseApiUrl } from "@/global";
import { useRouter } from "vue-router";
import { useApiRequest } from "@/composables/useApiRequest";

export const useUserStore = defineStore("user", () => {
  /* ------------------------------------------------------------------ */
  /* Estado                                                              */
  /* ------------------------------------------------------------------ */
  const router = useRouter();
  const currentUser = ref(null);
  const token = ref(null);

  const isAuthenticated = computed(() => !!currentUser.value && !!token.value);

  const authError = ref(false);
  const authErrorMessage = ref("");

  /* ------------------------------------------------------------------ */
  /* Inicialização                                                       */
  /* ------------------------------------------------------------------ */
  function init() {
    const storedToken = sessionStorage.getItem("token");
    const userId = sessionStorage.getItem("user_id");

    if (storedToken && userId) {
      token.value = storedToken;

      // Restaurar grupoId do sessionStorage
      const storedGrupoId = sessionStorage.getItem("user_grupo_id");

      currentUser.value = {
        id: parseInt(userId),
        name: sessionStorage.getItem("user_name") || "Usuário",
        email: sessionStorage.getItem("user_email") || null,
        fotoPerfil: sessionStorage.getItem("fotoPerfil") || null,
        roles: JSON.parse(sessionStorage.getItem("user_roles") || "[]"),
        grupoId: storedGrupoId ? parseInt(storedGrupoId) : null, // Restaurar grupoId
      };
    }
  }

  /* ------------------------------------------------------------------ */
  /* Login local                                                         */
  /* ------------------------------------------------------------------ */
  async function login(username, password) {
    try {
      authError.value = false;
      authErrorMessage.value = "";

      const trimmed = username.trim().toLowerCase();

      // MUDANÇA: Novo endpoint e estrutura
      const resp = await axios.post(`${baseApiUrl}/api/auth/login`, {
        email: trimmed,
        password: password, // era 'senha'
      });

      // MUDANÇA: Nova estrutura de resposta
      const tempToken = resp.data.accessToken; // Guardar temporariamente

      // Verificar role ANTES de salvar o token
      try {
        // Fazer request com o token temporário
        const userCheckResponse = await axios.get(`${baseApiUrl}/api/auth/me`, {
          headers: {
            Authorization: `Bearer ${tempToken}`
          }
        });

        const userData = userCheckResponse.data;

        // VALIDAÇÃO: Bloquear usuários com ROLE CLIENTE no ERP
        if (userData.roles && userData.roles.includes("CLIENTE")) {
          const clienteError = new Error("Clientes não podem acessar o ERP");
          clienteError.code = "CLIENTE_NOT_ALLOWED";
          throw clienteError;
        }

        // Se passou na validação, salvar o token
        token.value = tempToken;
        sessionStorage.setItem("token", token.value);
        sessionStorage.setItem("logado", "true");
      } catch (err) {
        // Se for erro de validação de CLIENTE, propagar
        if (err.code === "CLIENTE_NOT_ALLOWED") {
          throw err;
        }
        // Para outros erros, salvar o token e tentar continuar
        token.value = tempToken;
        sessionStorage.setItem("token", token.value);
        sessionStorage.setItem("logado", "true");
      }

      // MUDANÇA: Buscar dados do usuário atual via novo endpoint
      await fetchCurrentUserData();
      return true;
    } catch (err) {
      handleAuthError(err);
      return false;
    }
  }

  /* ------------------------------------------------------------------ */
  /* Buscar dados do usuário atual (NOVO MÉTODO)                        */
  /* ------------------------------------------------------------------ */
  async function fetchCurrentUserData() {
    try {
      const { apiRequest } = useApiRequest();

      // MUDANÇA: Usar endpoint /api/auth/me
      const user = await apiRequest("/api/auth/me");

      // NOTA: Removido redirecionamento automático para /admin
      // O usuário root agora seguirá o fluxo normal e será redirecionado para /home
      // A lógica de permissões baseadas em roles determinará o acesso

      // Salvar dados na sessão
      sessionStorage.setItem("user_name", user.nome || "");
      sessionStorage.setItem("user_id", user.id || "");
      sessionStorage.setItem("user_email", user.email || "");
      sessionStorage.setItem("fotoPerfil", user.fotoPerfil || "");
      sessionStorage.setItem("user_roles", JSON.stringify(user.roles || []));

      // SALVAR GRUPO ID SE VIER DO BACKEND
      if (user.grupoId) {
        sessionStorage.setItem("user_grupo_id", user.grupoId);
        sessionStorage.setItem("setUserGroup", user.grupoId); // Manter compatibilidade
      }

      currentUser.value = {
        id: user.id,
        name: user.nome,
        email: user.email,
        fotoPerfil: user.fotoPerfil,
        roles: user.roles,
        grupoId: user.grupoId || null, // Adicionar grupoId se vier do backend
      };

      // Buscar dados completos do perfil para obter grupoId
      await fetchFullProfile();
    } catch (err) {
      console.error("Erro ao buscar dados do usuário:", err);
      throw err;
    }
  }

  /* ------------------------------------------------------------------ */
  /* Buscar perfil completo (OPCIONAL - se precisar de mais dados)      */
  /* ------------------------------------------------------------------ */
  async function fetchFullProfile() {
    try {
      const { apiRequest } = useApiRequest();
      const profile = await apiRequest("/api/profile");

      // Atualizar currentUser com dados completos se necessário
      currentUser.value = {
        ...currentUser.value,
        telefone: profile.telefone,
        ativo: profile.ativo,
        emailVerificado: profile.emailVerificado,
        grupoId: profile.grupoId || null,
        // Se for funcionário, terá dados adicionais
        perfilFuncionario: profile.perfilFuncionario || null,
      };

      // Guardar informações adicionais se necessário
      if (profile.perfilFuncionario) {
        sessionStorage.setItem("user_cargo", profile.perfilFuncionario.cargo || "");
      }

      // SALVAR GRUPO ID NO SESSION STORAGE
      if (profile.grupoId) {
        sessionStorage.setItem("user_grupo_id", profile.grupoId);
      }
    } catch (err) {
      console.error("Erro ao buscar perfil completo:", err);
      // Não propagar erro pois é opcional
    }
  }

  /* ------------------------------------------------------------------ */
  /* DEPRECADO - Mantido para compatibilidade temporária                */
  /* ------------------------------------------------------------------ */
  async function fetchUserData(username) {
    console.warn("fetchUserData está deprecado. Use fetchCurrentUserData()");
    return fetchCurrentUserData();
  }

  /* ------------------------------------------------------------------ */
  /* 🆕  Processar token vindo do OAuth2                                 */
  /* ------------------------------------------------------------------ */
  async function processOAuth2Token(tokenFromUrl) {
    try {
      // Verificar role ANTES de salvar o token
      try {
        // Fazer request com o token temporário
        const userCheckResponse = await axios.get(`${baseApiUrl}/api/auth/me`, {
          headers: {
            Authorization: `Bearer ${tokenFromUrl}`
          }
        });

        const userData = userCheckResponse.data;

        // VALIDAÇÃO: Bloquear usuários com ROLE CLIENTE no ERP
        if (userData.roles && userData.roles.includes("CLIENTE")) {
          const clienteError = new Error("Clientes não podem acessar o ERP");
          clienteError.code = "CLIENTE_NOT_ALLOWED";
          throw clienteError;
        }
      } catch (err) {
        // Se for erro de validação de CLIENTE, propagar
        if (err.code === "CLIENTE_NOT_ALLOWED") {
          throw err;
        }
        // Para outros erros, continuar
      }

      // 1) Salvar o JWT na store e sessão
      token.value = tokenFromUrl;
      sessionStorage.setItem("token", token.value);
      sessionStorage.setItem("logado", "true");

      // 2) Buscar dados do usuário usando o novo método
      await fetchCurrentUserData();

      return {
        success: true,
        data: currentUser.value,
        nome: currentUser.value.name,
      };
    } catch (err) {
      console.error("Erro ao processar token OAuth2:", err);
      // Sinalizar erro de autenticação para ser exibido na tela de login
      handleAuthError(err);
      // Limpar sessão, mas preservar o estado de erro para o componente de login exibir
      logout(false, true);
      throw err;
    }
  }

  /* ------------------------------------------------------------------ */
  /* Logout                                                              */
  /* ------------------------------------------------------------------ */
  function logout(fromRedeploy = false, preserveAuthError = false) {
    currentUser.value = null;
    token.value = null;
    if (!preserveAuthError) {
      authError.value = false;
      authErrorMessage.value = "";
    }

    // Se for logout após redeploy, marcar para forçar refresh na página de login
    if (fromRedeploy) {
      sessionStorage.setItem("from_redeploy", "true");
    }

    sessionStorage.clear();

    // Preservar marcador de redeploy se foi definido
    if (fromRedeploy) {
      sessionStorage.setItem("from_redeploy", "true");
    }

    router.push("/");
  }

  /* ------------------------------------------------------------------ */
  /* Tratamento de erros de login                                        */
  /* ------------------------------------------------------------------ */
  function handleAuthError(err) {
    console.error("Erro de autenticação:", err);
    authError.value = true;

    // Tratar erro específico de cliente tentando acessar ERP
    if (err.code === "CLIENTE_NOT_ALLOWED") {
      authErrorMessage.value = "CLIENTE_NOT_ALLOWED"; // Código para ser traduzido no componente
      return;
    }

    if (err.code === "ECONNREFUSED" || err.code === "ERR_NETWORK" || !err.response) {
      authErrorMessage.value = "Servidor indisponível. Contacte o suporte.";
    } else if (err.response) {
      switch (err.response.status) {
        case 401:
          // Usar a mensagem do backend se disponível
          authErrorMessage.value = err.response.data?.error?.message || "Usuário ou senha inválidos";
          break;
        case 403:
          authErrorMessage.value = "Acesso negado";
          break;
        case 500:
          authErrorMessage.value = "Erro interno do servidor";
          break;
        case 503:
          authErrorMessage.value = "Serviço temporariamente indisponível";
          break;
        default:
          authErrorMessage.value = `Erro na conexão (${err.response.status})`;
      }
    } else {
      authErrorMessage.value = "Erro de conexão com o servidor";
    }
  }

  /* ------------------------------------------------------------------ */
  /* Computed Properties para Roles e Permissões                        */
  /* ------------------------------------------------------------------ */
  const hasSystemRole = computed(() => {
    return currentUser.value?.roles?.includes("SYSTEM") || false;
  });

  const hasAdminRole = computed(() => {
    return currentUser.value?.roles?.includes("ADMIN") || false;
  });

  const hasRootAccess = computed(() => {
    return hasSystemRole.value || hasAdminRole.value;
  });

  const canAccessMenu = computed(() => {
    return hasSystemRole.value || !!currentUser.value?.grupoId;
  });

  const canAccessAllDashboards = computed(() => {
    return hasSystemRole.value || hasAdminRole.value; // SYSTEM e ADMIN podem ver tudo
  });

  const getUserGroup = computed(() => {
    // Para SYSTEM, retornar null (backend entrega menu completo para id_grupo null)
    // Para usuários normais, usar grupoId
    return hasSystemRole.value ? null : currentUser.value?.grupoId;
  });

  const isRootUser = computed(() => {
    return currentUser.value?.email === "root@localhost";
  });

  /* ------------------------------------------------------------------ */
  /* Exposição                                                           */
  /* ------------------------------------------------------------------ */
  init();

  return {
    currentUser,
    isAuthenticated,
    authError,
    authErrorMessage,
    login,
    logout,
    fetchUserData, // Mantido para compatibilidade
    fetchCurrentUserData, // Novo método preferido
    fetchFullProfile, // Opcional para dados completos
    fetchUserProfile: fetchFullProfile, // Alias para compatibilidade com OAuth2Handler
    processOAuth2Token,
    // Novos computed properties para roles
    hasSystemRole,
    hasAdminRole,
    hasRootAccess,
    canAccessMenu,
    canAccessAllDashboards,
    getUserGroup,
    isRootUser,
  };
});
