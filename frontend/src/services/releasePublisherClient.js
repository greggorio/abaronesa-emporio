import axios from "axios";

const PUBLIC_PROBLEM_MESSAGES = Object.freeze({
  BAD_REQUEST: "Solicitação inválida.",
  UNAUTHORIZED: "Sessão expirada. Entre novamente.",
  FORBIDDEN: "Você não possui permissão para publicar releases.",
  NOT_FOUND: "Candidato ou operação não encontrado.",
  IDEMPOTENCY_CONFLICT: "A tentativa salva não corresponde à solicitação.",
  VERSION_RESERVATION_CONFLICT: "Outra publicação reservou essa versão.",
  UNPROCESSABLE: "Revise os dados informados.",
  RATE_LIMITED: "Muitas solicitações. Aguarde e tente novamente.",
  INTERNAL_ERROR: "O serviço encontrou um erro interno.",
  SERVICE_UNAVAILABLE: "O serviço de releases está indisponível.",
});

const PUBLIC_PROBLEM_CODES = new Set(Object.keys(PUBLIC_PROBLEM_MESSAGES));
const OPERATION_STATES = new Set([
  "REQUESTED",
  "VALIDATING",
  "PUBLISHING",
  "PUBLISHED",
  "FAILED",
]);
const VERSION_BUMPS = new Set(["MAJOR", "MINOR", "PATCH"]);
const SHA_PATTERN = /^[0-9a-f]{40}$/;
const SEMVER_PATTERN = /^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/;
const OPERATION_PATTERN = /^pub_[0-9a-f]{32}$/;
const MAX_RELEASE_PAGES = 10;
const IDEMPOTENCY_PATTERN =
  /^publisher-ui-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const DATE_TIME_PATTERN =
  /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(?:Z|([+-])(\d{2}):(\d{2}))$/;
const GENERIC_MESSAGE = "Não foi possível concluir a solicitação.";

export class ReleasePublisherClientError extends Error {
  constructor(kind, { code = null, status = null, traceId = null, message = null } = {}) {
    super(message || (code && PUBLIC_PROBLEM_MESSAGES[code]) || GENERIC_MESSAGE);
    this.name = "ReleasePublisherClientError";
    this.kind = kind;
    this.code = code;
    this.status = status;
    this.traceId = traceId;
  }
}

function isRecord(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function hasExactKeys(value, required, optional = []) {
  if (!isRecord(value)) return false;
  const keys = Object.keys(value);
  const allowed = new Set([...required, ...optional]);
  return required.every((key) => keys.includes(key)) && keys.every((key) => allowed.has(key));
}

function isDateTime(value) {
  if (typeof value !== "string") return false;
  const match = DATE_TIME_PATTERN.exec(value);
  if (!match || !Number.isFinite(Date.parse(value))) return false;
  const [, year, month, day, hour, minute, second, , offsetHour, offsetMinute] = match;
  const numericMonth = Number(month);
  const numericDay = Number(day);
  const daysInMonth = new Date(Date.UTC(Number(year), numericMonth, 0)).getUTCDate();
  return (
    numericMonth >= 1 &&
    numericMonth <= 12 &&
    numericDay >= 1 &&
    numericDay <= daysInMonth &&
    Number(hour) <= 23 &&
    Number(minute) <= 59 &&
    Number(second) <= 59 &&
    (offsetHour === undefined ||
      (Number(offsetHour) <= 23 && Number(offsetMinute) <= 59))
  );
}

function isHttpsUrl(value) {
  if (typeof value !== "string") return false;
  try {
    return new URL(value).protocol === "https:";
  } catch {
    return false;
  }
}

function invalidResponse() {
  return new ReleasePublisherClientError("INVALID_RESPONSE");
}

function validateProblemDetails(value, responseStatus) {
  if (
    !hasExactKeys(value, ["type", "title", "status", "code", "traceId"], ["detail"]) ||
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
      (typeof value.detail !== "string" || value.detail.length > 1000))
  ) {
    return null;
  }
  try {
    new URL(value.type);
  } catch {
    return null;
  }
  return {
    code: value.code,
    status: value.status,
    traceId: value.traceId,
  };
}

function responseError(response) {
  const problem = validateProblemDetails(response.data, response.status);
  if (problem) {
    return new ReleasePublisherClientError("PUBLIC_PROBLEM", problem);
  }
  const fallbackCode =
    response.status === 401 ? "UNAUTHORIZED" : response.status === 403 ? "FORBIDDEN" : null;
  return new ReleasePublisherClientError("HTTP_ERROR", {
    code: fallbackCode,
    status: Number.isInteger(response.status) ? response.status : null,
  });
}

