import { mount } from "@vue/test-utils";
import { nextTick } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/global", () => ({ baseApiUrl: "http://127.0.0.1:8080" }));

import ReleasePublisherConfig from "./ReleasePublisherConfig.vue";

const CANDIDATE_ID = "candidate-1234567890abcdef";
const SHA = "a".repeat(40);
const OPERATION_ID = `pub_${"b".repeat(32)}`;
const PUBLISHER_CONFIG = {
  mode: "publisher",
  url: "http://127.0.0.1:8090",
};

function candidate(id = CANDIDATE_ID, commitSubject = "fix: assunto do commit") {
  return {
    candidateId: id,
    sourceCommit: SHA,
    eligibility: "READY",
    ciStatus: "PASSED",
    manifestStatus: "VALID",
    createdAt: "2026-07-29T12:00:00Z",
    commitSubject,
  };
}

function release(version = "v1.2.3") {
  return {
    release: version,
    sourceCommit: SHA,
    state: "PUBLISHED",
    publishedAt: "2026-07-29T12:00:00Z",
  };
}

function operation(state = "REQUESTED", overrides = {}) {
  return {
    operationId: OPERATION_ID,
    state,
    candidateId: CANDIDATE_ID,
    release: state === "PUBLISHED" ? "v1.2.4" : null,
    workflowRunUrl: null,
    createdAt: "2026-07-29T12:00:00Z",
    updatedAt: "2026-07-29T12:00:01Z",
    errorCode: state === "FAILED" ? "PRIVATE_INTERNAL_CLASSIFICATION" : null,
    ...overrides,
  };
}

function attempt(operationId = null) {
  return {
    schemaVersion: 1,
    idempotencyKey: "publisher-ui-12345678-1234-4234-9234-123456789abc",
    request: {
      candidateId: CANDIDATE_ID,
      versionBump: "PATCH",
      description: "Release local",
      changelog: "Alterações validadas",
    },
    operationId,
    createdAt: "2026-07-29T12:00:00.000Z",
  };
}

function client(overrides = {}) {
  return {
    ensureCapabilities: vi.fn().mockResolvedValue({
      mode: "publisher",
      apiVersion: "v1",
      capabilities: ["release:read", "release:publish"],
    }),
    listCandidates: vi.fn().mockResolvedValue({
      items: [candidate()],
      nextCursor: null,
    }),
    listReleases: vi.fn().mockResolvedValue([release()]),
    publishRelease: vi.fn().mockResolvedValue(operation()),
    getOperation: vi.fn().mockResolvedValue(operation("VALIDATING")),
    ...overrides,
  };
}

function attemptStore(initial = null, order = null) {
  let stored = initial;
  return {
    ensureAvailable: vi.fn(),
    read: vi.fn(() => stored),
    create: vi.fn((request) => {
      order?.push("persist");
      stored = { ...attempt(), request };
      return stored;
    }),
    setOperationId: vi.fn((value, operationId) => {
      stored = { ...value, operationId };
      return stored;
    }),
    clear: vi.fn(() => {
      stored = null;
    }),
  };
}

function mountComponent({
  configuredClient = client(),
  configuredStore = attemptStore(),
  config = PUBLISHER_CONFIG,
  pollIntervalMs = 3000,
  pollTimeoutMs = 600000,
} = {}) {
  const passthrough = {
    template: "<div><slot /><slot name=\"action\" /></div>",
  };
  return mount(ReleasePublisherConfig, {
    props: {
      config,
      client: configuredClient,
      attemptStore: configuredStore,
      pollIntervalMs,
      pollTimeoutMs,
    },
    global: {
      stubs: {
        "q-banner": passthrough,
        "q-spinner": passthrough,
        "q-card": passthrough,
        "q-card-section": passthrough,
        "q-card-actions": passthrough,
        "q-form": passthrough,
        "q-select": passthrough,
        "q-input": passthrough,
        "q-btn": passthrough,
        "q-dialog": passthrough,
        "q-list": passthrough,
        "q-item": passthrough,
        "q-item-section": passthrough,
        "q-item-label": passthrough,
      },
    },
  });
}

