<template>
  <q-page padding>
    <section class="production-deployment" aria-labelledby="production-deployment-title">
      <div class="row items-center justify-between q-mb-md">
        <div>
          <h1 id="production-deployment-title" class="text-h5 q-my-none">
            Atualização do sistema
          </h1>
          <div class="text-caption text-grey-7">
            Atualização forward da instalação global autorizada pelo servidor
          </div>
        </div>
        <q-btn
          v-if="capabilityValid && operation"
          outline
          color="primary"
          label="Atualizar estado"
          :disable="statusLoading"
          @click="refreshOperation(true)"
        />
      </div>

      <q-banner v-if="configurationUnavailable" class="bg-grey-2 text-grey-9" rounded>
        Atualização de produção indisponível neste ambiente.
      </q-banner>

      <q-banner v-else-if="capabilityLoading" class="bg-grey-2 text-grey-9" rounded>
        Verificando autorização de atualização…
      </q-banner>

      <q-banner v-else-if="!capabilityValid" class="bg-grey-2 text-grey-9" rounded>
        Atualização de produção indisponível.
      </q-banner>

      <div v-else class="q-gutter-y-md">
        <q-banner v-if="errorMessage" class="bg-red-1 text-negative" rounded>
          {{ errorMessage }}
          <span v-if="supportCode"> Código de suporte: {{ supportCode }}</span>
        </q-banner>

        <q-banner v-if="currentUncertain" class="bg-orange-1 text-orange-10" rounded>
          Instalação incerta
        </q-banner>

        <q-card v-if="operation" flat bordered>
          <q-card-section>
            <div class="text-subtitle1">Estado da atualização</div>
            <div class="text-body1 q-mt-sm">{{ operationStateLabel }}</div>
            <div class="text-caption text-grey-7 q-mt-xs">
              {{ operation.targetRelease }}
            </div>
          </q-card-section>
        </q-card>

        <q-banner v-if="pendingWithoutOperation" class="bg-blue-1 text-blue-10" rounded>
          Há uma tentativa de atualização aguardando envio.
          <div class="q-mt-sm row q-gutter-sm">
            <q-btn
              color="primary"
              label="Retomar envio"
              :disable="submitLoading || currentUncertain"
              @click="submitAttempt(true)"
            />
            <q-btn flat color="negative" label="Descartar tentativa" @click="discardAttempt" />
          </div>
        </q-banner>

        <q-card v-if="current" flat bordered>
          <q-card-section>
            <div class="text-subtitle1">Instalação atual</div>
            <div class="q-mt-sm">Release: {{ current.release }}</div>
            <div>Commit: {{ current.sourceCommit }}</div>
            <div>Instalada em: {{ current.installedAt }}</div>
            <div>Estado: reconciliado</div>
          </q-card-section>
        </q-card>

        <q-card v-else-if="currentLoaded && !currentUncertain" flat bordered>
          <q-card-section>Instalação limpa. A primeira release elegível pode ser instalada.</q-card-section>
        </q-card>

        <q-card flat bordered>
          <q-card-section>
            <div class="text-subtitle1">Release elegível</div>
            <div v-if="eligibleReleases.length > 1" class="text-negative q-mt-sm">
              Não foi possível determinar uma release elegível.
            </div>
            <div v-else-if="eligibleReleases.length === 0" class="text-grey-7 q-mt-sm">
              Nenhuma atualização disponível.
            </div>
            <div v-else class="q-mt-sm">
              <div class="text-body1">{{ eligibleReleases[0].release }}</div>
              <div class="text-caption text-grey-7">
                Publicada em {{ eligibleReleases[0].publishedAt }}
              </div>
            </div>
          </q-card-section>
        </q-card>

        <q-card v-if="plan" flat bordered>
          <q-card-section>
            <div class="text-subtitle1">Plano da atualização</div>
            <div class="q-mt-sm">Origem: {{ plan.sourceRelease || "instalação limpa" }}</div>
            <div>Destino: {{ plan.targetRelease }}</div>
            <div>Migration necessária: {{ plan.migrationRequired ? "sim" : "não" }}</div>
            <div>Backup necessário: {{ plan.backupRequired ? "sim" : "não" }}</div>
            <ul class="q-mt-sm q-mb-none">
              <li v-for="component in plan.components" :key="component.component">
                {{ component.component }} — {{ component.action === "UPDATE" ? "Atualizar" : "Manter" }}
              </li>
            </ul>
          </q-card-section>
          <q-card-actions>
            <q-btn
              color="primary"
              label="Confirmar atualização"
              :disable="!canSubmit || submitLoading"
              :loading="submitLoading"
              @click="submitAttempt(false)"
            />
          </q-card-actions>
        </q-card>

        <q-card flat bordered>
          <q-card-section>
            <div class="text-subtitle1">Rollback comercial e recuperação</div>
            <div class="text-caption text-grey-7 q-mt-xs">
              A elegibilidade e as condições de segurança são decididas pelo deployer.
            </div>

            <q-banner v-if="rollbackBlocked" class="bg-orange-1 text-orange-10 q-mt-md" rounded>
              Instalação incerta; não iniciar nova operação e consultar suporte.
            </q-banner>

            <div v-if="rollbackOperation" class="q-mt-md">
              <div class="text-body1">{{ rollbackOperationStateLabel }}</div>
              <div class="text-caption text-grey-7 q-mt-xs">
                Alvo: {{ rollbackOperation.targetRelease }} · Origem: {{ rollbackOperation.sourceRelease }}
              </div>
              <div class="text-caption text-grey-7 q-mt-xs">
                Restauração indicada pelo servidor:
                {{ rollbackOperation.databaseRestoreRequired ? "sim" : "não" }}
              </div>
              <q-btn
                v-if="rollbackAttempt?.operationId && !rollbackOperationTerminal"
                class="q-mt-sm"
                outline
                color="primary"
                label="Atualizar estado do rollback"
                :disable="rollbackStatusLoading"
                @click="refreshRollbackOperation(true)"
              />
            </div>

            <q-banner v-if="rollbackPendingWithoutOperation" class="bg-blue-1 text-blue-10 q-mt-md" rounded>
              Há uma tentativa de rollback aguardando envio.
              <div class="q-mt-sm row q-gutter-sm">
                <q-btn
                  color="primary"
                  label="Retomar envio"
                  :disable="rollbackSubmitLoading || rollbackBlocked"
                  @click="submitRollback(true)"
                />
                <q-btn
                  flat
                  color="negative"
                  label="Descartar tentativa"
                  @click="discardRollbackAttempt"
                />
              </div>
            </q-banner>

            <div v-if="!rollbackOperation && !rollbackPendingWithoutOperation" class="q-mt-md">
              <label class="text-caption text-grey-8" for="rollback-release">
                Release global apresentada pelo servidor
              </label>
              <select
                id="rollback-release"
                v-model="rollbackTarget"
                class="rollback-release-select full-width q-mt-xs"
                :disabled="rollbackBlocked || rollbackSubmitLoading || rollbackReleases.length === 0"
              >
                <option value="">Selecione uma release</option>
                <option v-for="release in rollbackReleases" :key="release.release" :value="release.release">
                  {{ release.release }}
                </option>
              </select>

              <label class="text-caption text-grey-8 block q-mt-md" for="rollback-reason">
                Motivo do rollback
              </label>
              <textarea
                id="rollback-reason"
                v-model="rollbackReason"
                class="rollback-reason full-width q-mt-xs"
                rows="4"
                minlength="10"
                maxlength="1000"
                :disabled="rollbackBlocked || rollbackSubmitLoading"
              />
              <div class="text-caption text-grey-7 q-mt-xs">
                Uploads não são restaurados implicitamente. A sessão WhatsApp pode exigir reemparelhamento manual.
              </div>
              <q-btn
                class="q-mt-md"
                color="negative"
                label="Solicitar rollback"
                :disable="!canRequestRollback || rollbackSubmitLoading"
                :loading="rollbackSubmitLoading"
                @click="submitRollback(false)"
              />
            </div>
          </q-card-section>
        </q-card>
      </div>
    </section>
  </q-page>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { baseApiUrl } from "@/global";
