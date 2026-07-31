<template>
  <div class="empty-state">
    <div class="text-center q-pa-xl">
      <q-icon name="dashboard_customize" size="120px" color="grey-4" />

      <h5 class="q-mt-lg q-mb-sm text-grey-8">Bem-vindo ao Form Builder</h5>

      <p class="text-body1 text-grey-6 q-mb-lg" style="max-width: 500px; margin: 0 auto">
        Selecione uma entidade no painel lateral para começar a criar ou editar formulários dinâmicos.
      </p>

      <div class="row q-gutter-md justify-center q-mt-xl">
        <div class="feature-card">
          <q-icon name="drag_indicator" size="48px" color="primary" />
          <h6 class="q-mt-md q-mb-sm">Drag & Drop</h6>
          <p class="text-caption text-grey-6">Arraste campos para organizar seu formulário</p>
        </div>

        <div class="feature-card">
          <q-icon name="tune" size="48px" color="secondary" />
          <h6 class="q-mt-md q-mb-sm">Personalização</h6>
          <p class="text-caption text-grey-6">Configure propriedades e validações</p>
        </div>

        <div class="feature-card">
          <q-icon name="visibility" size="48px" color="accent" />
          <h6 class="q-mt-md q-mb-sm">Preview em Tempo Real</h6>
          <p class="text-caption text-grey-6">Visualize as alterações instantaneamente</p>
        </div>
      </div>

      <q-separator class="q-my-xl" />

      <div class="quick-tips">
        <h6 class="text-grey-7 q-mb-md">Dicas Rápidas</h6>
        <div class="row q-gutter-sm justify-center">
          <q-chip icon="lightbulb" color="amber-2" text-color="amber-9">Use Ctrl+S para salvar rapidamente</q-chip>
          <q-chip icon="lightbulb" color="amber-2" text-color="amber-9">Clique duplo em um campo para editar</q-chip>
          <q-chip icon="lightbulb" color="amber-2" text-color="amber-9">Arraste campos entre abas</q-chip>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.feature-card {
  background-color: $grey-1;
  padding: 24px;
  border-radius: 8px;
  width: 200px;
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  }

  h6 {
    margin: 0;
    font-size: 14px;
    font-weight: 500;
  }

  p {
    margin: 0;
    line-height: 1.4;
  }
}

.quick-tips {
  h6 {
    margin: 0;
    font-weight: 500;
  }
}
</style>