function normalizeBaseUrl(value, label) {
  if (typeof value !== "string" || value.length === 0) {
    throw new TypeError(`${label} is required`);
  }
  return value.replace(/\/+$/, "");
}

function transportRequest(transport, config) {
  if (typeof transport === "function") return transport(config);
  if (transport && typeof transport.request === "function") return transport.request(config);
  throw new TypeError("transport must be callable or expose request()");
}

async function send(transport, config) {
  try {
    const response = await transportRequest(transport, config);
    if (
      !isRecord(response) ||
      !Number.isInteger(response.status) ||
      !("data" in response)
    ) {
      throw invalidResponse();
    }
    return response;
  } catch (error) {
    if (error instanceof ReleasePublisherClientError) throw error;
    if (
      isRecord(error) &&
      isRecord(error.response) &&
      Number.isInteger(error.response.status) &&
      "data" in error.response
    ) {
      return error.response;
    }
    throw new ReleasePublisherClientError("NETWORK_ERROR");
  }
}

function requireStatus(response, expected) {
  if (response.status !== expected) throw responseError(response);
  return response.data;
}

function validateExchange(value) {
  if (
    !hasExactKeys(value, ["accessToken", "tokenType", "expiresIn", "scope"]) ||
    typeof value.accessToken !== "string" ||
    value.accessToken.length === 0 ||
    value.tokenType !== "Bearer" ||
    value.expiresIn !== 300 ||
    value.scope !== "release:read release:publish"
  ) {
    throw invalidResponse();
  }
  return value.accessToken;
}

function validateCapabilities(value) {
  if (
    !hasExactKeys(value, ["mode", "apiVersion", "capabilities"]) ||
    value.mode !== "publisher" ||
    value.apiVersion !== "v1" ||
    !Array.isArray(value.capabilities) ||
    value.capabilities.length !== 2 ||
    new Set(value.capabilities).size !== 2 ||
    !value.capabilities.includes("release:read") ||
    !value.capabilities.includes("release:publish")
  ) {
    throw invalidResponse();
  }
  return {
    mode: "publisher",
    apiVersion: "v1",
    capabilities: [...value.capabilities],
  };
}

function validateCursor(value) {
  if (value === null) return null;
  if (typeof value !== "string" || value.length < 1 || value.length > 256) {
    throw invalidResponse();
  }
  return value;
}

function validateCandidate(value) {
  if (
    !hasExactKeys(value, [
      "candidateId",
      "sourceCommit",
      "eligibility",
      "ciStatus",
      "manifestStatus",
      "createdAt",
    ]) ||
    typeof value.candidateId !== "string" ||
    value.candidateId.length < 12 ||
    value.candidateId.length > 128 ||
    typeof value.sourceCommit !== "string" ||
    !SHA_PATTERN.test(value.sourceCommit) ||
    value.eligibility !== "READY" ||
    value.ciStatus !== "PASSED" ||
    value.manifestStatus !== "VALID" ||
    !isDateTime(value.createdAt)
  ) {
    throw invalidResponse();
  }
  return {
    candidateId: value.candidateId,
    sourceCommit: value.sourceCommit,
    eligibility: value.eligibility,
    ciStatus: value.ciStatus,
    manifestStatus: value.manifestStatus,
    createdAt: value.createdAt,
  };
}

function validateRelease(value) {
  if (
    !hasExactKeys(value, ["release", "sourceCommit", "state", "publishedAt"]) ||
    typeof value.release !== "string" ||
    !SEMVER_PATTERN.test(value.release) ||
    typeof value.sourceCommit !== "string" ||
    !SHA_PATTERN.test(value.sourceCommit) ||
    value.state !== "PUBLISHED" ||
    !isDateTime(value.publishedAt)
  ) {
    throw invalidResponse();
  }
  return {
    release: value.release,
    sourceCommit: value.sourceCommit,
    state: value.state,
    publishedAt: value.publishedAt,
  };
}

function validatePage(value, itemValidator) {
  if (
    !hasExactKeys(value, ["items", "nextCursor"]) ||
    !Array.isArray(value.items) ||
    value.items.length > 100
  ) {
    throw invalidResponse();
  }
  return {
    items: value.items.map(itemValidator),
    nextCursor: validateCursor(value.nextCursor),
  };
}

function equalRecord(first, second) {
  return JSON.stringify(first) === JSON.stringify(second);
}

