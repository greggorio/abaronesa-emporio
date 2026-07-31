<template>
  <q-header class="text-grey" style="z-index: 200; background-color: #f5f1ed">
    <q-icon
      size="20px"
      v-if="!hideToggle"
      name="menu"
      class="text-secondary self-start cursor-pointer"
      aria-label="Menu"
      @click="layoutStore.toggleDrawer"
      style="position: absolute; margin-left: 5px"
    />
    <div class="q-pa-md row" style="margin-left: 15px; margin-top: -10px" v-if="userStore.isAuthenticated">
      <div class="col-lg-5 col-md-5 col-sm-12 col-xs-12">
        <!-- Breadcrumbs -->
        <div>
          <q-breadcrumbs class="text-caption" separator="/">
            <q-breadcrumbs-el
              v-for="(item, index) in breadcrumbs"
              :key="index"
              :icon="item.icon"
              :label="item.label"
              :class="item.class"
              :style="item.class !== 'secondary' ? 'cursor: pointer' : ''"
              @click="goTo(item.to)"
            />
          </q-breadcrumbs>
        </div>

        <!-- Ícone e Título -->
        <div class="header-title-container">
          <q-icon v-if="headerIcon" :name="headerIcon" size="28px" color="info" class="header-icon" />
          <span class="text-primary text-h4 text-weight-medium">
            {{ title }}
          </span>
        </div>
      </div>

      <!-- Área de controles do usuário -->
      <div
        class="col-lg-7 col-md-7 col-sm-12 col-xs-12 row items-center"
        style="
          background-color: #ffffff;
          border-radius: 50px;
          height: 50px;
          padding: 0 10px;
          margin-top: 5px;
          width: fit-content;
          display: inline-flex;
          margin-left: auto;
        "
      >
        <!-- AI Assistant -->
        <div class="col-6">
          <AICommandInput @command-processed="handleAICommand" @error="handleAIError" />
        </div>

        <!-- Coluna para Notificações (futuro) -->
        <div class="col-1 q-px-sm items-center justify-center">
          <q-btn round dense flat color="grey-6" icon="o_notifications" class="col-1" disable>
            <q-tooltip>Notificações (em breve)</q-tooltip>
          </q-btn>
        </div>

        <!-- Coluna para Tarefas -->
        <div class="col-1 q-px-sm items-center justify-center">
          <q-btn round dense flat color="grey-6" icon="format_list_bulleted" class="col-1" @click="goToTasks">
            <q-tooltip>Tarefas</q-tooltip>
          </q-btn>
        </div>

        <!-- Coluna para o Menu de Usuário -->
        <div class="col-4">
          <q-btn-dropdown dropdown-icon="o_arrow_drop_down" flat dense no-caps no-wrap class="text-primary">
            <template v-slot:label>
              <q-avatar size="35px">
                <img :src="loadImg()" @error="handleImageError" />
              </q-avatar>
              <div class="column q-ml-sm text-left" v-if="$q.screen.gt.xs">
                <span>{{ userName }}</span>
                <span class="text-info" style="margin-top: -8px; font-size: 0.7em">
                  {{ userRole.length > 15 ? userRole.slice(0, 15) + "..." : userRole }}
                  <q-tooltip v-if="userRole.length > 15">
                    {{ userRole }}
                  </q-tooltip>
                </span>
              </div>
            </template>

            <div class="row no-wrap q-pa-md">
              <div class="column">
                <div class="text-h6 q-mb-md">Conta</div>

                <q-item clickable v-ripple @click="mostrarDialogPerfil = true">
                  <q-item-section avatar>
                    <q-icon color="secondary" name="person" />
                  </q-item-section>
                  <q-item-section>Meu Perfil</q-item-section>
                </q-item>

                <q-item clickable v-ripple @click="openChangePasswordDialog">
                  <q-item-section avatar>
                    <q-icon color="secondary" name="lock" />
                  </q-item-section>
                  <q-item-section>Alterar Senha</q-item-section>
                </q-item>
              </div>

              <q-separator vertical inset class="q-mx-lg" />

              <div class="column items-center">
                <q-avatar size="72px">
                  <img :src="loadImg()" @error="handleImageError" />
                </q-avatar>
                <div class="text-subtitle1 q-mt-md q-mb-xs">{{ userName }}</div>
                <q-btn color="primary" label="Deslogar" push size="sm" v-close-popup @click="logout" />
              </div>
            </div>
          </q-btn-dropdown>
        </div>
      </div>
    </div>

    <!-- AI Response Handler (invisível - gerencia diálogos) -->
    <AIResponseHandler ref="responseHandler" />

    <!-- AI Feedback (notificações visuais) -->
    <AIFeedback />

    <!-- Diálogo de alteração de senha -->
    <q-dialog v-model="passwordDialog" persistent>
      <q-card style="width: 400px">
        <q-btn
          flat
          round
          dense
          color="white"
          icon="close"
          class="absolute-top-right q-ma-sm"
          style="cursor: pointer; z-index: 1000"
          @click="passwordDialog = false"
        />
        <q-card-section class="bg-primary text-white">
          <div class="text-h6">Alterar Senha</div>
        </q-card-section>

        <q-card-section>
          <q-form @submit="submitPasswordChange" class="q-gutter-md">
            <q-input
              v-model="passwordForm.currentPassword"
              filled
              type="password"
              label="Senha atual"
              :rules="[(val) => !!val || 'Por favor, informe sua senha atual']"
            />

            <q-input
              v-model="passwordForm.newPassword"
              filled
              type="password"
              label="Nova senha"
              :rules="[(val) => !!val || 'Por favor, informe sua nova senha', (val) => val.length >= 6 || 'A senha deve ter pelo menos 6 caracteres']"
            />

            <q-input
              v-model="passwordForm.confirmPassword"
              filled
              type="password"
              label="Confirme a nova senha"
              :rules="[
                (val) => !!val || 'Por favor, confirme sua nova senha',
                (val) => val === passwordForm.newPassword || 'As senhas não coincidem',
              ]"
            />
          </q-form>
        </q-card-section>

        <q-card-actions align="right">
          <q-btn flat label="Cancelar" color="secondary" v-close-popup />
          <q-btn flat label="Salvar" color="primary" :loading="loading" @click="submitPasswordChange" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-header>
  <PerfilUsuario v-model="mostrarDialogPerfil" />
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from "vue";
import eventBus from "@/eventBus";
import { useProgramStore } from "src/stores/programStore";
import { useUserStore } from "src/stores/userStore";
import personImage from "@/assets/person.png";
import { useApiRequest } from "@/composables/useApiRequest";
import { useQuasar } from "quasar";
import PerfilUsuario from "./forms/PerfilUsuario.vue";
import { useRouter } from "vue-router";
import { useLayoutStore } from "@/stores/layoutStore";

