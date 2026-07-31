import { describe, expect, it, vi } from "vitest";

import {
  PUBLIC_PROBLEM_MESSAGES,
  ReleasePublisherClientError,
  createReleasePublisherClient,
} from "./releasePublisherClient";

const ERP = "http://127.0.0.1:8080";
const PUBLISHER = "http://127.0.0.1:8090";
const CANDIDATE = "candidate-example-123";
const SHA = "1".repeat(40);
const KEY = "publisher-ui-123e4567-e89b-42d3-a456-426614174000";
const OPERATION = `pub_${"a".repeat(32)}`;

function exchangeResponse(overrides = {}) {
  return {
    status: 200,
    data: {
      accessToken: "publisher-token",
      tokenType: "Bearer",
      expiresIn: 300,
      scope: "release:read release:publish",
      ...overrides,
    },
  };
}

function capabilities(overrides = {}) {
  return {
    status: 200,
    data: {
      mode: "publisher",
      apiVersion: "v1",
      capabilities: ["release:read", "release:publish"],
      ...overrides,
    },
  };
}

function candidate(overrides = {}) {
  return {
    candidateId: CANDIDATE,
    sourceCommit: SHA,
    eligibility: "READY",
    ciStatus: "PASSED",
    manifestStatus: "VALID",
    createdAt: "2026-07-29T12:00:00Z",
    ...overrides,
  };
}

function release(version, overrides = {}) {
  return {
    release: version,
    sourceCommit: SHA,
    state: "PUBLISHED",
    publishedAt: "2026-07-29T12:00:00Z",
    ...overrides,
  };
}

function operation(overrides = {}) {
  return {
    operationId: OPERATION,
    state: "REQUESTED",
    candidateId: CANDIDATE,
    release: null,
    workflowRunUrl: null,
    createdAt: "2026-07-29T12:00:00Z",
    updatedAt: "2026-07-29T12:00:01Z",
    errorCode: null,
    ...overrides,
  };
}

function factory({ erpTransport, publisherTransport } = {}) {
  return createReleasePublisherClient({
    erpBaseUrl: ERP,
    publisherBaseUrl: PUBLISHER,
    getErpToken: () => "erp-token",
    erpTransport: erpTransport || vi.fn(async () => exchangeResponse()),
    publisherTransport:
      publisherTransport ||
      vi.fn(async (config) => {
        if (config.url.endsWith("/capabilities")) return capabilities();
        throw new Error("unexpected request");
      }),
  });
}

describe("releasePublisherClient identity and capabilities", () => {
  it("performs an exact bodyless exchange and keeps the publisher token in closure", async () => {
    const erpTransport = vi.fn(async () => exchangeResponse());
    const publisherTransport = vi.fn(async () => capabilities());
    const client = factory({ erpTransport, publisherTransport });

    expect(await client.ensureCapabilities()).toEqual(capabilities().data);
    expect(erpTransport).toHaveBeenCalledTimes(1);
    expect(erpTransport.mock.calls[0][0]).toEqual({
      method: "POST",
      url: `${ERP}/api/release-control/identity/token`,
      headers: {
        Authorization: "Bearer erp-token",
        Accept: "application/json",
      },
    });
    expect(JSON.stringify(client)).not.toContain("publisher-token");
    expect(publisherTransport.mock.calls[0][0].headers.Authorization).toBe(
      "Bearer publisher-token"
    );
  });

  it("uses one in-flight exchange for concurrent requests", async () => {
    let releaseExchange;
    const erpTransport = vi.fn(
      () =>
        new Promise((resolve) => {
          releaseExchange = resolve;
        })
    );
    const publisherTransport = vi.fn(async () => capabilities());
    const client = factory({ erpTransport, publisherTransport });
    const first = client.ensureCapabilities();
    const second = client.ensureCapabilities();
    await vi.waitFor(() => expect(erpTransport).toHaveBeenCalledTimes(1));
    releaseExchange(exchangeResponse());
    await Promise.all([first, second]);
    expect(erpTransport).toHaveBeenCalledTimes(1);
  });

  it.each([
    { accessToken: "" },
    { tokenType: "Basic" },
    { expiresIn: 299 },
    { scope: "release:read" },
    { extra: true },
  ])("rejects divergent exchange response %#", async (mutant) => {
    const erpTransport = vi.fn(async () => {
      const response = exchangeResponse();
      if ("extra" in mutant) response.data.extra = true;
      else Object.assign(response.data, mutant);
      return response;
    });
    await expect(factory({ erpTransport }).ensureCapabilities()).rejects.toMatchObject({
      kind: "INVALID_RESPONSE",
    });
  });

  it("accepts capability order but rejects missing, duplicate, extra and wrong mode", async () => {
    const valid = factory({
      publisherTransport: vi.fn(async () =>
        capabilities({ capabilities: ["release:publish", "release:read"] })
      ),
    });
    await expect(valid.ensureCapabilities()).resolves.toMatchObject({ mode: "publisher" });

    for (const data of [
      { ...capabilities().data, capabilities: ["release:read"] },
      { ...capabilities().data, capabilities: ["release:read", "release:read"] },
      { ...capabilities().data, mode: "deployer" },
      { ...capabilities().data, extra: true },
    ]) {
      const client = factory({
        publisherTransport: vi.fn(async () => ({ status: 200, data })),
      });
      await expect(client.ensureCapabilities()).rejects.toMatchObject({
        kind: "INVALID_RESPONSE",
      });
    }
  });
});