import { releaseDeployerConfig } from "@/config/releaseDeployer";
import {
  createReleaseDeployerClient,
  ReleaseDeployerClientError,
} from "@/services/releaseDeployerClient";
import { createReleaseDeployerAttemptStore } from "@/services/releaseDeployerAttempt";
import { createReleaseDeployerRollbackAttemptStore } from "@/services/releaseDeployerRollbackAttempt";

const POLL_INTERVAL_MS = 3000;
const POLL_TIMEOUT_MS = 10 * 60 * 1000;

const acceptedStates = new Set(["QUEUED", "SUCCEEDED", "FAILED"]);
const terminalStates = new Set(["SUCCEEDED", "FAILED"]);
const rollbackAcceptedStates = new Set([
  "QUEUED",
  "PRECHECKING",
  "RESTORING",
  "SWITCHING",
  "VERIFYING",
  "SUCCEEDED",
  "ROLLING_BACK",
  "ROLLED_BACK",
  "FAILED",
  "UNCERTAIN",
]);
const rollbackTerminalStates = new Set(["SUCCEEDED", "ROLLED_BACK", "FAILED", "UNCERTAIN"]);

const capabilityLoading = ref(false);
const capabilityValid = ref(false);
const configurationUnavailable = ref(releaseDeployerConfig.mode !== "deployer");
const current = ref(null);
const currentLoaded = ref(false);
const releases = ref([]);
const rollbackReleases = ref([]);
const rollbackTarget = ref("");
const rollbackReason = ref("");
const plan = ref(null);
const attempt = ref(null);
const rollbackAttempt = ref(null);
const operation = ref(null);
const rollbackOperation = ref(null);
const operationState = ref(null);
const rollbackOperationState = ref(null);
const currentUncertain = ref(false);
const errorMessage = ref("");
const supportCode = ref("");
const submitLoading = ref(false);
const statusLoading = ref(false);
const rollbackSubmitLoading = ref(false);
const rollbackStatusLoading = ref(false);
const pollingTimer = ref(null);
const rollbackPollingTimer = ref(null);
const operationBlocked = ref(false);
const rollbackBlocked = ref(false);
let pollInFlight = false;
let rollbackPollInFlight = false;
let pollStartedAt = 0;
let rollbackPollStartedAt = 0;