// Importar componentes AI
import AICommandInput from "@/components/ai-assistant/AICommandInput.vue";
import AIResponseHandler from "@/components/ai-assistant/AIResponseHandler.vue";
import AIFeedback from "@/components/ai-assistant/AIFeedback.vue";

const { apiRequest } = useApiRequest();
const $q = useQuasar();
const router = useRouter();

defineEmits(["toggleDrawer"]);

// Props
const props = defineProps({
  hideToggle: {
    type: Boolean,
    default: false,
  },
});

const layoutStore = useLayoutStore();

// Refs para AI Assistant
const responseHandler = ref(null);

// Store access
const programStore = useProgramStore();
const userStore = useUserStore();

// Reactive state
const mostrarDialogPerfil = ref(false);

// Password dialog state
const passwordDialog = ref(false);
const loading = ref(false);
const passwordForm = ref({
  currentPassword: "",
  newPassword: "",
  confirmPassword: "",
});

// Computed properties
const userName = computed(() => userStore.currentUser?.name || "");
const userRole = computed(() => {
  const roles = userStore.currentUser?.roles || [];
  if (roles.length > 0) {
    // Return the first role directly without translation
    const role = roles[0];
    // Remove "ROLE_" prefix if present
    return role.replace('ROLE_', '');
  }
  return "";
});