async function flush() {
  for (let index = 0; index < 8; index += 1) {
    await Promise.resolve();
    await nextTick();
  }
}

async function readyWrapper(options = {}) {
  const wrapper = mountComponent(options);
  await flush();
  return wrapper;
}

async function fillValidForm(wrapper) {
  wrapper.vm.selectedCandidateId = CANDIDATE_ID;
  wrapper.vm.versionBump = "PATCH";
  wrapper.vm.description = "  Release local  ";
  wrapper.vm.changelog = "  Alterações validadas  ";
  await nextTick();
}

describe("ReleasePublisherConfig", () => {
  beforeEach(() => {
    vi.useRealTimers();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("exibe configuração desabilitada sem criar sessão publisher", async () => {
    const configuredClient = client();
    const wrapper = await readyWrapper({
      config: { mode: "disabled", url: null },
      configuredClient,
    });

    expect(wrapper.get('[data-testid="disabled-state"]').text()).toContain(
      "desabilitada",
    );
    expect(configuredClient.ensureCapabilities).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it("carrega somente candidatos READY, release atual e estimativa", async () => {
    const wrapper = await readyWrapper();

    expect(wrapper.vm.visualState).toBe("ready");
    expect(wrapper.vm.candidates).toEqual([candidate()]);
    expect(wrapper.get('[data-testid="current-release"]').text()).toBe("v1.2.3");
    expect(wrapper.get('[data-testid="estimated-version"]').text()).toBe("v1.2.4");

    wrapper.vm.versionBump = "MAJOR";
    await nextTick();
    expect(wrapper.get('[data-testid="estimated-version"]').text()).toBe("v2.0.0");
    wrapper.unmount();
  });

  it("lista os commits que entram na release sem pedir escolha ao usuário", async () => {
    const configuredClient = client({
      listCandidates: vi.fn().mockResolvedValue({
        items: [
          candidate("candidate-novo-0000000000", "feat: UI padrão sem tema"),
          candidate("candidate-antigo-000000000", "fix: fallback de imagem"),
        ],
        nextCursor: null,
      }),
    });
    const wrapper = await readyWrapper({ configuredClient });

    const list = wrapper.get('[data-testid="incoming-candidates"]');
    expect(list.text()).toContain("feat: UI padrão sem tema");
    expect(list.text()).toContain("fix: fallback de imagem");
    expect(list.text()).toContain(SHA.slice(0, 7));
    // Comunicar, nao perguntar: nada na lista e selecionavel.
    expect(list.findAll("input")).toHaveLength(0);
    wrapper.unmount();
  });

  it("aponta sempre para o candidato mais recente, inclusive após concluir uma publicação", async () => {
    // Sem seletor na tela, uma selecao presa ao candidato ja publicado publicaria
    // de novo o mesmo commit e ignoraria os pushes que chegaram depois.
    const listCandidates = vi
      .fn()
      .mockResolvedValueOnce({ items: [candidate()], nextCursor: null })
      .mockResolvedValue({
        items: [candidate("candidate-novo-0000000000")],
        nextCursor: null,
      });
    const configuredClient = client({
      listCandidates,
      getOperation: vi.fn().mockResolvedValue(operation("PUBLISHED")),
    });
    const wrapper = await readyWrapper({
      configuredClient,
      configuredStore: attemptStore(attempt(OPERATION_ID)),
    });
    await flush();

    expect(wrapper.vm.operation.state).toBe("PUBLISHED");
    expect(wrapper.vm.selectedCandidateId).toBe("candidate-novo-0000000000");
    wrapper.unmount();
  });

  it("tentativa persistida mantém seu candidato mesmo com candidato mais novo no main", async () => {
    const configuredClient = client({
      listCandidates: vi.fn().mockResolvedValue({
        items: [candidate("candidate-novo-0000000000"), candidate()],
        nextCursor: null,
      }),
    });
    const wrapper = await readyWrapper({
      configuredClient,
      configuredStore: attemptStore(attempt()),
    });

    expect(wrapper.vm.selectedCandidateId).toBe(CANDIDATE_ID);
    wrapper.unmount();
  });

  it("cancelar confirmação não cria tentativa nem idempotency key", async () => {
    const configuredStore = attemptStore();
    const wrapper = await readyWrapper({ configuredStore });
    await fillValidForm(wrapper);

    wrapper.vm.openConfirmation();
    expect(wrapper.vm.confirmationOpen).toBe(true);
    wrapper.vm.cancelConfirmation();

    expect(configuredStore.create).not.toHaveBeenCalled();
    expect(wrapper.vm.confirmationOpen).toBe(false);
    wrapper.unmount();
  });

  it("persiste a tentativa antes do POST e envia payload trimado", async () => {
    const order = [];
    const configuredStore = attemptStore(null, order);
    const configuredClient = client({
      publishRelease: vi.fn(async () => {
        order.push("publish");
        return operation();
      }),
    });
    const wrapper = await readyWrapper({ configuredClient, configuredStore });
    await fillValidForm(wrapper);

    wrapper.vm.openConfirmation();
    await wrapper.vm.confirmSubmission();

    expect(order).toEqual(["persist", "publish"]);
    expect(configuredClient.publishRelease).toHaveBeenCalledWith(
      {
        candidateId: CANDIDATE_ID,
        versionBump: "PATCH",
        description: "Release local",
        changelog: "Alterações validadas",
      },
      attempt().idempotencyKey,
    );
    expect(configuredStore.setOperationId).toHaveBeenCalledWith(
      expect.any(Object),
      OPERATION_ID,
    );
    wrapper.unmount();
  });

  it("preserva envio incerto e só reenvia por ação explícita com a mesma tentativa", async () => {
    const networkError = Object.assign(new Error("raw secret response"), {
      kind: "NETWORK_ERROR",
    });
    const configuredClient = client({
      publishRelease: vi
        .fn()
        .mockRejectedValueOnce(networkError)
        .mockResolvedValueOnce(operation()),
    });
    const configuredStore = attemptStore();
    const wrapper = await readyWrapper({ configuredClient, configuredStore });
    await fillValidForm(wrapper);

    wrapper.vm.openConfirmation();
    await wrapper.vm.confirmSubmission();
    expect(configuredClient.publishRelease).toHaveBeenCalledTimes(1);
    expect(wrapper.get('[data-testid="resumable-state"]').text()).toContain(
      "não será reenviada automaticamente",
    );
    expect(wrapper.text()).not.toContain("raw secret response");

    await wrapper.vm.resumeSubmission();
    expect(configuredClient.publishRelease).toHaveBeenCalledTimes(2);
    expect(configuredClient.publishRelease.mock.calls[1]).toEqual(
      configuredClient.publishRelease.mock.calls[0],
    );
    wrapper.unmount();
  });

  it("Descartar tentativa exige confirmação local e não simula cancelamento remoto", async () => {
    const configuredStore = attemptStore(attempt());
    const configuredClient = client();
    const wrapper = await readyWrapper({ configuredClient, configuredStore });

    expect(wrapper.find('[data-testid="resumable-state"]').exists()).toBe(true);
    wrapper.vm.discardConfirmationOpen = true;
    wrapper.vm.discardAttempt();

    expect(wrapper.vm.discardConfirmationOpen).toBe(false);
    expect(configuredStore.clear).toHaveBeenCalledTimes(1);
    expect(configuredClient.publishRelease).not.toHaveBeenCalled();
    expect(configuredClient.getOperation).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it("restaura operationId consultando estado sem publicar novamente", async () => {
    const restored = attempt(OPERATION_ID);
    const configuredClient = client({
      getOperation: vi.fn().mockResolvedValue(operation("VALIDATING")),
    });
    const wrapper = await readyWrapper({
      configuredClient,
      configuredStore: attemptStore(restored),
    });
    await flush();

    expect(configuredClient.publishRelease).not.toHaveBeenCalled();
    expect(configuredClient.getOperation).toHaveBeenCalledWith(
      OPERATION_ID,
      CANDIDATE_ID,
    );
    expect(wrapper.vm.operation.state).toBe("VALIDATING");
    wrapper.unmount();
  });

  it("polling usa 3 segundos, não sobrepõe requests e avança estados", async () => {
    vi.useFakeTimers();
    let resolvePoll;
    const configuredClient = client({
      getOperation: vi.fn(
        () =>
          new Promise((resolve) => {
            resolvePoll = resolve;
          }),
      ),
    });
    const wrapper = await readyWrapper({
      configuredClient,
      configuredStore: attemptStore(attempt(OPERATION_ID)),
    });

    expect(configuredClient.getOperation).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(3000);
    expect(configuredClient.getOperation).toHaveBeenCalledTimes(1);

    resolvePoll(operation("PUBLISHING"));
    await flush();
    await vi.advanceTimersByTimeAsync(3000);
    expect(configuredClient.getOperation).toHaveBeenCalledTimes(2);
    wrapper.unmount();
  });

  it("estado terminal para polling, limpa tentativa e atualiza listas", async () => {
    vi.useFakeTimers();
    const configuredStore = attemptStore(attempt(OPERATION_ID));
    const configuredClient = client({
      getOperation: vi.fn().mockResolvedValue(operation("PUBLISHED")),
    });
    const wrapper = await readyWrapper({ configuredClient, configuredStore });
    await flush();

    expect(wrapper.vm.operation.state).toBe("PUBLISHED");
    expect(configuredStore.clear).toHaveBeenCalledTimes(1);
    expect(configuredClient.listCandidates).toHaveBeenCalledTimes(2);
    expect(configuredClient.listReleases).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(6000);
    expect(configuredClient.getOperation).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it("unmount cancela o timer de polling", async () => {
    vi.useFakeTimers();
    const configuredClient = client();
    const wrapper = await readyWrapper({
      configuredClient,
      configuredStore: attemptStore(attempt(OPERATION_ID)),
    });
    await flush();
    const calls = configuredClient.getOperation.mock.calls.length;

    wrapper.unmount();
    await vi.advanceTimersByTimeAsync(6000);
    expect(configuredClient.getOperation).toHaveBeenCalledTimes(calls);
  });

  it("timeout pausa polling e preserva tentativa para atualização manual", async () => {
    vi.useFakeTimers();
    const configuredStore = attemptStore(attempt(OPERATION_ID));
    const configuredClient = client({
      getOperation: vi.fn().mockResolvedValue(operation("VALIDATING")),
    });
    const wrapper = await readyWrapper({
      configuredClient,
      configuredStore,
      pollIntervalMs: 3000,
      pollTimeoutMs: 3000,
    });
    await flush();

    await vi.advanceTimersByTimeAsync(3000);
    await flush();
    expect(wrapper.vm.pollingPaused).toBe(true);
    expect(configuredStore.clear).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="polling-paused-state"]').text()).toContain(
      "tentativa permanece salva",
    );
    wrapper.unmount();
  });

  it("FAILED não renderiza errorCode bruto, detail remoto, token ou stack", async () => {
    const configuredClient = client({
      getOperation: vi.fn().mockResolvedValue(
        operation("FAILED", {
          errorCode: "GITHUB_SECRET_FAILURE",
        }),
      ),
    });
    const wrapper = await readyWrapper({
      configuredClient,
      configuredStore: attemptStore(attempt(OPERATION_ID)),
    });
    await flush();

    expect(wrapper.text()).toContain(
      "A publicação falhou. Consulte a operação nos logs do serviço.",
    );
    expect(wrapper.text()).not.toContain("GITHUB_SECRET_FAILURE");
    expect(wrapper.text()).not.toContain("accessToken");
    expect(wrapper.text()).not.toContain("stack");
    wrapper.unmount();
  });

  it("mostra somente mensagem pública e traceId sanitizado", async () => {
    const configuredClient = client({
      ensureCapabilities: vi.fn().mockRejectedValue({
        code: "SERVICE_UNAVAILABLE",
        traceId: "trace-support-123",
        detail: "remote response with token=secret",
      }),
    });
    const wrapper = await readyWrapper({ configuredClient });

    expect(wrapper.vm.visualState).toBe("unavailable");
    expect(wrapper.text()).toContain("O serviço de releases está indisponível.");
    expect(wrapper.text()).toContain("Código de suporte: trace-support-123");
    expect(wrapper.text()).not.toContain("remote response");
    expect(wrapper.text()).not.toContain("token=secret");
    wrapper.unmount();
  });
});
