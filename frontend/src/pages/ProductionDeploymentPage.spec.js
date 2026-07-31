import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const testState = vi.hoisted(() => ({
  client: null,
}));

vi.mock("@/global", () => ({ baseApiUrl: "https://erp.invalid" }));
vi.mock("@/config/releaseDeployer", () => ({
  releaseDeployerConfig: { mode: "deployer" },
}));
vi.mock("@/services/releaseDeployerClient", () => {
  class FakeReleaseDeployerClientError extends Error {
    constructor(kind, options = {}) {
      super(options.code || "Não foi possível concluir a solicitação.");
      this.kind = kind;
      this.code = options.code || null;
      this.traceId = options.traceId || null;
      this.activeOperationId = options.activeOperationId || null;
    }
  }
  return {
    ReleaseDeployerClientError: FakeReleaseDeployerClientError,
    createReleaseDeployerClient: () => testState.client,
  };
});

import ProductionDeploymentPage from "./ProductionDeploymentPage.vue";

const COMPONENTS = [
  "backend",
  "website_back",
  "frontend",
  "website_front",
  "whatsapp_service",
  "gateway",
];

function validPlan() {
  return {
    sourceRelease: "v1.3.0",
    targetRelease: "v1.4.0",
    components: COMPONENTS.map((component) => ({
      component,
      action: "UPDATE",
      currentDigest: null,
      targetDigest: `sha256:${"a".repeat(64)}`,
    })),
    migrationRequired: false,
    backupRequired: true,
  };
}

function validOperation(state = "QUEUED") {
  return {
    operationId: "dep_0123456789abcdef0123456789abcdef",
    operationType: "deployment",
    state,
    targetRelease: "v1.4.0",
    createdAt: "2026-07-31T12:00:00.000Z",
    updatedAt: "2026-07-31T12:00:00.000Z",
  };
}

function validRollbackOperation(state = "QUEUED") {
  return {
    operationId: "rbk_0123456789abcdef0123456789abcdef",
    operationType: "rollback",
    state,
    sourceRelease: "v1.4.0",
    targetRelease: "v1.3.0",
    databaseRestoreRequired: state === "RESTORING",
    createdAt: "2026-07-31T12:00:00.000Z",
    updatedAt: "2026-07-31T12:00:00.000Z",
  };
}

function makeClient() {
  return {
    capabilities: vi.fn(async () => ({
      mode: "deployer",
      apiVersion: "v1",
      capabilities: ["deployment:read", "deployment:execute", "deployment:rollback"],
    })),
    current: vi.fn(async () => ({
      release: "v1.3.0",
      sourceCommit: "b".repeat(40),
      installedAt: "2026-07-30T12:00:00.000Z",
      reconciled: true,
    })),
    releases: vi.fn(async () => [
      {
        release: "v1.4.0",
        sourceCommit: "c".repeat(40),
        publishedAt: "2026-07-31T11:00:00.000Z",
        eligible: true,
      },
    ]),
    plan: vi.fn(async () => validPlan()),
    requestDeployment: vi.fn(async () => validOperation()),
    operation: vi.fn(async () => validOperation()),
    requestRollback: vi.fn(async () => validRollbackOperation()),
    rollbackOperation: vi.fn(async () => validRollbackOperation()),
  };
}

const passthrough = { template: "<div><slot /></div>" };
const button = {
  props: ["label", "disable", "loading"],
  emits: ["click"],
  template: '<button :disabled="disable" @click="$emit(\'click\')">{{ label }}</button>',
};

function render() {
  return mount(ProductionDeploymentPage, {
    global: {
      stubs: {
        "q-page": passthrough,
        "q-banner": passthrough,
        "q-card": passthrough,
        "q-card-section": passthrough,
        "q-card-actions": passthrough,
        "q-btn": button,
      },
    },
  });
}

