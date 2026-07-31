// Funções utilitárias para tabelas

/**
 * Formata a data de yyyy-mm-dd para dd/mm/yyyy
 * @param {string} dateString - A data no formato yyyy-mm-dd
 * @returns {string} - A data formatada ou string vazia se inválida
 */
export function formatDate(dateString) {
  if (!dateString) return ""; // Retorna string vazia se não houver data

  try {
    const [year, month, day] = dateString.split("-");
    return `${day}/${month}/${year}`;
  } catch (error) {
    console.error("Formato de data inválido:", error);
    return dateString; // Retorna o valor original em caso de erro
  }
}

/**
 * Formata datetime de yyyy-mm-ddTHH:mm:ss para dd/mm/yyyy HH:mm
 * @param {string} datetimeString - O datetime no formato ISO
 * @returns {string} - O datetime formatado ou string vazia se inválida
 */
export function formatDateTime(datetimeString) {
  if (!datetimeString) return "";

  try {
    // Extrair data e hora do formato ISO
    const [datePart, timePart] = datetimeString.split("T");
    const [year, month, day] = datePart.split("-");
    const [hour, minute] = timePart.split(":");

    return `${day}/${month}/${year} ${hour}:${minute}`;
  } catch (error) {
    console.error("Formato de datetime inválido:", error);
    return datetimeString;
  }
}

/**
 * Formata a data atual no formato yyyy-mm-dd
 * @returns {string} - A data atual formatada
 */
export function formatCurrentDate() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
}

/**
 * Verifica se um valor é uma data válida em formato ISO ou yyyy-mm-dd
 * @param {any} value - O valor a ser verificado
 * @returns {boolean} - true se for uma data válida, false caso contrário
 */
export function isDateField(value) {
  if (typeof value !== "string") return false;

  // Checando formato yyyy-mm-dd
  const datePattern = /^\d{4}-\d{2}-\d{2}$/;
  if (datePattern.test(value)) return true;

  // Checando ISO 8601
  const isoPattern = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/;
  if (isoPattern.test(value)) return true;

  return false;
}

/**
 * Verifica se um valor é um número decimal
 * @param {any} value - O valor a ser verificado
 * @returns {boolean} - true se for um número decimal, false caso contrário
 */
export function isDouble(value) {
  if (typeof value === "number" && !isNaN(value)) {
    return !Number.isInteger(value) || String(value).includes(".");
  }
  if (typeof value === "string") {
    const trimmedValue = value.trim();
    const number = parseFloat(trimmedValue);
    return !isNaN(number) && trimmedValue.includes(".") && /^[-+]?\d*\.?\d+$/.test(trimmedValue);
  }
  return false;
}