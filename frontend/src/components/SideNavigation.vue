<template>
  <q-drawer
    :model-value="layoutStore.leftDrawerOpen"
    @update:model-value="layoutStore.setDrawerOpen($event)"
    show-if-above
    bordered
    style="overflow: hidden; color: #a3aed0"
    class="bg-white fit"
    :width="layoutStore.isDrawerCollapsed ? 64 : 250"
    behavior="default"
    :breakpoint="Number(600)"
    transition-show="fade"
    transition-hide="fade"
  >
    <!-- Logo do sistema -->
    <div
      class="q-pa-md flex items-center"
      :class="{
        'justify-center': !layoutStore.isDrawerCollapsed,
        'justify-start': layoutStore.isDrawerCollapsed,
      }"
      style="position: relative"
    >
      <div class="flex items-center" @click="goHome" style="cursor: pointer">
        <span v-if="layoutStore.isDrawerCollapsed" class="text-h5 text-primary">
          <strong>{{ appNameInitials }}</strong>
        </span>
        <span v-else class="text-h4 text-primary">
          <strong>{{ appName }}</strong>
        </span>
      </div>
    </div>

    <!-- Botão para expandir/colapsar menu -->
    <q-btn
      flat
      round
      dense
      color="info"
      :icon="layoutStore.isDrawerCollapsed ? 'fa fa-angle-right' : 'fa fa-angle-left'"
      @click="layoutStore.toggleCollapsed"
      aria-label="Toggle Drawer"
      @keyup.enter="layoutStore.toggleCollapsed"
      class="collapse-button"
    />

    <!-- Atalho para Seletor de Programas -->
    <div class="launcher-container q-py-sm">
      <q-btn flat no-caps class="launcher-btn" @click="showProgramSelector = true">
        <div class="launcher-content">
          <q-icon name="apps" size="20px" color="grey-7" />
          <span v-if="!layoutStore.isDrawerCollapsed" class="launcher-text">Programas</span>
        </div>
      </q-btn>
    </div>

    <!-- Menu de navegação -->
    <q-scroll-area
      :thumb-style="thumbStyle"
      :bar-style="barStyle"
      class="menu-scroll-area"
      :style="`height: calc(100vh - ${layoutStore.isDrawerCollapsed ? 130 : 150}px);`"
    >
      <q-list class="q-pb-xl drawer-menu-list">
        <div v-for="(group, index) in menuData" :key="index" class="q-pa-none q-ma-none menu-group">
          <q-item-label class="text-primary text-weight-medium" header v-if="!layoutStore.isDrawerCollapsed" dense>{{ group.title }}</q-item-label>
          <q-item
            v-for="(item, idx) in group.items"
            :key="`${index}-${idx}`"
            clickable
            class="menu-item"
            :class="{ 'collapsed-item': layoutStore.isDrawerCollapsed }"
            tag="a"
            @click="navigate(item.title, item.route)"
            dense
          >
            <q-item-section avatar style="height: 10px" :class="{ 'collapsed-avatar': layoutStore.isDrawerCollapsed }">
              <q-icon :class="item.ativo ? 'text-secondary' : 'text-info'" :name="item.icon" size="25px">
                <q-tooltip>{{ item.title }}</q-tooltip>
              </q-icon>
            </q-item-section>

            <q-item-section v-if="!layoutStore.isDrawerCollapsed" dense>
              <span :class="item.ativo ? 'text-primary text-weight-medium' : 'text-info'">{{ item.title }}</span>
            </q-item-section>
          </q-item>
        </div>
      </q-list>
    </q-scroll-area>

    <!-- Modal de seleção de programa -->
    <ProgramSelector v-model="showProgramSelector" :program-groups="menuData" />
  </q-drawer>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useProgramStore } from "@/stores/programStore";
import { useApiRequest } from "@/composables/useApiRequest";
import { useLayoutStore } from "@/stores/layoutStore";
import { useUserStore } from "@/stores/userStore";
import ProgramSelector from "./ProgramSelector.vue";

const programStore = useProgramStore();
const layoutStore = useLayoutStore();
const userStore = useUserStore();
const router = useRouter();
const { apiRequest } = useApiRequest();
const appName = ref("App");

const menuData = ref([]);
const showProgramSelector = ref(false);

// Estilos para a scrollbar
const thumbStyle = ref({
  right: "4px",
  width: "5px",
  backgroundColor: "#C67C48",
  opacity: 0.75,
});

const barStyle = ref({
  right: "2px",
  width: "6px",
  backgroundColor: "#e5e5e5",
  opacity: 0.8,
});

const appNameInitials = computed(() => {
  if (!appName.value) return "APP";
  const parts = appName.value.trim().split(/\s+/);
  if (parts.length === 1) {
    return (parts[0].slice(0, 2) || "AP").toUpperCase();
  }
  return (parts[0][0] + parts[1][0]).toUpperCase();
});

const loadAppName = async () => {
  try {
    const cfg = await apiRequest("/api/public/config");
    if (cfg?.appName) {
      appName.value = cfg.appName;
    }
  } catch (error) {
    console.warn("Não foi possível carregar appName público:", error);
  }
};