// Breadcrumbs
const breadcrumbs = ref([
  { icon: "home", label: "Home", to: "/home" },
  { label: "Dashboard", class: "secondary" },
]);

const title = computed(() => programStore.programTitle);
const headerIcon = computed(() => programStore.programIcon);

function goTo(path) {
  if (path && breadcrumbs.value[breadcrumbs.value.length - 1].to !== path) {
    router.push(path);
  }
}

function goToTasks() {
  router.push('/tasks');
}

// Ouvir o eventBus para atualizações do cabeçalho
const updateHeader = (data) => {
  if (data.breadcrumbs) {
    breadcrumbs.value = data.breadcrumbs;
  }
  if (data.title) {
    programStore.setProgramTitle(data.title);
  }
  if (data.icon !== undefined) {
    programStore.setProgramIcon(data.icon);
  }
};

const loadImg = () => {
  return userStore.currentUser?.fotoPerfil || personImage;
};

// Método para tratar erro de carregamento de imagem
const handleImageError = (event) => {
  event.target.src = personImage;
};

function logout() {
  userStore.logout();
}

function openChangePasswordDialog() {
  // Limpar formulário
  passwordForm.value = {
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  };

  // Abrir diálogo
  passwordDialog.value = true;
}

async function submitPasswordChange() {
  // Verificar se a senha de confirmação é igual à nova senha
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    showNotification("negative", "As senhas não coincidem");
    return;
  }

  // Verificar se a nova senha tem pelo menos 6 caracteres
  if (passwordForm.value.newPassword.length < 6) {
    showNotification("negative", "A nova senha deve ter pelo menos 6 caracteres");
    return;
  }

  loading.value = true;

  try {
    // Preparar dados para a API
    const senhaData = {
      currentPassword: passwordForm.value.currentPassword,
      newPassword: passwordForm.value.newPassword,
    };

    // Fazer requisição para a API
    await apiRequest("/api/profile/password", "PUT", senhaData);

    showNotification("positive", "Senha alterada com sucesso!");
    passwordDialog.value = false;
  } catch (error) {
    console.error("Erro na requisição:", error);
    showNotification("negative", error.message || "Erro ao tentar alterar a senha. Por favor, tente novamente.");
  } finally {
    loading.value = false;
  }
}

function showNotification(type, message) {
  $q.notify({
    type: type,
    message: message,
    position: "top",
    timeout: 3000,
  });
}

function handleProgramTitle(titulo) {
  programStore.setProgramTitle(titulo);
}

// Handlers para AI Assistant
const handleAICommand = ({ command, response }) => {
  console.log("Comando AI processado:", command);
  console.log("Resposta:", response);
};

const handleAIError = (error) => {
  console.error("Erro no AI Assistant:", error);

  $q.notify({
    type: "negative",
    message: "Erro ao processar comando",
    caption: error.message || "Tente novamente",
    position: "top",
  });
};

eventBus.on("program_title", handleProgramTitle);

// Lifecycle hooks
onMounted(() => {
  // Escutar eventos
  eventBus.on("update:header", updateHeader);
});

onBeforeUnmount(() => {
  eventBus.off("update:header", updateHeader);
  eventBus.off("program_title");
});
</script>

<style scoped>
.q-toolbar-title {
  display: flex;
  align-items: center;
  font-weight: bold;
}

@media print {
  .q-toolbar-title {
    display: none;
  }
}

.header-title-container {
  display: flex;
  align-items: center;
  margin-top: 8px;
  gap: 8px;
}

.header-icon {
  margin-left: -4px;
}
</style>
