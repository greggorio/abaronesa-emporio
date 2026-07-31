import { jsPDF } from "jspdf";
import html2canvas from "html2canvas";
import { useQuasar } from "quasar";
import { formatDate, formatDateTime } from "@/composables/useTableUtils";

export function useReportGenerator() {
  const $q = useQuasar();

  /**
   * Formata a data e hora atual para exibição no relatório
   * @returns {string} Data e hora formatadas
   */
  function getFormattedDateTime() {
    const today = new Date();
    const dateStr = today.toLocaleDateString("pt-BR");
    const timeStr = today.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });
    return `${dateStr} às ${timeStr}`;
  }

  /**
   * Obtém o label do operador baseado no tipo e valor
   * @param {string} operator - Código do operador
   * @param {string} type - Tipo de dados
   * @param {Array} operatorsList - Lista de operadores disponíveis
   * @returns {string} - Descrição do operador
   */
  function getOperatorLabel(operator, type, operatorsList) {
    const found = operatorsList.find((op) => op.value === operator);
    return found ? found.label.toLowerCase() : operator;
  }

  /**
   * Gera a descrição textual dos filtros ativos
   * @param {Object} options - Opções de configuração
   * @param {Object} options.localFilter - Filtro global
   * @param {Object} options.filters - Filtros avançados
   * @param {Array} options.columns - Colunas da tabela
   * @param {Function} options.getColumnType - Função para obter o tipo da coluna
   * @param {Array} options.stringOperators - Operadores para strings
   * @param {Array} options.numberOperators - Operadores para números
   * @param {Array} options.dateOperators - Operadores para datas
   * @returns {string} Descrição dos filtros ativos
   */
  function getActiveFiltersDescription({ localFilter, filters, columns, getColumnType, stringOperators, numberOperators, dateOperators }) {
    const activeFilters = [];

    // Adiciona filtro global se existir
    if (localFilter) {
      activeFilters.push(`Pesquisa global: "${localFilter}"`);
    }

    // Adiciona filtros avançados
    for (const field in filters) {
      const filter = filters[field];
      if (!filter.active) continue;

      // Encontra a coluna correspondente para obter o label
      const column = columns.find((col) => col.field === field);
      if (!column) continue;

      const columnLabel = column.label;
      const type = getColumnType(field);
      let filterDesc = "";

      switch (type) {
        case "string":
          filterDesc = `${columnLabel} ${getOperatorLabel(filter.operator, "string", stringOperators)} "${filter.value}"`;
          break;
        case "number":
          if (filter.operator === "between") {
            filterDesc = `${columnLabel} entre ${filter.value} e ${filter.value2}`;
          } else {
            filterDesc = `${columnLabel} ${getOperatorLabel(filter.operator, "number", numberOperators)} ${filter.value}`;
          }
          break;
        case "date":
          if (filter.operator === "today") {
            filterDesc = `${columnLabel} é a data atual`;
          } else if (filter.operator === "between") {
            filterDesc = `${columnLabel} entre ${formatDate(filter.value)} e ${formatDate(filter.value2)}`;
          } else {
            filterDesc = `${columnLabel} ${getOperatorLabel(filter.operator, "date", dateOperators)} ${formatDate(filter.value)}`;
          }
          break;
        case "boolean":
          filterDesc = `${columnLabel} é ${filter.value ? "Sim" : "Não"}`;
          break;
      }

      activeFilters.push(filterDesc);
    }

    return activeFilters.join("; ");
  }

  function formatByPreset(value, formatPreset, normalizedType) {
    if (!formatPreset || value === null || value === undefined) return value;

    if (typeof formatPreset !== "string") return value;

    const [presetType, presetParam] = formatPreset.split(":");
    const numericValue = typeof value === "number" ? value : parseFloat(String(value).replace(",", "."));

    switch (presetType) {
      case "currency":
        if (Number.isNaN(numericValue)) return value;
        return new Intl.NumberFormat("pt-BR", {
          style: "currency",
          currency: presetParam || "BRL",
        }).format(numericValue);
      case "number":
        if (Number.isNaN(numericValue)) return value;
        if (presetParam === "thousand") {
          return new Intl.NumberFormat("pt-BR").format(numericValue);
        }
        if (presetParam && !Number.isNaN(Number(presetParam))) {
          return numericValue.toFixed(parseInt(presetParam, 10)).replace(".", ",");
        }
        return numericValue;
      case "percent":
        return `${value}%`;
      case "boolean":
        if (presetParam === "ativo-inativo") return value ? "Ativo" : "Inativo";
        if (presetParam === "habilitado") return value ? "Habilitado" : "Desabilitado";
        return value ? "Sim" : "Não";
      case "date":
        if (typeof value === "string") {
          const dateOnly = value.includes("T") ? value.split("T")[0] : value;
          return formatDate(dateOnly);
        }
        return value;
      case "datetime":
        return typeof value === "string" ? formatDateTime(value) : value;
      default:
        // Se não reconhecido, tenta aplicar formatação baseada no tipo
        if (normalizedType === "CURRENCY") return formatByPreset(value, "currency", normalizedType);
        if (normalizedType === "NUMBER") return formatByPreset(value, "number", normalizedType);
        return value;
    }
  }

  /**
   * Gera um relatório PDF baseado nos dados e colunas visíveis da tabela
   * @param {Object} options - Opções de configuração do relatório
   * @param {Array} options.rows - Dados da tabela
   * @param {Array} options.visibleColumns - Colunas visíveis
   * @param {string} options.title - Título do relatório
   * @param {string|null} options.forcedOrientation - Orientação forçada ('portrait' ou 'landscape')
   * @param {Object|string} options.localFilter - Filtro global
   * @param {Object} options.filters - Filtros avançados
   * @param {Function} options.getColumnType - Função para obter o tipo da coluna
   * @param {Array} options.stringOperators - Operadores para strings
   * @param {Array} options.numberOperators - Operadores para números
   * @param {Array} options.dateOperators - Operadores para datas
   * @returns {Promise<void>}
   */
  async function generateReport({
    rows,
    visibleColumns,
    title,
    forcedOrientation = null,
    localFilter = "",
    filters = {},
    getColumnType,
    stringOperators = [],
    numberOperators = [],
    dateOperators = [],
  }) {
    // Verifica se há dados para o relatório
    if (!rows || rows.length === 0) {
      $q.notify({
        color: "warning",
        message: "Não há dados para gerar o relatório",
        icon: "warning",
      });
      return;
    }

    $q.notify({
      type: "primary",
      message: "Gerando relatório...",
      position: "center",
      spinner: true,
    });

    try {
      // Determina automaticamente a orientação com base no número de colunas
      // A menos que tenha sido especificada uma orientação
      const columnCount = visibleColumns.length;
      const orientation = forcedOrientation || (columnCount > 5 ? "landscape" : "portrait");

      // Define as dimensões de página baseadas na orientação
      const isLandscape = orientation === "landscape";
      const pageWidth = isLandscape ? 297 : 210;
      const pageHeight = isLandscape ? 210 : 297;
      const margin = 10;

      // Cria um contêiner temporário para renderizar o relatório
      const reportContainer = document.createElement("div");
      reportContainer.style.position = "absolute";
      reportContainer.style.left = "-9999px";
      // Ajusta a largura do contêiner conforme a orientação
      reportContainer.style.width = `${pageWidth}mm`;
      document.body.appendChild(reportContainer);

      // Obtém o título do relatório
      const reportTitle = `Relatório de ${title}`;

      // Ajusta o número de linhas por página com base na orientação
      // No formato paisagem podemos mostrar menos linhas devido à altura menor
      const rowsPerPage = isLandscape ? 14 : 20;
      const totalRows = rows.length;
      const totalPages = Math.ceil(totalRows / rowsPerPage);

      // Prepara a data atual formatada
      const dateTimeStr = getFormattedDateTime();

      // Prepara a string dos filtros ativos
      const activeFiltersStr = getActiveFiltersDescription({
        localFilter,
        filters,
        columns: visibleColumns,
        getColumnType,
        stringOperators,
        numberOperators,
        dateOperators,
      });

      // Cria o documento PDF com a orientação adequada
      const doc = new jsPDF({
        orientation: orientation,
        unit: "mm",
        format: "a4",
        compress: true,
      });

      // Percorre e renderiza cada página
      for (let pageIndex = 0; pageIndex < totalPages; pageIndex++) {
        // Se não for a primeira página, adiciona uma nova página
        if (pageIndex > 0) {
          doc.addPage();
        }

        // Calcula quais linhas pertencem a esta página
        const startRow = pageIndex * rowsPerPage;
        const endRow = Math.min(startRow + rowsPerPage, totalRows);
        const pageRows = rows.slice(startRow, endRow);

        // Limpa o contêiner e prepara para renderizar esta página
        reportContainer.innerHTML = "";

        // Cria a estrutura HTML da página
        const pageElement = document.createElement("div");
        pageElement.className = "report-page";
        pageElement.style.width = `${pageWidth}mm`;
        pageElement.style.height = `${pageHeight}mm`;
        pageElement.style.padding = "10mm";
        pageElement.style.backgroundColor = "white";
        pageElement.style.boxSizing = "border-box";
        pageElement.style.fontFamily = "Arial, sans-serif";
        pageElement.style.fontSize = "12px";
        pageElement.style.color = "#333";

        // Adiciona cabeçalho
        const headerElement = document.createElement("div");
        headerElement.style.display = "flex";
        headerElement.style.justifyContent = "space-between";
        headerElement.style.alignItems = "center";
        headerElement.style.marginBottom = "15px";
        headerElement.style.borderBottom = "2px solid #000000";
        headerElement.style.paddingBottom = "10px";

        // Logo (opcional)
        const logoElement = document.createElement("div");
        logoElement.style.width = "50px";
        logoElement.innerHTML =
          '<div style="width:40px;height:40px;background:#000000;border-radius:50%;display:flex;align-items:center;justify-content:center;color:white;font-weight:bold;">SD</div>';

        // Título
        const titleElement = document.createElement("div");
        titleElement.style.flex = "1";
        titleElement.style.textAlign = "center";
        titleElement.style.fontSize = "18px";
        titleElement.style.fontWeight = "bold";
        titleElement.style.color = "#000000";
        titleElement.textContent = reportTitle;

        // Data/Hora
        const dateElement = document.createElement("div");
        dateElement.style.fontSize = "10px";
        dateElement.style.textAlign = "right";
        dateElement.textContent = dateTimeStr;

        headerElement.appendChild(logoElement);
        headerElement.appendChild(titleElement);
        headerElement.appendChild(dateElement);
        pageElement.appendChild(headerElement);

        // Adiciona seção de filtros (SEMPRE exibe esta seção)
        const filtersElement = document.createElement("div");
        filtersElement.style.backgroundColor = "#f5f5f5";
        filtersElement.style.borderRadius = "4px";
        filtersElement.style.padding = "8px";
        filtersElement.style.marginBottom = "15px";
        filtersElement.style.fontSize = "11px";

        const filtersTitle = document.createElement("div");
        filtersTitle.style.fontWeight = "bold";
        filtersTitle.style.marginBottom = "5px";
        filtersTitle.textContent = "Filtros aplicados:";

        filtersElement.appendChild(filtersTitle);

        // Verifica se há filtros para exibir
        if (activeFiltersStr) {
          filtersElement.appendChild(document.createTextNode(activeFiltersStr));
        } else {
          // Caso não haja filtros, exibe uma mensagem indicando isso
          const noFiltersText = document.createElement("em");
          noFiltersText.textContent = "Nenhum filtro aplicado";
          noFiltersText.style.color = "#666";
          filtersElement.appendChild(noFiltersText);
        }

        pageElement.appendChild(filtersElement);

        // Cria tabela
        const tableElement = document.createElement("table");
        tableElement.style.width = "100%";
        tableElement.style.borderCollapse = "collapse";
        tableElement.style.marginBottom = "15px";

        // Cabeçalho da tabela
        const theadElement = document.createElement("thead");
        const headerRow = document.createElement("tr");
        headerRow.style.backgroundColor = "#A2A2A2";
        headerRow.style.color = "white";

        // Adiciona células de cabeçalho para colunas visíveis
        visibleColumns.forEach((column) => {
          const th = document.createElement("th");
          th.style.padding = "8px";
          th.style.textAlign = column.align || "left";
          th.style.borderBottom = "2px solid #ddd";
          th.style.fontSize = "12px";

          // No modo paisagem, podemos reduzir um pouco o tamanho da fonte para caber mais
          if (isLandscape && columnCount > 7) {
            th.style.fontSize = "11px";
            th.style.padding = "6px";
          }

          th.textContent = column.label;
          headerRow.appendChild(th);
        });

        theadElement.appendChild(headerRow);
        tableElement.appendChild(theadElement);

        // Corpo da tabela
        const tbodyElement = document.createElement("tbody");

        // Adiciona linhas de dados
        pageRows.forEach((row, index) => {
          const tr = document.createElement("tr");

          // Listras alternadas
          if (index % 2 === 1) {
            tr.style.backgroundColor = "#f9f9f9";
          }

          // Adiciona células para cada coluna visível
          visibleColumns.forEach((column) => {
            const td = document.createElement("td");
            td.style.padding = "8px";
            td.style.borderBottom = "1px solid #eee";
            td.style.textAlign = column.align || "left";

            // Ajusta o tamanho da fonte em modo paisagem com muitas colunas
            if (isLandscape && columnCount > 7) {
              td.style.fontSize = "11px";
              td.style.padding = "6px";
            }

            // Formata o valor de acordo com o tipo
            const value = row[column.field];
            let displayValue = "";

            if (value !== null && value !== undefined) {
              const normalizedType = column.type ? column.type.toUpperCase() : "";

              if (column.field === "pago" || normalizedType === "BOOLEAN") {
                displayValue = value ? "Sim" : "Não";
              } else if (typeof column.format === "function") {
                displayValue = column.format(value);
              } else if (typeof column.format === "string") {
                displayValue = formatByPreset(value, column.format, normalizedType);
              } else if (normalizedType === "DATETIME" && typeof value === "string") {
                // Para DATETIME, exibir data e hora (dd/MM/yyyy HH:mm)
                displayValue = formatDateTime(value);
              } else if (normalizedType === "DATE" && typeof value === "string") {
                // Para DATE, exibir apenas data (dd/MM/yyyy)
                const dateOnly = value.includes("T") ? value.split("T")[0] : value;
                displayValue = formatDate(dateOnly);
              } else if (normalizedType === "CURRENCY" || column.format === "currency") {
                displayValue = typeof value === "number" ? `R$ ${value.toFixed(2).replace(".", ",")}` : value;
              } else if (normalizedType === "NUMBER" || column.type === "decimal" || (typeof getColumnType === "function" && getColumnType(column.field) === "number")) {
                displayValue = typeof value === "number" ? value.toFixed(2).replace(".", ",") : value;
              } else {
                displayValue = String(value);
              }
            }

            td.textContent = displayValue;
            tr.appendChild(td);
          });

          tbodyElement.appendChild(tr);
        });

        tableElement.appendChild(tbodyElement);
        pageElement.appendChild(tableElement);

        // Adiciona seção de totais (opcional)
        if (pageIndex === totalPages - 1) {
          // Apenas na última página
          const totalsElement = document.createElement("div");
          totalsElement.style.marginTop = "20px";
          totalsElement.style.borderTop = "2px solid #ddd";
          totalsElement.style.paddingTop = "10px";
          totalsElement.style.display = "flex";
          totalsElement.style.justifyContent = "space-between";

          const totalLabel = document.createElement("div");
          totalLabel.style.fontWeight = "bold";
          totalLabel.textContent = "Total de registros:";

          const totalValue = document.createElement("div");
          totalValue.textContent = totalRows.toString();

          totalsElement.appendChild(totalLabel);
          totalsElement.appendChild(totalValue);
          pageElement.appendChild(totalsElement);
        }

        // Adiciona rodapé
        const footerElement = document.createElement("div");
        footerElement.style.position = "absolute";
        footerElement.style.bottom = "10mm";
        footerElement.style.left = "10mm";
        footerElement.style.right = "10mm";
        footerElement.style.textAlign = "center";
        footerElement.style.borderTop = "1px solid #ddd";
        footerElement.style.paddingTop = "5px";
        footerElement.style.fontSize = "9px";
        footerElement.style.color = "#666";
        footerElement.textContent = `Página ${pageIndex + 1} de ${totalPages}`;

        pageElement.appendChild(footerElement);
        reportContainer.appendChild(pageElement);

        // Renderiza a página para o PDF
        const canvas = await html2canvas(pageElement, {
          scale: 1.5,
          useCORS: true,
          logging: false,
          allowTaint: true,
          backgroundColor: "#FFFFFF",
        });

        const imgWidth = pageWidth - margin * 2;
        const imgHeight = (canvas.height * imgWidth) / canvas.width;
        const imgData = canvas.toDataURL("image/png", 0.95);

        doc.addImage(imgData, "PNG", margin, margin, imgWidth, imgHeight);
      }

      // Remove o contêiner temporário
      document.body.removeChild(reportContainer);

      // Abre o PDF em uma nova aba
      const pdfBlob = doc.output("blob");
      const pdfUrl = URL.createObjectURL(pdfBlob);
      window.open(pdfUrl, "_blank");

      // Limpa URL para liberar memória
      setTimeout(() => {
        URL.revokeObjectURL(pdfUrl);
      }, 100);

      // Notifica sucesso
      $q.notify({
        color: "positive",
        message: "Relatório gerado com sucesso",
        icon: "check_circle",
      });
    } catch (error) {
      console.error("Erro ao gerar relatório:", error);

      // Notifica erro
      $q.notify({
        color: "negative",
        message: "Erro ao gerar relatório",
        icon: "error",
        caption: error.message,
      });
    }
  }

  return {
    generateReport,
    getActiveFiltersDescription,
  };
}
