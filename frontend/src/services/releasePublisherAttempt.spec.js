import { describe, expect, it, vi } from "vitest";
import {
  MAX_ATTEMPT_BYTES,
  PENDING_ATTEMPT_KEY,
  createReleasePublisherAttemptStore,
  validatePendingAttempt,
} from "./releasePublisherAttempt";

const UUID = "12345678-1234-4234-9234-123456789abc";
const NOW = new Date("2026-07-29T18:00:00.000Z");
const request = {
  candidateId: "candidate_123",
  versionBump: "PATCH",
  description: "Descrição",
  changelog: "Mudança",
};

function memoryStorage() {
  const values = new Map();
  return {
    getItem: vi.fn((key) => values.get(key) ?? null),
    setItem: vi.fn((key, value) => values.set(key, value)),
    removeItem: vi.fn((key) => values.delete(key)),
    values,
  };
}

function store(storage = memoryStorage()) {
  return {
    storage,
    attempts: createReleasePublisherAttemptStore({
      storage,
      randomUUID: () => UUID,
      now: () => NOW,
    }),
  };
}

describe("releasePublisherAttempt", () => {
  it("creates the exact UUID v4 record before any caller can send", () => {
    const fixture = store();
    const attempt = fixture.attempts.create(request);
    expect(attempt).toEqual({
      schemaVersion: 1,
      idempotencyKey: `publisher-ui-${UUID}`,
      request,
      operationId: null,
      createdAt: NOW.toISOString(),
    });
    expect(fixture.storage.setItem).toHaveBeenCalledWith(
      PENDING_ATTEMPT_KEY,
      JSON.stringify(attempt),
    );
    expect(JSON.stringify(attempt)).not.toContain("token");
  });

  it("restores with and without operationId and preserves key/request", () => {
    const fixture = store();
    const created = fixture.attempts.create(request);
    expect(fixture.attempts.read()).toEqual(created);
    const updated = fixture.attempts.setOperationId(
      created,
      "pub_0123456789abcdef0123456789abcdef",
    );
    expect(fixture.attempts.read()).toEqual(updated);
    expect(updated.idempotencyKey).toBe(created.idempotencyKey);
    expect(updated.request).toEqual(created.request);
  });

  it.each([
    ["extra property", (value) => ({ ...value, token: "forbidden" })],
    ["schema", (value) => ({ ...value, schemaVersion: 2 })],
    ["uuid", (value) => ({ ...value, idempotencyKey: "publisher-ui-bad" })],
    ["operation", (value) => ({ ...value, operationId: "bad" })],
    ["timestamp", (value) => ({ ...value, createdAt: "yesterday" })],
    [
      "candidate",
      (value) => ({ ...value, request: { ...value.request, candidateId: "x" } }),
    ],
    [
      "bump",
      (value) => ({ ...value, request: { ...value.request, versionBump: "HOTFIX" } }),
    ],
    [
      "description",
      (value) => ({ ...value, request: { ...value.request, description: " " } }),
    ],
    [
      "changelog",
      (value) => ({
        ...value,
        request: { ...value.request, changelog: "x".repeat(10001) },
      }),
    ],
  ])("removes invalid %s records", (_name, mutate) => {
    const fixture = store();
    const valid = fixture.attempts.create(request);
    fixture.storage.values.set(PENDING_ATTEMPT_KEY, JSON.stringify(mutate(valid)));
    expect(fixture.attempts.read()).toBeNull();
    expect(fixture.storage.removeItem).toHaveBeenCalledWith(PENDING_ATTEMPT_KEY);
  });

  it("rejects records larger than 16 KiB", () => {
    const fixture = store();
    const valid = fixture.attempts.create(request);
    const oversized = {
      ...valid,
      request: {
        ...request,
        candidateId: "x".repeat(128),
        description: "x".repeat(500),
        changelog: "á".repeat(10000),
      },
    };
    expect(new TextEncoder().encode(JSON.stringify(oversized)).length).toBeGreaterThan(
      MAX_ATTEMPT_BYTES,
    );
    expect(() => validatePendingAttempt(oversized)).toThrow();
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
    const fixture = store(unavailable);
    expect(() => fixture.attempts.ensureAvailable()).toThrow(
      "Armazenamento da sessão indisponível.",
    );
    expect(() => fixture.attempts.create(request)).toThrow(
      "Armazenamento da sessão indisponível.",
    );
  });

  it("probes availability using only the canonical pending key", () => {
    const fixture = store();
    fixture.attempts.ensureAvailable();
    expect(fixture.storage.setItem).toHaveBeenCalledWith(PENDING_ATTEMPT_KEY, "");
    expect(fixture.storage.removeItem).toHaveBeenCalledWith(PENDING_ATTEMPT_KEY);
    expect(
      [...fixture.storage.values.keys()].filter((key) => key !== PENDING_ATTEMPT_KEY),
    ).toEqual([]);
  });

  it("clears terminal attempts without touching localStorage", () => {
    const fixture = store();
    fixture.attempts.create(request);
    fixture.attempts.clear();
    expect(fixture.storage.removeItem).toHaveBeenCalledWith(PENDING_ATTEMPT_KEY);
    expect(localStorage.getItem(PENDING_ATTEMPT_KEY)).toBeNull();
  });
});
