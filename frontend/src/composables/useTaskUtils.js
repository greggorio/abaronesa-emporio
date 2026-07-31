import { reactive } from 'vue';

export function useTaskUtils() {
  // Formatação de data amigável (dd/mm/yyyy)
  function formatDateFull(dateString) {
    if (!dateString) return "";

    const date = new Date(dateString);
    const day = date.getDate().toString().padStart(2, "0");
    const month = (date.getMonth() + 1).toString().padStart(2, "0");
    const year = date.getFullYear();

    return `${day}/${month}/${year}`;
  }

  // Formatação de data relativa
  function formatDate(dateString) {
    if (!dateString) return "";

    const date = new Date(dateString);
    const now = new Date();
    const diffTime = Math.abs(now - date);
    const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));

    if (diffDays === 0) {
      return "Hoje";
    } else if (diffDays === 1) {
      return "Ontem";
    } else if (diffDays < 7) {
      return `Há ${diffDays} dias`;
    } else {
      return formatDateFull(dateString);
    }
  }

  // Verifica se a tarefa está atrasada
  function isOverdue(dueDate) {
    if (!dueDate) return false;
    return new Date(dueDate) < new Date();
  }

  // Obtém iniciais de um nome
  function getInitials(name) {
    if (!name) return "?";
    if (typeof name === 'object' && name.nome) {
      return name.nome.charAt(0).toUpperCase();
    }
    return name.charAt(0).toUpperCase();
  }

  // Cor da prioridade para o ícone
  function getPriorityIconColor(priority) {
    switch (priority) {
      case "Baixa":
        return "green";
      case "Média":
        return "orange";
      case "Alta":
        return "deep-orange";
      case "Urgente":
        return "red";
      default:
        return "grey";
    }
  }

  // Classe CSS baseada na prioridade
  function getPriorityClass(priority) {
    switch (priority) {
      case "Baixa":
        return "priority-low";
      case "Média":
        return "priority-medium";
      case "Alta":
        return "priority-high";
      case "Urgente":
        return "priority-urgent";
      default:
        return "priority-none";
    }
  }

  return {
    formatDateFull,
    formatDate,
    isOverdue,
    getInitials,
    getPriorityIconColor,
    getPriorityClass,
  };
}