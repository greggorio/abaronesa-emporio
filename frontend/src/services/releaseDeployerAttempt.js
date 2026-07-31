export const PENDING_ATTEMPT_KEY = "emporio.releaseDeployer.pending.v1";
export const MAX_ATTEMPT_BYTES = 16 * 1024;

const RELEASE_PATTERN = /^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/;
const IDEMPOTENCY_PATTERN =
  /^deployer-ui-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const OPERATION_PATTERN = /^dep_[0-9a-f]{32}$/;

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

export function validateRelease(value) {
  if (typeof value !== "string" || !RELEASE_PATTERN.test(value)) {
    throw new Error("Release inválida.");
  }
  return value;
}

export function validatePendingAttempt(value) {
  if (
    !hasExactKeys(value, [
      "schemaVersion",
      "idempotencyKey",
      "release",
      "operationId",
      "createdAt",
    ]) ||
    value.schemaVersion !== 1 ||
    typeof value.idempotencyKey !== "string" ||
    !IDEMPOTENCY_PATTERN.test(value.idempotencyKey) ||
    typeof value.release !== "string" ||
    !RELEASE_PATTERN.test(value.release) ||
    !(
      value.operationId === null ||
      (typeof value.operationId === "string" && OPERATION_PATTERN.test(value.operationId))
    ) ||
    !isCanonicalDateTime(value.createdAt)
  ) {
    throw new Error("Tentativa salva inválida.");
  }

  const serialized = JSON.stringify(value);
  if (new TextEncoder().encode(serialized).length > MAX_ATTEMPT_BYTES) {
    throw new Error("Tentativa salva excede o limite.");
  }
  return Object.freeze({ ...value });
}

export function createReleaseDeployerAttemptStore({
  storage = globalThis.sessionStorage,
  randomUUID = () => globalThis.crypto.randomUUID(),
  now = () => new Date(),
} = {}) {
  function removePending() {
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

  function create(release) {
    const validatedRelease = validateRelease(release);
    const attempt = {
      schemaVersion: 1,
      idempotencyKey: `deployer-ui-${randomUUID()}`,
      release: validatedRelease,
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
    if (raw === null) return null;
    try {
      return validatePendingAttempt(JSON.parse(raw));
    } catch {
      removePending();
      return null;
    }
  }

  function setOperationId(attempt, operationId) {
    return persist({ ...attempt, operationId });
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
    clear: removePending,
    ensureAvailable,
  });
}
