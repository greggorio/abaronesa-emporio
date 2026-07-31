<template>
  <div class="app-identity">
    <q-card flat bordered>
      <q-card-section class="q-pb-none">
        <div class="text-h6 text-primary">Identidade da Aplicação</div>
        <div class="text-body2 text-grey-7 q-mt-xs">
          Defina o nome exibido e o segmento (ex.: bar, restaurante, cafeteria).
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <q-form @submit.prevent="save" class="q-gutter-md">
          <q-input
            v-model="form.appName"
            label="Nome da aplicação"
            outlined
            :loading="loading"
            :rules="[(val) => !!val || 'Informe o nome da aplicação']"
          />

          <q-select
            v-model="form.segmento"
            :options="segmentOptions"
            label="Segmento"
            outlined
            use-input
            fill-input
            hide-dropdown-icon
            new-value-mode="add"
            :loading="loading"
            @new-value="onNewSegment"
          >
            <template #append>
              <q-icon name="business" color="primary" />
            </template>
          </q-select>

          <q-banner dense class="bg-grey-1 text-grey-8">
            <div class="text-body2">Pré-visualização</div>
            <div class="text-subtitle1 text-primary q-mt-xs">
              Bem-vindo ao {{ form.appName || "sua aplicação" }}
            </div>
            <div class="text-caption">
              Acesse o sistema de gestão do seu {{ form.segmento || "negócio" }}
            </div>
          </q-banner>

          <div class="row q-col-gutter-md">
            <div class="col-auto">
              <q-btn color="primary" label="Salvar" type="submit" :loading="saving" :disable="!isDirty" />
            </div>
            <div class="col-auto">
              <q-btn flat color="secondary" label="Recarregar" @click="load" :loading="loading" />
            </div>
          </div>
        </q-form>
      </q-card-section>
    </q-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";

const { apiRequest } = useApiRequest();
const $q = useQuasar();

const form = ref({
  appName: "",
  segmento: "",
});

const original = ref({
  appName: "",
  segmento: "",
});

const loading = ref(false);
const saving = ref(false);

const segmentOptions = ref(["cafeteria", "bar", "restaurante", "food service"]);

const isDirty = computed(() => {
  return (
    form.value.appName !== original.value.appName ||
    form.value.segmento !== original.value.segmento
  );
});

const onNewSegment = (val, done) => {
  if (val && !segmentOptions.value.includes(val)) {
    segmentOptions.value = [...segmentOptions.value, val];
  }
  done(val, "add");
};

const loadConfigValue = async (key, fallback = "") => {
  try {
    const value = await apiRequest(`/api/configs/config/${key}`);
    return value ?? fallback;
  } catch (error) {
    console.warn(`Não foi possível carregar a config ${key}:`, error);
    return fallback;
  }
};

const load = async () => {
  loading.value = true;
  const appName = await loadConfigValue("app_name", "CafeTech");
  const segmento = await loadConfigValue("segmento", "cafeteria");

  form.value = { appName, segmento };
  original.value = { appName, segmento };
  loading.value = false;
};

const save = async () => {
  saving.value = true;
  try {
    await apiRequest(`/api/configs/config/app_name`, "PUT", { valor: form.value.appName });
    await apiRequest(`/api/configs/config/segmento`, "PUT", { valor: form.value.segmento });
    original.value = { ...form.value };
    $q.notify({
      type: "positive",
      message: "Configurações salvas com sucesso",
      position: "top",
    });
  } catch (error) {
    console.error("Erro ao salvar identidade da aplicação:", error);
    $q.notify({
      type: "negative",
      message: "Erro ao salvar configurações",
      caption: error?.message || "",
      position: "top",
    });
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  load();
});
</script>

<style scoped>
.app-identity {
  max-width: 900px;
  margin: 0 auto;
}
</style>
