export const formatCurrency = (value) => {
  if (value === null || value === undefined) return "R$ 0,00";

  const numero = parseFloat(value);
  if (isNaN(numero)) return "R$ 0,00";

  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL",
  }).format(numero);
};

export const formatPercent = (value) => {
  if (value === null || value === undefined) return "0%";

  const numero = parseFloat(value);
  if (isNaN(numero)) return "0%";

  return `${numero.toFixed(2).replace(".", ",")}%`;
};

export const formatCodigo = (codigo) => {
  if (!codigo) return "";
  return String(codigo).padStart(6, "0");
};

export const formatDate = (date) => {
  if (!date) return "";

  const d = new Date(date);
  if (isNaN(d.getTime())) return "";

  return new Intl.DateTimeFormat("pt-BR").format(d);
};

export const formatDateTime = (date) => {
  if (!date) return "";

  const d = new Date(date);
  if (isNaN(d.getTime())) return "";

  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(d);
};
