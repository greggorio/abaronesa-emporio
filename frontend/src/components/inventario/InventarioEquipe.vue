<template>
  <q-card flat class="bg-white q-mt-md" style="border-radius: 12px">
    <q-card-section>
      <div class="text-subtitle1 text-primary q-mb-sm">Equipe de Inventário</div>
      <div class="row q-col-gutter-md">
        <div v-for="membro in props.equipe" :key="membro.id" class="col-auto">
          <div class="text-center">
            <q-avatar size="48px">
              <img :src="loadImg(membro.id)" />
              <q-badge floating :color="membro.ativo ? 'primary' : 'grey'" rounded />
            </q-avatar>
            <div class="text-caption q-mt-xs">{{ membro.nome }}</div>
          </div>
        </div>
      </div>
    </q-card-section>
  </q-card>
</template>

<script setup>
import { defineProps } from "vue";
import { baseApiUrl } from "@/global";

const props = defineProps({
  equipe: {
    type: Array,
    required: true,
  },
});

const loadImg = (id_usuario) => {
  return `${baseApiUrl}/api/imagens/users/${id_usuario}.jpg`;
};
</script>
