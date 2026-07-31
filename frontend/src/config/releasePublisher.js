const LOOPBACK_HOSTS = new Set(["localhost", "127.0.0.1"]);

export function resolveReleasePublisherConfig(environment) {
  const env = environment ?? {};
  if (env.PROD === true) {
    return Object.freeze({ mode: "disabled", url: null });
  }
  if (env.DEV !== true) {
    return Object.freeze({ mode: "disabled", url: null });
  }

  const mode = env.VITE_RELEASE_CONTROL_MODE || "disabled";
  if (!["disabled", "publisher"].includes(mode)) {
    throw new Error("Configuração local do publisher inválida.");
  }
  if (mode === "disabled") {
    return Object.freeze({ mode, url: null });
  }

  const rawUrl = env.VITE_RELEASE_PUBLISHER_URL;
  if (typeof rawUrl !== "string" || rawUrl.length === 0) {
    throw new Error("URL local do publisher obrigatória.");
  }

  let parsed;
  try {
    parsed = new URL(rawUrl);
  } catch {
    throw new Error("URL local do publisher inválida.");
  }
  const authority = rawUrl.slice("http://".length).split(/[/?#]/, 1)[0];
  const hasExplicitPort = /:[0-9]+$/.test(authority);
  if (
    parsed.protocol !== "http:" ||
    !LOOPBACK_HOSTS.has(parsed.hostname) ||
    !hasExplicitPort ||
    parsed.username.length > 0 ||
    parsed.password.length > 0 ||
    !["", "/"].includes(parsed.pathname) ||
    parsed.search.length > 0 ||
    parsed.hash.length > 0
  ) {
    throw new Error("URL local do publisher inválida.");
  }

  return Object.freeze({ mode, url: parsed.origin });
}

export const releasePublisherConfig = resolveReleasePublisherConfig(import.meta.env);
