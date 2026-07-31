export const PENDING_ROLLBACK_ATTEMPT_KEY =
  "emporio.releaseDeployer.rollback.pending.v1";
export const MAX_ROLLBACK_ATTEMPT_BYTES = 16 * 1024;

const RELEASE_PATTERN = /^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/;
const IDEMPOTENCY_PATTERN =
  /^deployer-rollback-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const OPERATION_PATTERN = /^rbk_[0-9a-f]{32}$/;

function hasExactKeys(value, expected) {
  return (
    value !== null &&
    typeof value === "object" &&
    !Array.isArray(value) &&
    Object.keys(value).sort().join("|") === [...expected].sort().join("|")
  );
}

function isCanonicalDateTime(value) {
  if (typeof value !== "string" || Number.isNaN(Date.parse(value))) return false;
  try {
    return new Date(value).toISOString() === value;
  } catch {
    return false;
  }
}

export function validateRollbackRelease(value) {
  if (typeof value !== "string" || !RELEASE_PATTERN.test(value)) {
    throw new Error("Release inválida.");
  }
  return value;
}

export function validateRollbackReason(value) {
  if (typeof value !== "string" || value.length < 10 || value.length > 1000) {
    throw new Error("Motivo de rollback inválido.");
  }
  return value;
}

export function validateRollbackPendingAttempt(value) {
  if (
    !hasExactKeys(value, [
      "schemaVersion",
      "idempotencyKey",
      "release",
      "reason",
      "operationId",
      "createdAt",
    ]) ||
    value.schemaVersion !== 1 ||
    typeof value.idempotencyKey !== "string" ||
    !IDEMPOTENCY_PATTERN.test(value.idempotencyKey) ||
    typeof value.release !== "string" ||
    !RELEASE_PATTERN.test(value.release) ||
    typeof value.reason !== "string" ||
    value.reason.length < 10 ||
    value.reason.length > 1000 ||
    !(
      value.operationId === null ||
      (typeof value.operationId === "string" && OPERATION_PATTERN.test(value.operationId))
    ) ||
    !isCanonicalDateTime(value.createdAt)
  ) {
    throw new Error("Tentativa de rollback salva inválida.");
  }

  const serialized = JSON.stringify(value);
  if (new TextEncoder().encode(serialized).length > MAX_ROLLBACK_ATTEMPT_BYTES) {
    throw new Error("Tentativa de rollback salva excede o limite.");
  }
  return Object.freeze({ ...value });
}

export function createReleaseDeployerRollbackAttemptStore({
  storage = globalThis.sessionStorage,
  randomUUID = () => globalThis.crypto.randomUUID(),
  now = () => new Date(),
} = {}) {
  function clear() {
    try {
      storage.removeItem(PENDING_ROLLBACK_ATTEMPT_KEY);
    } catch {
      throw new Error("Armazenamento da sessão indisponível.");
    }
  }

  function persist(attempt) {
    const validated = validateRollbackPendingAttempt(attempt);
    try {
      storage.setItem(PENDING_ROLLBACK_ATTEMPT_KEY, JSON.stringify(validated));
    } catch {
      throw new Error("Armazenamento da sessão indisponível.");
    }
    return validated;
  }

  function create(release, reason) {
    const attempt = {
      schemaVersion: 1,
      idempotencyKey: `deployer-rollback-${randomUUID()}`,
      release: validateRollbackRelease(release),
      reason: validateRollbackReason(reason),
      operationId: null,
      createdAt: now().toISOString(),
    };
    return persist(attempt);
  }

  function read() {
    let raw;
    try {
      raw = storage.getItem(PENDING_ROLLBACK_ATTEMPT_KEY);
    } catch {
      throw new Error("Armazenamento da sessão indisponível.");
    }
    if (raw === null) return null;
    try {
      return validateRollbackPendingAttempt(JSON.parse(raw));
    } catch {
      clear();
      return null;
    }
  }

  function setOperationId(attempt, operationId) {
    return persist({ ...attempt, operationId });
  }

  function ensureAvailable() {
    try {
      const existing = storage.getItem(PENDING_ROLLBACK_ATTEMPT_KEY);
      if (existing === null) {
        storage.setItem(PENDING_ROLLBACK_ATTEMPT_KEY, "");
        storage.removeItem(PENDING_ROLLBACK_ATTEMPT_KEY);
      } else {
        storage.setItem(PENDING_ROLLBACK_ATTEMPT_KEY, existing);
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