describe("ProductionDeploymentPage", () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
    testState.client = makeClient();
    vi.stubGlobal("confirm", vi.fn(() => true));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("loads only after exact capabilities and sends the persisted idempotent request", async () => {
    const wrapper = render();
    await flushPromises();
    expect(wrapper.text()).toContain("Confirmar atualização");
    expect(wrapper.text()).toContain("backend");

    await wrapper.get("button").trigger("click");
    await flushPromises();
    expect(testState.client.requestDeployment).toHaveBeenCalledWith(
      "v1.4.0",
      expect.stringMatching(/^deployer-ui-[0-9a-f-]+$/),
    );
    const saved = JSON.parse(sessionStorage.getItem("emporio.releaseDeployer.pending.v1"));
    expect(saved.release).toBe("v1.4.0");
    expect(saved.operationId).toBe(validOperation().operationId);
    expect(JSON.stringify(saved)).not.toMatch(/token|digest|workflow/i);
    wrapper.unmount();
  });

  it("sends a rollback with only the server-listed release, reason and separate idempotency key", async () => {
    testState.client.releases.mockResolvedValueOnce([
      {
        release: "v1.3.0",
        sourceCommit: "c".repeat(40),
        publishedAt: "2026-07-31T11:00:00.000Z",
        eligible: false,
      },
    ]);
    testState.client.requestRollback.mockResolvedValueOnce({
      ...validRollbackOperation(),
      workflowRunUrl: "https://github.invalid/internal/run/123",
      errorCode: null,
    });
    const wrapper = render();
    await flushPromises();
    await wrapper.get("#rollback-release").setValue("v1.3.0");
    await wrapper.get("#rollback-reason").setValue("Operator solicitou retorno seguro");
    await wrapper
      .findAll("button")
      .find((item) => item.text() === "Solicitar rollback")
      .trigger("click");
    await flushPromises();

    expect(testState.client.requestRollback).toHaveBeenCalledWith(
      "v1.3.0",
      "Operator solicitou retorno seguro",
      expect.stringMatching(/^deployer-rollback-[0-9a-f-]+$/),
    );
    const saved = JSON.parse(
      sessionStorage.getItem("emporio.releaseDeployer.rollback.pending.v1"),
    );
    expect(saved.release).toBe("v1.3.0");
    expect(saved.reason).toBe("Operator solicitou retorno seguro");
    expect(saved.operationId).toBe(validRollbackOperation().operationId);
    expect(JSON.stringify(saved)).not.toMatch(/token|digest|workflow|command/i);
    expect(wrapper.text()).not.toMatch(/github\.invalid|workflow|errorCode/i);
    expect(testState.client.plan).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it("does not offer action when capability exchange or validation fails", async () => {
    testState.client.capabilities.mockRejectedValueOnce(new Error("invalid capability"));
    const wrapper = render();
    await flushPromises();
    expect(wrapper.text()).toContain("Atualização de produção indisponível");
    expect(wrapper.text()).not.toContain("Confirmar atualização");
    expect(testState.client.current).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it("blocks plan and POST for an uncertain installation", async () => {
    const error = new (await import("@/services/releaseDeployerClient")).ReleaseDeployerClientError(
      "PUBLIC_PROBLEM",
      { code: "CURRENT_INSTALLATION_UNRECONCILED", traceId: "trace-123456" },
    );
    testState.client.current.mockRejectedValueOnce(error);
    const wrapper = render();
    await flushPromises();
    expect(wrapper.text()).toContain("Instalação incerta");
    expect(wrapper.text()).not.toContain("Confirmar atualização");
    expect(testState.client.plan).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it("uses the server eligibility cardinality without computing a local candidate", async () => {
    testState.client.releases.mockResolvedValueOnce([]);
    const clean = render();
    await flushPromises();
    expect(clean.text()).toContain("Nenhuma atualização disponível");
    expect(testState.client.plan).not.toHaveBeenCalled();
    clean.unmount();

    testState.client = makeClient();
    testState.client.releases.mockResolvedValueOnce([
      {
        release: "v1.4.0",
        sourceCommit: "c".repeat(40),
        publishedAt: "2026-07-31T11:00:00.000Z",
        eligible: true,
      },
      {
        release: "v1.5.0",
        sourceCommit: "d".repeat(40),
        publishedAt: "2026-07-31T11:30:00.000Z",
        eligible: true,
      },
    ]);
    const inconsistent = render();
    await flushPromises();
    expect(inconsistent.text()).toContain("Não foi possível determinar uma release elegível");
    expect(testState.client.plan).not.toHaveBeenCalled();
    inconsistent.unmount();
  });

  it("reloads an operation using status only and removes terminal pending state", async () => {
    const resumedOperation = {
      ...validOperation("SUCCEEDED"),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    sessionStorage.setItem(
      "emporio.releaseDeployer.pending.v1",
      JSON.stringify({
        schemaVersion: 1,
        idempotencyKey: "deployer-ui-12345678-1234-4234-9234-123456789abc",
        release: "v1.4.0",
        operationId: validOperation().operationId,
        createdAt: resumedOperation.createdAt,
      }),
    );
    testState.client.operation.mockResolvedValueOnce(resumedOperation);
    expect(JSON.parse(sessionStorage.getItem("emporio.releaseDeployer.pending.v1")).operationId).toBe(
      validOperation().operationId,
    );
    const wrapper = render();
    await flushPromises();
    expect(testState.client.current).not.toHaveBeenCalled();
    expect(testState.client.releases).not.toHaveBeenCalled();
    await vi.waitFor(() =>
      expect(testState.client.operation).toHaveBeenCalledWith(validOperation().operationId),
    );
    expect(sessionStorage.getItem("emporio.releaseDeployer.pending.v1")).toBeNull();
    expect(wrapper.text()).toContain("Atualização concluída");
    wrapper.unmount();
  });

  it("reloads rollback through GET only and blocks an uncertain installation", async () => {
    const uncertain = {
      ...validRollbackOperation("UNCERTAIN"),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    sessionStorage.setItem(
      "emporio.releaseDeployer.rollback.pending.v1",
      JSON.stringify({
        schemaVersion: 1,
        idempotencyKey: "deployer-rollback-12345678-1234-4234-9234-123456789abc",
        release: "v1.3.0",
        reason: "Operator solicitou retorno seguro",
        operationId: uncertain.operationId,
        createdAt: uncertain.createdAt,
      }),
    );
    testState.client.rollbackOperation.mockResolvedValueOnce(uncertain);
    const wrapper = render();
    await flushPromises();
    await vi.waitFor(() =>
      expect(testState.client.rollbackOperation).toHaveBeenCalledWith(uncertain.operationId),
    );
    expect(testState.client.current).not.toHaveBeenCalled();
    expect(testState.client.releases).not.toHaveBeenCalled();
    expect(testState.client.requestRollback).not.toHaveBeenCalled();
    expect(sessionStorage.getItem("emporio.releaseDeployer.rollback.pending.v1")).not.toBeNull();
    expect(wrapper.text()).toContain("Instalação incerta");
    expect(wrapper.text()).toContain("não iniciar nova operação");
    wrapper.unmount();
  });

  it.each(["ROLLED_BACK", "FAILED"])(
    "treats rollback %s as terminal without reporting success",
    async (state) => {
      testState.client.requestRollback.mockResolvedValueOnce(validRollbackOperation(state));
      const wrapper = render();
      await flushPromises();
      await wrapper.get("#rollback-release").setValue("v1.4.0");
      await wrapper.get("#rollback-reason").setValue("Operator solicitou retorno seguro");
      await wrapper
        .findAll("button")
        .find((item) => item.text() === "Solicitar rollback")
        .trigger("click");
      await flushPromises();
      expect(wrapper.text()).not.toContain("Rollback comercial concluído");
      expect(sessionStorage.getItem("emporio.releaseDeployer.rollback.pending.v1")).toBeNull();
      wrapper.unmount();
    },
  );

  it("does not continue polling or invent a state outside the accepted enum", async () => {
    testState.client.requestDeployment.mockResolvedValueOnce(validOperation("PULLING"));
    const wrapper = render();
    await flushPromises();
    await wrapper.get("button").trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("Não foi possível concluir a solicitação");
    expect(testState.client.operation).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it("stops rollback polling at ten minutes and preserves the pending operation", async () => {
    sessionStorage.setItem(
      "emporio.releaseDeployer.rollback.pending.v1",
      JSON.stringify({
        schemaVersion: 1,
        idempotencyKey: "deployer-rollback-12345678-1234-4234-9234-123456789abc",
        release: "v1.3.0",
        reason: "Operator solicitou retorno seguro",
        operationId: validRollbackOperation().operationId,
        createdAt: "2026-07-31T12:00:00.000Z",
      }),
    );
    const wrapper = render();
    await flushPromises();
    expect(testState.client.rollbackOperation).not.toHaveBeenCalled();
    expect(sessionStorage.getItem("emporio.releaseDeployer.rollback.pending.v1")).not.toBeNull();
    expect(wrapper.text()).toContain("Não foi possível concluir a solicitação");
    wrapper.unmount();
  });

  it("stops at the continuous ten-minute timeout and preserves the attempt", async () => {
    sessionStorage.setItem(
      "emporio.releaseDeployer.pending.v1",
      JSON.stringify({
        schemaVersion: 1,
        idempotencyKey: "deployer-ui-12345678-1234-4234-9234-123456789abc",
        release: "v1.4.0",
        operationId: validOperation().operationId,
        createdAt: "2026-07-31T12:00:00.000Z",
      }),
    );
    const wrapper = render();
    await flushPromises();
    expect(testState.client.operation).not.toHaveBeenCalled();
    expect(sessionStorage.getItem("emporio.releaseDeployer.pending.v1")).not.toBeNull();
    expect(wrapper.text()).toContain("Não foi possível concluir a solicitação");
    wrapper.unmount();
  });
});
