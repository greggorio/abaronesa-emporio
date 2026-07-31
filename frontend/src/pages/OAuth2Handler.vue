<template>
  <div class="oauth2-handler-page">
    <div class="handler-container">
      <div class="handler-content">
        <q-spinner-cube size="60px" color="primary" class="q-mb-lg" />
        <h3 class="handler-title">{{ statusMessage }}</h3>
        <p class="handler-subtitle">{{ subMessage }}</p>

        <div v-if="hasError" class="error-actions q-mt-lg">
          <q-btn color="primary" @click="redirectToLogin" no-caps unelevated>Tentar Novamente</q-btn>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from "vue";
import { useRouter, useRoute } from "vue-router";

const router = useRouter();
const route = useRoute();

const statusMessage = ref("Processando autenticação...");
const subMessage = ref("Aguarde enquanto finalizamos seu login");
const hasError = ref(false);

let userStore = null;

onMounted(async () => {
  try {
    console.log("🔄 Iniciando processamento OAuth2...");
    console.log("URL atual:", window.location.href);
    console.log("Query params:", route.query);

    // Aguardar um pouco para garantir que tudo esteja carregado
    await new Promise((resolve) => setTimeout(resolve, 500));

    // Inicializar store
    const { useUserStore } = await import("src/stores/userStore");
    userStore = useUserStore();

    // Processar OAuth2
    await processOAuth2Login();
  } catch (error) {
    console.error("❌ Erro ao processar OAuth2:", error);
    showError("Erro interno do sistema");
  }
});

const processOAuth2Login = async () => {
  try {
    // Verificar se há erro na URL
    const hasError = route.query.error === "true";
    const errorMessage = route.query.message;

    if (hasError) {
      console.error("❌ Erro OAuth2 detectado:", errorMessage);
      showError(decodeURIComponent(errorMessage || "Falha na autenticação OAuth2"));
      return;
    }

    // Capturar token da URL (múltiplas formas)
    let token =
      route.query.token ||
      new URLSearchParams(window.location.search).get("token") ||
      new URLSearchParams(window.location.hash.split("?")[1]).get("token");

    console.log("🔍 Token capturado:", token ? `${token.substring(0, 20)}...` : "null");

    if (!token) {
      throw new Error("Token não encontrado na URL");
    }

    statusMessage.value = "Validando credenciais...";

    // 🔧 USANDO A FUNÇÃO processOAuth2Token DO USERSTORE
    const userData = await userStore.processOAuth2Token(token);
    console.log("✅ Dados do usuário obtidos:", userData.data);

    statusMessage.value = "Login realizado com sucesso!";
    subMessage.value = `Bem-vindo(a), ${userData.nome}!`;

    // 🔧 AGUARDAR UM POUCO E BUSCAR NOVAMENTE PARA GARANTIR
    setTimeout(async () => {
      try {
        await userStore.fetchUserProfile();
        console.log("🔄 Perfil atualizado após OAuth2:", userStore.currentUser);
      } catch (err) {
        console.warn("⚠️ Erro ao atualizar perfil:", err);
      }
    }, 1000);

    // 🔧 VERIFICAR AUTENTICAÇÃO E REDIRECIONAR
    setTimeout(async () => {
      await nextTick();

      if (userStore.isAuthenticated) {
        const user = userStore.currentUser;
        console.log("🔍 Verificação de role:", {
          userRoles: user?.roles,
          isAdmin: user?.roles?.includes("ADMIN") || false,
          redirecionarPara: user?.roles?.includes("ADMIN") ? "admin-dashboard" : "dashboard",
        });
        const isAdmin = user?.roles?.includes("ADMIN") || false;

        console.log("🔍 Verificação de role:", {
          userRoles: user?.roles,
          isAdmin,
          redirecionarPara: isAdmin ? "home" : "dashboard",
        });

        const redirectPath = isAdmin ? "/home" : "/home";

        console.log(`✅ Usuário autenticado, redirecionando para ${redirectPath}...`);
        router
          .push(redirectPath)
          .then(() => {
            console.log(`✅ Redirecionamento para ${redirectPath} concluído`);
          })
          .catch((err) => {
            console.error("❌ Erro no redirecionamento:", err);
            window.location.href = `/#${redirectPath}`;
          });
      } else {
        console.error("❌ Usuário não está autenticado após OAuth2!");
        console.error("🔍 Debug final:", {
          currentUserValue: userStore.currentUser,
          tokenValue: userStore.token,
          computedResult: userStore.isAuthenticated,
        });
        showError("Erro na autenticação. Dados não foram salvos corretamente.");
      }
    }, 2000); // 🔧 AUMENTADO PARA 2 SEGUNDOS PARA GARANTIR TEMPO SUFICIENTE
  } catch (error) {
    console.error("❌ Erro ao processar OAuth2:", error);
    // Verificar se é erro de CLIENTE tentando acessar ERP
    if (error.code === "CLIENTE_NOT_ALLOWED") {
      showError("Clientes não podem acessar o ERP. Use o aplicativo móvel.");
    } else {
      showError("Falha na autenticação. Tente fazer login novamente.");
    }
  }
};

const showError = (message) => {
  hasError.value = true;
  statusMessage.value = "Erro na autenticação";
  subMessage.value = message;
  console.error("❌ Erro OAuth2:", message);
};

const redirectToLogin = () => {
  router.push("/");
};
</script>

<style scoped lang="scss">
$primary-color: #b79149;
$text-dark: #1a1a1a;
$text-medium: #6b7280;
$bg-light: #faf8f5;

.oauth2-handler-page {
  min-height: 100vh;
  background: $bg-light;
  display: flex;
  align-items: center;
  justify-content: center;
}

.handler-container {
  max-width: 500px;
  width: 100%;
  padding: 2rem;
}

.handler-content {
  background: white;
  padding: 3rem 2rem;
  border-radius: 1rem;
  text-align: center;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
}

.handler-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: $text-dark;
  margin: 0 0 0.5rem 0;
}

.handler-subtitle {
  color: $text-medium;
  margin: 0;
  line-height: 1.5;
}

.error-actions {
  display: flex;
  justify-content: center;
}
</style>
