import axios from "axios";

export const DEPLOYER_CAPABILITIES = Object.freeze([
  "deployment:read",
  "deployment:execute",
  "deployment:rollback",
]);
export const DEPLOYER_SCOPE = "deployment:read deployment:execute deployment:rollback";
export const DEPLOYER_AUDIENCE = "emporio-release-control-deployer";
export const DEPLOYER_TOKEN_TTL = 300;
export const DEPLOYER_OPERATION_STATES = Object.freeze([
  "QUEUED",
  "SUCCEEDED",
  "FAILED",
]);
export const DEPLOYER_ROLLBACK_STATES = Object.freeze([
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
]);

export const PUBLIC_PROBLEM_MESSAGES = Object.freeze({
  BAD_REQUEST: "Solicitação inválida.",
  UNAUTHORIZED: "Sessão expirada. Entre novamente.",
  FORBIDDEN: "Você não possui permissão para atualizar.",
  NOT_FOUND: "Release ou operação não encontrada.",
  CURRENT_INSTALLATION_UNRECONCILED:
    "A instalação está incerta; consulte o suporte.",
  IDEMPOTENCY_CONFLICT: "A tentativa salva não corresponde à solicitação.",
  PRODUCTION_OPERATION_ACTIVE: "Já existe uma atualização em andamento.",
  RELEASE_NOT_ELIGIBLE: "Esta release não está elegível.",
  UNPROCESSABLE: "A resposta não pode ser usada para atualizar.",
  RATE_LIMITED: "Muitas solicitações. Aguarde e tente novamente.",
  INTERNAL_ERROR: "O serviço encontrou um erro interno.",
  SERVICE_UNAVAILABLE: "O serviço de atualização está indisponível.",
});

const PUBLIC_PROBLEM_CODES = new Set(Object.keys(PUBLIC_PROBLEM_MESSAGES));
const RELEASE_PATTERN = /^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/;
const SHA_PATTERN = /^[0-9a-f]{40}$/;
const DIGEST_PATTERN = /^sha256:[0-9a-f]{64}$/;
const OPERATION_PATTERN = /^dep_[0-9a-f]{32}$/;
const ROLLBACK_OPERATION_PATTERN = /^rbk_[0-9a-f]{32}$/;
const IDEMPOTENCY_PATTERN =
  /^deployer-ui-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const ROLLBACK_IDEMPOTENCY_PATTERN =
  /^deployer-rollback-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const DATE_TIME_PATTERN =
  /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(?:Z|([+-])(\d{2}):(\d{2}))$/;
const ERROR_CODE_PATTERN = /^[A-Z][A-Z0-9_]{2,63}$/;
const COMPONENTS = new Set([
  "backend",
  "website_back",
  "frontend",
  "website_front",
  "whatsapp_service",
  "gateway",
]);
const GENERIC_MESSAGE = "Não foi possível concluir a solicitação.";

export class ReleaseDeployerClientError extends Error {
  constructor(kind, { code = null, status = null, traceId = null, activeOperationId = null } = {}) {
    super((code && PUBLIC_PROBLEM_MESSAGES[code]) || GENERIC_MESSAGE);
    this.name = "ReleaseDeployerClientError";
    this.kind = kind;
    this.code = code;
    this.status = status;
    this.traceId = traceId;
    this.activeOperationId = activeOperationId;
  }
}

