<template>
  <q-layout>
    <q-page-container>
      <q-page v-if="!isAuthenticated" class="login-page">
        <!-- Seletor de Idiomas -->
        <div class="language-selector">
          <q-btn-dropdown
            flat
            dense
            no-caps
            :icon="currentLanguage.flag"
            :label="currentLanguage.code"
            dropdown-icon="keyboard_arrow_down"
            class="language-btn"
          >
            <q-list>
              <q-item
                v-for="lang in languages"
                :key="lang.code"
                clickable
                v-close-popup
                @click="changeLanguage(lang)"
                :active="currentLanguage.code === lang.code"
                active-class="bg-primary text-white"
              >
                <q-item-section avatar>
                  <q-icon :name="lang.flag" />
                </q-item-section>
                <q-item-section>
                  <q-item-label>{{ lang.name }}</q-item-label>
                </q-item-section>
              </q-item>
            </q-list>
          </q-btn-dropdown>
        </div>

        <div class="login-container">
          <!-- Cabeçalho com Logo -->
          <div class="header-section">
            <div class="company-branding">
              <h1 class="company-name">{{ appName }}</h1>
              <p class="company-tagline">{{ translations.companyTagline }}</p>
            </div>
          </div>

          <!-- Formulário de Login -->
          <q-card class="login-card" elevation="24">
            <q-card-section class="login-form-section">
              <div class="form-header">
                <h2 class="welcome-text">{{ welcomeText }}</h2>
                <p class="login-prompt">{{ translations.loginPrompt }}</p>
              </div>

              <q-form @submit.prevent="signin" class="login-form">
                <q-input
                  v-model="email"
                  :label="translations.username"
                  outlined
                  class="q-mb-md input-field"
                  autofocus
                  :rules="[(val) => !!val || translations.usernameRequired]"
                >
                  <template v-slot:prepend>
                    <q-icon name="person" color="primary" />
                  </template>
                </q-input>

                <q-input
                  v-model="password"
                  :label="translations.password"
                  outlined
                  class="q-mb-lg input-field"
                  type="password"
                  @keyup.enter="onEnter"
                  :rules="[(val) => !!val || translations.passwordRequired]"
                >
                  <template v-slot:prepend>
                    <q-icon name="lock" color="primary" />
                  </template>
                </q-input>

                <q-btn
                  type="submit"
                  :label="translations.signIn"
                  color="primary"
                  class="full-width login-button"
                  unelevated
                  :loading="loading"
                  size="lg"
                />
              </q-form>

              <div class="divider">
                <span class="divider-text">{{ translations.or }}</span>
              </div>

              <div class="social-login">
                <q-btn flat class="social-btn full-width" no-caps @click="loginWithGoogle" size="md">
                  <q-icon name="mail" size="18px" class="q-mr-sm" style="color: #C67C48" />
                  {{ translations.continueWithGoogle }}
                </q-btn>
              </div>

              <q-slide-transition>
                <q-banner v-if="userStore.authError" dense class="q-mt-md error-message" rounded>
                  <template v-slot:avatar>
                    <q-icon name="error_outline" color="white" />
                  </template>
                  {{ displayErrorMessage }}
                </q-banner>
              </q-slide-transition>
            </q-card-section>
          </q-card>
        </div>

        <!-- Informações de Versão (Canto Inferior Direito) -->
        <div class="version-info">
          <div class="version-container">
            <div v-if="systemVersions.backend" class="version-item">
              <q-icon name="dns" size="14px" />
              <span>{{ systemVersions.backend }}</span>
            </div>
            <div v-if="systemVersions.frontend" class="version-item">
              <q-icon name="web" size="14px" />
              <span>{{ systemVersions.frontend }}</span>
            </div>
          </div>
        </div>
      </q-page>
    </q-page-container>
  </q-layout>
</template>

<script setup>
import { baseApiUrl } from "@/global";
import { ref, computed, onMounted, watch } from "vue";
import { useRouter } from "vue-router";
import eventBus from "@/eventBus";
import { useProgramStore } from "@/stores/programStore";
import { useUserStore } from "@/stores/userStore";
import { useApiRequest } from "@/composables/useApiRequest";

const programStore = useProgramStore();
const userStore = useUserStore();
const router = useRouter();
const { apiRequest } = useApiRequest();