function mergeUnique(target, items, identity) {
  for (const item of items) {
    const key = identity(item);
    const existing = target.get(key);
    if (existing && !equalRecord(existing, item)) throw invalidResponse();
    if (!existing) target.set(key, item);
  }
}

function semverParts(value) {
  const match = SEMVER_PATTERN.exec(value);
  if (!match) throw invalidResponse();
  return [match[1], match[2], match[3]];
}

function compareDecimal(first, second) {
  if (first.length !== second.length) {
    return first.length > second.length ? 1 : -1;
  }
  if (first === second) return 0;
  return first > second ? 1 : -1;
}

function compareSemverDescending(first, second) {
  const left = semverParts(first.release);
  const right = semverParts(second.release);
  for (let index = 0; index < left.length; index += 1) {
    const comparison = compareDecimal(left[index], right[index]);
    if (comparison !== 0) return -comparison;
  }
  return 0;
}

function normalizePublishRequest(value) {
  if (
    !hasExactKeys(value, ["candidateId", "versionBump", "description", "changelog"]) ||
    typeof value.candidateId !== "string" ||
    value.candidateId.length < 12 ||
    value.candidateId.length > 128 ||
    !VERSION_BUMPS.has(value.versionBump) ||
    typeof value.description !== "string" ||
    typeof value.changelog !== "string"
  ) {
    throw new ReleasePublisherClientError("INVALID_REQUEST");
  }
  const description = value.description.trim();
  const changelog = value.changelog.trim();
  if (
    description.length < 1 ||
    description.length > 500 ||
    changelog.length < 1 ||
    changelog.length > 10000
  ) {
    throw new ReleasePublisherClientError("INVALID_REQUEST");
  }
  return {
    candidateId: value.candidateId,
    versionBump: value.versionBump,
    description,
    changelog,
  };
}

function validateOperation(value, expectedCandidateId, expectedOperationId = null) {
  if (
    !hasExactKeys(value, [
      "operationId",
      "state",
      "candidateId",
      "release",
      "workflowRunUrl",
      "createdAt",
      "updatedAt",
      "errorCode",
    ]) ||
    typeof value.operationId !== "string" ||
    !OPERATION_PATTERN.test(value.operationId) ||
    (expectedOperationId !== null && value.operationId !== expectedOperationId) ||
    !OPERATION_STATES.has(value.state) ||
    value.candidateId !== expectedCandidateId ||
    (value.release !== null &&
      (typeof value.release !== "string" || !SEMVER_PATTERN.test(value.release))) ||
    (value.workflowRunUrl !== null && !isHttpsUrl(value.workflowRunUrl)) ||
    !isDateTime(value.createdAt) ||
    !isDateTime(value.updatedAt) ||
    (value.errorCode !== null &&
      (typeof value.errorCode !== "string" || value.errorCode.length > 100))
  ) {
    throw invalidResponse();
  }
  return {
    operationId: value.operationId,
    state: value.state,
    candidateId: value.candidateId,
    release: value.release,
    workflowRunUrl: value.workflowRunUrl,
    createdAt: value.createdAt,
    updatedAt: value.updatedAt,
    errorCode: value.errorCode,
  };
}

