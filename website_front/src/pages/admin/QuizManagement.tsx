import React, { useState } from 'react';
import { Search, Plus, MoreHorizontal, Edit, Copy, Trash2, Eye, Shuffle, RotateCcw, Upload } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { useCategories } from '@/hooks/useCategories';
import { useQuestions } from '@/hooks/useQuestions';
import { QuestionImportDialog } from '@/components/admin/QuestionImportDialog';

type TabKey = 'questions' | 'categories';

type DifficultyLevel = 'EASY' | 'MEDIUM' | 'HARD' | 'EXPERT';

interface CategoryForm {
  name: string;
  description: string;
  difficultyLevel: DifficultyLevel;
}

interface QuestionForm {
  question: string;
  options: string[];
  correctAnswer: number;
  points: number;
  categoryId: number | null;
  active: boolean;
}

type FormState = CategoryForm | QuestionForm;

const EMPTY_CATEGORY_FORM: CategoryForm = {
  name: '',
  description: '',
  difficultyLevel: 'MEDIUM'
};

const EMPTY_QUESTION_FORM: QuestionForm = {
  question: '',
  options: ['', '', '', ''],
  correctAnswer: 0,
  points: 10,
  categoryId: null,
  active: true
};

const buildCategoryForm = (): CategoryForm => ({ ...EMPTY_CATEGORY_FORM });

const buildQuestionForm = (): QuestionForm => ({
  ...EMPTY_QUESTION_FORM,
  options: [...EMPTY_QUESTION_FORM.options]
});

type TabConfig<FormType> = {
  label: string;
  emptyForm: () => FormType;
  toForm: (item: any) => FormType;
  toPayload: (form: FormType) => any;
  save: (selected: any, payload: any) => Promise<any>;
  toggleStatus?: (id: number) => void;
};

