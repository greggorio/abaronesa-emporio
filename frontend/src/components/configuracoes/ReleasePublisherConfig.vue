<template>
  <section class="release-publisher q-gutter-md" aria-labelledby="release-publisher-title">
    <div>
      <h2 id="release-publisher-title" class="text-h6 q-my-none">
        Gerenciamento de Releases
      </h2>
      <p class="text-body2 text-grey-7 q-mb-none">
        Publique uma release global a partir de um candidato validado.
      </p>
    </div>

    <q-banner
      v-if="visualState === 'disabled'"
      class="bg-grey-2 text-grey-9"
      data-testid="disabled-state"
    >
      A configuração local do publisher está desabilitada.
    </q-banner>

    <q-banner
      v-else-if="visualState === 'loading'"
      class="bg-blue-1 text-primary"
      data-testid="loading-state"
    >
      <q-spinner class="q-mr-sm" aria-label="Carregando publisher" />
      Carregando candidatos e releases...
    </q-banner>

    <q-banner
      v-else-if="visualState === 'session-expired'"
      class="bg-orange-1 text-orange-10"
      data-testid="session-expired-state"
    >
      Sessão expirada. Entre novamente.
    </q-banner>

    <q-banner
      v-else-if="visualState === 'forbidden'"
      class="bg-red-1 text-negative"
      data-testid="forbidden-state"
    >
      Você não possui permissão para publicar releases.
    </q-banner>

    <q-banner
      v-else-if="visualState === 'unavailable'"
      class="bg-red-1 text-negative"
      data-testid="unavailable-state"
    >
      {{ publicMessage }}
      <template v-if="supportCode" #action>
        <span class="text-caption">Código de suporte: {{ supportCode }}</span>
      </template>
    </q-banner>

    <template v-else>
      <q-card flat bordered>
        <q-card-section class="row q-col-gutter-md">
          <div class="col-12 col-md-6">
            <div class="text-caption text-grey-7">Release atual</div>
            <div class="text-subtitle1" data-testid="current-release">
              {{ currentRelease }}
            </div>
          </div>
          <div class="col-12 col-md-6">
            <div class="text-caption text-grey-7">
              Próxima versão estimada
            </div>
            <div class="text-subtitle1" data-testid="estimated-version">
              {{ estimatedVersion }}
            </div>
            <div class="text-caption text-grey-7">
              Estimativa local; o manifesto e o workflow definem a versão efetiva.
            </div>
          </div>
        </q-card-section>
      </q-card>

      <q-banner
        v-if="candidates.length === 0"
        class="bg-grey-2 text-grey-9"
        data-testid="no-candidates-state"
      >
        {{ nothingToPublishMessage }}
      </q-banner>

      <q-form
        v-else
        class="q-gutter-md"
        data-testid="publisher-form"
        @submit.prevent="openConfirmation"
      >
        <!--
          A tela comunica, nao pergunta. Escolher candidato obrigaria quem publica
          a raciocinar sobre dependencias entre eles, e nao ha o que escolher: o
          manifesto e cumulativo, entao o mais recente ja incorpora os anteriores.
          A lista existe para dizer o que entra na proxima release, e serve de
          insumo para o changelog logo abaixo.
        -->
        <q-card flat bordered data-testid="incoming-candidates">
          <q-card-section class="q-pb-none">
            <div class="text-subtitle2">
              Commits que entram nesta release
            </div>
            <div class="text-caption text-grey-7">
              {{ incomingCaption }}
            </div>
          </q-card-section>
          <q-list dense>
            <q-item v-for="entry in incomingCandidates" :key="entry.candidateId">
              <q-item-section>
                <q-item-label>{{ entry.subject }}</q-item-label>
                <q-item-label caption>{{ entry.when }} · {{ entry.sha }}</q-item-label>
              </q-item-section>
            </q-item>
          </q-list>
        </q-card>

        <q-select
          v-model="versionBump"
          :options="versionBumpOptions"
          outlined
          label="Tipo de atualização"
          aria-label="Tipo de atualização"
          :disable="operationActive"
        />

        <q-input
          v-model="description"
          outlined
          label="Descrição"
          aria-label="Descrição"
          maxlength="500"
          counter
          :disable="operationActive"
        />

        <q-input
          v-model="changelog"
          outlined
          type="textarea"
          label="Changelog"
          aria-label="Changelog"
          maxlength="10000"
          counter
          :disable="operationActive"
        />

        <div class="row q-gutter-sm">
          <q-btn
            type="submit"
            color="primary"
            label="Publicar release"
            aria-label="Publicar release"
            :loading="submitting"
            :disable="!formValid || operationActive || submitting || pendingWithoutOperation"
          />
          <q-btn
            v-if="nextCandidateCursor"
            flat
            color="primary"
            label="Carregar mais"
            aria-label="Carregar mais candidatos"
            :loading="loadingMore"
            :disable="loadingMore || operationActive"
            @click="loadMoreCandidates"
          />
        </div>
      </q-form>

      <q-banner
        v-if="pendingWithoutOperation"
        class="bg-amber-1 text-brown-9"
        data-testid="resumable-state"
      >
        O resultado do envio anterior é incerto. A tentativa foi preservada e
        não será reenviada automaticamente.
        <template #action>
          <q-btn
            flat
            color="primary"
            label="Retomar envio"
            aria-label="Retomar envio"
            :loading="submitting"
            @click="resumeSubmission"
          />
          <q-btn
            flat
            color="negative"
            label="Descartar tentativa"
            aria-label="Descartar tentativa"
            @click="discardConfirmationOpen = true"
          />
        </template>
      </q-banner>

      <q-banner
        v-if="operation"
        class="bg-blue-1 text-primary"
        data-testid="operation-state"
      >
        <div class="text-weight-medium">{{ operationLabel }}</div>
        <div v-if="operation.state === 'FAILED'">
          A publicação falhou. Consulte a operação nos logs do serviço.
        </div>
        <div v-else-if="operation.state === 'PUBLISHED'">
          Release {{ operation.release }} publicada.
        </div>
        <a
          v-if="safeWorkflowRunUrl"
          :href="safeWorkflowRunUrl"
          target="_blank"
          rel="noopener noreferrer"
        >
          Abrir execução do workflow
        </a>
      </q-banner>

      <q-banner
        v-if="pollingPaused"
        class="bg-amber-1 text-brown-9"
        data-testid="polling-paused-state"
      >
        A atualização automática foi pausada. A tentativa permanece salva.
        <template #action>
          <q-btn
            flat
            color="primary"
            label="Atualizar estado"
            aria-label="Atualizar estado"
            @click="refreshOperation"
          />
        </template>
      </q-banner>

      <q-banner
        v-if="publicMessage && visualState === 'ready'"
        class="bg-orange-1 text-orange-10"
        data-testid="public-error-state"
      >
        {{ publicMessage }}
        <span v-if="supportCode" class="block text-caption">
          Código de suporte: {{ supportCode }}
        </span>
      </q-banner>
    </template>

    <q-dialog v-model="confirmationOpen" persistent>
      <q-card>
        <q-card-section>
          <div class="text-h6">Confirmar publicação</div>
          <p>Candidato: {{ confirmationRequest?.candidateId }}</p>
          <p>Incremento: {{ confirmationRequest?.versionBump }}</p>
          <p>Versão estimada: {{ estimatedVersion }}</p>
        </q-card-section>
        <q-card-actions align="right">
          <q-btn
            flat
            label="Cancelar"
            aria-label="Cancelar publicação"
            @click="cancelConfirmation"
          />
          <q-btn
            color="primary"
            label="Confirmar publicação"
            aria-label="Confirmar publicação"
            @click="confirmSubmission"
          />
        </q-card-actions>
      </q-card>
    </q-dialog>

    <q-dialog v-model="discardConfirmationOpen" persistent>
      <q-card>
        <q-card-section>
          Descartar a tentativa remove somente o registro local. A operação
          remota não será cancelada.
        </q-card-section>
        <q-card-actions align="right">
          <q-btn
            flat
            label="Manter tentativa"
            aria-label="Manter tentativa"
            @click="discardConfirmationOpen = false"
          />
          <q-btn
            color="negative"
            label="Descartar tentativa"
            aria-label="Confirmar descarte da tentativa"
            @click="discardAttempt"
          />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { baseApiUrl } from "@/global";