describe("releasePublisherClient retries", () => {
  it("refreshes once on 401 and retries the original request exactly once", async () => {
    const erpTransport = vi
      .fn()
      .mockResolvedValueOnce(exchangeResponse())
      .mockResolvedValueOnce(
        exchangeResponse({ accessToken: "publisher-token-refreshed" })
      );
    const publisherTransport = vi
      .fn()
      .mockResolvedValueOnce({ status: 401, data: {} })
      .mockResolvedValueOnce(capabilities());
    const client = factory({ erpTransport, publisherTransport });

    await expect(client.ensureCapabilities()).resolves.toMatchObject({ mode: "publisher" });
    expect(erpTransport).toHaveBeenCalledTimes(2);
    expect(publisherTransport).toHaveBeenCalledTimes(2);
    expect(publisherTransport.mock.calls[1][0].headers.Authorization).toBe(
      "Bearer publisher-token-refreshed"
    );
  });

  it("stops after a second 401", async () => {
    const publisherTransport = vi.fn(async () => ({ status: 401, data: {} }));
    await expect(factory({ publisherTransport }).ensureCapabilities()).rejects.toMatchObject({
      code: "UNAUTHORIZED",
      status: 401,
    });
    expect(publisherTransport).toHaveBeenCalledTimes(2);
  });

  it.each([
    [409, "IDEMPOTENCY_CONFLICT"],
    [500, "INTERNAL_ERROR"],
  ])("never retries POST status %i", async (status, code) => {
    const publisherTransport = vi.fn(async (config) => {
      if (config.url.endsWith("/capabilities")) return capabilities();
      return {
        status,
        data: {
          type: "https://errors.invalid/problem",
          title: "sanitized",
          status,
          code,
          detail: "must not escape",
          traceId: "trace-123456",
        },
      };
    });
    const client = factory({ publisherTransport });
    await expect(
      client.publishRelease(
        {
          candidateId: CANDIDATE,
          versionBump: "PATCH",
          description: "Description",
          changelog: "Changelog",
        },
        KEY
      )
    ).rejects.toMatchObject({
      code,
      traceId: "trace-123456",
      message: PUBLIC_PROBLEM_MESSAGES[code],
    });
    expect(publisherTransport).toHaveBeenCalledTimes(2);
  });

  it("never retries POST after a network failure", async () => {
    const publisherTransport = vi.fn(async (config) => {
      if (config.url.endsWith("/capabilities")) return capabilities();
      throw new Error("raw secret network message");
    });
    const client = factory({ publisherTransport });
    await expect(
      client.publishRelease(
        {
          candidateId: CANDIDATE,
          versionBump: "PATCH",
          description: "Description",
          changelog: "Changelog",
        },
        KEY
      )
    ).rejects.toMatchObject({ kind: "NETWORK_ERROR" });
    expect(publisherTransport).toHaveBeenCalledTimes(2);
  });
});

