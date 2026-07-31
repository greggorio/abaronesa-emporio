// composables/useActions.js
import { ref, computed } from "vue";
import { useQuasar } from "quasar";
import { useRouter } from "vue-router"; // NOVA IMPORTAÇÃO
import { useApiRequest } from "@/composables/useApiRequest";
import eventBus from "@/eventBus";

export function useActions() {
  const $q = useQuasar();
  const router = useRouter(); // NOVA INSTÂNCIA
  const { apiRequest } = useApiRequest();

  // Estado
  const loadingAction = ref(null);
  const currentCustomDialog = ref(null);
  const customDialogProps = ref({});
  const showCustomDialog = ref(false);

  // Função principal para processar ações
  async function handleAction(action, target = null, options = {}) {
    const {
      onSuccess,
      onError,
      onComplete,
      // Callbacks específicos para cada ação padrão
      onAdd,
      onEdit,
      onDelete,
      // Callback para recarregar dados após ação
      onRefresh,
    } = options;

    // Define o estado de loading
    loadingAction.value = action.action;

    try {
      // Verifica se precisa confirmação
      if (action.confirm) {
        const confirmed = await showConfirmDialog(action.confirm);
        if (!confirmed) {
          return;
        }
      }

      // Processa ação baseado no tipo
      switch (action.action) {
        case "add":
          // NOVA LÓGICA: Verifica se tem componente customizado
          if (action.dialogComponent) {
            await handleCustomDialog(action, target);
          } else if (onAdd) {
            await onAdd();
          } else {
            throw new Error('Handler para ação "add" não fornecido');
          }
          break;

        case "edit":
          if (!target) {
            throw new Error("Nenhum item selecionado para editar");
          }
          // NOVA LÓGICA: Verifica se tem componente customizado para edição
          if (action.dialogComponent) {
            await handleCustomDialog(action, target);
          } else if (onEdit) {
            await onEdit(target);
          } else {
            throw new Error('Handler para ação "edit" não fornecido');
          }
          break;

        case "delete":
          if (!target) {
            throw new Error("Nenhum item selecionado para excluir");
          }
          if (onDelete) {
            await onDelete(target);
          } else {
            throw new Error('Handler para ação "delete" não fornecido');
          }
          break;

        // NOVO CASE: navigate
        case "navigate":
        case "NAVIGATE":
          await handleNavigateAction(action, target);
          break;

        default:
          // Ações customizadas
          // NOVA LÓGICA: Verifica opensDialog primeiro
          if (action.opensDialog && action.dialogComponent) {
            await handleCustomDialog(action, target);
          } else if (action.component) {
            // Mantém compatibilidade com implementação anterior
            await handleCustomComponent(action, target);
          } else if (action.endpoint) {
            await executeBackendHandler(action, target);
            // Recarrega dados após ação bem-sucedida
            if (onRefresh) {
              await onRefresh();
            }
            // Notifica sucesso
            if (action.successMessage) {
              $q.notify({
                type: "positive",
                message: action.successMessage,
              });
            }
          } else if (action.handler) {
            // Emite evento para handler customizado
            eventBus.emit(`action-${action.handler}`, {
              action,
              target,
              options,
            });
          } else {
            // Fallback: emite evento genérico
            eventBus.emit(`action-${action.action}`, {
              action,
              target,
              options,
            });
          }
      }

      // Callback de sucesso
      if (onSuccess) {
        await onSuccess(action, target);
      }
    } catch (error) {
      console.error(`Erro ao executar ação ${action.action}:`, error);

      if (onError) {
        onError(error, action, target);
      } else {
        // Notificação de erro padrão
        $q.notify({
          type: "negative",
          message: error.message || `Erro ao executar ${action.label}`,
        });
      }
    } finally {
      // Limpa estado de loading
      loadingAction.value = null;

      // Callback de conclusão
      if (onComplete) {
        onComplete(action, target);
      }
    }
  }

  // NOVA FUNÇÃO: Handler para ações de navegação
  async function handleNavigateAction(action, target) {
    let route = action.route || action.path || action.url;

    if (!route) {
      throw new Error("Rota não especificada para ação navigate");
    }

    // Substituir placeholders na rota
    if (target && typeof route === "string") {
      // Substitui {id} pelo ID do registro
      route = route.replace(/\{id\}/g, target.id || "");

      // Substitui outros placeholders do objeto target
      route = route.replace(/\{(\w+)\}/g, (match, key) => {
        return target[key] || match;
      });
    }

    // Opções de navegação
    const navigationOptions = {
      path: route,
      query: action.query || {},
      params: action.params || {},
    };

    // Navegação em nova aba/janela
    if (action.openInNewTab || action.target === "_blank") {
      const resolvedRoute = router.resolve(navigationOptions);
      window.open(resolvedRoute.href, "_blank");
    } else {
      // Navegação normal
      await router.push(navigationOptions);
    }

    // Notificar se configurado
    if (action.successMessage) {
      $q.notify({
        type: "positive",
        message: action.successMessage,
      });
    }
  }

  // Mostra diálogo de confirmação
  async function showConfirmDialog(confirmConfig) {
    return new Promise((resolve) => {
      $q.dialog({
        title: confirmConfig.title || "Confirmar",
        message: confirmConfig.message || "Deseja confirmar esta ação?",
        ok: {
          label: confirmConfig.okLabel || "Confirmar",
          color: "primary",
        },
        cancel: {
          label: confirmConfig.cancelLabel || "Cancelar",
          color: "grey",
        },
        persistent: true,
      })
        .onOk(() => resolve(true))
        .onCancel(() => resolve(false))
        .onDismiss(() => resolve(false));
    });
  }

  // NOVA FUNÇÃO: Handler para dialogs customizados com opensDialog
  async function handleCustomDialog(action, target) {
    // Prepara as props do dialog
    const dialogProps = {
      registro: target,
      ...action.props,
      ...action.dialogProps,
      // Adiciona informações da ação se útil
      actionConfig: {
        action: action.action,
        endpoint: action.endpoint,
        successMessage: action.successMessage,
      },
    };

    // Emite evento para o PrgContainerNew lidar
    eventBus.emit("open-custom-dialog", {
      component: action.dialogComponent,
      props: dialogProps,
      action: action,
      target: target,
    });
  }

  // Handler para componentes customizados (mantém compatibilidade)
  async function handleCustomComponent(action, target) {
    try {
      // Tenta carregar o componente dinamicamente usando caminho relativo
      const componentModule = await import(`../components/dialogs/${action.component}.vue`);
      currentCustomDialog.value = componentModule.default;

      // Configura as props do diálogo
      customDialogProps.value = {
        registro: target,
        ...action.props,
      };

      showCustomDialog.value = true;
    } catch (error) {
      throw new Error(`Erro ao carregar componente ${action.component}: ${error.message}`);
    }
  }

  // Handler para chamadas ao backend
  async function executeBackendHandler(action, target) {
    if (!action.endpoint) {
      throw new Error("Endpoint não definido para a ação");
    }

    let endpoint = action.endpoint;
    let data = null;

    // Processa o target baseado no tipo
    if (Array.isArray(target)) {
      // Múltipla seleção
      data = { ids: target.map((item) => item.id) };
      // Não substitui {id} para ações em lote
    } else if (target && target.id) {
      // Seleção única
      endpoint = endpoint.replace("{id}", target.id);
      data = action.sendData ? target : null;
    } else if (target) {
      // Target sem ID (pode ser dados customizados)
      data = target;
    }

    // Executa a requisição
    const response = await apiRequest(endpoint, action.method || "POST", data);

    return response;
  }

  // Função para criar getters de ações por contexto
  function createActionGetters(allActions) {
    // Ações para o topo da página (header)
    const topActions = computed(() => allActions.value.filter((action) => !action.requiresSelection && !action.inlineOnly));

    // Ações por linha da tabela
    const rowActions = computed(() => allActions.value.filter((action) => action.inlineOnly !== false));

    // Ações para seleção múltipla
    const selectionActions = computed(() => allActions.value.filter((action) => action.requiresSelection && !action.inlineOnly));

    // Verifica se precisa dropdown nas ações de linha
    const hasSubActions = computed(() => rowActions.value.length > 3);

    // Ação de duplo clique
    const doubleClickAction = computed(() => allActions.value.find((action) => action.onDoubleClick));

    return {
      topActions,
      rowActions,
      selectionActions,
      hasSubActions,
      doubleClickAction,
    };
  }

  // Função auxiliar para verificar se uma ação está carregando
  function isActionLoading(action, itemId = null) {
    if (!loadingAction.value) return false;

    if (itemId) {
      return loadingAction.value === `${action}-${itemId}`;
    }

    return loadingAction.value === action;
  }

  // Função para definir loading com contexto de item
  function setActionLoading(action, itemId = null) {
    if (itemId) {
      loadingAction.value = `${action}-${itemId}`;
    } else {
      loadingAction.value = action;
    }
  }

  // NOVA FUNÇÃO: Verificar se ação deve abrir dialog
  function shouldOpenDialog(action) {
    // Verifica opensDialog explícito
    if (action.opensDialog) return true;

    // Verifica se é ADD ou EDIT com comportamento padrão
    if (["add", "edit"].includes(action.action) && !action.endpoint) return true;

    // Verifica se tem dialogComponent definido
    if (action.dialogComponent) return true;

    return false;
  }

  return {
    // Estado
    loadingAction,
    currentCustomDialog,
    customDialogProps,
    showCustomDialog,

    // Funções principais
    handleAction,
    showConfirmDialog,
    handleCustomDialog, // NOVA EXPORTAÇÃO
    handleCustomComponent,
    executeBackendHandler,
    handleNavigateAction, // NOVA EXPORTAÇÃO
    createActionGetters,

    // Funções auxiliares
    isActionLoading,
    setActionLoading,
    shouldOpenDialog, // NOVA EXPORTAÇÃO
  };
}