const client =
  releaseDeployerConfig.mode === "deployer"
    ? createReleaseDeployerClient({
        baseApiUrl,
        getErpToken: () => globalThis.sessionStorage?.getItem("token") ?? null,
      })
    : null;
const attempts = createReleaseDeployerAttemptStore();
const rollbackAttempts = createReleaseDeployerRollbackAttemptStore();

const eligibleReleases = computed(() => releases.value.filter((release) => release.eligible));
const pendingWithoutOperation = computed(() => !!attempt.value && !attempt.value.operationId && !operation.value);
const rollbackPendingWithoutOperation = computed(
  () => !!rollbackAttempt.value && !rollbackAttempt.value.operationId && !rollbackOperation.value,
);
const rollbackOperationTerminal = computed(() =>
  rollbackOperation.value ? rollbackTerminalStates.has(rollbackOperation.value.state) : false,
);
const operationStateLabel = computed(() => {
  if (operationState.value === "QUEUED") return "Aguardando reconciliação";
  if (operationState.value === "SUCCEEDED") return "Atualização concluída";
  if (operationState.value === "FAILED") return "Atualização falhou";
  return "Não foi possível determinar o estado da atualização.";
});
const rollbackOperationStateLabel = computed(() => {
  if (rollbackOperationState.value === "QUEUED") return "Aguardando reconciliação";
  if (rollbackOperationState.value === "PRECHECKING") return "Validando condições de segurança";
  if (rollbackOperationState.value === "RESTORING") return "Restaurando o banco conforme o backup verificado";
  if (rollbackOperationState.value === "SWITCHING") return "Aplicando a release anterior";
  if (rollbackOperationState.value === "VERIFYING") return "Verificando instalação e persistências";
  if (rollbackOperationState.value === "SUCCEEDED") return "Rollback comercial concluído";
  if (rollbackOperationState.value === "ROLLED_BACK") {
    return "A tentativa foi compensada; rollback comercial não concluído";
  }
  if (rollbackOperationState.value === "FAILED") return "Rollback impedido antes de side effect";
  if (rollbackOperationState.value === "UNCERTAIN") {
    return "Instalação incerta; não iniciar nova operação e consultar suporte";
  }
  return "Não foi possível determinar o estado do rollback.";
});
const canSubmit = computed(
  () =>
    capabilityValid.value &&
    !currentUncertain.value &&
    eligibleReleases.value.length === 1 &&
    !!plan.value &&
    !attempt.value &&
    !operation.value &&
    !operationBlocked.value,
);
const canRequestRollback = computed(
  () =>
    capabilityValid.value &&
    !currentUncertain.value &&
    rollbackReleases.value.some((release) => release.release === rollbackTarget.value) &&
    rollbackReason.value.length >= 10 &&
    rollbackReason.value.length <= 1000 &&
    !rollbackAttempt.value &&
    !rollbackOperation.value &&
    !rollbackBlocked.value,
);

