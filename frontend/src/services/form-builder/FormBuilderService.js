// src/services/form-builder/FormBuilderService.js
import { useApiRequest } from "@/composables/useApiRequest";

class FormBuilderService {
  constructor() {
    this.apiRequest = useApiRequest().apiRequest;
  }

  /**
   * Salva ou atualiza uma definição de formulário
   * @param {Object} formDefinition - Definição completa do formulário
   * @returns {Promise<Object>} Definição salva
   */
  async saveDefinition(formDefinition) {
    return await this.apiRequest("/api/form-builder/definitions/save", "POST", formDefinition);
  }

  /**
   * Valida estrutura do formulário
   * @param {Object} formDefinition - Definição para validar
   * @returns {Promise<Object>} Resultado da validação
   */
  async validateDefinition(formDefinition) {
    return await this.apiRequest("/api/form-builder/validate", "POST", formDefinition);
  }

  /**
   * Recarrega configuração específica
   * @param {string} entityType - Tipo da entidade
   * @returns {Promise<void>}
   */
  async reloadConfig(entityType) {
    return await this.apiRequest(`/api/form-builder/cache/reload/${entityType}`, "POST");
  }

  /**
   * Limpa todo o cache
   * @returns {Promise<void>}
   */
  async clearCache() {
    return await this.apiRequest("/api/form-builder/cache/clear", "POST");
  }

  /**
   * Salva rascunho localmente
   * @param {string} entityType - Tipo da entidade
   * @param {Object} definition - Definição do formulário
   */
  saveDraft(entityType, definition) {
    localStorage.setItem(`form-draft-${entityType}`, JSON.stringify(definition));
  }

  /**
   * Carrega rascunho local
   * @param {string} entityType - Tipo da entidade
   * @returns {Object|null} Rascunho ou null
   */
  loadDraft(entityType) {
    const draft = localStorage.getItem(`form-draft-${entityType}`);
    return draft ? JSON.parse(draft) : null;
  }

  /**
   * Remove rascunho local
   * @param {string} entityType - Tipo da entidade
   */
  clearDraft(entityType) {
    localStorage.removeItem(`form-draft-${entityType}`);
  }
}

export default new FormBuilderService();