export function createReleasePublisherClient({
  erpBaseUrl,
  publisherBaseUrl,
  getErpToken,
  erpTransport = axios,
  publisherTransport = axios,
}) {
  const erpUrl = normalizeBaseUrl(erpBaseUrl, "erpBaseUrl");
  const publisherUrl = normalizeBaseUrl(publisherBaseUrl, "publisherBaseUrl");
  if (typeof getErpToken !== "function") throw new TypeError("getErpToken is required");

  let publisherToken = null;
  let exchangeInFlight = null;
  let capabilitiesToken = null;
  const knownCandidates = new Map();

  async function exchange() {
    const erpToken = getErpToken();
    if (typeof erpToken !== "string" || erpToken.length === 0) {
      throw new ReleasePublisherClientError("ERP_SESSION_MISSING", {
        code: "UNAUTHORIZED",
        status: 401,
      });
    }
    const response = await send(erpTransport, {
      method: "POST",
      url: `${erpUrl}/api/release-control/identity/token`,
      headers: {
        Authorization: `Bearer ${erpToken}`,
        Accept: "application/json",
      },
    });
    publisherToken = validateExchange(requireStatus(response, 200));
    capabilitiesToken = null;
    return publisherToken;
  }

  async function token() {
    if (publisherToken !== null) return publisherToken;
    if (exchangeInFlight === null) {
      exchangeInFlight = exchange().finally(() => {
        exchangeInFlight = null;
      });
    }
    return exchangeInFlight;
  }

  async function protectedRequest(config, retry401 = true) {
    const usedToken = await token();
    let response = await send(publisherTransport, {
      ...config,
      headers: {
        ...config.headers,
        Authorization: `Bearer ${usedToken}`,
        Accept: "application/json",
      },
    });
    if (response.status === 401 && retry401) {
      if (publisherToken === usedToken) {
        publisherToken = null;
        capabilitiesToken = null;
      }
      const refreshedToken = await token();
      response = await send(publisherTransport, {
        ...config,
        headers: {
          ...config.headers,
          Authorization: `Bearer ${refreshedToken}`,
          Accept: "application/json",
        },
      });
    }
    return response;
  }

  async function ensureCapabilities() {
    const currentToken = await token();
    if (capabilitiesToken === currentToken) {
      return {
        mode: "publisher",
        apiVersion: "v1",
        capabilities: ["release:read", "release:publish"],
      };
    }
    const response = await protectedRequest({
      method: "GET",
      url: `${publisherUrl}/api/release-control/v1/capabilities`,
    });
    const value = validateCapabilities(requireStatus(response, 200));
    capabilitiesToken = publisherToken;
    return value;
  }

  async function listCandidates({ cursor = null } = {}) {
    await ensureCapabilities();
    const parameters = new URLSearchParams();
    parameters.set("eligibility", "READY");
    parameters.set("limit", "100");
    if (cursor !== null) parameters.set("cursor", validateCursor(cursor));
    const response = await protectedRequest({
      method: "GET",
      url: `${publisherUrl}/api/release-publisher/v1/candidates?${parameters.toString()}`,
    });
    const page = validatePage(requireStatus(response, 200), validateCandidate);
    if (cursor === null) knownCandidates.clear();
    const newItems = [];
    for (const item of page.items) {
      const existing = knownCandidates.get(item.candidateId);
      if (existing && !equalRecord(existing, item)) throw invalidResponse();
      if (!existing) {
        knownCandidates.set(item.candidateId, item);
        newItems.push(item);
      }
    }
    return { items: newItems, nextCursor: page.nextCursor };
  }

  async function listReleases() {
    await ensureCapabilities();
    const releases = new Map();
    let cursor = null;
    for (let pageNumber = 1; pageNumber <= MAX_RELEASE_PAGES; pageNumber += 1) {
      const parameters = new URLSearchParams();
      parameters.set("limit", "100");
      if (cursor !== null) parameters.set("cursor", cursor);
      const response = await protectedRequest({
        method: "GET",
        url: `${publisherUrl}/api/release-publisher/v1/releases?${parameters.toString()}`,
      });
      const page = validatePage(requireStatus(response, 200), validateRelease);
      mergeUnique(releases, page.items, (item) => item.release);
      cursor = page.nextCursor;
      if (cursor === null) {
        return [...releases.values()].sort(compareSemverDescending);
      }
    }
    throw invalidResponse();
  }

  async function publishRelease(request, idempotencyKey) {
    const normalizedRequest = normalizePublishRequest(request);
    if (typeof idempotencyKey !== "string" || !IDEMPOTENCY_PATTERN.test(idempotencyKey)) {
      throw new ReleasePublisherClientError("INVALID_IDEMPOTENCY_KEY");
    }
    await ensureCapabilities();
    const response = await protectedRequest({
      method: "POST",
      url: `${publisherUrl}/api/release-publisher/v1/releases`,
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
      },
      data: normalizedRequest,
    });
    return validateOperation(
      requireStatus(response, 202),
      normalizedRequest.candidateId
    );
  }

  async function getOperation(operationId, candidateId) {
    if (
      typeof operationId !== "string" ||
      !OPERATION_PATTERN.test(operationId) ||
      typeof candidateId !== "string" ||
      candidateId.length < 12 ||
      candidateId.length > 128
    ) {
      throw new ReleasePublisherClientError("INVALID_REQUEST");
    }
    const response = await protectedRequest({
      method: "GET",
      url: `${publisherUrl}/api/release-publisher/v1/operations/${operationId}`,
    });
    return validateOperation(requireStatus(response, 200), candidateId, operationId);
  }

  return Object.freeze({
    ensureCapabilities,
    listCandidates,
    listReleases,
    publishRelease,
    getOperation,
  });
}

export { PUBLIC_PROBLEM_MESSAGES };