const email = ref("");
const password = ref("");
const loading = ref(false);
const appName = ref("CaféTech");
const systemVersions = ref({
  backend: null,
  frontend: null,
});

// Sistema de idiomas
const languages = ref([
  {
    code: "ptBr",
    name: "Português (Brasil)",
    flag: "img:data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjEiIGhlaWdodD0iMTUiIHZpZXdCb3g9IjAgMCAyMSAxNSIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHJlY3Qgd2lkdGg9IjIxIiBoZWlnaHQ9IjE1IiBmaWxsPSIjMDA5QzNBIi8+Cjxwb2x5Z29uIHBvaW50cz0iMTAuNSwyLjUgMTkuNSw3LjUgMTAuNSwxMi41IDEuNSw3LjUiIGZpbGw9IiNGRkRGMDAiLz4KPGNpcmNsZSBjeD0iMTAuNSIgY3k9IjcuNSIgcj0iMy41IiBmaWxsPSIjMDAyNzc2Ii8+Cjx0ZXh0IHg9IjEwLjUiIHk9IjkiIHRleHQtYW5jaG9yPSJtaWRkbGUiIGZpbGw9IndoaXRlIiBmb250LXNpemU9IjIuNSIgZm9udC1mYW1pbHk9IkFyaWFsLCBzYW5zLXNlcmlmIiBmb250LXdlaWdodD0iNjAwIj5PUkRFTSBFIFBST0dSRVNTTzwvdGV4dD4KPC9zdmc+",
  },
  {
    code: "EN",
    name: "English",
    flag: "img:data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjEiIGhlaWdodD0iMTUiIHZpZXdCb3g9IjAgMCAyMSAxNSIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHJlY3Qgd2lkdGg9IjIxIiBoZWlnaHQ9IjE1IiBmaWxsPSIjMDEyMzY5Ii8+CjxyZWN0IHk9IjEiIHdpZHRoPSIyMSIgaGVpZ2h0PSIyIiBmaWxsPSJ3aGl0ZSIvPgo8cmVjdCB5PSI1IiB3aWR0aD0iMjEiIGhlaWdodD0iMSIgZmlsbD0iI0NFMTEyNiIvPgo8cmVjdCB5PSI4IiB3aWR0aD0iMjEiIGhlaWdodD0iMSIgZmlsbD0id2hpdGUiLz4KPHJlY3QgeT0iMTIiIHdpZHRoPSIyMSIgaGVpZ2h0PSIyIiBmaWxsPSJ3aGl0ZSIvPgo8cmVjdCB5PSIxIiB3aWR0aD0iOSIgaGVpZ2h0PSI4IiBmaWxsPSIjMDEyMzY5Ii8+Cjwvc3ZnPgo=",
  },
  {
    code: "FR",
    name: "Français",
    flag: "img:data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjEiIGhlaWdodD0iMTUiIHZpZXdCb3g9IjAgMCAyMSAxNSIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHJlY3Qgd2lkdGg9IjciIGhlaWdodD0iMTUiIGZpbGw9IiMwMDIzOTUiLz4KPHJlY3QgeD0iNyIgd2lkdGg9IjciIGhlaWdodD0iMTUiIGZpbGw9IndoaXRlIi8+CjxyZWN0IHg9IjE0IiB3aWR0aD0iNyIgaGVpZ2h0PSIxNSIgZmlsbD0iI0VEMjkzOSIvPgo8L3N2Zz4K",
  },
  {
    code: "ES",
    name: "Español",
    flag: "img:data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjEiIGhlaWdodD0iMTUiIHZpZXdCb3g9IjAgMCAyMSAxNSIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHJlY3Qgd2lkdGg9IjIxIiBoZWlnaHQ9IjE1IiBmaWxsPSIjQUEyNTFBIi8+CjxyZWN0IHk9IjMuNzUiIHdpZHRoPSIyMSIgaGVpZ2h0PSI3LjUiIGZpbGw9IiNGRkRCMDAiLz4KPC9zdmc+Cg==",
  },
]);

const currentLanguage = ref(languages.value[0]);

