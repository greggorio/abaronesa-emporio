#!/usr/bin/env node

import { chromium } from "playwright";

const BASE_URL = "http://localhost:8084";
const EMAIL = "root@localhost";
const PASSWORD = "123456";

const OUTPUT_DIR = ".ai-workflow/evidence/contas-pagar";
const OUTPUT_FILE = `${OUTPUT_DIR}/ui-${new Date().toISOString().replace(/[-:T\.]/g, "").slice(0, 15)}-contas-pagar-cadastro.png`;

async function main() {
  const browser = await chromium.launch({ headless: true }); // Changed to false for visibility
  const context = await browser.newContext({
    viewport: { width: 1516, height: 768 },
  });
  const page = await context.newPage();

  try {
    console.log("1. Navigating to login page...");
    await page.goto(BASE_URL + "/#/", { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(2000);

    console.log("2. Filling login credentials...");
    const emailInput = page.locator('input[type="email"], input[type="text"]').first();
    const passwordInput = page.locator('input[type="password"]').first();

    await emailInput.fill(EMAIL);
    await passwordInput.fill(PASSWORD);

    console.log("3. Clicking login button...");
    const submitButton = page.locator('button[type="submit"]').first();
    await submitButton.click();

    await page.waitForTimeout(3500);
    console.log("4. Login completed, navigating to contas a pagar...");

    console.log("5. Navigating to /#/container?programa=contas-pagar...");
    await page.goto(BASE_URL + "/#/container?programa=contas-pagar", { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(3000);

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

    await page.waitForTimeout(2000);

    console.log("8. Looking for form fields...");
    await page.waitForTimeout(2000);

    // Select supplier (fornecedor)
    const fornecedorSelect = page.locator('input[aria-label="Fornecedor"], input[placeholder*="fornecedor" i], .q-field__native[role="combobox"]').first();
    
    if (await fornecedorSelect.count() > 0) {
      await fornecedorSelect.click();
      await fornecedorSelect.fill("Fornecedor Teste Automatizado"); // Assuming we created this in previous test
      await page.waitForTimeout(1000);
      
      // Select the option from dropdown
      const fornecedorOption = page.locator('div.q-item:has-text("Fornecedor Teste Automatizado")').first();
      if (await fornecedorOption.count() > 0) {
        await fornecedorOption.click();
        console.log("   Selected fornecedor: Fornecedor Teste Automatizado");
      }
    }

    // Select expense category (categoria despesa)
    const categoriaSelect = page.locator('input[aria-label="Categoria Despesa"], input[placeholder*="categoria" i], .q-field__native[role="combobox"]').first();
    
    if (await categoriaSelect.count() > 0) {
      await categoriaSelect.click();
      await categoriaSelect.fill("Despesa Teste Automatizado"); // Assuming we created this in previous test
      await page.waitForTimeout(1000);
      
      // Select the option from dropdown
      const categoriaOption = page.locator('div.q-item:has-text("Despesa Teste Automatizado")').first();
      if (await categoriaOption.count() > 0) {
        await categoriaOption.click();
        console.log("   Selected categoria despesa: Despesa Teste Automatizado");
      }
    }

    // Fill description
    const descricaoInput = page.locator('textarea[aria-label="Descricao"], textarea[placeholder*="descricao" i], input[placeholder*="descricao" i]').first();
    
    if (await descricaoInput.count() > 0) {
      await descricaoInput.fill("Conta a pagar teste automatizado");
      console.log("   Filled descricao: Conta a pagar teste automatizado");
    }

    // Fill total amount
    const valorTotalInput = page.locator('input[aria-label*="Valor Total" i], input[placeholder*="0,00" i], input[type="number"][step="0.01"]').first();
    
    if (await valorTotalInput.count() > 0) {
      await valorTotalInput.fill("1000.00");
      console.log("   Filled valor total: 1000.00");
    }

    // Fill number of installments
    const numeroParcelasInput = page.locator('input[aria-label*="Numero Parcelas" i], input[type="number"][step="1"]').nth(1); // Second number input
    
    if (await numeroParcelasInput.count() > 0) {
      await numeroParcelasInput.fill("2");
      console.log("   Filled numero parcelas: 2");
    }

    await page.waitForTimeout(1000);

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

    await page.waitForTimeout(3000);

    // Generate installments
    console.log("11. Looking for 'Gerar Parcelas' button...");
    const gerarParcelasButton = page.locator('button:has-text("Gerar Parcelas")');
    if (await gerarParcelasButton.count() > 0) {
      await gerarParcelasButton.click();
      console.log("12. Clicked Gerar Parcelas button");
      await page.waitForTimeout(2000);
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