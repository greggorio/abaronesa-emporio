import { useApiRequest } from "@/composables/useApiRequest";

const { apiRequest } = useApiRequest();

export const generateAiImage = async (productId, force = false) => {
  const url = `/api/produtos/${productId}/signage/generate-ai-image?force=${force}`;
  const body = { force };
  const response = await apiRequest(url, "POST", body);
  return response;
};

export const toggleUseAiImage = async (productId, useAiImage) => {
  const url = `/api/produtos/${productId}/signage`;
  const body = { useAiImage };
  const response = await apiRequest(url, "PATCH", body);
  return response;
};