function isRecord(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function hasExactKeys(value, required, optional = []) {
  if (!isRecord(value)) return false;
  const allowed = new Set([...required, ...optional]);
  const keys = Object.keys(value);
  return required.every((key) => keys.includes(key)) && keys.every((key) => allowed.has(key));
}

function isDateTime(value) {
  if (typeof value !== "string") return false;
  const match = DATE_TIME_PATTERN.exec(value);
  if (!match || Number.isNaN(Date.parse(value))) return false;
  const [, year, month, day, hour, minute, second, , offsetHour, offsetMinute] = match;
  const monthNumber = Number(month);
  const dayNumber = Number(day);
  const daysInMonth = new Date(Date.UTC(Number(year), monthNumber, 0)).getUTCDate();
  return (
    monthNumber >= 1 &&
    monthNumber <= 12 &&
    dayNumber >= 1 &&
    dayNumber <= daysInMonth &&
    Number(hour) <= 23 &&
    Number(minute) <= 59 &&
    Number(second) <= 59 &&
    (offsetHour === undefined ||
      (Number(offsetHour) <= 23 && Number(offsetMinute) <= 59))
  );
}

function isRelease(value) {
  return typeof value === "string" && RELEASE_PATTERN.test(value);
}

function isOperationId(value) {
  return typeof value === "string" && OPERATION_PATTERN.test(value);
}

function isRollbackOperationId(value) {
  return typeof value === "string" && ROLLBACK_OPERATION_PATTERN.test(value);
}

function isAnyOperationId(value) {
  return isOperationId(value) || isRollbackOperationId(value);
}

function invalidResponse() {
  return new ReleaseDeployerClientError("INVALID_RESPONSE");
}

function normalizeBaseUrl(value) {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new TypeError("baseApiUrl is required");
  }
  const normalized = value.replace(/\/+$/, "");
  if (/127\.0\.0\.1:8121(?:\/|$)/.test(normalized)) {
    throw new TypeError("deployer API must use the ERP same-origin proxy");
  }
  return normalized;
}

function transportRequest(transport, config) {
  if (typeof transport === "function") return transport(config);
  if (transport && typeof transport.request === "function") return transport.request(config);
  throw new TypeError("transport must be callable or expose request()");
}

async function send(transport, config) {
  try {
    const response = await transportRequest(transport, config);
    if (!isRecord(response) || !Number.isInteger(response.status) || !("data" in response)) {
      throw invalidResponse();
    }
    return response;
  } catch (error) {
    if (error instanceof ReleaseDeployerClientError) throw error;
    if (
      isRecord(error) &&
      isRecord(error.response) &&
      Number.isInteger(error.response.status) &&
      "data" in error.response
    ) {
      return error.response;
    }
    throw new ReleaseDeployerClientError("NETWORK_ERROR");
  }
}

function parseProblemDetails(value, responseStatus) {
  if (
    !hasExactKeys(value, ["type", "title", "status", "code", "traceId"], [
      "detail",
      "activeOperationId",
    ]) ||
    typeof value.type !== "string" ||
    typeof value.title !== "string" ||
    value.title.length > 200 ||
    !Number.isInteger(value.status) ||
    value.status < 400 ||
    value.status > 599 ||
    value.status !== responseStatus ||
    !PUBLIC_PROBLEM_CODES.has(value.code) ||
    typeof value.traceId !== "string" ||
    value.traceId.length < 8 ||
    value.traceId.length > 128 ||
    ("detail" in value &&
      (typeof value.detail !== "string" || value.detail.length > 1000)) ||
    ("activeOperationId" in value && !isAnyOperationId(value.activeOperationId))
  ) {
    return null;
  }
  try {
    new URL(value.type);
  } catch {
    return null;
  }
  if (
    "activeOperationId" in value &&
    (value.code !== "PRODUCTION_OPERATION_ACTIVE" || responseStatus !== 409)
  ) {
    return null;
  }
  return {
    code: value.code,
    status: value.status,
    traceId: value.traceId,
    activeOperationId: value.activeOperationId ?? null,
  };
}

function responseError(response) {
  const problem = parseProblemDetails(response.data, response.status);
  if (problem) return new ReleaseDeployerClientError("PUBLIC_PROBLEM", problem);
  const fallbackCode =
    response.status === 401
      ? "UNAUTHORIZED"
      : response.status === 403
        ? "FORBIDDEN"
        : response.status === 404
          ? "NOT_FOUND"
          : null;
  return new ReleaseDeployerClientError("HTTP_ERROR", {
    code: fallbackCode,
    status: Number.isInteger(response.status) ? response.status : null,
  });
}

function requireStatus(response, expected) {
  if (response.status !== expected) throw responseError(response);
  return response.data;
}

function decodeBase64Url(value) {
  if (typeof value !== "string" || !/^[A-Za-z0-9_-]+$/.test(value)) throw invalidResponse();
  const padded = value.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((value.length + 3) % 4);
  try {
    const binary =
      typeof atob === "function"
        ? atob(padded)
        : Buffer.from(padded, "base64").toString("binary");
    const bytes = Array.from(binary, (character) => character.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(new Uint8Array(bytes)));
  } catch {
    throw invalidResponse();
  }
}