const QuizManagement = () => {
  const [activeTab, setActiveTab] = useState<TabKey>('questions');
  const [selectedItem, setSelectedItem] = useState<any>(null);
  const [showInactive, setShowInactive] = useState(false);
  const [selectedCategoryFilter, setSelectedCategoryFilter] = useState<any>(null);
  const canCreateQuestion = Boolean(selectedCategoryFilter?.id);
  const newQuestionDisabled = activeTab === 'questions' && !canCreateQuestion;

  // Usar dados reais de categorias
  const {
    categories,
    isLoading: isLoadingCategories,
    isError: isErrorCategories,
    createCategory,
    updateCategory,
    deleteCategory,
    toggleCategoryStatus,
    refetch: refetchCategories,
    isCreating,
    isUpdating,
    isDeleting,
    createError,
    updateError
  } = useCategories();

  // Usar dados reais de perguntas
  const {
    questions,
    isLoading: isLoadingQuestions,
    isError: isErrorQuestions,
    createQuestion,
    updateQuestion,
    deleteQuestion,
    toggleQuestionStatus,
    refetch: refetchQuestions,
    isCreating: isCreatingQuestion,
    isUpdating: isUpdatingQuestion,
    isDeleting: isDeletingQuestion,
    createError: createQuestionError,
    updateError: updateQuestionError
  } = useQuestions();

  const handleImported = async () => {
    // Atualizar as listas após importação bem-sucedida
    await Promise.all([
      refetchQuestions(),
      refetchCategories()
    ]);
  };

  const TAB = React.useMemo<Record<TabKey, TabConfig<any>>>(() => ({
    categories: {
      label: 'Categorias',
      emptyForm: () => buildCategoryForm(),
      toForm: (category) => ({
        ...buildCategoryForm(),
        name: category?.name ?? '',
        description: category?.description ?? '',
        difficultyLevel: category?.difficultyLevel ?? 'MEDIUM'
      }),
      toPayload: (form) => ({
        name: form.name,
        description: form.description,
        difficultyLevel: form.difficultyLevel
      }),
      save: async (selected, payload) => {
        if (selected?.id) {
          return await updateCategory({ id: selected.id, data: payload });
        }
        return await createCategory(payload);
      },
      toggleStatus: (id: number) => toggleCategoryStatus(id)
    },
    questions: {
      label: 'Perguntas',
      emptyForm: () => buildQuestionForm(),
      toForm: (question) => {
        const base = buildQuestionForm();
        return {
          ...base,
          question: question?.question ?? base.question,
          options: question?.options ? [...question.options] : base.options,
          correctAnswer: question?.correctAnswer ?? base.correctAnswer,
          points: question?.points ?? base.points,
          categoryId: question?.category?.id ?? question?.categoryId ?? base.categoryId,
          active: question?.active ?? base.active
        };
      },
      toPayload: (form) => ({
        question: form.question,
        options: form.options,
        correctAnswer: form.correctAnswer,
        points: form.points,
        active: form.active,
        category: form.categoryId ? { id: form.categoryId } : null
      }),
      save: async (selected, payload) => {
        if (selected?.id) {
          return await updateQuestion({ id: selected.id, data: payload });
        }
        return await createQuestion(payload);
      },
      toggleStatus: (id: number) => toggleQuestionStatus(id)
    }
  }), [
    createCategory,
    updateCategory,
    toggleCategoryStatus,
    createQuestion,
    updateQuestion,
    toggleQuestionStatus
  ]);

  const [form, setForm] = useState<any>(() => TAB['questions'].emptyForm());
  const [savedFormSnapshot, setSavedFormSnapshot] = useState<FormState>(() => TAB['questions'].emptyForm());
  const [isSaving, setIsSaving] = useState(false);
  const [isImportOpen, setIsImportOpen] = useState(false);

  React.useEffect(() => {
    const config = TAB[activeTab];
    const nextForm = selectedItem ? config.toForm(selectedItem) : config.emptyForm();
    setForm(nextForm);
    setSavedFormSnapshot(nextForm);
  }, [activeTab, selectedItem, TAB]);

  const startNewItem = (tab: TabKey = activeTab) => {
    if (tab === 'questions' && !selectedCategoryFilter?.id) {
      return;
    }

    setActiveTab(tab);
    const newForm = TAB[tab].emptyForm();

    if (tab === 'questions') {
      newForm.categoryId = selectedCategoryFilter?.id ?? null;
    }

    setForm(newForm);
    setSavedFormSnapshot(newForm);
    setSelectedItem({
      id: null,
      ...newForm,
      ...(tab === 'categories' ? { active: true } : {})
    });
  };

  const isFormDirty = JSON.stringify(form) !== JSON.stringify(savedFormSnapshot);
  const canSaveCurrent = Boolean(selectedItem) && isFormDirty && !isSaving;

  const saveCurrent = async () => {
    if (!canSaveCurrent) {
      return;
    }

    setIsSaving(true);
    const config = TAB[activeTab];
    const payload = config.toPayload(form);
    try {
      const savedItem = await config.save(selectedItem, payload);
      if (savedItem) {
        setSelectedItem(savedItem);
      }
    } finally {
      setIsSaving(false);
    }
  };

  const saveAndNext = async () => {
    await saveCurrent();
    startNewItem();
  };

  const editingExistingItem = Boolean(selectedItem?.id);
  const editorTitle = editingExistingItem
    ? activeTab === 'questions'
      ? `Editando #${selectedItem.id} - Pergunta`
      : `Editando #${selectedItem.id} - Categoria`
    : activeTab === 'questions'
      ? 'Nova Pergunta'
      : 'Nova Categoria';

  return (
    <div className="h-full bg-[#FBF6F2] flex flex-col">
      {/* Topbar */}
      <div className="bg-white shadow-sm p-4 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <h1 className="text-xl font-display text-[#2A1F1B]">Quiz</h1>
          <span className="text-[#8B7355]/70">{'>'}</span>
          <span className="text-[#8B7355]/70 capitalize">{activeTab === 'questions' ? 'Perguntas' : 'Categorias'}</span>
        </div>

          <Button
            size="sm"
            className="bg-[#D7B899] hover:bg-[#C6A887] text-[#2A1F1B] px-4"
            onClick={() => window.location.href = '/quiz/admin'}
          >
            Iniciar Sessão
          </Button>        

        <div className="flex items-center gap-3">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-[#8B7355]/40" size={16} />
            <Input
              placeholder="Buscar perguntas ou categorias..."
              className="pl-10 w-80 bg-[#F5EDE6] border-[#D7B899] text-[#8B7355]/80"
            />
          </div>

          {activeTab === 'questions' && (
            <Button
              variant="outline"
              size="sm"
              className="border-[#D7B899] text-[#2A1F1B] hover:bg-[#F5EDE6]"
              onClick={() => setIsImportOpen(true)}
            >
              <Upload className="w-4 h-4 mr-2" />
              Importar
            </Button>
          )}

          <Button
            variant="default"
            size="sm"
            className={`bg-[#D7B899] hover:bg-[#C6A887] text-[#2A1F1B] ${newQuestionDisabled ? 'opacity-60 cursor-not-allowed' : ''}`}
            disabled={newQuestionDisabled}
            onClick={() => startNewItem()}
          >
            <Plus className="w-4 h-4 mr-2" />
            {activeTab === 'questions' ? 'Nova Pergunta' : 'Nova Categoria'}
          </Button>
        </div>
      </div>

      <div className="flex flex-1 overflow-hidden">
        {/* Sidebar Esquerda */}
        <div className="w-64 bg-white shadow-sm flex flex-col overflow-hidden">
          <div className="p-2">
            <div className="flex rounded-lg overflow-hidden bg-[#F5EDE6]">
              <button
                className={`flex-1 py-2 text-sm font-medium ${activeTab === 'questions' ? 'bg-[#D7B899] text-[#2A1F1B]' : 'bg-white text-[#8B7355]'}`}
                onClick={() => setActiveTab('questions')}
              >
                Perguntas
              </button>
              <button
                className={`flex-1 py-2 text-sm font-medium ${activeTab === 'categories' ? 'bg-[#D7B899] text-[#2A1F1B]' : 'bg-white text-[#8B7355]'}`}
                onClick={() => setActiveTab('categories')}
              >
                Categorias
              </button>
            </div>
          </div>

          <div className="p-2 flex-1 overflow-y-auto">
            <h3 className="text-xs font-semibold text-[#8B7355] uppercase tracking-wider mb-2 px-2">Categorias</h3>
            <div className="space-y-1">
              {isLoadingCategories ? (
                <div className="p-2 text-center text-[#8B7355]/70">Carregando...</div>
              ) : isErrorCategories ? (
                <div className="p-2 text-center text-red-500">Erro ao carregar categorias</div>
              ) : (
                [...categories]
                  .sort((a, b) => a.name.localeCompare(b.name))
                  .map((category) => (
                  <div
                    key={category.id}
                    className={`flex items-center justify-between p-2 rounded cursor-pointer hover:bg-[#F5EDE6] ${
                      selectedCategoryFilter?.id === category.id ? 'bg-[#F5EDE6]' : ''
                    }`}
                    onClick={() => {
                      setSelectedCategoryFilter(category);
                      setActiveTab('questions');
                      setSelectedItem(null);
                    }}
                  >
                    <span className="text-sm text-[#2A1F1B] truncate">
                      <span className="font-semibold text-[#2A1F1B] mr-1">#{category.id}</span>
                      {category.name}
                    </span>
                    <Badge variant="secondary" className="bg-[#D7B899]/20 text-[#8B7355]">
                      {category.questionCount}
                    </Badge>
                  </div>
                ))
              )}
            </div>
          </div>

          <div className="p-2">
            <Button
              variant="outline"
              className={`w-full border-[#D7B899] text-[#2A1F1B] hover:bg-[#F5EDE6] ${newQuestionDisabled ? 'opacity-60 cursor-not-allowed' : ''}`}
              disabled={newQuestionDisabled}
              onClick={() => startNewItem()}
            >
              <Plus className="w-4 h-4 mr-2" />
              {activeTab === 'questions' ? 'Pergunta' : 'Categoria'}
            </Button>

            <div className="flex items-center justify-between mt-2">
              <span className="text-xs text-[#8B7355]">Mostrar inativas</span>
              <div
                className={`w-10 h-5 flex items-center rounded-full p-1 cursor-pointer ${showInactive ? 'bg-[#D7B899]' : 'bg-[#D7B899]'}`}
                onClick={() => setShowInactive(!showInactive)}
              >
                <div className={`bg-white w-3 h-3 rounded-full shadow-md transform transition-transform ${showInactive ? 'translate-x-5' : ''}`}></div>
              </div>
            </div>
          </div>
        </div>

        {/* Coluna Central - Lista */}
        <div className="flex-1 flex flex-col overflow-hidden">
          {/* Lista de Itens */}
          <div className="flex-1 overflow-y-auto p-2 space-y-2">
            {activeTab === 'questions' ? (
              // Lista de Perguntas
              isLoadingQuestions ? (
                <div className="flex items-center justify-center p-8">
                  <div className="text-center">
                    <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[#D7B899] mx-auto mb-2"></div>
                    <p className="text-[#8B7355]/70">Carregando perguntas...</p>
                  </div>
                </div>
              ) : isErrorQuestions ? (
                <div className="flex items-center justify-center p-8">
                  <div className="text-center">
                    <p className="text-red-500 mb-2">Erro ao carregar perguntas</p>
                    <Button onClick={() => window.location.reload()} className="bg-[#D7B899] hover:bg-[#C6A887] text-[#2A1F1B]">
                      Tentar novamente
                    </Button>
                  </div>
                </div>
              ) : (
                // Filtrar perguntas por categoria se uma categoria estiver selecionada
                questions.filter(question => {
                  // Se não estiver na aba de perguntas, mostrar todas
                  if (activeTab !== 'questions') return true;

                  // Se nenhuma categoria estiver selecionada, mostrar todas
                  if (!selectedCategoryFilter?.id) return true;

                  // Caso contrário, filtrar por categoria
                  return question.category?.id === selectedCategoryFilter.id;
                }).length === 0 ? (
                  <div className="flex items-center justify-center p-8">
                    <div className="text-center">
                      <p className="text-[#8B7355]/70 mb-2">
                        {selectedCategoryFilter && activeTab === 'questions'
                          ? `Nenhuma pergunta encontrada para a categoria "${selectedCategoryFilter.name}"`
                          : 'Nenhuma pergunta encontrada'}
                      </p>
                      <Button
                        disabled={!selectedCategoryFilter?.id}
                        onClick={() => startNewItem()}
                        className={`bg-[#D7B899] hover:bg-[#C6A887] text-[#2A1F1B] ${!selectedCategoryFilter?.id ? 'opacity-60 cursor-not-allowed' : ''}`}
                      >
                        Criar primeira pergunta
                      </Button>
                    </div>
                  </div>
                ) : (
                    questions
                      .filter(question => {
                        // Se não estiver na aba de perguntas, mostrar todas
                        if (activeTab !== 'questions') return true;

                        // Se nenhuma categoria estiver selecionada, mostrar todas
                        if (!selectedCategoryFilter?.id) return true;

                        // Caso contrário, filtrar por categoria
                        return question.category?.id === selectedCategoryFilter.id;
                      })
                    .map((question) => (
                      <div
                        key={question.id}
                        className={`rounded-lg p-4 cursor-pointer hover:bg-white transition-colors shadow-sm ${
                          selectedItem?.id === question.id ? 'bg-white ring-2 ring-[#D7B899]' : 'bg-white'
                        }`}
                        onClick={() => setSelectedItem(question)}
                      >
                      <div className="flex justify-between">
                        <div>
                          <h3 className="font-medium text-[#2A1F1B] line-clamp-2">{question.question}</h3>
                          <div className="flex gap-2 mt-2">
                            <Badge variant="secondary" className="bg-[#D7B899]/20 text-[#8B7355]">
                              {question.category?.name || 'Sem categoria'}
                            </Badge>
                            <Badge variant="secondary" className="bg-[#8B7355]/10 text-[#8B7355]">
                              {question.points} pts
                            </Badge>
                            <Badge variant={question.active ? "default" : "outline"} className={question.active ? "bg-green-500" : "bg-red-100"}>
                              {question.active ? 'Ativa' : 'Inativa'}
                            </Badge>
                          </div>
                        </div>

                        <div className="flex gap-1">
                          <Button
                            variant="ghost"
                            size="sm"
                            className="h-8 w-8 p-0 text-[#8B7355] hover:text-[#6B3E26]"
                            onClick={(e) => {
                              e.stopPropagation();
                              setSelectedItem(question);
                            }}
                          >
                            <Edit className="w-4 h-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="h-8 w-8 p-0 text-[#8B7355] hover:text-[#6B3E26]"
                            onClick={(e) => {
                              e.stopPropagation();
                              // Copiar pergunta
                              const newQuestion = { ...question, id: null };
                              setSelectedItem(newQuestion);
                            }}
                          >
                            <Copy className="w-4 h-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="h-8 w-8 p-0 text-[#8B7355] hover:text-[#D65A31]"
                            onClick={(e) => {
                              e.stopPropagation();
                              if (window.confirm(`Tem certeza que deseja excluir a pergunta "${question.question}"?`)) {
                                deleteQuestion(question.id);
                              }
                            }}
                          >
                            <Trash2 className="w-4 h-4" />
                          </Button>
                        </div>
                      </div>
                    </div>
                ))
              )
            )
          ) : (
              // Lista de Categorias
              isLoadingCategories ? (
                <div className="flex items-center justify-center p-8">
                  <div className="text-center">
                    <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[#D7B899] mx-auto mb-2"></div>
                    <p className="text-[#8B7355]/70">Carregando categorias...</p>
                  </div>
                </div>
              ) : isErrorCategories ? (
                <div className="flex items-center justify-center p-8">
                  <div className="text-center">
                    <p className="text-red-500 mb-2">Erro ao carregar categorias</p>
                    <Button onClick={() => window.location.reload()} className="bg-[#D7B899] hover:bg-[#C6A887] text-[#2A1F1B]">
                      Tentar novamente
                    </Button>
                  </div>
                </div>
              ) : categories.length === 0 ? (
                <div className="flex items-center justify-center p-8">
                  <div className="text-center">
                    <p className="text-[#8B7355]/70 mb-2">Nenhuma categoria encontrada</p>
                    <Button 
                      onClick={() => {
                        setActiveTab('categories');
                        startNewItem('categories');
                      }}
                      className="bg-[#D7B899] hover:bg-[#C6A887] text-[#2A1F1B]"
                    >
                      Criar primeira categoria
                    </Button>
                  </div>
                </div>
              ) : (
                categories.map((category) => (
                  <div
                    key={category.id}
                    className={`rounded-lg p-4 cursor-pointer hover:bg-white transition-colors shadow-sm ${
                      selectedItem?.id === category.id ? 'bg-white ring-2 ring-[#D7B899]' : 'bg-white'
                    }`}
                    onClick={() => {
                      setSelectedItem(category);
                      setActiveTab('questions'); // Mudar para aba de perguntas ao clicar em uma categoria
                    }}
                  >
                    <div className="flex justify-between">
                      <div>
                        <h3 className="font-medium text-[#2A1F1B]">{category.name}</h3>
                        <p className="text-sm text-[#8B7355]/80 mt-1">{category.description}</p>
                        <div className="flex gap-2 mt-2">
                          <Badge variant="secondary" className="bg-[#8B7355]/10 text-[#8B7355]">
                            {category.questionCount} perguntas
                          </Badge>
                          <Badge variant={category.active ? "default" : "outline"} className={category.active ? "bg-green-500" : "bg-red-100"}>
                            {category.active ? 'Ativa' : 'Inativa'}
                          </Badge>
                        </div>
                      </div>

                      <div className="flex gap-1">
                        <Button 
                          variant="ghost" 
                          size="sm" 
                          className="h-8 w-8 p-0 text-[#8B7355] hover:text-[#6B3E26]"
                          onClick={(e) => {
                            e.stopPropagation();
                            setSelectedItem(category);
                            setActiveTab('categories');
                          }}
                        >
                          <Edit className="w-4 h-4" />
                        </Button>
                        <Button 
                          variant="ghost" 
                          size="sm" 
                          className="h-8 w-8 p-0 text-[#8B7355] hover:text-[#D65A31]"
                          onClick={(e) => {
                            e.stopPropagation();
                            if (window.confirm(`Tem certeza que deseja excluir a categoria "${category.name}"?`)) {
                              deleteCategory(category.id);
                            }
                          }}
                        >
                          <Trash2 className="w-4 h-4" />
                        </Button>
                      </div>
                    </div>
                  </div>
                ))
              )
            )}
          </div>
        </div>

        {/* Coluna Direita - Editor */}
        <div className="w-96 bg-white shadow-sm flex flex-col overflow-hidden">
          {selectedItem ? (
            <>
                <div className="p-3 shadow-sm flex items-center justify-between">
                  <h2 className="font-display text-lg text-[#2A1F1B]">{editorTitle}</h2>
                <div className="flex gap-2">
                  <Button
                    variant="default"
                    size="sm"
                    className={`bg-[#D7B899] hover:bg-[#C6A887] text-[#2A1F1B] ${!canSaveCurrent ? 'opacity-60 cursor-not-allowed' : ''}`}
                    onClick={saveCurrent}
                    disabled={!canSaveCurrent}
                  >
                    Salvar
                  </Button>
                </div>
              </div>

              <div className="flex-1 overflow-y-auto p-3">
                {activeTab === 'questions' ? (
                  <div className="space-y-2">
                    <div>
                      <label className="block text-xs font-medium text-[#2A1F1B] mb-1">Pergunta</label>
                      <textarea
                        className="w-full border border-[#D7B899] rounded-lg p-2 text-sm text-[#2A1F1B] bg-white min-h-[70px]"
                        value={form.question}
                        onChange={(e) => setForm({ ...form, question: e.target.value })}
                        placeholder="Digite a pergunta aqui..."
                      ></textarea>
                      <div className="text-xs text-[#8B7355]/70 mt-1">{(form.question || '').length}/200 caracteres</div>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-xs font-medium text-[#2A1F1B] mb-1">Categoria</label>
                        <select
                          className="w-full border border-[#D7B899] rounded-lg p-1.5 text-sm text-[#2A1F1B] bg-white"
                          value={form.categoryId ?? ''}
                          onChange={(e) => setForm({ ...form, categoryId: e.target.value ? Number(e.target.value) : null })}
                        >
                          <option value="">Selecione uma categoria</option>
                          {categories.map(cat => (
                            <option key={cat.id} value={cat.id}>{cat.name}</option>
                          ))}
                        </select>
                      </div>

                      <div>
                        <label className="block text-xs font-medium text-[#2A1F1B] mb-1">Pontuação</label>
                        <input
                          type="number"
                          className="w-full border border-[#D7B899] rounded-lg p-1.5 text-sm text-[#2A1F1B] bg-white"
                          value={form.points}
                          onChange={(e) => setForm({ ...form, points: Number(e.target.value) })}
                        />
                      </div>
                    </div>

                    <div>
                      <label className="block text-xs font-medium text-[#2A1F1B] mb-1">Opções de Resposta</label>
                      <div className="space-y-1.5">
                        {(form.options || []).map((option, optIndex) => (
                          <div key={optIndex} className="flex items-center gap-2">
                            <input
                              type="radio"
                              name="correct-answer"
                              className="accent-[#D7B899] focus:ring-[#D7B899]/70 w-4 h-4"
                              checked={optIndex === form.correctAnswer}
                              onChange={() => setForm({ ...form, correctAnswer: optIndex })}
                            />
                            <input
                              type="text"
                              className="flex-1 border border-[#D7B899] rounded-lg p-1.5 text-sm text-[#2A1F1B] bg-white"
                              value={option}
                              onChange={(e) => {
                                const newOptions = [...form.options];
                                newOptions[optIndex] = e.target.value;
                                setForm({ ...form, options: newOptions });
                              }}
                              placeholder={`Opção ${optIndex + 1}`}
                            />
                          </div>
                        ))}
                      </div>
                    </div>

                    <div className="flex gap-2 pt-1">
                      <Button
                        variant="outline"
                        size="sm"
                        className="flex-1 border-[#D7B899] text-[#2A1F1B] hover:bg-[#F5EDE6]"
                        onClick={() => {
                          const shuffledOptions = [...form.options].sort(() => Math.random() - 0.5);
                          setForm({ ...form, options: shuffledOptions });
                        }}
                      >
                        <Shuffle className="w-4 h-4 mr-2" />
                        Embaralhar
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        className="border-[#D7B899] text-[#2A1F1B] hover:bg-[#F5EDE6]"
                        onClick={() => {
                          setForm({
                            ...form,
                            options: [...form.options, '']
                          });
                        }}
                      >
                        +
                      </Button>
                    </div>

                  </div>
                ) : (
                  <div className="space-y-2">
                    <div>
                      <label className="block text-xs font-medium text-[#2A1F1B] mb-1">Nome</label>
                      <input
                        type="text"
                        className="w-full border border-[#D7B899] rounded-lg p-1.5 text-sm text-[#2A1F1B] bg-white"
                        value={form.name}
                        onChange={(e) => setForm({ ...form, name: e.target.value })}
                        placeholder="Nome da categoria"
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-medium text-[#2A1F1B] mb-1">Descrição</label>
                      <textarea
                        className="w-full border border-[#D7B899] rounded-lg p-1.5 text-sm text-[#2A1F1B] bg-white min-h-[60px]"
                        value={form.description}
                        onChange={(e) => setForm({ ...form, description: e.target.value })}
                        placeholder="Descrição da categoria"
                      ></textarea>
                    </div>

                    <div>
                      <label className="block text-xs font-medium text-[#2A1F1B] mb-1">Nível de Dificuldade</label>
                      <select 
                        className="w-full border border-[#D7B899] rounded-lg p-1.5 text-sm text-[#2A1F1B] bg-white"
                        value={form.difficultyLevel}
                        onChange={(e) => setForm({ ...form, difficultyLevel: e.target.value as DifficultyLevel })}
                      >
                        <option value="EASY">Fácil</option>
                        <option value="MEDIUM">Médio</option>
                        <option value="HARD">Difícil</option>
                        <option value="EXPERT">Expert</option>
                      </select>
                    </div>

                    <div className="flex items-center justify-between pt-1">
                      <span className="text-xs font-medium text-[#2A1F1B]">Ativa</span>
                      <div
                        className={`w-10 h-5 flex items-center rounded-full p-1 cursor-pointer ${selectedItem?.active ? 'bg-[#D7B899]' : 'bg-[#D7B899]'}`}
                        onClick={() => {
                          if (selectedItem?.id) {
                            TAB[activeTab].toggleStatus?.(selectedItem.id);
                          }
                        }}
                      >
                        <div className={`bg-white w-3 h-3 rounded-full shadow-md transform transition-transform ${selectedItem?.active ? 'translate-x-5' : ''}`}></div>
                      </div>
                    </div>
                  </div>
                )}
              </div>

              <div className="p-3 shadow-sm">
                {isFormDirty && (
                  <div className="text-xs text-[#8B7355]/70 mb-2">* Alterações não salvas</div>
                )}
                <div className="flex gap-2">
                  <Button
                    variant="default"
                    className={`flex-1 bg-[#D7B899] hover:bg-[#C6A887] text-[#2A1F1B] ${!canSaveCurrent ? 'opacity-60 cursor-not-allowed' : ''}`}
                    onClick={saveCurrent}
                    disabled={!canSaveCurrent}
                  >
                    Salvar
                  </Button>
                  <Button
                    variant="outline"
                    className={`flex-1 border-[#D7B899] text-[#2A1F1B] hover:bg-[#F5EDE6] ${isSaving ? 'opacity-60 cursor-not-allowed' : ''}`}
                    onClick={saveAndNext}
                    disabled={isSaving}
                  >
                    Salvar e Próximo
                  </Button>
                </div>
              </div>
            </>
          ) : (
            <div className="flex-1 flex items-center justify-center text-[#8B7355]/70 p-4">
              <div className="text-center">
                <Eye className="w-12 h-12 mx-auto mb-3 text-[#D7B899]" />
                <h3 className="font-medium text-[#2A1F1B]">Selecione um item</h3>
                <p className="text-sm">Clique em uma pergunta ou categoria para editar</p>
              </div>
            </div>
          )}
        </div>
      </div>
      <QuestionImportDialog
        open={isImportOpen}
        onOpenChange={setIsImportOpen}
        onImported={handleImported}
      />
    </div>
  );
};

export default QuizManagement;