const loadMenu = async () => {
  console.log("Carregando menu...");
  try {
    // Verificar se o usuário tem permissão para acessar menu
    if (!userStore.canAccessMenu) {
      console.warn("Usuário sem permissão para acessar menu");
      return;
    }

    const groupId = userStore.getUserGroup;

    // Log para debug
    console.log("Carregando menu para:", {
      hasSystemRole: userStore.hasSystemRole,
      groupId: groupId,
      canAccessMenu: userStore.canAccessMenu,
      endpoint: `/api/menu/${groupId}`,
    });

    // Mesma URL para todos os usuários - groupId pode ser null ou um número
    const apiURL = `/api/menu/${groupId}`;
    const response = await apiRequest(apiURL);
    if (response) {
      menuData.value = response;
      // Injeta secao dedicada do modulo de validade
      const validadeGroup = {
        title: "Validade",
        items: [
          {
            title: "Operação",
            route: "validade-operacao",
            icon: "inventory_2",
            ativo: false,
          },
          {
            title: "Painel de Validade",
            route: "validade-painel",
            icon: "analytics",
            ativo: false,
          },
          {
            title: "Histórico",
            route: "validade-historico",
            icon: "history",
            ativo: false,
          },
        ],
      };
      menuData.value = [...menuData.value, validadeGroup];
      console.log(`Menu carregado com sucesso para ${userStore.hasSystemRole ? "usuário SYSTEM" : `grupo ${groupId}`}:`, response);
    } else {
      console.warn("Resposta vazia do servidor para menu");
    }
  } catch (error) {
    console.error("Erro ao carregar os grupos do menu:", error);
  }
};

onMounted(() => {
  loadAppName();
  loadMenu();
});

const goHome = () => {
  router.push("/home");
};

const navigate = (program_title, route) => {
  programStore.setProgramTitle(program_title);

  menuData.value.forEach((group) => {
    group.items.forEach((item) => {
      item.ativo = false; // Desativar todos os itens
    });
  });

  // Encontrar o item clicado e ativá-lo
  menuData.value.forEach((group) => {
    group.items.forEach((item) => {
      if (item.route === route) {
        item.ativo = true; // Ativar o item correspondente
      }
    });
  });

  // Roteamento específico para cada tipo de programa
  if (route === "home") {
    router.push("/home");
  } else if (route === "configuracoes") {
    router.push("/configuracoes");
  } else if (route === "agenda-execucao") {
    router.push("/agenda-execucao");
  } else if (route === "pedidos-compra") {
    router.push("/pedidos-compra");
  } else if (route === "producao") {
    router.push("/producao");
  } else if (route === "validade-operacao") {
    router.push("/validade/operacao");
  } else if (route === "validade-painel") {
    router.push("/validade/painel");
  } else if (route === "validade-historico") {
    router.push("/validade/historico");
  } else {
    // Todas as outras rotas vão para container com query
    router.push({ path: "/container", query: { programa: route } });
  }
};
</script>

<style lang="scss" scoped>
// Garantir que não há overflow quando o menu está colapsado
.drawer-menu-list {
  width: 100%;
  min-width: 0;
}

.menu-group {
  width: 100%;
  min-width: 0;
}

.menu-item {
  padding: 8px 16px;
  transition: all 0.3s;
  width: 100%;
  min-width: 0;

  &.collapsed-item {
    padding: 8px 4px;
    justify-content: center;
  }
}

.menu-item:hover {
  background-color: var(--q-color-primary-light);
  color: var(--q-color-primary);
}

.collapsed-avatar {
  min-width: auto;
  padding: 0;
  margin: 0 auto;
}

.collapse-button {
  position: absolute;
  top: 3.8%;
  right: -14px;
  transform: translateY(-50%);
  z-index: 1000;
  background-color: #f5f1ed;
  border: 1px solid #ccc;
  width: 28px;
  height: 28px;
  min-width: 28px;
  min-height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;

  :deep(.q-btn__content) {
    display: flex;
    align-items: center;
    justify-content: center;
  }

  :deep(i) {
    margin: 0;
    line-height: 1;
    font-size: 0.65em;
  }
}

// Nova classe para a área de scroll
.menu-scroll-area {
  overflow-x: hidden;

  // Garante que não há scroll horizontal
  :deep(.scroll) {
    padding-bottom: 20px;
    overflow-x: hidden !important;
    width: 100% !important;
  }

  // Remove qualquer estilo que possa causar overflow
  :deep(.q-item) {
    min-width: 0;
  }

  :deep(.q-item__section--avatar) {
    min-width: 0;
  }
}

// Estilo para o botão de iniciar programas
.launcher-container {
  padding: 0 8px;
  margin-bottom: 8px;
  width: 100%;
  min-width: 0;
}

.launcher-btn {
  width: 100%;
  min-width: 0;
  border-radius: 8px;
  background: linear-gradient(145deg, #f6f7fa, #ffffff);
  box-shadow: 2px 2px 5px rgba(0, 0, 0, 0.03), -2px -2px 5px rgba(255, 255, 255, 0.8);
  transition: all 0.3s ease;
  padding: 8px;

  &:hover {
    box-shadow: 1px 1px 3px rgba(0, 0, 0, 0.05), -1px -1px 3px rgba(255, 255, 255, 0.9);
  }

  &:active {
    box-shadow: inset 2px 2px 5px rgba(0, 0, 0, 0.05), inset -2px -2px 5px rgba(255, 255, 255, 0.8);
  }

  .launcher-content {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    min-width: 0;

    .launcher-text {
      margin-left: 10px;
      font-size: 0.9rem;
      color: #555;
      font-weight: 500;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }
}
</style>
