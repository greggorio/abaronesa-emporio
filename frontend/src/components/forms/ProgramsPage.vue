<template>
  <PrgContainerNew :programa="programaParam" :show-image-column="true" :custom-slots="customSlots" @loaded="onDataLoaded" @error="onLoadError" />
</template>

<script setup>
import { ref, computed, h } from "vue";
import { useQuasar } from "quasar";
import { useRoute } from "vue-router";
import { baseApiUrl } from "@/global";
import PrgContainerNew from "@/components/PrgContainerNew.vue";

const route = useRoute();
const programaParam = computed(() => route.query.programa || "default-program");

const $q = useQuasar();

// Slots customizados para renderização especial
const customSlots = computed(() => [
  {
    name: "body-cell-cover",
    component: {
      props: ["props"],
      setup(props) {
        const getImageUrl = (path) => {
          if (!path) return "";
          if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
          }
          if (path.startsWith("/")) {
            return `${baseApiUrl}${path}`;
          }
          return `${baseApiUrl}/media/${path}`;
        };

        const handleImageError = (event) => {
          console.error("Erro ao carregar imagem:", event.target.src);
        };

        return () =>
          h("div", [
            props.props.row.cover
              ? h("img", {
                  src: getImageUrl(props.props.row.cover),
                  alt: props.props.row.descricao || props.props.row.nome,
                  style: "width: 60px; height: 60px; object-fit: cover; border-radius: 4px;",
                  onError: handleImageError,
                })
              : h("div", {
                  style: "width: 60px; height: 60px; background: #f5f5f5; border-radius: 4px;",
                }),
          ]);
      },
    },
  },
]);

// Handlers de eventos
function onDataLoaded(response) {
  console.log("Dados carregados:", response);
  // Aqui você pode fazer algo com os dados carregados, se necessário
}

function onLoadError(error) {
  $q.notify({
    type: "negative",
    message: `Erro ao carregar subcategorias: ${error.message}`,
  });
}
</script>