function clearError() {
  errorMessage.value = "";
  supportCode.value = "";
}

function showError(error) {
  if (error instanceof ReleaseDeployerClientError) {
    errorMessage.value = error.message;
    supportCode.value = error.traceId || "";
    return;
  }
  if (error?.message === "Armazenamento da sessão indisponível.") {
    errorMessage.value = error.message;
    return;
  }
  errorMessage.value = "Não foi possível concluir a solicitação.";
  supportCode.value = "";
}

function canonicalPlan(value) {
  if (!value) return null;
  return JSON.stringify({
    ...value,
    components: [...value.components].sort((left, right) =>
      left.component.localeCompare(right.component),
    ),
  });
}

function stopPolling() {
  if (pollingTimer.value !== null) {
    clearInterval(pollingTimer.value);
    pollingTimer.value = null;
  }
}

function stopRollbackPolling() {
  if (rollbackPollingTimer.value !== null) {
    clearInterval(rollbackPollingTimer.value);
    rollbackPollingTimer.value = null;
  }
}

async function refreshOperation(manual = false) {
  if (!client || !attempt.value?.operationId || pollInFlight) return;
  if (manual) clearError();
  if (Date.now() - pollStartedAt >= POLL_TIMEOUT_MS) {
    stopPolling();
    showError(new Error("timeout"));
    return;
  }

  pollInFlight = true;
  statusLoading.value = true;
  try {
    const nextOperation = await client.operation(attempt.value.operationId);
    if (nextOperation.targetRelease !== attempt.value.release) {
      stopPolling();
      showError(new Error("divergent operation"));
      return;
    }
    if (!acceptedStates.has(nextOperation.state)) {
      stopPolling();
      showError(new Error("unknown operation state"));
      return;
    }
    operation.value = nextOperation;
    operationState.value = nextOperation.state;
    if (terminalStates.has(nextOperation.state)) {
      stopPolling();
      attempts.clear();
      attempt.value = null;
    }
  } catch (error) {
    stopPolling();
    showError(error);
  } finally {
    statusLoading.value = false;
    pollInFlight = false;
  }
}

async function refreshRollbackOperation(manual = false) {
  if (!client || !rollbackAttempt.value?.operationId || rollbackPollInFlight) return;
  if (manual) clearError();
  if (Date.now() - rollbackPollStartedAt >= POLL_TIMEOUT_MS) {
    stopRollbackPolling();
    showError(new Error("timeout"));
    return;
  }

  rollbackPollInFlight = true;
  rollbackStatusLoading.value = true;
  try {
    const nextOperation = await client.rollbackOperation(rollbackAttempt.value.operationId);
    if (nextOperation.targetRelease !== rollbackAttempt.value.release) {
      stopRollbackPolling();
      showError(new Error("divergent rollback operation"));
      return;
    }
    if (!rollbackAcceptedStates.has(nextOperation.state)) {
      stopRollbackPolling();
      showError(new Error("unknown rollback operation state"));
      return;
    }
    rollbackOperation.value = nextOperation;
    rollbackOperationState.value = nextOperation.state;
    if (nextOperation.state === "UNCERTAIN") rollbackBlocked.value = true;
    if (rollbackTerminalStates.has(nextOperation.state)) {
      stopRollbackPolling();
      if (nextOperation.state !== "UNCERTAIN") {
        rollbackAttempts.clear();
        rollbackAttempt.value = null;
      }
    }
  } catch (error) {
    stopRollbackPolling();
    showError(error);
  } finally {
    rollbackStatusLoading.value = false;
    rollbackPollInFlight = false;
  }
}