import {
  releasePublisherConfig,
} from "@/config/releasePublisher";
import {
  PUBLIC_PROBLEM_MESSAGES,
  createReleasePublisherClient,
} from "@/services/releasePublisherClient";
import {
  createReleasePublisherAttemptStore,
  normalizePublicationRequest,
} from "@/services/releasePublisherAttempt";

const POLL_INTERVAL_MS = 3_000;
const POLL_TIMEOUT_MS = 10 * 60 * 1000;

const props = defineProps({
  config: {
    type: Object,
    default: () => releasePublisherConfig,
  },
  client: {
    type: Object,
    default: null,
  },
  attemptStore: {
    type: Object,
    default: null,
  },
  pollIntervalMs: {
    type: Number,
    default: undefined,
  },
  pollTimeoutMs: {
    type: Number,
    default: undefined,
  },
});

const TERMINAL_STATES = new Set(["PUBLISHED", "FAILED"]);
const ACTIVE_STATES = new Set(["REQUESTED", "VALIDATING", "PUBLISHING"]);
const DEFAULT_ERROR_MESSAGE = "Não foi possível concluir a solicitação.";

const visualState = ref(props.config.mode === "publisher" ? "loading" : "disabled");
const candidates = ref([]);
const releases = ref([]);
const nextCandidateCursor = ref(null);
const selectedCandidateId = ref(null);
const versionBump = ref("PATCH");
const description = ref("");
const changelog = ref("");
const loadingMore = ref(false);
const submitting = ref(false);
const confirmationOpen = ref(false);
const discardConfirmationOpen = ref(false);
const confirmationRequest = ref(null);
const pendingAttempt = ref(null);
const operation = ref(null);
const pollingPaused = ref(false);
const publicMessage = ref("");
const supportCode = ref("");

