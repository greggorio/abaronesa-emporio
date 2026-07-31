import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { quizApi } from '@/lib/quiz-api';

export const useQuestions = () => {
  const queryClient = useQueryClient();

  // Query para obter todas as perguntas
  const {
    data: questions,
    isLoading,
    isError,
    refetch
  } = useQuery({
    queryKey: ['questions'],
    queryFn: () => quizApi.getAllQuestions(),
    staleTime: 5 * 60 * 1000, // 5 minutos
  });

  // Mutation para criar pergunta
  const createQuestionMutation = useMutation({
    mutationFn: (questionData: any) =>
      quizApi.createQuestion(questionData),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['questions'] });
      queryClient.invalidateQueries({ queryKey: ['categories'] }); // Para atualizar contadores
    },
  });

  // Mutation para atualizar pergunta
  const updateQuestionMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: any }) =>
      quizApi.updateQuestion(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['questions'] });
    },
  });

  // Mutation para alternar status da pergunta
  const toggleQuestionStatusMutation = useMutation({
    mutationFn: (id: number) => quizApi.toggleQuestionStatus(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['questions'] });
    },
  });

  // Mutation para deletar pergunta
  const deleteQuestionMutation = useMutation({
    mutationFn: (id: number) => quizApi.deleteQuestion(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['questions'] });
      queryClient.invalidateQueries({ queryKey: ['categories'] }); // Para atualizar contadores
    },
  });

  return {
    questions: questions || [],
    isLoading,
    isError,
    refetch,
    createQuestion: createQuestionMutation.mutateAsync,
    updateQuestion: updateQuestionMutation.mutateAsync,
    toggleQuestionStatus: toggleQuestionStatusMutation.mutate,
    deleteQuestion: deleteQuestionMutation.mutate,
    isCreating: createQuestionMutation.isPending,
    isUpdating: updateQuestionMutation.isPending,
    isToggling: toggleQuestionStatusMutation.isPending,
    isDeleting: deleteQuestionMutation.isPending,
    createError: createQuestionMutation.error,
    updateError: updateQuestionMutation.error,
    toggleError: toggleQuestionStatusMutation.error,
    deleteError: deleteQuestionMutation.error,
  };
};
