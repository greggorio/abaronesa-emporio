// src/services/form-builder/EntityDiscoveryService.js
import { useApiRequest } from "@/composables/useApiRequest";

class EntityDiscoveryService {
  constructor() {
    this.apiRequest = useApiRequest().apiRequest;
  }

  /**
   * Lista todas as entidades disponíveis
   * @returns {Promise<Array>} Lista de entidades com entityType e className
   */
  async getAvailableEntities() {
    return await this.apiRequest("/api/form-builder/available-entities");
  }

  /**
   * Detecta os campos de uma entidade específica
   * @param {string} entityType - Tipo da entidade (ex: 'categorias')
   * @returns {Promise<Array>} Lista de campos com name e type
   */
  async detectFields(entityType) {
    if (!entityType || typeof entityType !== "string") {
      throw new Error(`entityType inválido: ${entityType}`);
    }

    return await this.apiRequest(`/api/form-builder/detect-fields/${entityType}`);
  }

  /**
   * Carrega definição existente se houver
   * @param {string} entityType - Tipo da entidade
   * @returns {Promise<Object|null>} Definição existente ou null
   */
  async loadExistingDefinition(entityType) {
    try {
      const response = await this.apiRequest(`/api/form-builder/definitions/${entityType}`);

      // Se a resposta estiver vazia ou sem tabs/actions, considerar como não existente
      if (!response || ((!response.tabs || response.tabs.length === 0) && (!response.actions || response.actions.length === 0))) {
        return null;
      }

      return response;
    } catch (error) {
      // Em caso de erro, retornar null
      return null;
    }
  }

  /**
   * Obtém preview do formulário
   * @param {string} entityType - Tipo da entidade
   * @returns {Promise<Object>} Preview da configuração
   */
  async getFormPreview(entityType) {
    return await this.apiRequest(`/api/form-builder/preview/${entityType}`);
  }
}

export default new EntityDiscoveryService();
