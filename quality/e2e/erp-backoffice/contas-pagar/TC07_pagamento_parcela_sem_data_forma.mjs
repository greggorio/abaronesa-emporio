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
const OUTPUT_FILE = `${OUTPUT_DIR}/ui-${new Date().toISOString().replace(/[-:T\.]/g, "").slice(0, 15)}-contas-pagar-pagamento-sem-data-forma-erro.png`;

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

    console.log("6. Looking for an existing account to attempt payment...");
    
    // Double-click on the first row to edit
    const firstRow = page.locator('.q-table tbody tr').first();
    if (await firstRow.count() > 0) {
      await firstRow.dblclick();
      console.log("7. Double clicked on first account");
      await page.waitForTimeout(config.timeouts.navigation);
    }

    console.log("8. Looking for installment payment buttons...");
    
    // Wait for the installment table to load
    await page.waitForSelector('.parcelas-table');
    
    // Find the first unpaid installment row
    const firstUnpaidInstallment = page.locator('tr:has(td:has-text("Em aberto"))').first();
    
    if (await firstUnpaidInstallment.count() > 0) {
      // Intentionally NOT filling payment date or payment method
      
      // Click the payment confirmation button
      const confirmPaymentButton = firstUnpaidInstallment.locator('button:has-text("Confirmar Pagamento")').first();
      if (await confirmPaymentButton.count() > 0) {
        await confirmPaymentButton.click();
        console.log("9. Clicked confirm payment button without filling date/method");
        await page.waitForTimeout(config.timeouts.default);
      }
    }

    console.log("10. Taking screenshot...");
    const fs = await import("fs/promises");
    await fs.mkdir(OUTPUT_DIR, { recursive: true });
    await page.screenshot({ path: OUTPUT_FILE, fullPage: false });
    console.log(`   Screenshot saved to: ${OUTPUT_FILE}`);

    console.log("\n=== Payment attempt completed - expecting error message ===");
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