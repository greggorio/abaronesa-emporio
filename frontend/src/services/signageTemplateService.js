import { useApiRequest } from "@/composables/useApiRequest";

const { apiRequest } = useApiRequest();

export const getTemplates = async () => {
  const response = await apiRequest("/api/signage/templates");
  return response;
};

export const getTemplateById = async (templateId) => {
  const response = await apiRequest(`/api/signage/templates/${templateId}`);
  return response;
};

export const updateTemplate = async (templateId, payload) => {
  const response = await apiRequest(`/api/signage/templates/${templateId}`, "PATCH", payload);
  return response;
};
