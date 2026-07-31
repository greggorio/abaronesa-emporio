<template>
  <div>
    <q-card class="q-mb-md">
      <q-card-section>
        <div class="text-h6">Operações em Massa</div>
      </q-card-section>
      <q-card-section>
        <div class="row q-col-gutter-md">
          <div class="col-md-6 col-sm-12">
            <q-card>
              <q-card-section>
                <div class="text-h6">Zerar Estoque</div>
                <div class="q-mt-sm">Define a quantidade de todos os produtos como zero. Use com cautela.</div>
              </q-card-section>
              <q-card-actions>
                <q-btn
                  color="negative"
                  label="Zerar Estoque"
                  @click="confirmAction(
                    'Zerar Estoque',
                    'Esta ação definirá o estoque de TODOS os produtos como zero. Deseja continuar?',
                    zerarEstoque
                  )"
                />
              </q-card-actions>
            </q-card>
          </div>
        </div>
      </q-card-section>
    </q-card>

    <!-- Modal de confirmação -->
    <q-dialog v-model="confirmDialog">
      <q-card style="min-width: 350px">
        <q-card-section class="row items-center">
          <div class="text-h6">{{ modalTitle }}</div>
          <q-space />
          <q-btn icon="close" flat round dense v-close-popup />
        </q-card-section>

        <q-card-section>
          {{ modalMessage }}
        </q-card-section>

        <q-card-actions align="right">
          <q-btn flat label="Cancelar" color="primary" v-close-popup />
          <q-btn flat label="Confirmar" color="negative" @click="executeAction" v-close-popup />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useQuasar } from "quasar";
import { useApiRequest } from "@/composables/useApiRequest";

const { apiRequest } = useApiRequest();
const $q = useQuasar();

// Estado
const confirmDialog = ref(false);
const modalTitle = ref("");
const modalMessage = ref("");
const currentAction = ref(() => {});

// Métodos
const confirmAction = (title, message, action) => {
  modalTitle.value = title;
  modalMessage.value = message;
  currentAction.value = action;
  confirmDialog.value = true;
};

const executeAction = () => {
  currentAction.value();
  confirmDialog.value = false;
};

// Ação específica para zerar estoque
const zerarEstoque = async () => {
  try {
    console.log("Executando: Zerar Estoque");
    const resp = await apiRequest("/api/estoque/zerar-estoque");
    
    if (resp.success) {
      $q.notify({
        type: "positive",
        message: resp.message || "Estoque zerado com sucesso!",
        position: "top",
        timeout: 2000,
      });
    } else {
      $q.notify({
        type: "negative",
        message: resp.message || "Erro ao zerar estoque",
        position: "top",
        timeout: 2000,
      });
    }
  } catch (error) {
    console.error("Erro ao zerar estoque:", error);
    $q.notify({
      type: "negative",
      message: error.message || "Erro ao zerar estoque",
      position: "top",
      timeout: 2000,
    });
  }
};
</script>