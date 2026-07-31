import { describe, expect, it } from "vitest";
import { resolveReleasePublisherConfig } from "./releasePublisher";

const publisher = {
  DEV: true,
  PROD: false,
  VITE_RELEASE_CONTROL_MODE: "publisher",
  VITE_RELEASE_PUBLISHER_URL: "http://127.0.0.1:8090",
};

describe("resolveReleasePublisherConfig", () => {
  it("defaults development to disabled and ignores its URL", () => {
    expect(
      resolveReleasePublisherConfig({
        DEV: true,
        PROD: false,
        VITE_RELEASE_PUBLISHER_URL: "not-a-url",
      }),
    ).toEqual({ mode: "disabled", url: null });
  });

  it("accepts and normalizes the canonical publisher configuration", () => {
    expect(
      resolveReleasePublisherConfig({
        ...publisher,
        VITE_RELEASE_PUBLISHER_URL: "http://localhost:8090/",
      }),
    ).toEqual({ mode: "publisher", url: "http://localhost:8090" });
    expect(
      resolveReleasePublisherConfig({
        ...publisher,
        VITE_RELEASE_PUBLISHER_URL: "http://localhost:80",
      }),
    ).toEqual({ mode: "publisher", url: "http://localhost" });
  });

  it.each(["deployer", "unknown"])("rejects mode %s", (mode) => {
    expect(() =>
      resolveReleasePublisherConfig({
        ...publisher,
        VITE_RELEASE_CONTROL_MODE: mode,
      }),
    ).toThrow();
  });

  it.each([
    undefined,
    "",
    "https://127.0.0.1:8090",
    "http://example.com:8090",
    "http://127.0.0.1",
    "http://user@127.0.0.1:8090",
    "http://127.0.0.1:8090/api",
    "http://127.0.0.1:8090?x=1",
    "http://127.0.0.1:8090#x",
  ])("rejects publisher URL %s", (url) => {
    expect(() =>
      resolveReleasePublisherConfig({
        ...publisher,
        VITE_RELEASE_PUBLISHER_URL: url,
      }),
    ).toThrow();
  });

  it("always disables production without consulting runtime config", () => {
    globalThis.window.RuntimeConfig = {
      releasePublisherUrl: "http://127.0.0.1:8090",
    };
    try {
      expect(
        resolveReleasePublisherConfig({
          ...publisher,
          DEV: false,
          PROD: true,
        }),
      ).toEqual({ mode: "disabled", url: null });
    } finally {
      delete globalThis.window.RuntimeConfig;
    }
  });
});
