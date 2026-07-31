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
const OUTPUT_FILE = `${OUTPUT_DIR}/ui-${new Date().toISOString().replace(/[-:T\.]/g, "").slice(0, 15)}-categoria-despesa-exclusao-associada-erro.png`;

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

    console.log("6. Looking for an existing category with associated accounts...");
    
    // Find the first row in the table
    const firstRow = page.locator('.q-table tbody tr').first();
    if (await firstRow.count() > 0) {
      // Click the delete button in the row (assuming there's a delete button)
      const deleteButton = firstRow.locator('button:has-text("Excluir"), button[aria-label*="excluir" i]');
      
      if (await deleteButton.count() > 0) {
        await deleteButton.first().click();
        console.log("7. Clicked delete button");
        
        // Wait for confirmation dialog
        await page.waitForTimeout(config.timeouts.default);
        
        // Look for confirmation button in dialog
        const confirmButton = page.locator('button:has-text("Confirma"), button:has-text("Sim"), .q-btn:has-text("Confirma")');
        if (await confirmButton.count() > 0) {
          await confirmButton.first().click();
          console.log("8. Clicked confirmation button");
          
          // Wait for response
          await page.waitForTimeout(config.timeouts.default);
        }
      } else {
        console.log("   No delete button found in the row");
      }
    }

    console.log("9. Taking screenshot...");
    const fs = await import("fs/promises");
    await fs.mkdir(OUTPUT_DIR, { recursive: true });
    await page.screenshot({ path: OUTPUT_FILE, fullPage: false });
    console.log(`   Screenshot saved to: ${OUTPUT_FILE}`);

    console.log("\n=== Execution completed - expecting error message ===");
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