// Traduções
const translationsData = {
  ptBr: {
    welcome: "Bem-vindo ao {{appName}}",
    loginPrompt: "Acesse o sistema de gestão do seu negócio",
    companyTagline: "Sistema de gestão para o seu negócio",
    username: "Usuário",
    password: "Senha",
    signIn: "Entrar",
    or: "ou",
    continueWithGoogle: "Continuar com Google",
    authError: "Erro de autenticação",
    usernameRequired: "Usuário é obrigatório",
    passwordRequired: "Senha é obrigatória",
    clienteNotAllowed: "Clientes não podem acessar o sistema de gestão. Use o aplicativo móvel.",
  },
  EN: {
    welcome: "Welcome to {{appName}}",
    loginPrompt: "Access your business management system",
    companyTagline: "Business management system",
    username: "Username",
    password: "Password",
    signIn: "Sign In",
    or: "or",
    continueWithGoogle: "Continue with Google",
    authError: "Authentication error",
    usernameRequired: "Username is required",
    passwordRequired: "Password is required",
    clienteNotAllowed: "Customers cannot access the management system. Please use the mobile app.",
  },
  FR: {
    welcome: "Bienvenue à {{appName}}",
    loginPrompt: "Accédez au système de gestion de votre entreprise",
    companyTagline: "Système de gestion pour votre entreprise",
    username: "Nom d'utilisateur",
    password: "Mot de passe",
    signIn: "Se connecter",
    or: "ou",
    continueWithGoogle: "Continuer avec Google",
    authError: "Erreur d'authentification",
    usernameRequired: "Le nom d'utilisateur est obligatoire",
    passwordRequired: "Le mot de passe est obligatoire",
    clienteNotAllowed: "Les clients ne peuvent pas accéder au système de gestion. Veuillez utiliser l'application mobile.",
  },
  ES: {
    welcome: "Bienvenido a {{appName}}",
    loginPrompt: "Acceda al sistema de gestión de su negocio",
    companyTagline: "Sistema de gestión para su negocio",
    username: "Usuario",
    password: "Contraseña",
    signIn: "Iniciar Sesión",
    or: "o",
    continueWithGoogle: "Continuar con Google",
    authError: "Error de autenticación",
    usernameRequired: "El usuario es obligatorio",
    passwordRequired: "La contraseña es obligatoria",
    clienteNotAllowed: "Los clientes no pueden acceder al sistema de gestión. Por favor use la aplicación móvil.",
  },
};

const translations = computed(() => translationsData[currentLanguage.value.code]);
const welcomeText = computed(() =>
  translations.value.welcome.replace("{{appName}}", appName.value || "CaféTech")
);

const isAuthenticated = computed(() => userStore.isAuthenticated);

// Computed para mensagem de erro traduzida
const displayErrorMessage = computed(() => {
  if (userStore.authErrorMessage === "CLIENTE_NOT_ALLOWED") {
    return translations.value.clienteNotAllowed;
  }
  return userStore.authErrorMessage || translations.value.authError;
});

const signin = async () => {
  loading.value = true;
  try {
    const success = await userStore.login(email.value, password.value);
    if (success) {
      programStore.setProgramTitle("Dashboard");
      eventBus.emit("authenticated", true);
      router.push("/home");
    }
  } finally {
    loading.value = false;
  }
};

const loginWithGoogle = () => {
  window.location.href = `${baseApiUrl}/api/auth/oauth2/google/erp`;
};

const onEnter = () => {
  signin();
};

const changeLanguage = (language) => {
  currentLanguage.value = language;
  // Aqui você poderia também salvar a preferência no localStorage
  localStorage.setItem("preferred-language", language.code);
};

const loadPublicConfig = async () => {
  try {
    const cfg = await apiRequest("/api/public/config");
    if (cfg?.appName) {
      appName.value = cfg.appName;
    }
  } catch (error) {
    console.warn("Não foi possível carregar configuração pública:", error);
  }
};

// Função para carregar as versões do sistema
const loadSystemVersions = async () => {
  try {
    // Primeiro, tenta buscar as versões específicas dos componentes
    try {
      // Tenta buscar informações do cliente padrão para versões separadas
      const defaultClientResponse = await apiRequest("/api/docker/default-client");
      if (defaultClientResponse?.exists) {
        const clientInfo = await apiRequest(`/api/docker/clients/${defaultClientResponse.client}`);
        if (clientInfo) {
          systemVersions.value = {
            backend: clientInfo.backendVersion || null,
            frontend: clientInfo.frontendVersion || null,
          };
          return;
        }
      }
    } catch (dockerError) {
      console.debug("Informações Docker não disponíveis, tentando endpoint de versões:", dockerError);
    }

    // Fallback: usar endpoint de versões gerais
    const latestResponse = await apiRequest("/api/versions/latest");
    if (latestResponse) {
      systemVersions.value = {
        backend: latestResponse.version,
        frontend: latestResponse.version,
      };
    }
  } catch (error) {
    // Se não conseguir carregar, não mostra erro na tela de login
    console.warn("Não foi possível carregar as versões do sistema:", error);
  }
};

