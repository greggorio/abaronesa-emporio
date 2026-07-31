<template>
  <q-dialog v-model="showDialog" persistent>
    <q-card style="min-width: 600px">
      <q-btn
        flat
        round
        dense
        color="white"
        icon="close"
        class="absolute-top-right q-ma-sm"
        style="cursor: pointer; z-index: 1000"
        @click="closeDialog"
      />

      <q-card-section class="bg-primary text-white">
        <div class="text-h6">Meu Perfil</div>
      </q-card-section>

      <q-card-section>
        <div class="row q-col-gutter-md">
          <!-- Avatar -->
          <div class="col-12 text-center">
            <q-avatar size="120px">
              <img :src="loadImg()" @error="handleImageError" />
            </q-avatar>
            <!-- <div class="q-mt-md">
              <q-btn flat color="primary" label="Alterar Foto" @click="uploadAvatar" />
            </div> -->
          </div>

          <!-- Dados básicos -->
          <div class="col-12 col-md-6">
            <q-input
              v-model="form.nome"
              filled
              label="Nome completo"
              :rules="[(val) => !!val || 'Nome é obrigatório']"
            />
          </div>

          <div class="col-12 col-md-6">
            <q-input
              v-model="form.email"
              filled
              label="E-mail"
              type="email"
              disable
              hint="E-mail não pode ser alterado"
            />
          </div>

          <div class="col-12 col-md-6">
            <q-input
              v-model="form.telefone"
              filled
              label="Telefone"
              mask="(##) #####-####"
              unmasked-value
            />
          </div>

          <div class="col-12 col-md-6">
            <q-input
              v-model="form.roles"
              filled
              label="Perfil"
              disable
              hint="Perfil de acesso"
            />
          </div>
        </div>
      </q-card-section>

      <q-card-actions align="right">
        <q-btn flat label="Cancelar" color="secondary" @click="closeDialog" />
        <q-btn flat label="Salvar" color="primary" :loading="loading" @click="saveProfile" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { ref, watch, computed } from "vue";
import { useUserStore } from "src/stores/userStore";
import { useApiRequest } from "@/composables/useApiRequest";
import { useQuasar } from "quasar";
import personImage from "@/assets/person.png";

const { apiRequest } = useApiRequest();
const $q = useQuasar();
const userStore = useUserStore();

const props = defineProps({
  modelValue: Boolean,
});

const emit = defineEmits(["update:modelValue"]);

const showDialog = ref(false);
const loading = ref(false);

const form = ref({
  nome: "",
  email: "",
  telefone: "",
  roles: "",
});

// Watch para sincronizar com v-model
watch(
  () => props.modelValue,
  (newVal) => {
    showDialog.value = newVal;
    if (newVal) {
      loadUserData();
    }
  }
);

watch(showDialog, (newVal) => {
  emit("update:modelValue", newVal);
});

const loadUserData = () => {
  const user = userStore.currentUser;
  if (user) {
    form.value = {
      nome: user.nome || "",
      email: user.email || "",
      telefone: user.telefone || "",
      roles: formatRoles(user.roles),
    };
  }
};

const formatRoles = (roles) => {
  if (!roles || roles.length === 0) return "";

  const roleMap = {
    SYSTEM: "Sistema",
    ADMIN: "Administrador",
    FUNCIONARIO: "Funcionário",
    CLIENTE: "Cliente",
  };

  return roles.map(role => roleMap[role] || role).join(", ");
};

const loadImg = () => {
  return userStore.currentUser?.fotoPerfil || personImage;
};

const handleImageError = (event) => {
  event.target.src = personImage;
};

const closeDialog = () => {
  showDialog.value = false;
};

const saveProfile = async () => {
  loading.value = true;

  try {
    const updateData = {
      nome: form.value.nome,
      telefone: form.value.telefone,
    };

    await apiRequest("/api/profile", "PUT", updateData);

    // Atualizar dados no store
    await userStore.fetchUserProfile();

    $q.notify({
      type: "positive",
      message: "Perfil atualizado com sucesso!",
      position: "top",
    });

    closeDialog();
  } catch (error) {
    console.error("Erro ao atualizar perfil:", error);
    $q.notify({
      type: "negative",
      message: error.message || "Erro ao atualizar perfil",
      position: "top",
    });
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
</style>