function startPolling() {
  stopPolling();
  pollStartedAt = Math.min(pollStartedAt || Date.now(), Date.now());
  void refreshOperation();
  pollingTimer.value = setInterval(() => {
    void refreshOperation();
  }, POLL_INTERVAL_MS);
}

function startRollbackPolling() {
  stopRollbackPolling();
  rollbackPollStartedAt = Math.min(rollbackPollStartedAt || Date.now(), Date.now());
  void refreshRollbackOperation();
  rollbackPollingTimer.value = setInterval(() => {
    void refreshRollbackOperation();
  }, POLL_INTERVAL_MS);
}

async function loadData() {
  if (!client) return;
  try {
    const installation = await client.current();
    current.value = installation;
    currentLoaded.value = true;
    const available = await client.releases();
    releases.value = available;
    rollbackReleases.value = available;
    if (rollbackAttempt.value && !rollbackAttempt.value.operationId) {
      rollbackTarget.value = rollbackAttempt.value.release;
      rollbackReason.value = rollbackAttempt.value.reason;
    }
    if (eligibleReleases.value.length === 1 && !currentUncertain.value) {
      const nextPlan = await client.plan(eligibleReleases.value[0].release);
      if (nextPlan.targetRelease !== eligibleReleases.value[0].release) {
        throw new Error("divergent plan");
      }
      plan.value = nextPlan;
    }
  } catch (error) {
    if (
      error instanceof ReleaseDeployerClientError &&
      error.code === "CURRENT_INSTALLATION_UNRECONCILED"
    ) {
      currentUncertain.value = true;
    }
    showError(error);
  }
}

async function submitRollback(resume) {
  if (!client || rollbackSubmitLoading.value || currentUncertain.value || rollbackBlocked.value) return;
  const selected = resume ? rollbackAttempt.value?.release : rollbackTarget.value;
  const reason = resume ? rollbackAttempt.value?.reason : rollbackReason.value;
  if (!selected || !reason || (resume && !rollbackAttempt.value)) return;
  if (!resume && !canRequestRollback.value) return;
  if (!rollbackReleases.value.some((release) => release.release === selected)) {
    showError(new Error("rollback release was not returned by the server"));
    return;
  }
  const confirmed =
    typeof globalThis.confirm === "function" &&
    globalThis.confirm(
      `Confirmar rollback para ${selected} com o motivo informado? O deployer decidirá as condições de restauração.`,
    );
  if (!confirmed) return;

  clearError();
  rollbackSubmitLoading.value = true;
  try {
    const savedAttempt = resume
      ? rollbackAttempt.value
      : rollbackAttempts.create(selected, reason);
    rollbackAttempt.value = savedAttempt;
    const createdOperation = await client.requestRollback(
      savedAttempt.release,
      savedAttempt.reason,
      savedAttempt.idempotencyKey,
    );
    rollbackAttempt.value = rollbackAttempts.setOperationId(
      savedAttempt,
      createdOperation.operationId,
    );
    rollbackOperation.value = createdOperation;
    rollbackOperationState.value = createdOperation.state;
    rollbackPollStartedAt = Date.parse(createdOperation.createdAt) || Date.now();
    if (createdOperation.state === "UNCERTAIN") {
      rollbackBlocked.value = true;
    } else if (rollbackTerminalStates.has(createdOperation.state)) {
      rollbackAttempts.clear();
      rollbackAttempt.value = null;
    } else if (rollbackAcceptedStates.has(createdOperation.state)) {
      startRollbackPolling();
    } else {
      stopRollbackPolling();
      showError(new Error("unknown rollback operation state"));
    }
  } catch (error) {
    if (error instanceof ReleaseDeployerClientError && error.code === "IDEMPOTENCY_CONFLICT") {
      rollbackAttempts.clear();
      rollbackAttempt.value = null;
      rollbackBlocked.value = true;
    }
    if (
      error instanceof ReleaseDeployerClientError &&
      error.code === "PRODUCTION_OPERATION_ACTIVE"
    ) {
      rollbackBlocked.value = true;
    }
    showError(error);
  } finally {
    rollbackSubmitLoading.value = false;
  }
}

