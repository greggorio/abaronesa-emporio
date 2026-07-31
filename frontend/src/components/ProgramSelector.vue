<template>
  <q-dialog v-model="isOpen" transition-show="slide-down" transition-hide="slide-up" maximized class="program-selector-dialog">
    <q-card class="program-selector-card" flat>
      <div class="header-container">
        <div class="search-container">
          <q-input v-model="searchText" placeholder="Buscar programa" outlined dense bg-color="white" class="search-input" clearable>
            <template v-slot:prepend>
              <q-icon name="search" color="grey-7" />
            </template>
            <template v-slot:append>
              <q-btn round flat icon="close" size="sm" color="grey-7" @click="closeSelector" />
            </template>
          </q-input>
        </div>
      </div>

      <div class="category-tabs-container">
        <div class="category-tabs">
          <div class="category-tab" :class="{ active: selectedCategory === null }" @click="selectedCategory = null">
            <span class="category-name">TODOS</span>
            <div class="category-indicator bg-grey-5"></div>
          </div>
          <div
            v-for="(group, idx) in programGroups"
            :key="idx"
            class="category-tab"
            :class="{ active: selectedCategory === group.title }"
            @click="selectedCategory = group.title"
            :style="
              selectedCategory === group.title
                ? `--program-category-color: ${getCategoryColor(group.title)}; --program-category-color-light: ${getCategoryColor(group.title, 0.1)}`
                : ''
            "
          >
            <span class="category-name">{{ group.title }}</span>
            <div class="category-indicator" :style="`background: ${getCategoryColor(group.title)}`"></div>
          </div>
        </div>
      </div>

      <div class="programs-container">
        <transition-group name="programs-fade" tag="div" class="row q-col-gutter-md">
          <div v-for="program in filteredPrograms" :key="program.route" class="col-6 col-sm-4 col-md-3 col-lg-2 col-xl-2 program-item-wrapper">
            <div
              class="program-item"
              @click="selectProgram(program.title, program.route)"
              v-ripple="{ center: true, color: getCategoryColor(getCategoryForProgram(program), 0.2) }"
              :style="`--program-category-color: ${getCategoryColor(
                getCategoryForProgram(program)
              )}; --program-category-color-light: ${getCategoryColor(getCategoryForProgram(program), 0.1)}`"
            >
              <div class="program-icon-container">
                <q-icon :name="program.icon" size="28px" :color="getCategoryColor(getCategoryForProgram(program))" class="program-icon" />
              </div>
              <div class="program-title">{{ program.title }}</div>
              <div class="program-category" :style="`color: ${getCategoryColor(getCategoryForProgram(program))}`">
                {{ getCategoryForProgram(program) }}
              </div>
            </div>
          </div>
        </transition-group>

        <div class="empty-state" v-if="!filteredPrograms.length && !searchText">
          <q-icon name="apps" size="64px" color="grey-4" />
          <div class="empty-title">Selecione uma categoria</div>
          <div class="empty-subtitle">Ou comece a digitar para buscar</div>
        </div>
        <div class="empty-state" v-if="!filteredPrograms.length && searchText">
          <q-icon name="find_in_page" size="64px" color="grey-4" />
          <div class="empty-title">Nenhum programa encontrado</div>
          <div class="empty-subtitle">Tente refinar sua busca ou limpar o filtro</div>
        </div>
      </div>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { ref, computed, watch } from "vue";
import { useRouter } from "vue-router";
import { useProgramStore } from "@/stores/programStore";

// Props e emits
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  programGroups: {
    type: Array,
    required: true,
  },
});

const emit = defineEmits(["update:modelValue"]);

// Roteador e store
const router = useRouter();
const programStore = useProgramStore();

// Estado local
const isOpen = ref(props.modelValue);
const searchText = ref("");
const selectedCategory = ref(null);

// Mapeamento de cores para categorias
const categoryColors = {
  Principal: "#4CBFA9",
  Operações: "#4CBFA9",
  "Configurações Avançadas": "#A08BC7",
  "Gerenciamento Produtos": "#8D9EC7",
  Financeiro: "#E8AD74",
  Cadastros: "#88AED1",
};

// Assistente para obter cores de categoria
const getCategoryColor = (category, opacity = 1) => {
  const colorHex = categoryColors[category] || "#bdbdbd";

  if (opacity === 1 || !colorHex.startsWith("#")) return colorHex;

  let r = 0,
    g = 0,
    b = 0;
  const hexPure = colorHex.slice(1);

  if (hexPure.length === 6) {
    r = parseInt(hexPure.slice(0, 2), 16);
    g = parseInt(hexPure.slice(2, 4), 16);
    b = parseInt(hexPure.slice(4, 6), 16);
  } else if (hexPure.length === 3) {
    r = parseInt(hexPure.slice(0, 1) + hexPure.slice(0, 1), 16);
    g = parseInt(hexPure.slice(1, 2) + hexPure.slice(1, 2), 16);
    b = parseInt(hexPure.slice(2, 3) + hexPure.slice(2, 3), 16);
  } else {
    return colorHex;
  }

  return `rgba(${r}, ${g}, ${b}, ${opacity})`;
};

// Descobre a categoria de um programa
const getCategoryForProgram = (program) => {
  for (const group of props.programGroups) {
    if (group.items.some((item) => item.route === program.route)) {
      return group.title;
    }
  }
  return "Outros";
};

// Lista ordenada de todos os programas
const allPrograms = computed(() => {
  const programs = [];
  props.programGroups.forEach((group) => {
    group.items.forEach((item) => {
      programs.push({
        ...item,
        category: group.title,
      });
    });
  });

  return programs.sort((a, b) => a.title.localeCompare(b.title));
});

// Programas filtrados por pesquisa e categoria
const filteredPrograms = computed(() => {
  let result = [...allPrograms.value];

  if (selectedCategory.value) {
    result = result.filter((program) => program.category === selectedCategory.value);
  }

  if (searchText.value) {
    const searchLower = searchText.value.toLowerCase().trim();
    if (searchLower) {
      result = result.filter(
        (program) => program.title.toLowerCase().includes(searchLower) || (program.category && program.category.toLowerCase().includes(searchLower))
      );
    }
  }

  return result;
});

// Assistente para selecionar um programa
const selectProgram = (title, route) => {
  programStore.setProgramTitle(title);
  isOpen.value = false;

  if (route === "home") {
    router.push("/home");
  } else {
    router.push({ path: "/container", query: { programa: route } });
  }
};

const closeSelector = () => {
  isOpen.value = false;
};

// Sincroniza o estado local com o v-model
watch(
  () => props.modelValue,
  (newValue) => {
    isOpen.value = newValue;
    if (!newValue) {
      searchText.value = "";
      selectedCategory.value = null;
    }
  }
);

watch(
  () => isOpen.value,
  (newValue) => {
    emit("update:modelValue", newValue);

    if (!newValue) {
      searchText.value = "";
      selectedCategory.value = null;
    }
  }
);
</script>

<style lang="scss">
.program-selector-dialog {
  .q-dialog__backdrop {
    backdrop-filter: blur(5px);
    transition: backdrop-filter 0.3s ease;
    background-color: rgba(0, 0, 0, 0.3);
  }
}

.program-selector-card {
  background: #f8fafd;
  max-width: 1400px !important;
  margin: 2rem auto;
  height: calc(100% - 4rem);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);

  .header-container {
    padding: 1.5rem 2.5rem 1rem;
    border-bottom: 1px solid #e8edf3;

    .search-container {
      max-width: 550px;
      margin: 0 auto;

      .search-input {
        border-radius: 10px;

        .q-field__control {
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
          &:hover {
            box-shadow: 0 3px 10px rgba(0, 0, 0, 0.07);
          }
        }
      }
    }
  }

  .category-tabs-container {
    padding: 0 1.5rem;
    overflow-x: auto;

    .category-tabs {
      display: flex;
      justify-content: flex-start;
      min-width: max-content;
      padding: 0.5rem 0;

      .category-tab {
        padding: 0.75rem 1.25rem;
        margin-right: 0.5rem;
        cursor: pointer;
        position: relative;
        user-select: none;
        transition: all 0.25s ease-in-out;
        border-radius: 8px;

        .category-name {
          font-size: 0.8rem;
          font-weight: 500;
          color: #5c6f82;
          letter-spacing: 0.3px;
          transition: color 0.25s ease, font-weight 0.25s ease;
          display: block;
          text-align: center;
        }

        .category-indicator {
          position: absolute;
          bottom: -1px;
          left: 15%;
          width: 70%;
          height: 3px;
          border-radius: 3px;
          transform: scaleX(0);
          transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1), background-color 0.3s ease;
          background: var(--program-category-color, #bdbdbd);
        }

        &:hover:not(.active) {
          background-color: rgba(92, 111, 130, 0.05);
          .category-name {
            color: #2c3e50;
          }
        }

        &.active {
          .category-name {
            color: var(--program-category-color, #1d1d1d);
            font-weight: 600;
          }

          .category-indicator {
            transform: scaleX(1);
            background: var(--program-category-color, #bdbdbd);
          }
        }
      }
    }
  }

  .programs-container {
    padding: 1.5rem 2.5rem;
    flex: 1;
    overflow-y: auto;

    .program-item-wrapper {
      display: flex;
      padding-bottom: 1.25rem;
    }

    .program-item {
      --program-category-color: #bdbdbd;
      --program-category-color-light: rgba(189, 189, 189, 0.1);

      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 1.5rem 0.75rem;
      cursor: pointer;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      border-radius: 14px;
      background: white;
      box-shadow: 0 3px 8px rgba(156, 176, 199, 0.15);
      height: 100%;
      width: 100%;
      position: relative;
      overflow: hidden;
      border: 1px solid #eef2f7;

      &:hover {
        transform: translateY(-5px);
        box-shadow: 0 6px 18px rgba(156, 176, 199, 0.25);
        border-color: var(--program-category-color-light);
        background-color: var(--program-category-color-light);

        .program-icon {
          transform: scale(1.15);
        }
      }

      .program-icon-container {
        display: flex;
        justify-content: center;
        align-items: center;
        width: 55px;
        height: 55px;
        border-radius: 14px;
        background-color: #f5f8fc;
        margin-bottom: 1rem;
        transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

        .program-icon {
          transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
        }
      }

      .program-title {
        font-size: 0.85rem;
        font-weight: 600;
        text-align: center;
        color: #34495e;
        margin-bottom: 0.3rem;
        line-height: 1.3;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
        text-overflow: ellipsis;
        min-height: calc(1.3em * 2);
        word-break: break-word;
      }

      .program-category {
        font-size: 0.7rem;
        font-weight: 500;
        text-align: center;
        opacity: 0.9;
        margin-top: auto;
        padding-top: 0.25rem;
      }
    }
  }

  .empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 4rem 1rem;
    text-align: center;
    opacity: 0.7;
    flex: 1;

    .empty-title {
      font-size: 1.1rem;
      font-weight: 500;
      color: #5c6f82;
      margin-top: 1.25rem;
    }

    .empty-subtitle {
      font-size: 0.9rem;
      color: #8a9bab;
      margin-top: 0.5rem;
      max-width: 300px;
    }
  }
}
</style>
