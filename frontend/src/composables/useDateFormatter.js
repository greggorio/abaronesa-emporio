// src/composables/useDateFormatter.js

import { ref } from "vue";

export function useDateFormatter() {
  /**
   * Formata uma data para o formato esperado pela API
   * @param {String|Date} date - Data a ser formatada
   * @param {String} format - Formato desejado (padrão: 'YYYY-MM-DD')
   * @returns {String|null} - Data formatada ou null se inválida
   */
  const formatarDataParaAPI = (date, format = "YYYY-MM-DD") => {
    if (!date) return null;

    try {
      // Converte string para objeto Date, se necessário
      const dateObj = typeof date === "string" ? new Date(date) : date;

      // Verifica se é um objeto Date válido
      if (isNaN(dateObj.getTime())) {
        console.warn("Data inválida:", date);
        return null;
      }

      // Formata de acordo com o formato solicitado
      switch (format) {
        case "YYYY-MM-DD":
          return dateObj.toISOString().split("T")[0];

        case "YYYY-MM-DD HH:mm:ss":
          return dateObj.toISOString().replace("T", " ").substring(0, 19);

        case "DD/MM/YYYY":
          return `${String(dateObj.getDate()).padStart(2, "0")}/${String(dateObj.getMonth() + 1).padStart(2, "0")}/${dateObj.getFullYear()}`;

        case "timestamp":
          return dateObj.getTime();

        case "iso":
          return dateObj.toISOString();

        default:
          return dateObj.toISOString().split("T")[0]; // Default to YYYY-MM-DD
      }
    } catch (error) {
      console.error("Erro ao formatar data:", error);
      return null;
    }
  };

  /**
   * Converte data no formato ISO para formato legível (DD/MM/YYYY)
   * @param {String} isoDate - Data em formato ISO
   * @returns {String} - Data formatada ou string vazia se inválida
   */
  const formatDisplayDate = (isoDate) => {
    if (!isoDate) return "";

    try {
      const date = new Date(isoDate);
      const day = String(date.getDate()).padStart(2, "0");
      const month = String(date.getMonth() + 1).padStart(2, "0");
      const year = date.getFullYear();

      return `${day}/${month}/${year}`;
    } catch (e) {
      return "";
    }
  };

  /**
   * Converte string de data em formato brasileiro para formato ISO
   * @param {String} brDate - Data no formato DD/MM/YYYY
   * @returns {String} - Data em formato YYYY-MM-DD
   */
  const convertBrToIso = (brDate) => {
    if (!brDate || brDate.trim() === "") return null;

    try {
      const [day, month, year] = brDate.split("/");
      return `${year}-${month.padStart(2, "0")}-${day.padStart(2, "0")}`;
    } catch (e) {
      return null;
    }
  };

  /**
   * Verifica se uma data no formato YYYY-MM-DD é hoje ou futura
   * @param {String} date - Data no formato YYYY-MM-DD
   * @returns {Boolean} - true se a data for hoje ou futura
   */
  const isDateTodayOrFuture = (date) => {
    if (!date) return false;

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const compareDateObj = new Date(date);
    compareDateObj.setHours(0, 0, 0, 0);

    return compareDateObj >= today;
  };

  /**
   * Calcula a diferença entre duas datas em dias
   * @param {String|Date} dateStart - Data inicial
   * @param {String|Date} dateEnd - Data final (default: hoje)
   * @returns {Number} - Diferença em dias
   */
  const daysBetween = (dateStart, dateEnd = new Date()) => {
    const start = new Date(dateStart);
    const end = new Date(dateEnd);

    // Removendo horas para comparar apenas dias
    start.setHours(0, 0, 0, 0);
    end.setHours(0, 0, 0, 0);

    // Calculando diferença
    const diffTime = Math.abs(end - start);
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

    return diffDays;
  };

  return {
    formatarDataParaAPI,
    formatDisplayDate,
    convertBrToIso,
    isDateTodayOrFuture,
    daysBetween,
  };
}
