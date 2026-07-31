import { describe, expect, it, vi } from "vitest";
import {
  MAX_ROLLBACK_ATTEMPT_BYTES,
  PENDING_ROLLBACK_ATTEMPT_KEY,
  createReleaseDeployerRollbackAttemptStore,
  validateRollbackPendingAttempt,
} from "./releaseDeployerRollbackAttempt";

const UUID = "12345678-1234-4234-9234-123456789abc";
const NOW = new Date("2026-07-31T12:00:00.000Z");

function memoryStorage() {
  const values = new Map();
  return {
    getItem: vi.fn((key) => values.get(key) ?? null),
    setItem: vi.fn((key, value) => values.set(key, value)),
    removeItem: vi.fn((key) => values.delete(key)),
    values,
  };
}

function fixture(storage = memoryStorage()) {
  return {
    storage,
    attempts: createReleaseDeployerRollbackAttemptStore({
      storage,
      randomUUID: () => UUID,
      now: () => NOW,
    }),
  };
}

describe("releaseDeployerRollbackAttempt", () => {
  it("persists only the separate safe rollback record", () => {
    const value = fixture();
    const attempt = value.attempts.create("v1.3.0", "Operator solicitou retorno seguro");
    expect(attempt).toEqual({
      schemaVersion: 1,
      idempotencyKey: `deployer-rollback-${UUID}`,
      release: "v1.3.0",
      reason: "Operator solicitou retorno seguro",
      operationId: null,
      createdAt: NOW.toISOString(),
    });
    expect(value.storage.setItem).toHaveBeenCalledWith(
      PENDING_ROLLBACK_ATTEMPT_KEY,
      JSON.stringify(attempt),
    );
    expect(value.storage.values.has("emporio.releaseDeployer.pending.v1")).toBe(false);
    expect(JSON.stringify(attempt)).not.toMatch(/token|digest|workflow|url|command/i);
  });

  it("keeps the same key, reason and release when resumed with operationId", () => {
    const value = fixture();
    const created = value.attempts.create("v1.3.0", "Operator solicitou retorno seguro");
    const updated = value.attempts.setOperationId(
      created,
      "rbk_0123456789abcdef0123456789abcdef",
    );
    expect(updated.idempotencyKey).toBe(created.idempotencyKey);
    expect(updated.reason).toBe(created.reason);
    expect(value.attempts.read()).toEqual(updated);
  });

  it.each([
    ["extra field", (value) => ({ ...value, token: "forbidden" })],
    ["schema", (value) => ({ ...value, schemaVersion: 2 })],
    ["UUID", (value) => ({ ...value, idempotencyKey: "deployer-ui-invalid" })],
    ["release", (value) => ({ ...value, release: "1.3.0" })],
    ["reason", (value) => ({ ...value, reason: "short" })],
    ["operation", (value) => ({ ...value, operationId: "dep_invalid" })],
    ["date", (value) => ({ ...value, createdAt: "yesterday" })],
  ])("removes %s records", (_name, mutate) => {
    const value = fixture();
    const valid = value.attempts.create("v1.3.0", "Operator solicitou retorno seguro");
    value.storage.values.set(PENDING_ROLLBACK_ATTEMPT_KEY, JSON.stringify(mutate(valid)));
    expect(value.attempts.read()).toBeNull();
    expect(value.storage.removeItem).toHaveBeenCalledWith(PENDING_ROLLBACK_ATTEMPT_KEY);
  });

  it("fails closed when sessionStorage is unavailable", () => {
    const unavailable = {
      getItem: vi.fn(() => {
        throw new Error("blocked");
      }),
      setItem: vi.fn(() => {
        throw new Error("blocked");
      }),
      removeItem: vi.fn(() => {
        throw new Error("blocked");
      }),
    };
    const value = fixture(unavailable);
    expect(() => value.attempts.ensureAvailable()).toThrow(
      "Armazenamento da sessão indisponível.",
    );
    expect(() =>
      value.attempts.create("v1.3.0", "Operator solicitou retorno seguro"),
    ).toThrow("Armazenamento da sessão indisponível.");
  });

  it("enforces the storage size limit and never uses localStorage", () => {
    const value = fixture();
    const valid = value.attempts.create("v1.3.0", "Operator solicitou retorno seguro");
    const oversized = { ...valid, reason: "x".repeat(MAX_ROLLBACK_ATTEMPT_BYTES) };
    expect(() => validateRollbackPendingAttempt(oversized)).toThrow();
    value.attempts.clear();
    expect(localStorage.getItem(PENDING_ROLLBACK_ATTEMPT_KEY)).toBeNull();
  });
});
