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

const OUTPUT_DIR = config.screenshots.directory + "contas-pagar";
const OUTPUT_FILE = `${OUTPUT_DIR}/ui-${new Date().toISOString().replace(/[-:T\.]/g, "").slice(0, 15)}-contas-pagar-atualizacao.png`;

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
    console.log("4. Login completed, navigating to contas a pagar...");

    console.log("5. Navigating to /#/container?programa=contas-pagar...");
    await page.goto(BASE_URL + "/#/container?programa=contas-pagar", { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(config.timeouts.navigation);

    console.log("6. Looking for an existing account to update...");
    
    // Double-click on the first row to edit
    const firstRow = page.locator('.q-table tbody tr').first();
    if (await firstRow.count() > 0) {
      await firstRow.dblclick();
      console.log("7. Double clicked on first account");
      await page.waitForTimeout(config.timeouts.navigation);
    }

    console.log("8. Updating account payable information...");
    
    // Update the description field
    const descricaoInput = page.locator('textarea[aria-label="Descricao"], textarea[placeholder*="descricao" i], input[placeholder*="descricao" i]').first();
    if (await descricaoInput.count() > 0) {
      await descricaoInput.fill(config.testData.contaPagar.descricao + " - Atualizado");
      console.log(`   Updated descricao: ${config.testData.contaPagar.descricao} - Atualizado`);
    }

    // Update the total value
    const valorTotalInput = page.locator('input[aria-label*="Valor Total" i], input[placeholder*="0,00" i], input[type="number"][step="0.01"]').first();
    if (await valorTotalInput.count() > 0) {
      await valorTotalInput.fill("1500.00");
      console.log(`   Updated valor total: 1500.00`);
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

    console.log("\n=== Update execution completed successfully ===");
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