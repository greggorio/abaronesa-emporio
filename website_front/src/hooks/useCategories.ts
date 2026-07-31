import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { quizApi } from '@/lib/quiz-api';

export const useCategories = () => {
  const queryClient = useQueryClient();

  // Query para obter todas as categorias
  const {
    data: categories,
    isLoading,
    isError,
    refetch
  } = useQuery({
    queryKey: ['categories'],
    queryFn: () => quizApi.getAllCategories(),
    staleTime: 5 * 60 * 1000, // 5 minutos
  });

  // Mutation para criar categoria
  const createCategoryMutation = useMutation({
    mutationFn: (categoryData: { name: string; description: string; difficultyLevel: string }) => 
      quizApi.createCategory(categoryData),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
    },
  });

  // Mutation para atualizar categoria
  const updateCategoryMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<{ name: string; description: string; difficultyLevel: string }> }) => 
      quizApi.updateCategory(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
    },
  });

  // Mutation para alternar status da categoria
  const toggleCategoryStatusMutation = useMutation({
    mutationFn: (id: number) => quizApi.toggleCategoryStatus(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
    },
  });

  // Mutation para deletar categoria
  const deleteCategoryMutation = useMutation({
    mutationFn: (id: number) => quizApi.deleteCategory(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
    },
  });

  return {
    categories: categories || [],
    isLoading,
    isError,
    refetch,
    createCategory: createCategoryMutation.mutateAsync,
    updateCategory: updateCategoryMutation.mutateAsync,
    toggleCategoryStatus: toggleCategoryStatusMutation.mutate,
    deleteCategory: deleteCategoryMutation.mutate,
    isCreating: createCategoryMutation.isPending,
    isUpdating: updateCategoryMutation.isPending,
    isToggling: toggleCategoryStatusMutation.isPending,
    isDeleting: deleteCategoryMutation.isPending,
    createError: createCategoryMutation.error,
    updateError: updateCategoryMutation.error,
    toggleError: toggleCategoryStatusMutation.error,
    deleteError: deleteCategoryMutation.error,
  };
};
