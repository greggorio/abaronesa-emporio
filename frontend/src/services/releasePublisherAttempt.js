export const PENDING_ATTEMPT_KEY = "emporio.releasePublisher.pending.v1";
export const MAX_ATTEMPT_BYTES = 16 * 1024;

const UUID_PATTERN =
  /^publisher-ui-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const OPERATION_PATTERN = /^pub_[0-9a-f]{32}$/;
const BUMPS = new Set(["MAJOR", "MINOR", "PATCH"]);

function hasExactKeys(value, expected) {
  return (
    value !== null &&
    typeof value === "object" &&
    !Array.isArray(value) &&
    Object.keys(value).sort().join("|") === [...expected].sort().join("|")
  );
}

export function normalizePublicationRequest(value) {
  if (
    !hasExactKeys(value, [
      "candidateId",
      "versionBump",
      "description",
      "changelog",
    ]) ||
    typeof value.candidateId !== "string" ||
    value.candidateId.length < 12 ||
    value.candidateId.length > 128 ||
    typeof value.versionBump !== "string" ||
    !BUMPS.has(value.versionBump) ||
    typeof value.description !== "string" ||
    value.description !== value.description.trim() ||
    value.description.length < 1 ||
    value.description.length > 500 ||
    typeof value.changelog !== "string" ||
    value.changelog !== value.changelog.trim() ||
    value.changelog.length < 1 ||
    value.changelog.length > 10000
  ) {
    throw new Error("Solicitação de publicação inválida.");
  }
  return Object.freeze({ ...value });
}

export function validatePendingAttempt(value) {
  if (
    !hasExactKeys(value, [
      "schemaVersion",
      "idempotencyKey",
      "request",
      "operationId",
      "createdAt",
    ]) ||
    value.schemaVersion !== 1 ||
    typeof value.idempotencyKey !== "string" ||
    !UUID_PATTERN.test(value.idempotencyKey) ||
    !(
      value.operationId === null ||
      (typeof value.operationId === "string" &&
        OPERATION_PATTERN.test(value.operationId))
    ) ||
    typeof value.createdAt !== "string" ||
    Number.isNaN(Date.parse(value.createdAt)) ||
    new Date(value.createdAt).toISOString() !== value.createdAt
  ) {
    throw new Error("Tentativa salva inválida.");
  }
  normalizePublicationRequest(value.request);
  const serialized = JSON.stringify(value);
  if (new TextEncoder().encode(serialized).length > MAX_ATTEMPT_BYTES) {
    throw new Error("Tentativa salva excede o limite.");
  }
  return Object.freeze({
    ...value,
    request: Object.freeze({ ...value.request }),
  });
}

export function createReleasePublisherAttemptStore({
  storage = globalThis.sessionStorage,
  randomUUID = () => globalThis.crypto.randomUUID(),
  now = () => new Date(),
} = {}) {
  function removeInvalid() {
    try {
      storage.removeItem(PENDING_ATTEMPT_KEY);
    } catch {
      throw new Error("Armazenamento da sessão indisponível.");
    }
  }

  function persist(attempt) {
    const validated = validatePendingAttempt(attempt);
    try {
      storage.setItem(PENDING_ATTEMPT_KEY, JSON.stringify(validated));
    } catch {
      throw new Error("Armazenamento da sessão indisponível.");
    }
    return validated;
  }

  function create(request) {
    const validatedRequest = normalizePublicationRequest(request);
    const uuid = randomUUID();
    const attempt = {
      schemaVersion: 1,
      idempotencyKey: `publisher-ui-${uuid}`,
      request: validatedRequest,
      operationId: null,
      createdAt: now().toISOString(),
    };
    return persist(attempt);
  }

  function read() {
    let raw;
    try {
      raw = storage.getItem(PENDING_ATTEMPT_KEY);
    } catch {
      throw new Error("Armazenamento da sessão indisponível.");
    }
    if (raw === null) {
      return null;
    }
    try {
      return validatePendingAttempt(JSON.parse(raw));
    } catch {
      removeInvalid();
      return null;
    }
  }

  function setOperationId(attempt, operationId) {
    return persist({ ...attempt, operationId });
  }

  function clear() {
    removeInvalid();
  }

  function ensureAvailable() {
    try {
      const existing = storage.getItem(PENDING_ATTEMPT_KEY);
      if (existing === null) {
        storage.setItem(PENDING_ATTEMPT_KEY, "");
        storage.removeItem(PENDING_ATTEMPT_KEY);
      } else {
        storage.setItem(PENDING_ATTEMPT_KEY, existing);
      }
    } catch {
      throw new Error("Armazenamento da sessão indisponível.");
    }
  }

  return Object.freeze({
    create,
    read,
    setOperationId,
    clear,
    ensureAvailable,
  });
}
