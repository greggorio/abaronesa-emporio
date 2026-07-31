// Composable para exportação de dados tabulares
import { useQuasar } from "quasar";
import { formatDate, isDouble } from "./useTableUtils";

/**
 * Fornece funções para exportação de dados tabulares
 * @returns {Object} Objeto contendo funções de exportação
 */
export function useTableExport() {
  const $q = useQuasar();

  /**
   * Exporta os dados filtrados para um arquivo CSV
   * @param {Array} filteredRows - Linhas filtradas a serem exportadas
   * @param {Array} columns - Definições das colunas da tabela
   * @param {String} filename - Nome opcional do arquivo (sem extensão)
   */
  function exportToCSV(filteredRows, columns, filename) {
    // Verifica se há dados para exportar
    if (!filteredRows || filteredRows.length === 0) {
      // Notifica que não há dados
      if ($q && $q.notify) {
        $q.notify({
          color: "warning",
          message: "Não há dados para exportar",
          icon: "warning",
        });
      }
      return;
    }

    // Prepara os cabeçalhos das colunas
    const headers = columns.map((col) => col.label);

    // Prepara as linhas de dados, formatando os valores
    const rows = filteredRows.map((row) => {
      return columns.map((col) => {
        const value = row[col.field];

        // Formata os valores conforme o tipo
        if (value === undefined || value === null) return "";

        if (col.field === "pago") {
          return value ? "Sim" : "Não";
        }

        if (col.type === "date" && typeof value === "string") {
          return formatDate(value);
        }

        if (col.type === "decimal" || isDouble(value)) {
          return typeof value === "number" ? value.toFixed(2).replace(".", ",") : value;
        }

        return value;
      });
    });

    // Combina cabeçalhos e linhas
    const csvContent = [headers.join(";"), ...rows.map((row) => row.join(";"))].join("\n");

    // Adiciona BOM para correta exibição de caracteres especiais
    const BOM = "\uFEFF";
    const blob = new Blob([BOM + csvContent], { type: "text/csv;charset=utf-8;" });

    // Cria link para download e força o clique
    const link = document.createElement("a");
    const date = new Date().toISOString().split("T")[0]; // YYYY-MM-DD

    // Define o nome do arquivo com data atual
    const finalFilename = filename || `tabela_dados_${date}.csv`;

    // Cria uma URL para o Blob
    const url = URL.createObjectURL(blob);

    // Configura e dispara o download
    link.setAttribute("href", url);
    link.setAttribute("download", finalFilename);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    // Libera a URL criada
    URL.revokeObjectURL(url);

    // Feedback para o usuário
    if ($q && $q.notify) {
      $q.notify({
        color: "positive",
        message: "Exportação concluída com sucesso",
        icon: "check_circle",
      });
    }
  }

  return {
    exportToCSV,
  };
}
