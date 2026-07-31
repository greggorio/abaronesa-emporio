// src/services/aiService.js
import { useApiRequest } from "@/composables/useApiRequest";

export function useAIService() {
  const { apiRequest } = useApiRequest();

  /**
   * Processar prompt usando a API do backend (rota legacy compatível com sistema de referência)
   */
  const processPrompt = async (prompt, options = {}) => {
    try {
      const encodedPrompt = encodeURIComponent(prompt);
      const response = await apiRequest(`/dashboard/requestia/${encodedPrompt}`);
      return response;
    } catch (error) {
      console.error("Erro ao processar prompt:", error);
      throw error;
    }
  };

  /**
   * Buscar templates de exemplo
   */
  const getTemplates = async (tipo = "todos") => {
    try {
      const response = await apiRequest(`/api/ai-assistant/templates/${tipo}`);
      return response;
    } catch (error) {
      console.error("Erro ao buscar templates:", error);
      // Retornar templates locais como fallback
      return getLocalTemplates(tipo);
    }
  };

  /**
   * Templates locais como fallback
   */
  const getLocalTemplates = (tipo) => {
    const templates = {
      cliente: [
        "cadastrar cliente João Silva cpf 12345678900 email joao@email.com",
        "novo cliente empresa ABC Ltda cnpj 12345678000190",
        "criar cliente Maria Santos telefone 11999887766",
      ],
      produto: [
        "cadastrar produto Pão Francês preço 0.50 categoria pães",
        "novo produto Bolo de Chocolate valor 35.00 tipo bolos",
        "criar produto Sonho preço 4.50 categoria doces",
      ],
      todos: {
        cliente: ["cadastrar cliente [nome] cpf [cpf] email [email]", "novo cliente empresa [razão social] cnpj [cnpj]"],
        produto: ["cadastrar produto [nome] preço [valor] categoria [categoria]"],
      },
    };

    return templates[tipo] || templates.todos;
  };

  /**
   * Validar CPF
   */
  const validateCPF = (cpf) => {
    if (!cpf) return false;

    // Remove caracteres não numéricos
    cpf = cpf.replace(/[^\d]/g, "");

    // Verifica se tem 11 dígitos
    if (cpf.length !== 11) return false;

    // Verifica se todos os dígitos são iguais
    if (/^(\d)\1+$/.test(cpf)) return false;

    // Validação do CPF
    let sum = 0;
    let remainder;

    for (let i = 1; i <= 9; i++) {
      sum += parseInt(cpf.substring(i - 1, i)) * (11 - i);
    }

    remainder = (sum * 10) % 11;
    if (remainder === 10 || remainder === 11) remainder = 0;
    if (remainder !== parseInt(cpf.substring(9, 10))) return false;

    sum = 0;
    for (let i = 1; i <= 10; i++) {
      sum += parseInt(cpf.substring(i - 1, i)) * (12 - i);
    }

    remainder = (sum * 10) % 11;
    if (remainder === 10 || remainder === 11) remainder = 0;
    if (remainder !== parseInt(cpf.substring(10, 11))) return false;

    return true;
  };

  /**
   * Validar CNPJ
   */
  const validateCNPJ = (cnpj) => {
    if (!cnpj) return false;

    // Remove caracteres não numéricos
    cnpj = cnpj.replace(/[^\d]/g, "");

    // Verifica se tem 14 dígitos
    if (cnpj.length !== 14) return false;

    // Verifica se todos os dígitos são iguais
    if (/^(\d)\1+$/.test(cnpj)) return false;

    // Validação do CNPJ
    let size = cnpj.length - 2;
    let numbers = cnpj.substring(0, size);
    let digits = cnpj.substring(size);
    let sum = 0;
    let pos = size - 7;

    for (let i = size; i >= 1; i--) {
      sum += numbers.charAt(size - i) * pos--;
      if (pos < 2) pos = 9;
    }

    let result = sum % 11 < 2 ? 0 : 11 - (sum % 11);
    if (result !== parseInt(digits.charAt(0))) return false;

    size = size + 1;
    numbers = cnpj.substring(0, size);
    sum = 0;
    pos = size - 7;

    for (let i = size; i >= 1; i--) {
      sum += numbers.charAt(size - i) * pos--;
      if (pos < 2) pos = 9;
    }

    result = sum % 11 < 2 ? 0 : 11 - (sum % 11);
    if (result !== parseInt(digits.charAt(1))) return false;

    return true;
  };

  /**
   * Formatar CPF
   */
  const formatCPF = (cpf) => {
    if (!cpf) return "";
    cpf = cpf.replace(/\D/g, "");
    return cpf.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, "$1.$2.$3-$4");
  };

  /**
   * Formatar CNPJ
   */
  const formatCNPJ = (cnpj) => {
    if (!cnpj) return "";
    cnpj = cnpj.replace(/\D/g, "");
    return cnpj.replace(/(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})/, "$1.$2.$3/$4-$5");
  };

  /**
   * Formatar telefone
   */
  const formatPhone = (phone) => {
    if (!phone) return "";
    phone = phone.replace(/\D/g, "");

    if (phone.length === 11) {
      return phone.replace(/(\d{2})(\d{5})(\d{4})/, "($1) $2-$3");
    } else if (phone.length === 10) {
      return phone.replace(/(\d{2})(\d{4})(\d{4})/, "($1) $2-$3");
    }

    return phone;
  };

  /**
   * Formatar CEP
   */
  const formatCEP = (cep) => {
    if (!cep) return "";
    cep = cep.replace(/\D/g, "");
    return cep.replace(/(\d{5})(\d{3})/, "$1-$2");
  };

  return {
    // Métodos principais
    processPrompt,
    getTemplates,

    // Validações
    validateCPF,
    validateCNPJ,

    // Formatações
    formatCPF,
    formatCNPJ,
    formatPhone,
    formatCEP,
  };
}
