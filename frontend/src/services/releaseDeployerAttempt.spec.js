import { describe, expect, it, vi } from "vitest";
import {
  MAX_ATTEMPT_BYTES,
  PENDING_ATTEMPT_KEY,
  createReleaseDeployerAttemptStore,
  validatePendingAttempt,
} from "./releaseDeployerAttempt";

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
    attempts: createReleaseDeployerAttemptStore({
      storage,
      randomUUID: () => UUID,
      now: () => NOW,
    }),
  };
}

describe("releaseDeployerAttempt", () => {
  it("persists the exact safe record before a request", () => {
    const value = fixture();
    const attempt = value.attempts.create("v1.4.0");
    expect(attempt).toEqual({
      schemaVersion: 1,
      idempotencyKey: `deployer-ui-${UUID}`,
      release: "v1.4.0",
      operationId: null,
      createdAt: NOW.toISOString(),
    });
    expect(value.storage.setItem).toHaveBeenCalledWith(
      PENDING_ATTEMPT_KEY,
      JSON.stringify(attempt),
    );
    expect(JSON.stringify(attempt)).not.toMatch(/token|digest|workflow/i);
  });

  it("keeps the same key and release when operationId is filled", () => {
    const value = fixture();
    const created = value.attempts.create("v1.4.0");
    const updated = value.attempts.setOperationId(
      created,
      "dep_0123456789abcdef0123456789abcdef",
    );
    expect(updated.idempotencyKey).toBe(created.idempotencyKey);
    expect(updated.release).toBe(created.release);
    expect(value.attempts.read()).toEqual(updated);
  });

  it.each([
    ["extra field", (value) => ({ ...value, token: "forbidden" })],
    ["schema", (value) => ({ ...value, schemaVersion: 2 })],
    ["UUID", (value) => ({ ...value, idempotencyKey: "deployer-ui-invalid" })],
    ["release", (value) => ({ ...value, release: "1.4.0" })],
    ["operation", (value) => ({ ...value, operationId: "rbk_invalid" })],
    ["date", (value) => ({ ...value, createdAt: "yesterday" })],
  ])("removes %s records", (_name, mutate) => {
    const value = fixture();
    const valid = value.attempts.create("v1.4.0");
    value.storage.values.set(PENDING_ATTEMPT_KEY, JSON.stringify(mutate(valid)));
    expect(value.attempts.read()).toBeNull();
    expect(value.storage.removeItem).toHaveBeenCalledWith(PENDING_ATTEMPT_KEY);
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
    expect(() => value.attempts.create("v1.4.0")).toThrow(
      "Armazenamento da sessão indisponível.",
    );
  });

  it("enforces the storage size limit and does not use localStorage", () => {
    const value = fixture();
    const valid = value.attempts.create("v1.4.0");
    const oversized = { ...valid, extra: "x".repeat(MAX_ATTEMPT_BYTES) };
    expect(() => validatePendingAttempt(oversized)).toThrow();
    value.attempts.clear();
    expect(localStorage.getItem(PENDING_ATTEMPT_KEY)).toBeNull();
  });
});