let client = props.client;
let attemptStore = props.attemptStore;
let pollTimer = null;
let pollInFlight = false;
let pollingStartedAt = 0;
let disposed = false;

const versionBumpOptions = ["MAJOR", "MINOR", "PATCH"];
const candidateDateFormatter = new Intl.DateTimeFormat("pt-BR", {
  day: "2-digit",
  month: "short",
  hour: "2-digit",
  minute: "2-digit",
});

// Cada entrada e um commit do main ainda nao incorporado a release corrente.
// Data, sha curto e assunto dizem a quem publica o que exatamente entra.
const incomingCandidates = computed(() =>
  candidates.value.map((candidate) => ({
    candidateId: candidate.candidateId,
    when: candidateDateFormatter.format(new Date(candidate.createdAt)),
    sha: candidate.sourceCommit.slice(0, 7),
    subject: candidate.commitSubject ?? "(sem assunto no commit)",
  })),
);
const currentRelease = computed(() => releases.value[0]?.release ?? "Nenhuma");

// Sem release publicada ainda, falar em "release atual" nao diz nada a quem le;
// os textos passam a descrever a primeira publicacao.
const hasPublishedRelease = computed(() => releases.value.length > 0);
const incomingCaption = computed(() =>
  hasPublishedRelease.value
    ? `Tudo que está no main e ainda não foi incorporado à ${currentRelease.value}.`
    : "Tudo que está no main. Esta será a primeira release publicada.",
);
const nothingToPublishMessage = computed(() =>
  hasPublishedRelease.value
    ? `Nada a publicar: ${currentRelease.value} já contém tudo que está no main.`
    : "Nenhum candidato disponível para publicar.",
);
const selectedCandidateReady = computed(() =>
  candidates.value.some(
    (candidate) =>
      candidate.candidateId === selectedCandidateId.value &&
      candidate.eligibility === "READY",
  ),
);
const operationActive = computed(
  () => operation.value !== null && ACTIVE_STATES.has(operation.value.state),
);
const pendingWithoutOperation = computed(
  () => pendingAttempt.value !== null && pendingAttempt.value.operationId === null,
);
const formValid = computed(() => {
  const trimmedDescription = description.value.trim();
  const trimmedChangelog = changelog.value.trim();
  return (
    selectedCandidateReady.value &&
    versionBumpOptions.includes(versionBump.value) &&
    trimmedDescription.length >= 1 &&
    trimmedDescription.length <= 500 &&
    trimmedChangelog.length >= 1 &&
    trimmedChangelog.length <= 10000
  );
});
const estimatedVersion = computed(() =>
  estimateNextVersion(currentRelease.value, versionBump.value),
);
const operationLabel = computed(() => {
  const labels = {
    REQUESTED: "Publicação solicitada",
    VALIDATING: "Validando candidato",
    PUBLISHING: "Publicando release",
    PUBLISHED: "Publicação concluída",
    FAILED: "Publicação falhou",
  };
  return labels[operation.value?.state] ?? "";
});
const safeWorkflowRunUrl = computed(() => {
  const value = operation.value?.workflowRunUrl;
  if (typeof value !== "string") return null;
  try {
    return new URL(value).protocol === "https:" ? value : null;
  } catch {
    return null;
  }
});

