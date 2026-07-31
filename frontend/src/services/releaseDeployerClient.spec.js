import { describe, expect, it, vi } from "vitest";
import {
  DEPLOYER_AUDIENCE,
  DEPLOYER_SCOPE,
  ReleaseDeployerClientError,
  createReleaseDeployerClient,
  validateCapabilities,
  validateDeploymentOperation,
  validateDeploymentPlan,
  validateExchange,
  validateRollbackOperation,
} from "./releaseDeployerClient";

const BASE = "https://erp.invalid";
const ERP_TOKEN = "erp-session-token";
const UUID = "deployer-ui-12345678-1234-4234-9234-123456789abc";
const ROLLBACK_UUID =
  "deployer-rollback-12345678-1234-4234-9234-123456789abc";
const SHA = "a".repeat(40);
const DIGEST = `sha256:${"b".repeat(64)}`;
const CREATED = "2026-07-31T12:00:00.000Z";

function encode(value) {
  return Buffer.from(JSON.stringify(value)).toString("base64url");
}

function jwt(overrides = {}) {
  const iat = overrides.iat ?? Math.floor(Date.now() / 1000);
  const claims = {
    aud: DEPLOYER_AUDIENCE,
    scope: DEPLOYER_SCOPE,
    iat,
    exp: iat + 300,
    ...overrides,
  };
  return `${encode({ alg: "RS256", typ: "JWT" })}.${encode(claims)}.signature`;
}

function exchange(overrides = {}) {
  return {
    status: 200,
    data: {
      accessToken: jwt(overrides.claims),
      tokenType: overrides.tokenType ?? "Bearer",
      expiresIn: overrides.expiresIn ?? 300,
      scope: overrides.scope ?? DEPLOYER_SCOPE,
    },
  };
}

function problem(code, status = code === "CURRENT_INSTALLATION_UNRECONCILED" ? 409 : 409) {
  return {
    status,
    data: {
      type: "https://erp.invalid/problems/deployment",
      title: "public problem",
      status,
      code,
      traceId: "trace-123456",
    },
  };
}

function capabilities() {
  return {
    mode: "deployer",
    apiVersion: "v1",
    capabilities: [
      "deployment:read",
      "deployment:execute",
      "deployment:rollback",
    ],
  };
}

function operation(state = "QUEUED") {
  return {
    operationId: "dep_0123456789abcdef0123456789abcdef",
    operationType: "deployment",
    state,
    targetRelease: "v1.4.0",
    createdAt: CREATED,
    updatedAt: CREATED,
  };
}

function rollbackOperation(state = "QUEUED") {
  return {
    operationId: "rbk_0123456789abcdef0123456789abcdef",
    operationType: "rollback",
    state,
    sourceRelease: "v1.4.0",
    targetRelease: "v1.3.0",
    databaseRestoreRequired: state === "RESTORING",
    createdAt: CREATED,
    updatedAt: CREATED,
  };
}

function currentInstallation() {
  return {
    release: "v1.3.0",
    sourceCommit: SHA,
    installedAt: CREATED,
    reconciled: true,
  };
}

function plan() {
  return {
    sourceRelease: "v1.3.0",
    targetRelease: "v1.4.0",
    components: [
      "backend",
      "website_back",
      "frontend",
      "website_front",
      "whatsapp_service",
      "gateway",
    ].map((component) => ({
      component,
      action: "UPDATE",
      currentDigest: null,
      targetDigest: DIGEST,
    })),
    migrationRequired: false,
    backupRequired: true,
  };
}

function clientFor(responses) {
  const calls = [];
  const transport = vi.fn(async (config) => {
    calls.push(config);
    const next = responses.shift();
    if (next instanceof Error) throw next;
    return next;
  });
  return {
    calls,
    transport,
    client: createReleaseDeployerClient({
      baseApiUrl: BASE,
      transport,
      getErpToken: () => ERP_TOKEN,
    }),
  };
}