// Redirecionamento se já estiver autenticado
onMounted(() => {
  // Forçar refresh se vier de um redeploy (parâmetro na URL ou sessionStorage)
  const fromRedeploy = sessionStorage.getItem("from_redeploy");
  if (fromRedeploy) {
    sessionStorage.removeItem("from_redeploy");
    // Forçar refresh completo para garantir que nenhum cache antigo permanece
    if (!sessionStorage.getItem("refresh_forced")) {
      sessionStorage.setItem("refresh_forced", "true");
      window.location.reload(true); // força refresh completo
      return;
    } else {
      sessionStorage.removeItem("refresh_forced");
    }
  }

  // Carregar idioma salvo
  const savedLanguage = localStorage.getItem("preferred-language");
  if (savedLanguage) {
    const lang = languages.value.find((l) => l.code === savedLanguage);
    if (lang) {
      currentLanguage.value = lang;
    }
  }

  loadPublicConfig();

  if (isAuthenticated.value) {
    router.push("/home");
  } else {
    // Carregar as versões do sistema na tela de login
    loadSystemVersions();
  }
});

// Monitorar mudanças na autenticação para redirecionamento
watch(isAuthenticated, (newValue) => {
  if (newValue) {
    router.push("/home");
  }
});
</script>

<style scoped>
/* Layout principal */
.login-page {
  position: relative;
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f1ed 0%, #e8dfd6 50%, #d9cec2 100%);
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
  overflow: hidden;
}

.login-page::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image: radial-gradient(circle at 20% 50%, rgba(215, 184, 153, 0.08) 0%, transparent 50%),
    radial-gradient(circle at 80% 80%, rgba(198, 124, 72, 0.06) 0%, transparent 50%);
  pointer-events: none;
}

/* Seletor de idiomas */
.language-selector {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 10;
}