function estimateNextVersion(release, bump) {
  const match = /^v(\d+)\.(\d+)\.(\d+)$/.exec(release);
  let major = "0";
  let minor = "0";
  let patch = "0";
  if (match) {
    [, major, minor, patch] = match;
  }
  if (bump === "MAJOR") return `v${incrementDecimal(major)}.0.0`;
  if (bump === "MINOR") return `v${major}.${incrementDecimal(minor)}.0`;
  return `v${major}.${minor}.${incrementDecimal(patch)}`;
}

function incrementDecimal(value) {
  const digits = value.split("");
  let carry = 1;
  for (let index = digits.length - 1; index >= 0 && carry === 1; index -= 1) {
    const next = Number(digits[index]) + carry;
    digits[index] = String(next % 10);
    carry = next === 10 ? 1 : 0;
  }
  if (carry === 1) digits.unshift("1");
  return digits.join("");
}

function initializeDependencies() {
  if (client === null) {
    client = createReleasePublisherClient({
      erpBaseUrl: baseApiUrl,
      publisherBaseUrl: props.config.url,
      getErpToken: () => globalThis.sessionStorage.getItem("token"),
    });
  }
  if (attemptStore === null) {
    attemptStore = createReleasePublisherAttemptStore();
  }
}

function requestFromForm() {
  return normalizePublicationRequest({
    candidateId: selectedCandidateId.value,
    versionBump: versionBump.value,
    description: description.value.trim(),
    changelog: changelog.value.trim(),
  });
}

function restoreForm(attempt) {
  selectedCandidateId.value = attempt.request.candidateId;
  versionBump.value = attempt.request.versionBump;
  description.value = attempt.request.description;
  changelog.value = attempt.request.changelog;
}

function clearPublicError() {
  publicMessage.value = "";
  supportCode.value = "";
}

function classifyError(error, fallback = DEFAULT_ERROR_MESSAGE) {
  const code = typeof error?.code === "string" ? error.code : null;
  publicMessage.value = PUBLIC_PROBLEM_MESSAGES[code] ?? fallback;
  supportCode.value =
    typeof error?.traceId === "string" && error.traceId.length > 0
      ? error.traceId
      : "";
  if (code === "UNAUTHORIZED" || error?.kind === "ERP_SESSION_MISSING") {
    visualState.value = "session-expired";
  } else if (code === "FORBIDDEN") {
    visualState.value = "forbidden";
  }
}

