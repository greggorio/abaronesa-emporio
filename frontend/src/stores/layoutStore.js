import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useLayoutStore = defineStore('layout', () => {
  // Estado do drawer
  const leftDrawerOpen = ref(false);
  const isDrawerCollapsed = ref(true);
  
  // Estado do tipo de loja
  const tipoLoja = ref("");
  
  // Ações relacionadas ao drawer
  const toggleDrawer = () => {
    leftDrawerOpen.value = !leftDrawerOpen.value;
  };
  
  const setDrawerOpen = (value) => {
    leftDrawerOpen.value = value;
  };
  
  const toggleCollapsed = () => {
    isDrawerCollapsed.value = !isDrawerCollapsed.value;
  };
  
  const setCollapsed = (value) => {
    isDrawerCollapsed.value = value;
  };
  
  // Ações relacionadas ao tipo de loja
  const setTipoLoja = (tipo) => {
    tipoLoja.value = tipo;
  };

  return {
    // Estados
    leftDrawerOpen,
    isDrawerCollapsed,
    tipoLoja,
    
    // Ações
    toggleDrawer,
    setDrawerOpen,
    toggleCollapsed,
    setCollapsed,
    setTipoLoja,
  };
});