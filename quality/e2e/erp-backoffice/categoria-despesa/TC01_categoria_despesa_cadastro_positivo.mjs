#!/usr/bin/env node

import { chromium } from "playwright";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const configPath = path.join(__dirname, "..", "test-config.json");
const config = JSON.parse(fs.readFileSync(configPath, "utf8"));

const BASE_URL = config.baseUrl;
const EMAIL = config.credentials.email;
const PASSWORD = config.credentials.password;

const OUTPUT_DIR = config.screenshots.directory + "categoria-despesa";
const OUTPUT_FILE = `${OUTPUT_DIR}/ui-${new Date().toISOString().replace(/[-:T\.]/g, "").slice(0, 15)}-categoria-despesa-cadastro-positivo.png`;

async function main() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1516, height: 768 },
  });
  const page = await context.newPage();

  try {
    console.log("1. Navigating to login page...");
    await page.goto(BASE_URL + "/#/", { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(config.timeouts.navigation);

    console.log("2. Filling login credentials...");
    const emailInput = page.locator('input[type="email"], input[type="text"]').first();
    const passwordInput = page.locator('input[type="password"]').first();

    await emailInput.fill(EMAIL);
    await passwordInput.fill(PASSWORD);

    console.log("3. Clicking login button...");
    const submitButton = page.locator('button[type="submit"]').first();
    await submitButton.click();

    await page.waitForTimeout(config.timeouts.navigation);
    console.log("4. Login completed, navigating to categorias de despesa...");

    console.log("5. Navigating to /#/container?programa=categorias-despesa...");
    await page.goto(BASE_URL + "/#/container?programa=categorias-despesa", { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(config.timeouts.navigation);

    console.log("6. Looking for 'adicionar' button...");
    const addButtons = page.locator('button:has-text("adicionar"), button:has-text("Adicionar"), .q-btn:has-text("adicionar"), [aria-label*="adicionar"]');
    const addButtonCount = await addButtons.count();
    console.log(`   Found ${addButtonCount} add button(s)`);

    if (addButtonCount > 0) {
      await addButtons.first().click();
      console.log("7. Clicked adicionar button");
    } else {
      console.log("   No explicit add button found, trying alternative...");
    }

    await page.waitForTimeout(config.timeouts.default);

    console.log("8. Looking for dialog and form fields...");
    await page.waitForTimeout(config.timeouts.default);

    const nomeInput = page.locator('input[aria-label="Nome"], input[label="Nome"], input[placeholder="Nome"]').first();

    const nomeCount = await nomeInput.count();
    console.log(`   Found ${nomeCount} nome input(s)`);

    if (nomeCount > 0) {
      await nomeInput.click();
      await nomeInput.fill(config.testData.categoriaDespesa.nome);
      console.log(`   Filled nome: ${config.testData.categoriaDespesa.nome}`);
    }

    await page.waitForTimeout(config.timeouts.default);

    console.log("9. Looking for save button...");
    const saveButtons = page.locator('button:has-text("salvar"), button:has-text("Salvar"), .q-btn:has-text("salvar"), [aria-label*="salvar"]');
    const saveButtonCount = await saveButtons.count();
    console.log(`   Found ${saveButtonCount} save button(s)`);

    if (saveButtonCount > 0) {
      await saveButtons.first().click();
      console.log("10. Clicked save button");
    } else {
      console.log("   No explicit save button found");
    }

    await page.waitForTimeout(config.timeouts.navigation);

    console.log("11. Taking screenshot...");
    const fs = await import("fs/promises");
    await fs.mkdir(OUTPUT_DIR, { recursive: true });
    await page.screenshot({ path: OUTPUT_FILE, fullPage: false });
    console.log(`   Screenshot saved to: ${OUTPUT_FILE}`);

    console.log("\n=== Execution completed successfully ===");
    console.log(`Screenshot: ${OUTPUT_FILE}`);

  } catch (err) {
    console.error("Error during execution:", err.message);
    try {
      const fs = await import("fs/promises");
      await fs.mkdir(OUTPUT_DIR, { recursive: true });
      await page.screenshot({ path: OUTPUT_FILE, fullPage: true });
      console.log(`Error screenshot saved to: ${OUTPUT_FILE}`);
    } catch (screenshotErr) {
      console.error("Failed to take error screenshot:", screenshotErr.message);
    }
    process.exit(1);
  } finally {
    await browser.close();
  }
}

main();