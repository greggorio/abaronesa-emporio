import { ref, defineAsyncComponent, h } from 'vue'

export function useDynamicComponents() {
  const components = import.meta.glob('../components/**/*.vue')
  const loadedComponents = ref({})

  const EmptyComponent = {
    setup() {
      return () => h('div')
    }
  }

  const LoadingComponent = {
    setup() {
      return () => h('div', 'Carregando...')
    }
  }

  const ErrorComponent = {
    setup() {
      return () => h('div', 'Erro ao carregar componente')
    }
  }

  function loadComponent(componentName, basePath = '../components') {
    if (!componentName) return EmptyComponent

    if (!loadedComponents.value[componentName]) {
      loadedComponents.value[componentName] = defineAsyncComponent({
        loader: async () => {
          // Tentar diferentes caminhos
          const possiblePaths = [
            `${basePath}/${componentName}.vue`,
            `${basePath}/${componentName}/${componentName}.vue`,
            `${basePath}/forms/${componentName}.vue`,
            `${basePath}/dialogs/${componentName}.vue`
          ]

          for (const path of possiblePaths) {
            if (components[path]) {
              try {
                const module = await components[path]()
                return module.default || module
              } catch (error) {
                console.warn(`Erro ao carregar ${path}:`, error)
              }
            }
          }

          console.error(`Componente ${componentName} não encontrado`)
          return EmptyComponent
        },
        loadingComponent: LoadingComponent,
        errorComponent: ErrorComponent,
        delay: 200,
        timeout: 3000
      })
    }

    return loadedComponents.value[componentName]
  }

  return {
    loadComponent,
    EmptyComponent,
    LoadingComponent,
    ErrorComponent
  }
}