describe("releasePublisherClient candidates and releases", () => {
  it("loads only a strict READY candidate page and preserves an opaque cursor", async () => {
    const publisherTransport = vi.fn(async (config) => {
      if (config.url.endsWith("/capabilities")) return capabilities();
      return {
        status: 200,
        data: { items: [candidate()], nextCursor: "opaque+/cursor==" },
      };
    });
    const client = factory({ publisherTransport });
    await expect(client.listCandidates({ cursor: "previous+/==" })).resolves.toEqual({
      items: [candidate()],
      nextCursor: "opaque+/cursor==",
    });
    const requestUrl = publisherTransport.mock.calls[1][0].url;
    expect(requestUrl).toContain("eligibility=READY");
    expect(requestUrl).toContain("limit=100");
    expect(requestUrl).toContain("cursor=previous%2B%2F%3D%3D");
  });

  it.each([
    candidate({ eligibility: "NOT_ELIGIBLE" }),
    candidate({ ciStatus: "PENDING" }),
    candidate({ manifestStatus: "INVALID" }),
    candidate({ sourceCommit: SHA.toUpperCase().replaceAll("1", "A") }),
    candidate({ createdAt: "2026-02-30T12:00:00Z" }),
    { ...candidate(), extra: true },
  ])("rejects divergent candidate %#", async (mutant) => {
    const publisherTransport = vi.fn(async (config) => {
      if (config.url.endsWith("/capabilities")) return capabilities();
      return { status: 200, data: { items: [mutant], nextCursor: null } };
    });
    await expect(factory({ publisherTransport }).listCandidates()).rejects.toMatchObject({
      kind: "INVALID_RESPONSE",
    });
  });

  it("deduplicates identical candidates across pages and rejects divergent duplicates", async () => {
    const identicalPages = [
      { items: [candidate()], nextCursor: "next" },
      { items: [candidate()], nextCursor: null },
    ];
    const identicalTransport = vi.fn(async (config) => {
      if (config.url.endsWith("/capabilities")) return capabilities();
      return { status: 200, data: identicalPages.shift() };
    });
    const identicalClient = factory({ publisherTransport: identicalTransport });
    await expect(identicalClient.listCandidates()).resolves.toMatchObject({
      items: [candidate()],
    });
    await expect(
      identicalClient.listCandidates({ cursor: "next" })
    ).resolves.toMatchObject({ items: [] });

    const divergentPages = [
      { items: [candidate()], nextCursor: "next" },
      {
        items: [candidate({ sourceCommit: "2".repeat(40) })],
        nextCursor: null,
      },
    ];
    const divergentTransport = vi.fn(async (config) => {
      if (config.url.endsWith("/capabilities")) return capabilities();
      return { status: 200, data: divergentPages.shift() };
    });
    const divergentClient = factory({ publisherTransport: divergentTransport });
    await divergentClient.listCandidates();
    await expect(
      divergentClient.listCandidates({ cursor: "next" })
    ).rejects.toMatchObject({ kind: "INVALID_RESPONSE" });
  });

  it("loads at most ten release pages, deduplicates and sorts numeric SemVer", async () => {
    const pages = [
      {
        items: [release("v2.0.0"), release("v10.0.0")],
        nextCursor: "opaque cursor",
      },
      {
        items: [release("v2.0.0"), release("v1.999999999999999999999.0")],
        nextCursor: null,
      },
    ];
    const publisherTransport = vi.fn(async (config) => {
      if (config.url.endsWith("/capabilities")) return capabilities();
      return { status: 200, data: pages.shift() };
    });
    const result = await factory({ publisherTransport }).listReleases();
    expect(result.map((item) => item.release)).toEqual([
      "v10.0.0",
      "v2.0.0",
      "v1.999999999999999999999.0",
    ]);
    expect(publisherTransport.mock.calls[2][0].url).toContain(
      "cursor=opaque+cursor"
    );
  });

  it("rejects a divergent duplicate and a cursor after page ten", async () => {
    const duplicateTransport = vi.fn(async (config) => {
      if (config.url.endsWith("/capabilities")) return capabilities();
      if (!config.url.includes("cursor=")) {
        return {
          status: 200,
          data: { items: [release("v1.0.0")], nextCursor: "next" },
        };
      }
      return {
        status: 200,
        data: {
          items: [release("v1.0.0", { sourceCommit: "2".repeat(40) })],
          nextCursor: null,
        },
      };
    });
    await expect(factory({ publisherTransport: duplicateTransport }).listReleases()).rejects.toMatchObject({
      kind: "INVALID_RESPONSE",
    });

    const endlessTransport = vi.fn(async (config) => {
      if (config.url.endsWith("/capabilities")) return capabilities();
      return { status: 200, data: { items: [], nextCursor: "next" } };
    });
    await expect(factory({ publisherTransport: endlessTransport }).listReleases()).rejects.toMatchObject({
      kind: "INVALID_RESPONSE",
    });
    expect(endlessTransport).toHaveBeenCalledTimes(11);
  });
});