function validateJwtClaims(token) {
  if (typeof token !== "string" || token.length === 0) throw invalidResponse();
  const parts = token.split(".");
  if (parts.length !== 3) throw invalidResponse();
  const header = decodeBase64Url(parts[0]);
  const claims = decodeBase64Url(parts[1]);
  if (
    !isRecord(header) ||
    header.alg !== "RS256" ||
    header.typ !== "JWT" ||
    !isRecord(claims) ||
    claims.aud !== DEPLOYER_AUDIENCE ||
    claims.scope !== DEPLOYER_SCOPE ||
    !Number.isInteger(claims.iat) ||
    !Number.isInteger(claims.exp) ||
    claims.exp - claims.iat !== DEPLOYER_TOKEN_TTL
  ) {
    throw invalidResponse();
  }
  return token;
}

export function validateExchange(value) {
  if (
    !hasExactKeys(value, ["accessToken", "tokenType", "expiresIn", "scope"]) ||
    typeof value.accessToken !== "string" ||
    value.accessToken.length === 0 ||
    value.tokenType !== "Bearer" ||
    value.expiresIn !== DEPLOYER_TOKEN_TTL ||
    value.scope !== DEPLOYER_SCOPE
  ) {
    throw invalidResponse();
  }
  return validateJwtClaims(value.accessToken);
}

export function validateCapabilities(value) {
  if (
    !hasExactKeys(value, ["mode", "apiVersion", "capabilities"]) ||
    value.mode !== "deployer" ||
    value.apiVersion !== "v1" ||
    !Array.isArray(value.capabilities) ||
    value.capabilities.length !== DEPLOYER_CAPABILITIES.length ||
    new Set(value.capabilities).size !== DEPLOYER_CAPABILITIES.length ||
    !DEPLOYER_CAPABILITIES.every(
      (capability, index) => value.capabilities[index] === capability,
    )
  ) {
    throw invalidResponse();
  }
  return {
    mode: "deployer",
    apiVersion: "v1",
    capabilities: [...DEPLOYER_CAPABILITIES],
  };
}

export function validateCurrentInstallation(value) {
  if (
    !hasExactKeys(value, ["release", "sourceCommit", "installedAt", "reconciled"]) ||
    !isRelease(value.release) ||
    typeof value.sourceCommit !== "string" ||
    !SHA_PATTERN.test(value.sourceCommit) ||
    !isDateTime(value.installedAt) ||
    value.reconciled !== true
  ) {
    throw invalidResponse();
  }
  return { ...value };
}

function validateReleaseSummary(value) {
  if (
    !hasExactKeys(value, ["release", "sourceCommit", "publishedAt", "eligible"]) ||
    !isRelease(value.release) ||
    typeof value.sourceCommit !== "string" ||
    !SHA_PATTERN.test(value.sourceCommit) ||
    !isDateTime(value.publishedAt) ||
    typeof value.eligible !== "boolean"
  ) {
    throw invalidResponse();
  }
  return { ...value };
}

function validateCursor(value) {
  if (value === null) return null;
  if (typeof value !== "string" || value.length < 1 || value.length > 256) {
    throw invalidResponse();
  }
  return value;
}

export function validateReleasePage(value) {
  if (
    !hasExactKeys(value, ["items"], ["nextCursor"]) ||
    !Array.isArray(value.items) ||
    value.items.length > 100 ||
    ("nextCursor" in value && validateCursor(value.nextCursor) === undefined)
  ) {
    throw invalidResponse();
  }
  return {
    items: value.items.map(validateReleaseSummary),
    nextCursor: "nextCursor" in value ? validateCursor(value.nextCursor) : null,
  };
}

function validateDigest(value) {
  return typeof value === "string" && DIGEST_PATTERN.test(value);
}

function isHttpsUrl(value) {
  if (typeof value !== "string") return false;
  try {
    return new URL(value).protocol === "https:";
  } catch {
    return false;
  }
}

