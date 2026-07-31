export function useTableColumns() {
  function capitalizar(str) {
    if (!str) return "";
    return str.charAt(0).toUpperCase() + str.slice(1);
  }

  function generateColumns(definitions, options = {}) {
    const { includeImage = true, includeActions = true, imageField = "cover", actionsWidth = "100px" } = options;

    const columns = definitions
      .filter((p) => p.name && p.name.trim() !== "" && (p.show !== undefined ? p.show : true))
      .map((param) => {
        const campo = param.name;
        const icon = param.icon || "";
        const alinhamento = param.align || "left";
        const largura = param.width;
        const tipo = param.type || param.tipo || "text";
        const format = param.format || param.formato || null;
        const label = param.label || capitalizar(campo.replace(/_/g, " "));
        const required = param.required !== undefined ? param.required : true;

        // CORREÇÃO: Usar sortable do backend para definir customSortable
        // mas manter sortable como false para evitar conflito com Quasar
        const customSortable = param.sortable === true || param.customSortable === true;

        const show = param.show !== undefined ? param.show : true;

        return {
          name: campo,
          required,
          // Mantém o tipo vindo do backend, mas preserva o formato para a tabela decidir a renderização
          type: tipo,
          format, // ex.: 'currency', 'percent', etc.
          icon,
          label,
          field: param.dataField || campo,
          dataField: param.dataField,
          customSortable, // Usa sortable do backend OU customSortable
          sortable: false, // SEMPRE false para desabilitar ordenação do Quasar
          style: largura ? `width: ${largura}px` : undefined,
          headerStyle: tipo === "decimal" ? `width: ${largura ? largura + "px" : "100px"}` : undefined,
          align: alinhamento,
          show,
        };
      });

    // Adicionar coluna de imagem se configurado
    /*     if (includeImage && !columns.some((col) => col.name === imageField)) {
      columns.unshift({
        name: imageField,
        field: imageField,
        label: "Imagem",
        align: "center",
        style: "width: 80px",
        customSortable: false,
      });
    } */

    // Adicionar coluna de ações se configurado
    if (includeActions && !columns.some((col) => col.name === "acoes")) {
      columns.push({
        name: "acoes",
        label: "Ações",
        align: "right",
        field: "id",
        style: `width: ${actionsWidth}`,
        customSortable: false,
        sortable: false, // Ações não são ordenáveis
      });
    }

    return columns;
  }

  function customSort(rows, sortBy, descending) {
    const data = [...rows];

    if (sortBy) {
      data.sort((a, b) => {
        let valA = a[sortBy];
        let valB = b[sortBy];

        if (valA === null || valA === undefined) valA = "";
        if (valB === null || valB === undefined) valB = "";

        if (typeof valA === "string" && typeof valB === "string") {
          if (isNaN(valA) || isNaN(valB)) {
            return descending ? valB.localeCompare(valA) : valA.localeCompare(valB);
          } else {
            const numA = Number(valA);
            const numB = Number(valB);
            return descending ? numB - numA : numA - numB;
          }
        } else {
          const numA = Number(valA);
          const numB = Number(valB);
          return descending ? numB - numA : numA - numB;
        }
      });
    }

    return data;
  }

  return {
    generateColumns,
    customSort,
  };
}
