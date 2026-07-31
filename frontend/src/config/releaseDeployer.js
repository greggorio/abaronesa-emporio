const DISABLED = Object.freeze({ mode: "disabled" });

function runtimeConfigFor(environment) {
  if (environment && typeof environment.runtimeConfig === "object") {
    return environment.runtimeConfig;
  }
  if (environment && typeof environment.RUNTIME_CONFIG === "object") {
    return environment.RUNTIME_CONFIG;
  }
  if (typeof window !== "undefined" && window.RuntimeConfig) {
    return window.RuntimeConfig;
  }
  return null;
}

export function resolveReleaseDeployerConfig(environment) {
  const env = environment ?? {};

  // The deployer is deliberately production-only. Local publisher mode stays
  // isolated in releasePublisher.js and is never inferred here.
  if (env.DEV === true || env.PROD !== true) {
    return DISABLED;
  }

  const runtime = runtimeConfigFor(env);
  if (
    runtime === null ||
    typeof runtime.releaseControlMode !== "string" ||
    runtime.releaseControlMode !== "deployer"
  ) {
    return DISABLED;
  }

  return Object.freeze({ mode: "deployer" });
}

export const releaseDeployerConfig = resolveReleaseDeployerConfig({
  ...import.meta.env,
  runtimeConfig: typeof window !== "undefined" ? window.RuntimeConfig : null,
});