.language-btn {
  background: rgba(255, 255, 255, 0.9);
  color: #6B3E26;
  backdrop-filter: blur(15px);
  border-radius: 10px;
  min-width: 85px;
  border: 1px solid rgba(215, 184, 153, 0.3);
  box-shadow: 0 2px 8px rgba(107, 62, 38, 0.08);
  font-weight: 600;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.language-btn:hover {
  background: white;
  border-color: rgba(198, 124, 72, 0.4);
  box-shadow: 0 4px 12px rgba(107, 62, 38, 0.12);
  transform: translateY(-1px);
}

/* Container principal */
.login-container {
  width: 100%;
  max-width: 450px;
  display: flex;
  flex-direction: column;
  gap: 30px;
}

/* Seção do cabeçalho */
.header-section {
  text-align: center;
  margin-bottom: 20px;
  background: transparent;
}

.company-branding {
  color: #6B3E26;
  background: transparent;
}

.company-name {
  font-size: 48px;
  font-weight: 800;
  margin: 0;
  text-shadow: 0 2px 8px rgba(107, 62, 38, 0.12);
  letter-spacing: -1.5px;
  background: linear-gradient(135deg, #6B3E26 0%, #5a3320 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.company-tagline {
  font-size: 12px;
  font-weight: 500;
  margin: 12px 0 0 0;
  color: #8B7355;
  opacity: 0.9;
  letter-spacing: 1.2px;
  text-transform: uppercase;
}

/* Card de login */
.login-card {
  background: rgba(255, 255, 255, 0.98);
  backdrop-filter: blur(20px);
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 20px 60px rgba(107, 62, 38, 0.10), 0 8px 16px rgba(139, 115, 85, 0.06);
  overflow: hidden;
  position: relative;
}

.login-card::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, #6B3E26 0%, #C67C48 50%, #D7B899 100%);
}

.login-form-section {
  padding: 40px 32px;
}

/* Cabeçalho do formulário */
.form-header {
  text-align: center;
  margin-bottom: 32px;
}

.welcome-text {
  font-size: 32px;
  font-weight: 700;
  margin: 0 0 8px 0;
  color: #2d3748;
  background: linear-gradient(135deg, #6B3E26 0%, #C67C48 50%, #D7B899 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.login-prompt {
  font-size: 15px;
  color: #8B7355;
  margin: 0;
  font-weight: 400;
  letter-spacing: 0.3px;
}

/* Formulário */
.login-form {
  margin-bottom: 24px;
}

.input-field {
  margin-bottom: 20px;
}

.input-field :deep(.q-field__control) {
  height: 56px;
  border-radius: 14px;
  background: #faf8f6;
  border: 2px solid #e8dfd6;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.input-field :deep(.q-field__control:hover) {
  border-color: #d9cec2;
  background: #fcfbfa;
  box-shadow: 0 2px 8px rgba(107, 62, 38, 0.04);
}

.input-field :deep(.q-field--focused .q-field__control) {
  border-color: #C67C48;
  background: white;
  box-shadow: 0 4px 12px rgba(198, 124, 72, 0.12);
}

.input-field :deep(.q-field__label) {
  color: #4a5568;
  font-weight: 500;
}

/* Botão de login */
.login-button {
  height: 58px;
  font-size: 16px;
  font-weight: 700;
  text-transform: none;
  border-radius: 14px;
  background: linear-gradient(135deg, #6B3E26 0%, #C67C48 100%);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 6px 20px rgba(107, 62, 38, 0.25);
  letter-spacing: 0.3px;
}

.login-button:hover {
  transform: translateY(-3px) scale(1.01);
  box-shadow: 0 12px 35px rgba(107, 62, 38, 0.35);
  background: linear-gradient(135deg, #5a3320 0%, #b56d3d 100%);
}

.login-button:active {
  transform: translateY(-1px) scale(0.99);
  box-shadow: 0 4px 15px rgba(107, 62, 38, 0.25);
}

/* Divisor */
.divider {
  position: relative;
  text-align: center;
  margin: 28px 0;
}

.divider::before {
  content: "";
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent 0%, #d9cec2 50%, transparent 100%);
}

.divider-text {
  background: rgba(255, 255, 255, 0.98);
  color: #8B7355;
  padding: 0 20px;
  font-size: 13px;
  font-weight: 600;
  position: relative;
  text-transform: lowercase;
  letter-spacing: 0.5px;
}

/* Login social */
.social-login {
  margin-bottom: 24px;
}

.social-btn {
  height: 52px;
  border-radius: 12px;
  background: #faf8f6;
  color: #6B3E26;
  font-weight: 500;
  transition: all 0.3s ease;
}

.social-btn:hover {
  background: linear-gradient(135deg, #f5f1ed 0%, #e8dfd6 100%);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(107, 62, 38, 0.12);
}

/* Mensagem de erro */
.error-message {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  color: white;
  border-radius: 14px;
  font-weight: 500;
  border: none;
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);
  animation: slideIn 0.3s ease-out;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Informações de versão (canto inferior direito) */
.version-info {
  position: fixed;
  bottom: 20px;
  right: 20px;
  z-index: 10;
}

.version-container {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-end;
}

.version-item {
  display: flex;
  align-items: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(15px);
  padding: 6px 12px;
  border-radius: 20px;
  color: #6B3E26;
  font-size: 11px;
  font-weight: 600;
  border: 1px solid rgba(215, 184, 153, 0.3);
  box-shadow: 0 2px 8px rgba(107, 62, 38, 0.08);
}

.version-item span {
  font-family: "SF Mono", "Monaco", "Inconsolata", "Roboto Mono", monospace;
  font-weight: 600;
}

/* Responsive */
@media (max-width: 600px) {
  .login-page {
    padding: 16px;
  }

  .login-container {
    gap: 20px;
  }

  .company-name {
    font-size: 36px;
  }

  .company-tagline {
    font-size: 14px;
  }

  .login-form-section {
    padding: 32px 24px;
  }

  .welcome-text {
    font-size: 28px;
  }

  .language-selector {
    top: 16px;
    right: 16px;
  }

  .version-info {
    bottom: 16px;
    right: 16px;
  }

  .version-container {
    align-items: center;
  }

  .version-item {
    font-size: 10px;
    padding: 3px 8px;
  }
}

@media (max-width: 480px) {
  .login-container {
    max-width: 100%;
  }

  .company-name {
    font-size: 32px;
  }

  .login-form-section {
    padding: 28px 20px;
  }
}
</style>
