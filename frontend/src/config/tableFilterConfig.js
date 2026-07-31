/**
 * Configurações dos operadores e opções para filtros de tabela
 */

/**
 * Operadores disponíveis para filtros de texto
 */
export const stringOperators = [
  { label: "Contém", value: "contains" },
  { label: "Igual a", value: "equals" },
  { label: "Começa com", value: "startsWith" },
  { label: "Termina com", value: "endsWith" },
  { label: "Não contém", value: "notContains" },
];

/**
 * Operadores disponíveis para filtros numéricos
 */
export const numberOperators = [
  { label: "Igual a", value: "equals" },
  { label: "Diferente de", value: "notEquals" },
  { label: "Maior que", value: "greaterThan" },
  { label: "Maior ou igual a", value: "greaterThanOrEqual" },
  { label: "Menor que", value: "lessThan" },
  { label: "Menor ou igual a", value: "lessThanOrEqual" },
  { label: "Entre", value: "between" },
];

/**
 * Operadores disponíveis para filtros de data
 */
export const dateOperators = [
  { label: "Data atual", value: "today" },
  { label: "Igual a", value: "equals" },
  { label: "A partir de", value: "after" },
  { label: "Até", value: "before" },
  { label: "Entre", value: "between" },
];

/**
 * Opções para filtros booleanos
 */
export const booleanOptions = [
  { label: "Sim", value: true },
  { label: "Não", value: false },
];

/**
 * Obtém o operador padrão baseado no tipo de dados
 * @param {string} type - Tipo de dados ('string', 'number', 'date', 'boolean')
 * @returns {string} - Operador padrão para o tipo
 */
export function getDefaultOperatorByType(type) {
  switch (type) {
    case "string":
      return "contains";
    case "number":
      return "equals";
    case "date":
      return "equals";
    case "boolean":
      return "equals";
    default:
      return "contains";
  }
}
