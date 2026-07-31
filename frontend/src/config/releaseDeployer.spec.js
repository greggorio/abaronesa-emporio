import { describe, expect, it } from "vitest";
import { resolveReleaseDeployerConfig } from "./releaseDeployer";

describe("releaseDeployer config", () => {
  it("enables only a production runtime deployer value", () => {
    expect(
      resolveReleaseDeployerConfig({
        PROD: true,
        runtimeConfig: { releaseControlMode: "deployer" },
      }),
    ).toEqual({ mode: "deployer" });
  });

  it.each([
    ["development", { DEV: true, PROD: false, runtimeConfig: { releaseControlMode: "deployer" } }],
    ["missing", { PROD: true, runtimeConfig: {} }],
    ["publisher", { PROD: true, runtimeConfig: { releaseControlMode: "publisher" } }],
    ["unknown", { PROD: true, runtimeConfig: { releaseControlMode: "other" } }],
    ["wrong type", { PROD: true, runtimeConfig: { releaseControlMode: 1 } }],
  ])("fails closed for %s", (_name, environment) => {
    expect(resolveReleaseDeployerConfig(environment)).toEqual({ mode: "disabled" });
  });
});