async function submitAttempt(resume) {
  if (!client || submitLoading.value || currentUncertain.value) return;
  const selected = resume ? attempt.value?.release : eligibleReleases.value[0]?.release;
  if (!selected || (resume && !attempt.value)) return;
  if (!resume && !canSubmit.value) return;
  if (!eligibleReleases.value.some((release) => release.release === selected)) {
    showError(new Error("release is no longer eligible"));
    return;
  }

  clearError();
  submitLoading.value = true;
  try {
    const refreshedPlan = await client.plan(selected);
    if (
      refreshedPlan.targetRelease !== selected ||
      (plan.value && canonicalPlan(refreshedPlan) !== canonicalPlan(plan.value))
    ) {
      throw new Error("divergent plan");
    }
    plan.value = refreshedPlan;

    const savedAttempt = resume ? attempt.value : attempts.create(selected);
    attempt.value = savedAttempt;
    const createdOperation = await client.requestDeployment(
      savedAttempt.release,
      savedAttempt.idempotencyKey,
    );
    attempt.value = attempts.setOperationId(savedAttempt, createdOperation.operationId);
    operation.value = createdOperation;
    operationState.value = createdOperation.state;
    pollStartedAt = Date.parse(createdOperation.createdAt) || Date.now();
    if (terminalStates.has(createdOperation.state)) {
      attempts.clear();
      attempt.value = null;
    } else if (acceptedStates.has(createdOperation.state)) {
      startPolling();
    } else {
      stopPolling();
      showError(new Error("unknown operation state"));
    }
  } catch (error) {
    if (error instanceof ReleaseDeployerClientError && error.code === "IDEMPOTENCY_CONFLICT") {
      attempts.clear();
      attempt.value = null;
    }
    if (
      error instanceof ReleaseDeployerClientError &&
      error.code === "PRODUCTION_OPERATION_ACTIVE"
    ) {
      operationBlocked.value = true;
    }
    showError(error);
  } finally {
    submitLoading.value = false;
  }
}

function discardAttempt() {
  if (!attempt.value) return;
  const confirmed =
    typeof globalThis.confirm === "function" &&
    globalThis.confirm("Descartar a tentativa de atualização?");
  if (!confirmed) return;
  attempts.clear();
  attempt.value = null;
  clearError();
}

function discardRollbackAttempt() {
  if (!rollbackAttempt.value) return;
  const confirmed =
    typeof globalThis.confirm === "function" &&
    globalThis.confirm("Descartar a tentativa de rollback?");
  if (!confirmed) return;
  rollbackAttempts.clear();
  rollbackAttempt.value = null;
  rollbackTarget.value = "";
  rollbackReason.value = "";
  clearError();
}

async function initialize() {
  if (configurationUnavailable.value || !client) return;
  capabilityLoading.value = true;
  try {
    attempt.value = attempts.read();
    rollbackAttempt.value = rollbackAttempts.read();
    const capabilities = await client.capabilities();
    capabilityValid.value =
      capabilities.mode === "deployer" &&
      capabilities.apiVersion === "v1" &&
      capabilities.capabilities.length === 3 &&
      capabilities.capabilities[0] === "deployment:read" &&
      capabilities.capabilities[1] === "deployment:execute" &&
      capabilities.capabilities[2] === "deployment:rollback";
    if (!capabilityValid.value) return;
    if (attempt.value?.operationId) {
      pollStartedAt = Date.parse(attempt.value.createdAt) || Date.now();
      startPolling();
    }
    if (rollbackAttempt.value?.operationId) {
      rollbackPollStartedAt = Date.parse(rollbackAttempt.value.createdAt) || Date.now();
      startRollbackPolling();
    }
    if (!attempt.value?.operationId && !rollbackAttempt.value?.operationId) await loadData();
  } catch (error) {
    showError(error);
  } finally {
    capabilityLoading.value = false;
  }
}

onMounted(() => {
  void initialize();
});

onBeforeUnmount(() => {
  stopPolling();
  stopRollbackPolling();
});
</script>

<style scoped>
.production-deployment {
  max-width: 960px;
  margin: 0 auto;
}
</style>