export function validateDeploymentPlan(value) {
  if (
    !hasExactKeys(value, [
      "sourceRelease",
      "targetRelease",
      "components",
      "migrationRequired",
      "backupRequired",
    ]) ||
    !(value.sourceRelease === null || isRelease(value.sourceRelease)) ||
    !isRelease(value.targetRelease) ||
    !Array.isArray(value.components) ||
    value.components.length !== COMPONENTS.size ||
    typeof value.migrationRequired !== "boolean" ||
    typeof value.backupRequired !== "boolean"
  ) {
    throw invalidResponse();
  }

  const components = value.components.map((component) => {
    if (
      !hasExactKeys(component, ["component", "action", "currentDigest", "targetDigest"]) ||
      !COMPONENTS.has(component.component) ||
      !["KEEP", "UPDATE"].includes(component.action) ||
      !(component.currentDigest === null || validateDigest(component.currentDigest)) ||
      !validateDigest(component.targetDigest)
    ) {
      throw invalidResponse();
    }
    return { ...component };
  });

  if (new Set(components.map((component) => component.component)).size !== COMPONENTS.size) {
    throw invalidResponse();
  }
  return { ...value, components };
}

export function validateDeploymentOperation(value) {
  if (
    !hasExactKeys(
      value,
      ["operationId", "operationType", "state", "targetRelease", "createdAt", "updatedAt"],
      ["workflowRunUrl", "errorCode"],
    ) ||
    !isOperationId(value.operationId) ||
    value.operationType !== "deployment" ||
    !DEPLOYER_OPERATION_STATES.includes(value.state) ||
    !isRelease(value.targetRelease) ||
    !isDateTime(value.createdAt) ||
    !isDateTime(value.updatedAt) ||
    ("workflowRunUrl" in value &&
      value.workflowRunUrl !== null &&
      !isHttpsUrl(value.workflowRunUrl)) ||
    ("errorCode" in value &&
      value.errorCode !== null &&
      (typeof value.errorCode !== "string" || value.errorCode.length > 100))
  ) {
    throw invalidResponse();
  }
  return { ...value };
}

export function validateRollbackOperation(value) {
  if (
    !hasExactKeys(
      value,
      [
        "operationId",
        "operationType",
        "state",
        "sourceRelease",
        "targetRelease",
        "databaseRestoreRequired",
        "createdAt",
        "updatedAt",
      ],
      ["workflowRunUrl", "errorCode"],
    ) ||
    !isRollbackOperationId(value.operationId) ||
    value.operationType !== "rollback" ||
    !DEPLOYER_ROLLBACK_STATES.includes(value.state) ||
    !isRelease(value.sourceRelease) ||
    !isRelease(value.targetRelease) ||
    typeof value.databaseRestoreRequired !== "boolean" ||
    !isDateTime(value.createdAt) ||
    !isDateTime(value.updatedAt) ||
    ("workflowRunUrl" in value &&
      value.workflowRunUrl !== null &&
      !isHttpsUrl(value.workflowRunUrl)) ||
    ("errorCode" in value &&
      value.errorCode !== null &&
      (typeof value.errorCode !== "string" ||
        !ERROR_CODE_PATTERN.test(value.errorCode) ||
        value.errorCode.length > 100))
  ) {
    throw invalidResponse();
  }
  return { ...value };
}

