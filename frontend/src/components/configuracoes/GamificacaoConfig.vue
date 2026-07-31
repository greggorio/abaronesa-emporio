<template>
  <div class="gamificacao-config">
    <q-card class="config-card q-mb-lg" flat bordered>
      <q-card-section class="card-header">
        <div class="header-content">
          <div>
            <div class="text-h5 text-weight-medium text-grey-9">Gamificação</div>
            <div class="text-body2 text-grey-7 q-mt-xs">Regras de pontuação e recompensas do programa</div>
          </div>
          <q-icon name="o_emoji_events" size="48px" color="amber-5" class="header-icon" />
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="text-h6 text-forest-dark">Regras de Pontuação</div>
        <p class="text-body2 text-grey-7">
          Ajuste os parâmetros básicos que definem como os clientes acumulam pontos no programa.
        </p>

        <q-form @submit.prevent="handleSave">
          <div class="row q-col-gutter-md q-mt-md">
            <div class="col-xs-12 col-md-6">
              <q-toggle
                v-model="form.gamificacao_ativo"
                label="Gamificação ativa"
                color="primary"
                dense
                left-label
              />
              <p class="text-caption text-grey-6">
                Controle se o sistema deve calcular pontos automaticamente.
              </p>
            </div>

            <div class="col-xs-12 col-md-6">
              <q-input
                v-model.number="form.gamificacao_valor_para_1_ponto"
                type="number"
                label="Valor para 1 ponto (R$)"
                outlined
                dense
                step="0.10"
                min="0"
                suffix="reais"
                :loading="loading"
              />
              <p class="text-caption text-grey-6">
                Valor em reais que corresponde à geração de 1 ponto.
              </p>
            </div>

            <div class="col-xs-12 col-md-6">
              <q-select
                v-model="form.gamificacao_arredondamento"
                label="Arredondamento"
                options-map="label,value"
                :options="arredondamentos"
                outlined
                dense
              />
              <p class="text-caption text-grey-6">
                Como o total de pontos é arredondado após o cálculo.
              </p>
            </div>

            <div class="col-xs-12 col-md-6">
              <q-input
                v-model.number="form.gamificacao_expiracao_pontos_em_dias"
                type="number"
                label="Expiração dos pontos (dias)"
                outlined
                dense
                min="0"
                suffix="dias"
              />
              <p class="text-caption text-grey-6">
                Defina quantos dias os pontos permanecem válidos (0 = sem expiração).
              </p>
            </div>
          </div>

          <div class="row q-mt-lg">
            <div class="col-xs-12">
              <q-btn
                type="submit"
                label="Salvar regras"
                color="primary"
                :loading="saving"
                :disabled="saving"
              />
            </div>
          </div>
        </q-form>
      </q-card-section>
    </q-card>

    <!-- Seção de Recompensas -->
    <RecompensasConfig />
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";
import RecompensasConfig from "./RecompensasConfig.vue";

const { apiRequest } = useApiRequest();
const $q = useQuasar();

const form = ref({
  gamificacao_ativo: true,
  gamificacao_valor_para_1_ponto: 5.0,
  gamificacao_arredondamento: "FLOOR",
  gamificacao_expiracao_pontos_em_dias: 0,
});
const loading = ref(false);
const saving = ref(false);

const arredondamentos = [
  { label: "Floor", value: "FLOOR" },
  { label: "Ceil", value: "CEIL" },
  { label: "Round", value: "ROUND" },
];

const loadDirectConfigs = async () => {
  try {
    loading.value = true;
    const response = await apiRequest("/api/configs");
    if (!response) return;

    response
      .filter((config) => config.chave.startsWith("gamificacao_"))
      .forEach((config) => {
        form.value[config.chave] = parseValue(config.chave, config.valor);
      });
  } catch (error) {
    console.error("Erro ao carregar configurações de gamificação:", error);
    $q.notify({
      type: "negative",
      message: "Não foi possível carregar as configurações de gamificação.",
      position: "top-right",
    });
  } finally {
    loading.value = false;
  }
};

const parseValue = (key, valor) => {
  if (valor === null || valor === undefined) {
    return form.value[key];
  }
  switch (key) {
    case "gamificacao_ativo":
      return valor === "true";
    case "gamificacao_valor_para_1_ponto":
      return Number.isFinite(Number(valor)) ? Number(valor) : form.value[key];
    case "gamificacao_arredondamento":
      return valor || form.value[key];
    case "gamificacao_expiracao_pontos_em_dias":
      return Number.isFinite(Number(valor)) ? Number(valor) : form.value[key];
    default:
      return valor;
  }
};

const handleSave = async () => {
  saving.value = true;
  try {
    await Promise.all([
      updateConfigValue("gamificacao_ativo", form.value.gamificacao_ativo ? "true" : "false"),
      updateConfigValue(
        "gamificacao_valor_para_1_ponto",
        Number(form.value.gamificacao_valor_para_1_ponto || 0).toFixed(2)
      ),
      updateConfigValue("gamificacao_arredondamento", form.value.gamificacao_arredondamento || "FLOOR"),
      updateConfigValue(
        "gamificacao_expiracao_pontos_em_dias",
        String(Math.max(0, form.value.gamificacao_expiracao_pontos_em_dias || 0))
      ),
    ]);
    $q.notify({
      type: "positive",
      message: "Configurações de gamificação atualizadas com sucesso!",
      position: "top-right",
      timeout: 1500,
    });
    await loadDirectConfigs();
  } catch (error) {
    console.error("Erro ao salvar gamificação:", error);
    $q.notify({
      type: "negative",
      message: "Não foi possível salvar os dados de gamificação.",
      position: "top-right",
    });
  } finally {
    saving.value = false;
  }
};

const updateConfigValue = async (key, valor) => {
  await apiRequest(`/api/configs/config/${key}`, "PUT", { valor });
};

onMounted(() => {
  loadDirectConfigs();
});
</script>

<style scoped lang="scss">
.gamificacao-config {
  margin: 0 auto;
  padding: 0 16px;
}

.config-card {
  border-radius: 12px;
  box-shadow: 0 1px 5px rgba(0, 0, 0, 0.08);
}

.card-header {
  background: linear-gradient(135deg, #f7f1ff 0%, #f1f5ff 100%);
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-icon {
  opacity: 0.85;
}
</style>