function mergeCandidates(items) {
  const merged = new Map(
    candidates.value.map((candidate) => [candidate.candidateId, candidate]),
  );
  for (const candidate of items) {
    const previous = merged.get(candidate.candidateId);
    if (previous && JSON.stringify(previous) !== JSON.stringify(candidate)) {
      throw new Error("Resposta de candidatos inconsistente.");
    }
    if (!previous) merged.set(candidate.candidateId, candidate);
  }
  candidates.value = [...merged.values()];
}

async function refreshLists() {
  const [candidatePage, publishedReleases] = await Promise.all([
    client.listCandidates(),
    client.listReleases(),
  ]);
  candidates.value = [];
  mergeCandidates(candidatePage.items);
  nextCandidateCursor.value = candidatePage.nextCursor;
  releases.value = publishedReleases;
  // Sem seletor na tela, o candidato acompanha sempre o mais recente: publicar
  // e publicar o topo do main. A unica excecao e uma tentativa ja persistida,
  // que precisa concluir com o candidato que ela registrou, e nao com outro que
  // tenha surgido no meio do caminho.
  if (pendingAttempt.value === null && candidates.value.length > 0) {
    selectedCandidateId.value = candidates.value[0].candidateId;
  }
}

async function loadMoreCandidates() {
  if (nextCandidateCursor.value === null || loadingMore.value) return;
  loadingMore.value = true;
  clearPublicError();
  try {
    const page = await client.listCandidates({
      cursor: nextCandidateCursor.value,
    });
    mergeCandidates(page.items);
    nextCandidateCursor.value = page.nextCursor;
  } catch (error) {
    classifyError(error);
  } finally {
    loadingMore.value = false;
  }
}

function openConfirmation() {
  clearPublicError();
  if (!formValid.value || operationActive.value || pendingWithoutOperation.value) {
    publicMessage.value = "Revise os dados informados.";
    return;
  }
  try {
    confirmationRequest.value = requestFromForm();
    confirmationOpen.value = true;
  } catch {
    publicMessage.value = "Revise os dados informados.";
  }
}

function cancelConfirmation() {
  confirmationOpen.value = false;
  confirmationRequest.value = null;
}

async function confirmSubmission() {
  const request = confirmationRequest.value;
  cancelConfirmation();
  if (request === null) return;
  let attempt;
  try {
    attempt = attemptStore.create(request);
    pendingAttempt.value = attempt;
  } catch {
    publicMessage.value = "Armazenamento da sessão indisponível.";
    return;
  }
  await sendAttempt(attempt);
}

async function resumeSubmission() {
  if (pendingAttempt.value?.operationId !== null) return;
  await sendAttempt(pendingAttempt.value);
}

async function sendAttempt(attempt) {
  if (submitting.value) return;
  submitting.value = true;
  clearPublicError();
  try {
    const result = await client.publishRelease(
      attempt.request,
      attempt.idempotencyKey,
    );
    pendingAttempt.value = attemptStore.setOperationId(
      attempt,
      result.operationId,
    );
    operation.value = result;
    visualState.value = "ready";
    if (TERMINAL_STATES.has(result.state)) {
      await finishTerminal(result);
    } else {
      startPolling(false);
    }
  } catch (error) {
    classifyError(
      error,
      "O envio não pôde ser confirmado. Retome a tentativa explicitamente.",
    );
  } finally {
    submitting.value = false;
  }
}

function clearPollingTimer() {
  if (pollTimer !== null) {
    clearTimeout(pollTimer);
    pollTimer = null;
  }
}

function schedulePoll() {
  clearPollingTimer();
  if (
    disposed ||
    pollingPaused.value ||
    operation.value === null ||
    !ACTIVE_STATES.has(operation.value.state)
  ) {
    return;
  }
  pollTimer = setTimeout(() => {
    void pollOperation();
  }, props.pollIntervalMs ?? POLL_INTERVAL_MS);
}

