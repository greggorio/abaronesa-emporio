<template>
  <div class="row q-col-gutter-md q-mb-md">
    <div class="col-12">
      <q-card class="bg-white shadow-1" style="border-radius: 12px; overflow: hidden">
        <!-- Detalhes do Inventário -->
        <q-card-section class="q-py-xs q-px-sm">
          <div class="row items-start">
            <div class="col-12 col-md-9">
              <div class="row q-col-gutter-xs">
                <div class="col-12 col-sm-6 col-md-4">
                  <q-item class="q-pa-none q-py-xs" dense>
                    <q-item-section avatar>
                      <q-icon name="event" color="secondary" size="xs" />
                    </q-item-section>
                    <q-item-section>
                      <q-item-label caption class="text-caption">Status</q-item-label>
                      <q-item-label class="text-caption">{{ inventarioLocal.status }}</q-item-label>
                    </q-item-section>
                  </q-item>
                </div>
                <div class="col-12 col-sm-6 col-md-4">
                  <q-item class="q-pa-none q-py-xs" dense>
                    <q-item-section avatar>
                      <q-icon name="event" color="secondary" size="xs" />
                    </q-item-section>
                    <q-item-section>
                      <q-item-label caption class="text-caption">Criado em</q-item-label>
                      <q-item-label class="text-caption">{{ inventarioLocal.data_criacao_formatada }}</q-item-label>
                    </q-item-section>
                  </q-item>
                </div>
                <div class="col-12 col-sm-6 col-md-4">
                  <q-item class="q-pa-none q-py-xs" dense>
                    <q-item-section avatar>
                      <q-icon name="person" color="secondary" size="xs" />
                    </q-item-section>
                    <q-item-section>
                      <q-item-label caption class="text-caption">Criado por</q-item-label>
                      <q-item-label class="text-caption">{{ inventarioLocal.nome_usuario_criacao }}</q-item-label>
                    </q-item-section>
                  </q-item>
                </div>
                <div class="col-12 col-sm-6 col-md-4">
                  <q-item class="q-pa-none q-py-xs" dense>
                    <q-item-section avatar>
                      <q-icon name="schedule" color="secondary" size="xs" />
                    </q-item-section>
                    <q-item-section>
                      <q-item-label caption class="text-caption">Agendado para</q-item-label>

                      <div v-if="!isEditing">
                        <q-item-label v-if="inventarioLocal.data_agendamento_formatada" class="text-caption">
                          {{ inventarioLocal.data_agendamento }}
                        </q-item-label>
                        <q-item-label v-else class="text-caption text-grey-6">não agendado</q-item-label>
                      </div>
                      <div v-else>
                        <q-input
                          v-model="editedForm.data_agendamento"
                          dense
                          outlined
                          placeholder="DD/MM/AAAA"
                          mask="##/##/####"
                          :rules="[(val) => !!val || 'Data de agendamento é obrigatória', validateAgendamentoDate]"
                          class="text-caption"
                          style="min-height: 32px"
                        >
                          <template v-slot:append>
                            <q-icon name="event" class="cursor-pointer" size="xs">
                              <q-popup-proxy cover transition-show="scale" transition-hide="scale">
                                <q-date
                                  v-model="editedForm.data_agendamento"
                                  mask="DD/MM/YYYY"
                                  today-btn
                                  minimal
                                  :options="dateOptions"
                                  @update:model-value="updateDisplayDate"
                                >
                                  <div class="row items-center justify-end">
                                    <q-btn v-close-popup label="Ok" color="primary" flat size="sm" />
                                  </div>
                                </q-date>
                              </q-popup-proxy>
                            </q-icon>
                          </template>
                        </q-input>
                      </div>
                    </q-item-section>
                  </q-item>
                </div>
                <div class="col-12 col-sm-6 col-md-4">
                  <q-item class="q-pa-none q-py-xs" dense>
                    <q-item-section avatar>
                      <q-icon name="pending_actions" color="secondary" size="xs" />
                    </q-item-section>
                    <q-item-section>
                      <q-item-label caption class="text-caption">Previsão de Finalização</q-item-label>
                      <div v-if="!isEditing">
                        <q-item-label v-if="inventarioLocal.data_previsao_finalizacao_formatada" class="text-caption">
                          {{ inventarioLocal.data_previsao_finalizacao_formatada }}
                        </q-item-label>
                        <q-item-label v-else class="text-caption text-grey-6">não informado</q-item-label>
                      </div>
                      <div v-else>
                        <q-input
                          v-model="editedForm.data_previsao_finalizacao_display"
                          dense
                          outlined
                          placeholder="DD/MM/AAAA"
                          mask="##/##/####"
                          :rules="[validatePrevisaoDate]"
                          class="text-caption"
                          style="min-height: 32px"
                        >
                          <template v-slot:append>
                            <q-icon name="event" class="cursor-pointer" size="xs">
                              <q-popup-proxy cover transition-show="scale" transition-hide="scale">
                                <q-date
                                  v-model="editedForm.data_previsao_finalizacao"
                                  mask="DD/MM/YYYY"
                                  today-btn
                                  minimal
                                  :options="dateOptions"
                                  @update:model-value="updateDisplayDatePrevisao"
                                >
                                  <div class="row items-center justify-end">
                                    <q-btn v-close-popup label="Ok" color="primary" flat size="sm" />
                                  </div>
                                </q-date>
                              </q-popup-proxy>
                            </q-icon>
                          </template>
                        </q-input>
                      </div>
                    </q-item-section>
                  </q-item>
                </div>
                <div class="col-12 col-sm-6 col-md-4">
                  <q-item class="q-pa-none q-py-xs" dense>
                    <q-item-section avatar>
                      <q-icon name="location_on" color="secondary" size="xs" />
                    </q-item-section>
                    <q-item-section>
                      <q-item-label caption class="text-caption">Local</q-item-label>
                      <div v-if="!isEditing">
                        <q-item-label v-if="inventarioLocal.nome_local" class="text-caption">
                          {{ inventarioLocal.nome_local }}
                        </q-item-label>
                        <q-item-label v-else class="text-caption text-grey-6">não informado</q-item-label>
                      </div>
                      <div v-else>
                        <q-input
                          v-model="editedForm.local"
                          dense
                          outlined
                          label="Local"
                          placeholder="Digite o local"
                          class="text-caption"
                          style="min-height: 32px"
                        />
                      </div>
                    </q-item-section>
                  </q-item>
                </div>
                <div class="col-12 col-sm-6 col-md-4">
                  <q-item class="q-pa-none q-py-xs" dense>
                    <q-item-section avatar>
                      <q-icon name="fact_check" color="secondary" size="xs" />
                    </q-item-section>
                    <q-item-section>
                      <q-item-label caption class="text-caption">Contagem atual</q-item-label>
                      <q-item-label class="text-caption">{{ inventarioLocal.estatisticas?.num_contagens || 0 }}</q-item-label>
                    </q-item-section>
                  </q-item>
                </div>
                <div class="col-12 col-sm-6 col-md-4">
                  <q-item class="q-pa-none q-py-xs" dense>
                    <q-item-section avatar>
                      <q-icon name="analytics" color="secondary" size="xs" />
                    </q-item-section>
                    <q-item-section>
                      <q-item-label caption class="text-caption">Status contagem</q-item-label>
                      <q-item-label class="text-caption">{{ inventarioLocal.contagens[0]?.status || "Não iniciada" }}</q-item-label>
                    </q-item-section>
                  </q-item>
                </div>
                <div class="col-12 col-sm-6 col-md-4">
                  <q-item class="q-pa-none q-py-xs" dense>
                    <q-item-section avatar>
                      <q-icon name="inventory_2" color="secondary" size="xs" />
                    </q-item-section>
                    <q-item-section>
                      <q-item-label caption class="text-caption">Tipo</q-item-label>
                      <q-item-label class="text-caption">{{ inventarioLocal.tipo }}</q-item-label>
                    </q-item-section>
                  </q-item>
                </div>
              </div>
            </div>
            <!-- Substitua esta parte dentro de <div class="col-12 col-md-3 ..."> -->
            <div class="col-12 col-md-3 q-py-xs">
              <div class="row items-center justify-around rounded-borders p-2">
                <!-- Bloco Progresso -->
                <div class="col-auto text-center">
                  <div class="text-caption text-weight-medium mb-1">Progresso</div>
                  <q-circular-progress
                    show-value
                    font-size="12px"
                    :value="inventarioLocal.estatisticas?.progresso || 0"
                    size="80px"
                    :thickness="0.2"
                    color="secondary"
                    track-color="grey-3"
                  >
                    {{ inventarioLocal.estatisticas?.progresso || 0 }}%
                  </q-circular-progress>
                </div>
                <!-- Bloco Duração -->
                <div
                  v-if="inventarioLocal.status === 'EM_CONTAGEM' || inventarioLocal.status === 'REVISAO'"
                  class="col-6 col-md-3 col-lg-3 text-center q-mt-xs"
                >
                  <div class="text-caption text-weight-medium">Duração</div>
                  <div class="duration-display q-ma-xs" :class="{ running: inventarioLocal.status !== 'CONCLUIDO' }">
                    <span class="text-weight-bold">{{ duracao }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </q-card-section>
        <!-- Mensagem de edição -->
        <div v-if="isEditing" class="text-caption text-center text-red q-py-sm">* Pressione ESC para cancelar edição</div>

        <!-- Barra de Ações -->
        <q-separator />
        <q-card-actions class="q-pa-md" v-if="inventarioLocal.status !== 'CANCELADO'">
          <div class="row full-width q-col-gutter-sm">
            <div v-if="buttonInventarioConfig" class="col-auto">
              <q-btn
                dense
                color="secondary"
                :icon="buttonInventarioConfig.label === 'Iniciar Inventário' ? 'play_arrow' : 'check'"
                :label="buttonInventarioConfig.label"
                @click="buttonInventarioConfig.label === 'Iniciar Inventário' ? iniciarInventario() : finalizarInventario()"
                :disable="buttonInventarioConfig.disable"
                :title="
                  buttonInventarioConfig.disable && inventarioLocal.status === 'AGENDADO' ? 'Inventário indisponível antes da data agendada' : ''
                "
              />
            </div>

            <div class="col-auto">
              <q-btn
                v-if="inventarioLocal.contagens[0]?.status == 'EM_ANDAMENTO' || inventarioLocal.status == 'REVISAO'"
                dense
                color="secondary"
                outline
                icon="done_all"
                :label="inventarioLocal.status == 'REVISAO' ? 'Nova contagem' : 'Encerrar Contagem'"
                @click="inventarioLocal.status == 'REVISAO' ? novaContagem() : finalizarContagem()"
              />
            </div>
            <div class="col-auto">
              <q-btn
                v-if="inventarioLocal.status == 'ABERTO' || inventarioLocal.status == 'AGENDADO'"
                dense
                color="secondary"
                outline
                :icon="isEditing ? 'save' : 'edit'"
                :label="isEditing ? 'Salvar' : 'Editar'"
                @click="isEditing ? salvarEdicao() : editarInventario()"
              />
            </div>
            <div class="col-auto">
              <q-btn
                v-if="inventarioLocal.status == 'ABERTO' || inventarioLocal.status == 'AGENDADO'"
                dense
                color="secondary"
                outline
                icon="cancel"
                label="Cancelar Inventário"
                @click="cancelarInventario"
              />
            </div>
            <div class="col-auto">
              <q-btn dense color="secondary" outline icon="file_download" label="Exportar" @click="exportarInventario" />
            </div>
          </div>
        </q-card-actions>
      </q-card>
    </div>
    <q-dialog v-model="showFinalizarDialog" persistent>
      <q-card style="min-width: 350px">
        <q-card-section>
          <div class="text-h6">Finalizar Contagem</div>
        </q-card-section>
        <q-card-section>
          <p>Tem certeza que deseja finalizar esta contagem?</p>
          <p class="text-caption">Esta ação não pode ser desfeita.</p>
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="Cancelar" color="grey" v-close-popup />
          <q-btn flat label="Finalizar" color="primary" @click="confirmarFinalizarContagem" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<script setup>
import { defineProps, defineEmits, ref, computed, onMounted, onBeforeUnmount, watch } from "vue";
import { useApiRequest } from "@/composables/useApiRequest";
import { useQuasar } from "quasar";
import eventBus from "@/eventBus";

const $q = useQuasar();
const { apiRequest } = useApiRequest();

const props = defineProps({
  inventario: {
    type: Object,
    required: true,
  },
});

const inventarioLocal = ref(null);

watch(
  () => props.inventario,
  (valor) => {
    if (valor) {
      inventarioLocal.value = { ...valor };
    }
  },
  { immediate: true, deep: true }
);

const emit = defineEmits(["update:inventario"]);

const showFinalizarDialog = ref(false);
const loading = ref(false);

const duracao = ref("00:00:00");
let intervalId = null;

// Calcula e formata a duração do inventário
const atualizarDuracao = () => {
  try {
    // Função auxiliar para converter data brasileira para Date
    const parsearDataBrasileira = (dataStr) => {
      if (!dataStr) return null;

      // Remove a parte dos segundos se houver (pega só até os minutos)
      const dataLimpa = dataStr.split(":").slice(0, 2).join(":");

      // Separa data e hora
      const [data, hora] = dataStr.split(" ");
      const [dia, mes, ano] = data.split("/");

      // Se houver hora, separa horas, minutos e segundos
      if (hora) {
        const [horas, minutos, segundos = "00"] = hora.split(":");
        return new Date(ano, mes - 1, dia, horas, minutos, segundos);
      }

      // Se não houver hora
      return new Date(ano, mes - 1, dia);
    };

    // Obtém a data de início corretamente
    const dataInicioStr = props.inventario.data_inicio || props.inventario.data_inicio_formatada;
    const dataInicio = parsearDataBrasileira(dataInicioStr);

    if (!dataInicio || isNaN(dataInicio.getTime())) {
      throw new Error("Data de início inválida");
    }

    // Data atual ou data de conclusão se estiver concluído
    let dataFim;
    if (props.inventario.status === "CONCLUIDO" && props.inventario.data_conclusao) {
      dataFim = parsearDataBrasileira(props.inventario.data_conclusao);
    } else {
      dataFim = new Date();
    }

    // Calcula a diferença em segundos
    const diferencaSegundos = Math.floor((dataFim - dataInicio) / 1000);

    // Evita valores negativos
    if (diferencaSegundos < 0) {
      duracao.value = "00:00:00";
      return;
    }

    // Formata para hh:mm:ss
    const horas = Math.floor(diferencaSegundos / 3600);
    const minutos = Math.floor((diferencaSegundos % 3600) / 60);
    const segundos = diferencaSegundos % 60;

    duracao.value = String(horas).padStart(2, "0") + ":" + String(minutos).padStart(2, "0") + ":" + String(segundos).padStart(2, "0");
  } catch (error) {
    console.error("Erro ao calcular duração:", error);
    duracao.value = "00:00:00";
  }
};

const buttonInventarioConfig = computed(() => {
  const status = inventarioLocal.value.status;
  const dataAgendamento = inventarioLocal.value.data_agendamento;
  const dataAtual = new Date();

  switch (status) {
    case "RASCUNHO":
      return {
        label: "Iniciar Inventário",
        disable: true,
      };
    case "AGENDADO":
      if (new Date(dataAgendamento) > dataAtual) {
        return {
          label: "Iniciar Inventário",
          disable: true,
        };
      } else {
        return {
          label: "Iniciar Inventário",
          disable: false,
        };
      }
    case "ABERTO":
    case "EM_CONTAGEM":
      return {
        label: "Finalizar Inventário",
        disable: true,
      };
    case "REVISAO":
      return {
        label: "Finalizar Inventário",
        disable: false,
      };
    case "CONCLUIDO":
    case "CANCELADO":
      return null; // Não mostra botão
    default:
      return {
        label: "",
        disable: true,
      };
  }
});

// Controle de edição
const isEditing = ref(false);

const finalizarContagem = () => {
  showFinalizarDialog.value = true;
};

// Confirmar finalização da contagem
const confirmarFinalizarContagem = async () => {
  try {
    loading.value = true;
    showFinalizarDialog.value = false;

    const url = `/api/inventarios/contagens/${props.inventario.id}/${props.inventario.contagens[0].id}/finalizar`;

    const response = await apiRequest(url, "POST");
    if (response) {
      $q.notify({
        color: "positive",
        message: "Contagem finalizada com sucesso!",
        icon: "check_circle",
      });

      emit("update:inventario", {
        ...response,
      });
    }
  } catch (error) {
    console.error("Erro ao finalizar contagem:", error);
  } finally {
    loading.value = false;
  }
};

const initEditForm = () => {
  // Função para converter data formatada DD/MM/YYYY para o formato interno YYYY/MM/DD
  const formatDateForInternal = (dateString) => {
    if (!dateString || typeof dateString !== "string") return "";

    try {
      // Extrair apenas a parte da data, ignorando a hora
      const datePart = dateString.split(" ")[0];

      // Converter de DD/MM/YYYY para YYYY/MM/DD
      const parts = datePart.split("/");
      if (parts.length !== 3) return "";

      return `${parts[0]}/${parts[1]}/${parts[2]}`;
    } catch (error) {
      console.error(`Erro ao formatar data ${dateString}:`, error);
      return "";
    }
  };

  // Inicializar o formulário com os valores atuais
  const agendamentoDate = formatDateForInternal(inventarioLocal.value.data_agendamento_formatada);
  const previsaoDate = formatDateForInternal(props.inventario.data_previsao_finalizacao_formatada);

  editedForm.value = {
    data_agendamento: agendamentoDate,
    data_agendamento_display: inventarioLocal.value.data_agendamento_formatada ? inventarioLocal.value.data_agendamento_formatada.split(" ")[0] : "",
    data_previsao_finalizacao: previsaoDate,
    data_previsao_finalizacao_display: inventarioLocal.value.data_previsao_finalizacao_formatada
      ? inventarioLocal.value.data_previsao_finalizacao_formatada.split(" ")[0]
      : "",
    local: inventarioLocal.value.nome_local || "",
  };
};

const editarInventario = () => {
  isEditing.value = true;

  initEditForm();

  // Adicionar listener para tecla Esc para cancelar edição
  window.addEventListener("keydown", handleEscKey);
};

const salvarEdicao = async () => {
  try {
    // Validação das datas
    const hoje = new Date();
    hoje.setHours(0, 0, 0, 0); // Zerar horas para comparar apenas datas

    let dataAgendamento = null;
    if (editedForm.value.data_agendamento) {
      const parts = editedForm.value.data_agendamento.split("/");
      // Reorganizar para YYYY-MM-DD (do formato DD/MM/YYYY)
      if (parts.length === 3) {
        dataAgendamento = `${parts[2]}-${parts[1]}-${parts[0]}T00:00:00`;
      }
    }

    let dataPrevisao = null;
    if (editedForm.value.data_previsao_finalizacao) {
      const parts = editedForm.value.data_previsao_finalizacao.split("/");
      // Reorganizar para YYYY-MM-DD (do formato DD/MM/YYYY)
      if (parts.length === 3) {
        dataPrevisao = `${parts[2]}-${parts[1]}-${parts[0]}T00:00:00`;
      }
    }

    // Validação das datas
    if (dataAgendamento) {
      const hoje = new Date();
      hoje.setHours(0, 0, 0, 0);
      const dataAgendamentoObj = new Date(dataAgendamento);

      if (dataAgendamentoObj < hoje) {
        $q.notify({
          color: "negative",
          message: "A data de agendamento não pode ser anterior à data atual",
          icon: "error",
        });
        return;
      }

      if (dataPrevisao) {
        const dataPrevisaoObj = new Date(dataPrevisao);
        if (dataPrevisaoObj < dataAgendamentoObj) {
          $q.notify({
            color: "negative",
            message: "A data prevista para finalização não pode ser anterior à data de agendamento",
            icon: "error",
          });
          return;
        }
      }
    }

    // Dados válidos, prosseguir com o salvamento
    const dadosAtualizados = {
      id: props.inventario.id,
      dataAgendamento: dataAgendamento,
      dataPrevisaoFinalizacao: dataPrevisao,
      local: editedForm.value.local,
    };

    const inventarioAtualizado = await apiRequest(`/api/inventarios/${props.inventario.id}`, "PATCH", dadosAtualizados);

    if (inventarioAtualizado) {
      $q.notify({
        color: "positive",
        message: "Inventário atualizado com sucesso!",
        icon: "check_circle",
      });

      const dadosAdaptados = adaptarRespostaParaFormulario(inventarioAtualizado);
      inventarioLocal.value.status = dadosAdaptados.status;
      inventarioLocal.value.data_agendamento = dadosAdaptados.data_agendamento ? dadosAdaptados.data_agendamento : null;
      inventarioLocal.value.data_previsao_finalizacao = dadosAdaptados.data_previsao_finalizacao ? dadosAdaptados.data_previsao_finalizacao : null;
      inventarioLocal.value.data_agendamento_formatada = dadosAdaptados.data_agendamento ? formatarDataParaBR(dadosAdaptados.data_agendamento) : null;
      editedForm.value.data_agendamento_display = inventarioLocal.value.data_agendamento_formatada;
      inventarioLocal.value.data_previsao_finalizacao_formatada = dadosAdaptados.data_previsao_finalizacao
        ? formatarDataParaBR(dadosAdaptados.data_previsao_finalizacao)
        : null;
      editedForm.value.data_previsao_finalizacao_display = inventarioLocal.value.data_previsao_finalizacao_formatada;
      inventarioLocal.value.nome_local = dadosAdaptados.nome_local;
      editedForm.value.local = dadosAdaptados.nome_local;

      isEditing.value = false;
      window.removeEventListener("keydown", handleEscKey);
    } else {
      throw new Error("Erro ao atualizar inventário");
    }
  } catch (error) {
    console.error("Erro ao salvar edição:", error);
    $q.notify({
      color: "negative",
      message: "Erro ao atualizar inventário",
      icon: "error",
    });
  }
};

function formatarDataParaBR(dataISO) {
  if (!dataISO) return "";

  const data = new Date(dataISO);

  // Checagem extra pra garantir que é uma data válida
  if (isNaN(data.getTime())) return "";

  const dia = String(data.getDate()).padStart(2, "0");
  const mes = String(data.getMonth() + 1).padStart(2, "0"); // Mês começa do 0
  const ano = data.getFullYear();

  return `${dia}/${mes}/${ano}`;
}

function adaptarRespostaParaFormulario(respostaApi) {
  return {
    status: respostaApi.status,
    data_agendamento: respostaApi.dataAgendamento,
    nome_local: respostaApi.local,
    data_previsao_finalizacao: respostaApi.dataPrevisaoFinalizacao,
  };
}

const novaContagem = async () => {
  try {
    $q.dialog({
      title: "Confirmar",
      message: "Deseja iniciar uma nova contagem para os itens divergentes?",
      cancel: true,
      persistent: true,
    }).onOk(async () => {
      try {
        const response = await apiRequest(`/api/inventarios/${props.inventario.id}/iniciar-contagem`, "POST");

        if (response) {
          emit("update:inventario", {
            ...response,
          });
          inventarioLocal.value.contagens[0].status = "EM_ANDAMENTO";
          $q.notify({
            color: "positive",
            message: "Nova contagem iniciada com sucesso!",
            icon: "check_circle",
          });
        }
      } catch (error) {
        let errorMessage = "Erro ao iniciar nova contagem.";

        if (error.message) errorMessage = error.message;

        $q.dialog({
          title: "Erro",
          message: errorMessage,
          color: "negative",
          icon: "error",
          ok: "Fechar",
        });
      }
    });
  } catch (error) {
    console.error(error);
  }
};

const finalizarInventario = async () => {
  try {
    $q.dialog({
      title: "Confirmar",
      message: "Deseja finalizar este inventário? Esta ação irá gerar movimentação de estoque dos itens divergentes.",
      cancel: true,
      persistent: true,
    }).onOk(async () => {
      try {
        const response = await apiRequest(`/api/inventarios/${props.inventario.id}/finalizar-inventario`, "POST");

        if (response) {
          inventarioLocal.value.status = "CONCLUIDO";

          eventBus.emit("inventario-concluido");
          $q.notify({
            color: "positive",
            message: "Inventário finalizado com sucesso!",
            icon: "check_circle",
          });
        }
      } catch (error) {
        let errorMessage = "Erro ao finalizar inventário.";

        if (error.message) errorMessage = error.message;

        $q.dialog({
          title: "Erro",
          message: errorMessage,
          color: "negative",
          icon: "error",
          ok: "Fechar",
        });
      }
    });
  } catch (error) {
    console.error(error);
  }
};

const cancelarInventario = async () => {
  try {
    $q.dialog({
      title: "Confirmar",
      message: "Deseja cancelar este inventário?",
      cancel: true,
      persistent: true,
    }).onOk(async () => {
      try {
        const response = await apiRequest(`/api/inventarios/${props.inventario.id}/cancelar-inventario`, "POST");

        if (response) {
          $q.notify({
            color: "positive",
            message: "Inventário cancelado com sucesso!",
            icon: "check_circle",
          });
        }
        inventarioLocal.value.status = "CANCELADO";
      } catch (error) {
        let errorMessage = "Erro ao cancelar inventário.";

        if (error.message) errorMessage = error.message;

        $q.dialog({
          title: "Erro",
          message: errorMessage,
          color: "negative",
          icon: "error",
          ok: "Fechar",
        });
      }
    });
  } catch (error) {
    console.error(error);
  }
};

const iniciarInventario = async () => {
  try {
    // Verificar se o inventário está em estado que permite iniciar contagem
    if (props.inventario.status === "ABERTO" || props.inventario.status === "CONCLUIDO") {
      $q.notify({
        color: "negative",
        message: "Este inventário já foi iniciado ou está concluído.",
        icon: "error",
      });
      return;
    }

    // Exibir confirmação antes de iniciar
    $q.dialog({
      title: "Confirmar",
      message: "Deseja iniciar a contagem deste inventário?",
      cancel: true,
      persistent: true,
    }).onOk(async () => {
      try {
        // Chamar a API para iniciar contagem
        const response = await apiRequest(`/api/inventarios/${props.inventario.id}/iniciar-inventario`, "POST");

        if (response) {
          $q.notify({
            color: "positive",
            message: "Inventário iniciado com sucesso!",
            icon: "check_circle",
          });

          emit("update:inventario", {
            ...response,
          });
        }
      } catch (error) {
        // Tratar erros dentro da operação de contagem
        mostrarErroEmDialog(error);
      }
    });
  } catch (error) {
    mostrarErroEmDialog(error);
  }
};

// Função auxiliar para mostrar erro em dialog
const mostrarErroEmDialog = (error) => {
  console.error("Erro ao iniciar contagem:", error);

  // Extrair a mensagem de erro
  let mensagemErro = "Erro ao iniciar contagem do inventário";

  if (error.message) {
    mensagemErro = error.message;
  } else if (error.response && error.response.data) {
    mensagemErro = error.response.data.message || mensagemErro;
  }

  // Exibir o erro em um dialog
  $q.dialog({
    title: "Erro ao iniciar contagem",
    message: mensagemErro,
    html: true,
    class: "bg-negative text-white",
    ok: {
      label: "Fechar",
      color: "white",
      flat: true,
    },
    style: {
      "max-width": "500px",
    },
    // Formatar a mensagem para melhor exibição
    render: (h) =>
      h(
        "div",
        {
          style: {
            "white-space": "pre-line",
            "word-break": "break-word",
          },
        },
        [mensagemErro]
      ),
  });
};

const cancelarEdicao = () => {
  isEditing.value = false;
  window.removeEventListener("keydown", handleEscKey);
};

const handleEscKey = (event) => {
  if (event.key === "Escape" && isEditing.value) {
    cancelarEdicao();
  }
};

const exportarInventario = () => {
  console.log("Exportar inventário");
  // Implementar lógica para exportar
};

const verHistorico = () => {
  console.log("Ver histórico");
  // Implementar lógica para ver histórico
};

onMounted(() => {
  // Atualiza imediatamente
  atualizarDuracao();

  // Se o inventário não estiver finalizado, configura atualização a cada segundo
  if (props.inventario.status !== "CONCLUIDO") {
    intervalId = setInterval(atualizarDuracao, 1000);
  }
});

// Remover event listener ao desmontar componente
onBeforeUnmount(() => {
  window.removeEventListener("keydown", handleEscKey);

  if (intervalId) {
    clearInterval(intervalId);
  }
});

/*
  eventos de formulário
*/

// Formulário de edição
const editedForm = ref({
  data_agendamento: "",
  data_agendamento_display: "",
  data_previsao_finalizacao: "",
  data_previsao_finalizacao_display: "",
  local: null,
});

const updateDisplayDatePrevisao = (val) => {
  if (val) {
    const parts = val.split("/");
    if (parts.length === 3) {
      editedForm.value.data_previsao_finalizacao_display = val;
    }
  }
};

const updateDisplayDate = (val) => {
  if (val) {
    const parts = val.split("/");
    if (parts.length === 3) {
      editedForm.value.data_agendamento_display = val;
    }
  }
};

const validateAgendamentoDate = (val) => {
  if (!val) return true;

  const parts = val.split("/");
  if (parts.length !== 3) return "Formato de data inválido";

  const date = new Date(parseInt(parts[2]), parseInt(parts[1]) - 1, parseInt(parts[0]));
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  return date >= today || "Data não pode ser anterior à hoje";
};

// Função para validar data de previsão
const validatePrevisaoDate = (val) => {
  if (!val) return true;

  const parts = val.split("/");
  if (parts.length !== 3) return "Formato de data inválido";

  const datePrevisao = new Date(parseInt(parts[2]), parseInt(parts[1]) - 1, parseInt(parts[0]));

  if (editedForm.value.data_agendamento_display) {
    const partsAgendamento = editedForm.value.data_agendamento_display.split("/");
    if (partsAgendamento.length === 3) {
      const dateAgendamento = new Date(parseInt(partsAgendamento[2]), parseInt(partsAgendamento[1]) - 1, parseInt(partsAgendamento[0]));

      return datePrevisao >= dateAgendamento || "Data de finalização não pode ser anterior à data de agendamento";
    }
  }

  return true;
};

// Opções para restringir datas passadas no calendário
const dateOptions = (date) => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const currentDate = new Date(date);
  return currentDate >= today;
};
</script>

<style scoped>
/* Estilos para o display de duração */
.duration-display {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 70px;
  height: 70px;
  border-radius: 50%;
  background-color: #f5f5f5;
  margin: 0 auto;
  font-size: 12px;
  border: 2px solid #e0e0e0;
}

.duration-display.running {
  border-color: #ff9800;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(255, 152, 0, 0.4);
  }
  70% {
    box-shadow: 0 0 0 8px rgba(255, 152, 0, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(255, 152, 0, 0);
  }
}
</style>
