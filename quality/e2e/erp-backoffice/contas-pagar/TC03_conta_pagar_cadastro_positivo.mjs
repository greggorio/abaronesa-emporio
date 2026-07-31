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
const OUTPUT_FILE = `${OUTPUT_DIR}/ui-${new Date().toISOString().replace(/[-:T\.]/g, "").slice(0, 15)}-contas-pagar-cadastro-positivo.png`;

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

    console.log("8. Looking for form fields...");
    await page.waitForTimeout(config.timeouts.default);

    // Select supplier (fornecedor)
    const fornecedorSelect = page.locator('input[aria-label="Fornecedor"], input[placeholder*="fornecedor" i], .q-field__native[role="combobox"]').first();
    
    if (await fornecedorSelect.count() > 0) {
      await fornecedorSelect.click();
      await fornecedorSelect.fill(config.testData.fornecedor.razaoSocial);
      await page.waitForTimeout(1000);
      
      // Select the option from dropdown
      const fornecedorOption = page.locator('div.q-item:has-text("' + config.testData.fornecedor.razaoSocial + '")').first();
      if (await fornecedorOption.count() > 0) {
        await fornecedorOption.click();
        console.log(`   Selected fornecedor: ${config.testData.fornecedor.razaoSocial}`);
      }
    }

    // Select expense category (categoria despesa)
    const categoriaSelect = page.locator('input[aria-label="Categoria Despesa"], input[placeholder*="categoria" i], .q-field__native[role="combobox"]').first();
    
    if (await categoriaSelect.count() > 0) {
      await categoriaSelect.click();
      await categoriaSelect.fill(config.testData.categoriaDespesa.nome);
      await page.waitForTimeout(1000);
      
      // Select the option from dropdown
      const categoriaOption = page.locator('div.q-item:has-text("' + config.testData.categoriaDespesa.nome + '")').first();
      if (await categoriaOption.count() > 0) {
        await categoriaOption.click();
        console.log(`   Selected categoria despesa: ${config.testData.categoriaDespesa.nome}`);
      }
    }

    // Fill description
    const descricaoInput = page.locator('textarea[aria-label="Descricao"], textarea[placeholder*="descricao" i], input[placeholder*="descricao" i]').first();
    
    if (await descricaoInput.count() > 0) {
      await descricaoInput.fill(config.testData.contaPagar.descricao);
      console.log(`   Filled descricao: ${config.testData.contaPagar.descricao}`);
    }

    // Fill total amount
    const valorTotalInput = page.locator('input[aria-label*="Valor Total" i], input[placeholder*="0,00" i], input[type="number"][step="0.01"]').first();
    
    if (await valorTotalInput.count() > 0) {
      await valorTotalInput.fill(config.testData.contaPagar.valorTotal.toString());
      console.log(`   Filled valor total: ${config.testData.contaPagar.valorTotal}`);
    }

    // Fill number of installments
    const numeroParcelasInput = page.locator('input[aria-label*="Numero Parcelas" i], input[type="number"][step="1"]').nth(1); // Second number input
    
    if (await numeroParcelasInput.count() > 0) {
      await numeroParcelasInput.fill(config.testData.contaPagar.numeroParcelas.toString());
      console.log(`   Filled numero parcelas: ${config.testData.contaPagar.numeroParcelas}`);
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

    // Generate installments
    console.log("11. Looking for 'Gerar Parcelas' button...");
    const gerarParcelasButton = page.locator('button:has-text("Gerar Parcelas")');
    if (await gerarParcelasButton.count() > 0) {
      await gerarParcelasButton.click();
      console.log("12. Clicked Gerar Parcelas button");
      await page.waitForTimeout(config.timeouts.default);
    }

    console.log("13. Taking screenshot...");
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