function startPolling(immediate) {
  pollingPaused.value = false;
  pollingStartedAt = Date.now();
  if (immediate) {
    void pollOperation();
  } else {
    schedulePoll();
  }
}

async function pollOperation() {
  if (
    pollInFlight ||
    pendingAttempt.value?.operationId == null ||
    operation.value === null ||
    !ACTIVE_STATES.has(operation.value.state)
  ) {
    return;
  }
  if (Date.now() - pollingStartedAt >= (props.pollTimeoutMs ?? POLL_TIMEOUT_MS)) {
    pollingPaused.value = true;
    clearPollingTimer();
    return;
  }
  pollInFlight = true;
  try {
    const result = await client.getOperation(
      pendingAttempt.value.operationId,
      pendingAttempt.value.request.candidateId,
    );
    operation.value = result;
    clearPublicError();
    if (TERMINAL_STATES.has(result.state)) {
      await finishTerminal(result);
    } else {
      schedulePoll();
    }
  } catch (error) {
    classifyError(error, "Não foi possível atualizar o estado da publicação.");
    pollingPaused.value = true;
    clearPollingTimer();
  } finally {
    pollInFlight = false;
  }
}

async function refreshOperation() {
  if (pendingAttempt.value?.operationId == null) return;
  pollingPaused.value = false;
  pollingStartedAt = Date.now();
  await pollOperation();
}

async function finishTerminal(result) {
  clearPollingTimer();
  pollingPaused.value = false;
  operation.value = result;
  try {
    attemptStore.clear();
    pendingAttempt.value = null;
  } catch {
    publicMessage.value = "Armazenamento da sessão indisponível.";
  }
  try {
    await refreshLists();
  } catch (error) {
    classifyError(error, "Não foi possível atualizar candidatos e releases.");
  }
}

function discardAttempt() {
  discardConfirmationOpen.value = false;
  try {
    attemptStore.clear();
    pendingAttempt.value = null;
    clearPublicError();
  } catch {
    publicMessage.value = "Armazenamento da sessão indisponível.";
  }
}

async function bootstrap() {
  if (props.config.mode !== "publisher") return;
  try {
    initializeDependencies();
    attemptStore.ensureAvailable();
    await client.ensureCapabilities();
    await refreshLists();
    pendingAttempt.value = attemptStore.read();
    if (pendingAttempt.value !== null) {
      restoreForm(pendingAttempt.value);
    }
    visualState.value = "ready";
    if (pendingAttempt.value?.operationId) {
      operation.value = {
        operationId: pendingAttempt.value.operationId,
        state: "REQUESTED",
        candidateId: pendingAttempt.value.request.candidateId,
        release: null,
        workflowRunUrl: null,
        createdAt: pendingAttempt.value.createdAt,
        updatedAt: pendingAttempt.value.createdAt,
        errorCode: null,
      };
      startPolling(true);
    }
  } catch (error) {
    classifyError(error, "O serviço de releases está indisponível.");
    if (!["session-expired", "forbidden"].includes(visualState.value)) {
      visualState.value = "unavailable";
    }
  }
}

onMounted(() => {
  void bootstrap();
});

onBeforeUnmount(() => {
  disposed = true;
  clearPollingTimer();
});

defineExpose({
  candidates,
  releases,
  selectedCandidateId,
  versionBump,
  description,
  changelog,
  visualState,
  pendingAttempt,
  operation,
  pollingPaused,
  discardConfirmationOpen,
  estimatedVersion,
  openConfirmation,
  cancelConfirmation,
  confirmSubmission,
  resumeSubmission,
  discardAttempt,
  refreshOperation,
  loadMoreCandidates,
});
</script>

<style scoped>
.release-publisher {
  max-width: 960px;
}
</style>