describe("releasePublisherClient publication and polling", () => {
  it("sends exact trimmed payload and idempotency key", async () => {
    const publisherTransport = vi.fn(async (config) => {
      if (config.url.endsWith("/capabilities")) return capabilities();
      return { status: 202, data: operation() };
    });
    const client = factory({ publisherTransport });
    await expect(
      client.publishRelease(
        {
          candidateId: CANDIDATE,
          versionBump: "PATCH",
          description: "  Description  ",
          changelog: "  Changelog\n",
        },
        KEY
      )
    ).resolves.toEqual(operation());
    expect(publisherTransport.mock.calls[1][0]).toMatchObject({
      method: "POST",
      url: `${PUBLISHER}/api/release-publisher/v1/releases`,
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": KEY,
        Authorization: "Bearer publisher-token",
        Accept: "application/json",
      },
      data: {
        candidateId: CANDIDATE,
        versionBump: "PATCH",
        description: "Description",
        changelog: "Changelog",
      },
    });
  });

  it.each([
    operation({ operationId: "operation-invalid" }),
    operation({ candidateId: "different-candidate" }),
    operation({ state: "SUCCEEDED" }),
    operation({ release: "v01.0.0" }),
    operation({ workflowRunUrl: "http://example.invalid/run" }),
    operation({ createdAt: "invalid" }),
    { ...operation(), extra: true },
  ])("rejects divergent publication operation %#", async (mutant) => {
    const publisherTransport = vi.fn(async (config) => {
      if (config.url.endsWith("/capabilities")) return capabilities();
      return { status: 202, data: mutant };
    });
    await expect(
      factory({ publisherTransport }).publishRelease(
        {
          candidateId: CANDIDATE,
          versionBump: "PATCH",
          description: "Description",
          changelog: "Changelog",
        },
        KEY
      )
    ).rejects.toMatchObject({ kind: "INVALID_RESPONSE" });
  });

  it("polls the exact operation and requires path, response and candidate bindings", async () => {
    const publisherTransport = vi.fn(async () => ({ status: 200, data: operation() }));
    const client = factory({ publisherTransport });
    await expect(client.getOperation(OPERATION, CANDIDATE)).resolves.toEqual(operation());
    expect(publisherTransport.mock.calls[0][0].url).toBe(
      `${PUBLISHER}/api/release-publisher/v1/operations/${OPERATION}`
    );

    const divergent = factory({
      publisherTransport: vi.fn(async () => ({
        status: 200,
        data: operation({ operationId: `pub_${"b".repeat(32)}` }),
      })),
    });
    await expect(divergent.getOperation(OPERATION, CANDIDATE)).rejects.toMatchObject({
      kind: "INVALID_RESPONSE",
    });
  });

  it("maps valid ProblemDetails without retaining remote detail", async () => {
    const publisherTransport = vi.fn(async () => ({
      status: 404,
      data: {
        type: "https://errors.invalid/not-found",
        title: "Not found",
        status: 404,
        code: "NOT_FOUND",
        detail: "internal candidate classification",
        traceId: "trace-123456",
      },
    }));
    const client = factory({ publisherTransport });
    let caught;
    try {
      await client.getOperation(OPERATION, CANDIDATE);
    } catch (error) {
      caught = error;
    }
    expect(caught).toBeInstanceOf(ReleasePublisherClientError);
    expect(caught).toMatchObject({
      code: "NOT_FOUND",
      status: 404,
      traceId: "trace-123456",
      message: PUBLIC_PROBLEM_MESSAGES.NOT_FOUND,
    });
    expect(JSON.stringify(caught)).not.toContain("internal candidate classification");
  });

  it("uses a generic sanitized error for malformed ProblemDetails", async () => {
    const publisherTransport = vi.fn(async () => ({
      status: 500,
      data: {
        code: "GITHUB_SECRET_FAILURE",
        detail: "raw secret",
      },
    }));
    const client = factory({ publisherTransport });
    await expect(client.getOperation(OPERATION, CANDIDATE)).rejects.toMatchObject({
      kind: "HTTP_ERROR",
      code: null,
      message: "Não foi possível concluir a solicitação.",
    });
  });
});
