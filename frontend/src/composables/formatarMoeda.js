export function useFormatarMoeda() {
  const formatarMoeda = (valor) => {
    if (valor === null || valor === undefined || valor === "") {
      return "";
    }

    if (!isNaN(valor) && typeof valor === "number") {
      const options = {
        style: "currency",
        currency: "BRL",
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      };

      let numero = parseFloat(valor);

      const valorStr = numero.toFixed(3);
      const valorAjustado = parseFloat(valorStr.slice(0, -1));

      return valorAjustado.toLocaleString("pt-BR", options);
    }
    return valor;
  };

  return {
    formatarMoeda,
  };
}