export function createReleaseDeployerClient({
  baseApiUrl,
  transport = axios,
  getErpToken = () => globalThis.sessionStorage?.getItem("token") ?? null,
} = {}) {
  const baseUrl = normalizeBaseUrl(baseApiUrl);
  let deployerToken = null;
  let exchangePromise = null;

  async function exchangeIdentity() {
    if (exchangePromise) return exchangePromise;
    exchangePromise = (async () => {
      const erpToken = getErpToken();
      if (typeof erpToken !== "string" || erpToken.length === 0) {
        throw new ReleaseDeployerClientError("UNAUTHORIZED", { code: "UNAUTHORIZED", status: 401 });
      }
      const response = await send(transport, {
        method: "POST",
        url: `${baseUrl}/api/release-control/identity/deployer/token`,
        headers: { Authorization: `Bearer ${erpToken}` },
      });
      const token = validateExchange(requireStatus(response, 200));
      deployerToken = token;
      return token;
    })();
    try {
      return await exchangePromise;
    } finally {
      exchangePromise = null;
    }
  }

  async function protectedRequest(config) {
    let retriedAfter401 = false;
    while (true) {
      const token = deployerToken || (await exchangeIdentity());
      const response = await send(transport, {
        ...config,
        headers: { ...(config.headers || {}), Authorization: `Bearer ${token}` },
      });
      if (response.status === 401 && !retriedAfter401) {
        deployerToken = null;
        retriedAfter401 = true;
        continue;
      }
      return response;
    }
  }

  async function capabilities() {
    return validateCapabilities(
      requireStatus(
        await protectedRequest({
          method: "GET",
          url: `${baseUrl}/api/release-control/v1/capabilities`,
        }),
        200,
      ),
    );
  }

  async function current() {
    const response = await protectedRequest({
      method: "GET",
      url: `${baseUrl}/api/deployment-control/v1/current`,
    });
    if (response.status === 404) return null;
    return validateCurrentInstallation(requireStatus(response, 200));
  }

  async function releases() {
    const items = [];
    let cursor = null;
    for (let page = 0; page < 10; page += 1) {
      const query = new URLSearchParams({ limit: "100" });
      if (cursor !== null) query.set("cursor", cursor);
      const response = await protectedRequest({
        method: "GET",
        url: `${baseUrl}/api/deployment-control/v1/releases?${query.toString()}`,
      });
      const pageData = validateReleasePage(requireStatus(response, 200));
      items.push(...pageData.items);
      cursor = pageData.nextCursor;
      if (cursor === null) return items;
    }
    throw invalidResponse();
  }

  async function plan(release) {
    if (!isRelease(release)) throw new ReleaseDeployerClientError("INVALID_REQUEST");
    return validateDeploymentPlan(
      requireStatus(
        await protectedRequest({
          method: "GET",
          url: `${baseUrl}/api/deployment-control/v1/releases/${encodeURIComponent(release)}/plan`,
        }),
        200,
      ),
    );
  }

  async function requestDeployment(release, idempotencyKey) {
    if (!isRelease(release) || !IDEMPOTENCY_PATTERN.test(idempotencyKey)) {
      throw new ReleaseDeployerClientError("INVALID_REQUEST");
    }
    return validateDeploymentOperation(
      requireStatus(
        await protectedRequest({
          method: "POST",
          url: `${baseUrl}/api/deployment-control/v1/deployments`,
          headers: { "Idempotency-Key": idempotencyKey },
          data: { release },
        }),
        202,
      ),
    );
  }

  async function operation(operationId) {
    if (!isOperationId(operationId)) throw new ReleaseDeployerClientError("INVALID_REQUEST");
    return validateDeploymentOperation(
      requireStatus(
        await protectedRequest({
          method: "GET",
          url: `${baseUrl}/api/deployment-control/v1/deployments/${encodeURIComponent(operationId)}`,
        }),
        200,
      ),
    );
  }

  async function requestRollback(release, reason, idempotencyKey) {
    if (
      !isRelease(release) ||
      typeof reason !== "string" ||
      reason.length < 10 ||
      reason.length > 1000 ||
      !ROLLBACK_IDEMPOTENCY_PATTERN.test(idempotencyKey)
    ) {
      throw new ReleaseDeployerClientError("INVALID_REQUEST");
    }
    return validateRollbackOperation(
      requireStatus(
        await protectedRequest({
          method: "POST",
          url: `${baseUrl}/api/deployment-control/v1/rollbacks`,
          headers: { "Idempotency-Key": idempotencyKey },
          data: { release, reason },
        }),
        202,
      ),
    );
  }

  async function rollbackOperation(operationId) {
    if (!isRollbackOperationId(operationId)) {
      throw new ReleaseDeployerClientError("INVALID_REQUEST");
    }
    return validateRollbackOperation(
      requireStatus(
        await protectedRequest({
          method: "GET",
          url: `${baseUrl}/api/deployment-control/v1/rollbacks/${encodeURIComponent(operationId)}`,
        }),
        200,
      ),
    );
  }

  return Object.freeze({
    exchangeIdentity,
    capabilities,
    current,
    releases,
    plan,
    requestDeployment,
    operation,
    requestRollback,
    rollbackOperation,
  });
}