describe("releaseDeployerClient", () => {
  it.each([
    ["extra capability", { ...capabilities(), extra: true }],
    ["missing capability", { ...capabilities(), capabilities: ["deployment:read"] }],
    ["rollback capability", { ...capabilities(), capabilities: ["deployment:read", "deployment:rollback"] }],
    ["wrong mode", { ...capabilities(), mode: "publisher" }],
    ["wrong version", { ...capabilities(), apiVersion: "v2" }],
  ])("rejects %s", (_name, value) => {
    expect(() => validateCapabilities(value)).toThrow();
  });

  it("rejects capabilities in a non-canonical order", () => {
    expect(() =>
      validateCapabilities({
        ...capabilities(),
        capabilities: [
          "deployment:rollback",
          "deployment:read",
          "deployment:execute",
        ],
      }),
    ).toThrow();
  });

  it.each([
    ["audience", { claims: { aud: "emporio-release-control" } }],
    ["scope", { scope: "deployment:read" }],
    ["TTL", { claims: { iat: 1000, exp: 1301 } }],
    ["token type", { tokenType: "Token" }],
  ])("rejects exchange with invalid %s", (_name, overrides) => {
    expect(() => validateExchange(exchange(overrides).data)).toThrow();
  });

  it("exchanges through the ERP origin and accepts only the exact capability", async () => {
    const value = clientFor([exchange(), { status: 200, data: capabilities() }]);
    await expect(value.client.capabilities()).resolves.toEqual(capabilities());
    expect(value.calls[0]).toMatchObject({
      method: "POST",
      url: `${BASE}/api/release-control/identity/deployer/token`,
      headers: { Authorization: `Bearer ${ERP_TOKEN}` },
    });
    expect(value.calls[0].data).toBeUndefined();
    expect(value.calls[1].url).toBe(`${BASE}/api/release-control/v1/capabilities`);
    expect(value.calls[1].url).not.toContain("8121");
  });

  it("sends only release and Idempotency-Key on deployment", async () => {
    const value = clientFor([exchange(), { status: 202, data: operation() }]);
    await value.client.requestDeployment("v1.4.0", UUID);
    const request = value.calls[1];
    expect(request).toMatchObject({
      method: "POST",
      url: `${BASE}/api/deployment-control/v1/deployments`,
      data: { release: "v1.4.0" },
      headers: { "Idempotency-Key": UUID },
    });
    expect(Object.keys(request.data)).toEqual(["release"]);
    expect(request.url).not.toContain("rollback");
  });

  it("sends only release and reason on rollback and reads the rollback GET", async () => {
    const value = clientFor([
      exchange(),
      { status: 202, data: rollbackOperation() },
      { status: 200, data: rollbackOperation("VERIFYING") },
    ]);
    await expect(
      value.client.requestRollback(
        "v1.3.0",
        "Operator solicitou retorno seguro",
        ROLLBACK_UUID,
      ),
    ).resolves.toEqual(rollbackOperation());
    const post = value.calls[1];
    expect(post).toMatchObject({
      method: "POST",
      url: `${BASE}/api/deployment-control/v1/rollbacks`,
      headers: { "Idempotency-Key": ROLLBACK_UUID },
      data: { release: "v1.3.0", reason: "Operator solicitou retorno seguro" },
    });
    expect(Object.keys(post.data)).toEqual(["release", "reason"]);
    await expect(
      value.client.rollbackOperation(rollbackOperation().operationId),
    ).resolves.toEqual(rollbackOperation("VERIFYING"));
    expect(value.calls[2].url).toBe(
      `${BASE}/api/deployment-control/v1/rollbacks/${rollbackOperation().operationId}`,
    );
    expect(value.calls[2].url).not.toContain("8121");
  });

  it("performs at most one new exchange after a 401", async () => {
    const value = clientFor([
      exchange(),
      { status: 401, data: {} },
      exchange(),
      { status: 200, data: capabilities() },
    ]);
    await expect(value.client.capabilities()).resolves.toEqual(capabilities());
    expect(value.calls.filter((call) => call.url.endsWith("/token"))).toHaveLength(2);
  });

  it("does not retry network failures", async () => {
    const value = clientFor([exchange(), new Error("network")]);
    await expect(value.client.capabilities()).rejects.toMatchObject({ kind: "NETWORK_ERROR" });
    expect(value.calls.filter((call) => call.url.endsWith("/token"))).toHaveLength(1);
  });

  it("handles clean install and uncertain installation without inventing current data", async () => {
    const clean = clientFor([exchange(), { status: 404, data: {} }]);
    await expect(clean.client.current()).resolves.toBeNull();
    const uncertain = clientFor([exchange(), problem("CURRENT_INSTALLATION_UNRECONCILED")]);
    await expect(uncertain.client.current()).rejects.toMatchObject({
      code: "CURRENT_INSTALLATION_UNRECONCILED",
    });
  });

  it("validates a reconciled current installation and rejects malformed fields", async () => {
    const valid = clientFor([exchange(), { status: 200, data: currentInstallation() }]);
    await expect(valid.client.current()).resolves.toEqual(currentInstallation());
    for (const invalid of [
      { ...currentInstallation(), extra: true },
      { ...currentInstallation(), release: "1.3.0" },
      { ...currentInstallation(), sourceCommit: "bad" },
      { ...currentInstallation(), installedAt: "yesterday" },
      { ...currentInstallation(), reconciled: false },
    ]) {
      const value = clientFor([exchange(), { status: 200, data: invalid }]);
      await expect(value.client.current()).rejects.toMatchObject({ kind: "INVALID_RESPONSE" });
    }
  });

  it("rejects invalid schemas and plans without exactly six components", () => {
    expect(() => validateDeploymentPlan({ ...plan(), extra: true })).toThrow();
    expect(() => validateDeploymentOperation({ ...operation(), operationId: "rbk_bad" })).toThrow();
    expect(() => validateDeploymentOperation({ ...operation(), state: "PULLING" })).toThrow();
    expect(() => validateDeploymentPlan({ ...plan(), components: plan().components.slice(0, 5) })).toThrow();
  });

  it("validates every rollback state and rejects divergent response shapes", () => {
    for (const state of [
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
    ]) {
      expect(validateRollbackOperation(rollbackOperation(state)).state).toBe(state);
    }
    for (const invalid of [
      { ...rollbackOperation(), extra: true },
      { ...rollbackOperation(), operationId: "dep_0123456789abcdef0123456789abcdef" },
      { ...rollbackOperation(), operationType: "deployment" },
      { ...rollbackOperation(), state: "PULLING" },
      { ...rollbackOperation(), sourceRelease: "v01.3.0" },
      { ...rollbackOperation(), databaseRestoreRequired: "yes" },
      { ...rollbackOperation(), errorCode: "bad" },
    ]) {
      expect(() => validateRollbackOperation(invalid)).toThrow();
    }
  });

  it("keeps public conflicts safe, including the optional active operation", async () => {
    const value = clientFor([
      exchange(),
      {
        ...problem("PRODUCTION_OPERATION_ACTIVE"),
        data: {
          ...problem("PRODUCTION_OPERATION_ACTIVE").data,
          activeOperationId: operation().operationId,
          detail: "internal detail must not be exposed",
        },
      },
    ]);
    await expect(value.client.requestDeployment("v1.4.0", UUID)).rejects.toMatchObject({
      code: "PRODUCTION_OPERATION_ACTIVE",
      activeOperationId: operation().operationId,
    });
  });

  it("blocks an active-operation conflict whether or not an id is returned", async () => {
    for (const response of [problem("PRODUCTION_OPERATION_ACTIVE"), {
      ...problem("PRODUCTION_OPERATION_ACTIVE"),
      data: {
        ...problem("PRODUCTION_OPERATION_ACTIVE").data,
        detail: "not displayed",
      },
    }]) {
      const value = clientFor([exchange(), response]);
      await expect(value.client.requestDeployment("v1.4.0", UUID)).rejects.toMatchObject({
        code: "PRODUCTION_OPERATION_ACTIVE",
      });
    }
  });

  it("rejects invalid idempotency keys before any request", async () => {
    const value = clientFor([]);
    await expect(value.client.requestDeployment("v1.4.0", "bad")).rejects.toMatchObject({
      kind: "INVALID_REQUEST",
    });
    expect(value.transport).not.toHaveBeenCalled();
  });

  it("rejects malformed rollback requests before transport", async () => {
    const value = clientFor([]);
    for (const args of [
      ["v1.3.0", "short", ROLLBACK_UUID],
      ["v1.3.0", "Operator solicitou retorno seguro", "bad"],
      ["v01.3.0", "Operator solicitou retorno seguro", ROLLBACK_UUID],
    ]) {
      await expect(value.client.requestRollback(...args)).rejects.toMatchObject({
        kind: "INVALID_REQUEST",
      });
    }
    expect(value.transport).not.toHaveBeenCalled();
  });

  it("never accepts a direct private deployer origin", () => {
    expect(() =>
      createReleaseDeployerClient({ baseApiUrl: "http://127.0.0.1:8121" }),
    ).toThrow();
  });
});
