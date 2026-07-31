<template>
  <div class="row q-col-gutter-md">
    <div class="col-12 col-md-7">
      <div class="text-h6 text-primary q-mb-md">Produtos com Maior Divergência</div>
      <q-card flat bordered style="background-color: #f5f1ed">
        <div style="max-height: 300px; overflow-y: auto">
          <q-list separator dense>
            <q-item v-for="(produto, i) in produtosDivergentes" :key="i" class="q-py-sm">
              <q-item-section avatar>
                <q-avatar size="xs">
                  <img :src="`https://picsum.photos/seed/${i + 30}/100/100`" />
                </q-avatar>
              </q-item-section>
              <q-item-section>
                <q-item-label class="text-weight-medium text-subtitle2">{{ produto.descricao_produto }}</q-item-label>
                <q-item-label caption class="text-caption">Código: {{ produto.codigo_produto }}</q-item-label>
              </q-item-section>
              <q-item-section side>
                <div class="row items-center">
                  <div class="col-auto q-pr-xs text-right">
                    <div class="text-caption">Sistema: {{ produto.quantidade_contada }}</div>
                    <div class="text-caption">Contagem: {{ getUltimaContagem(produto) }}</div>
                  </div>
                  <q-badge dense :color="produto.divergencia < 0 ? 'negative' : 'positive'" class="q-py-xs q-px-sm text-caption">
                    {{ produto.divergencia }}%
                  </q-badge>
                </div>
              </q-item-section>
            </q-item>
          </q-list>
        </div>
      </q-card>
    </div>

    <div class="col-12 col-md-5">
      <div class="text-h6 text-primary q-mb-md">Análise de Divergências</div>
      <q-card flat bordered class="bg-[#f5f1ed] q-pa-md">
        <div style="height: 300px; position: relative" class="flex flex-center text-primary text-weight-medium">
          [Gráfico de Divergências por Categoria]
        </div>
        <q-list class="q-mt-md">
          <q-item>
            <q-item-section>
              <div class="row justify-between q-col-gutter-md">
                <div class="col-auto">
                  <q-badge color="negative" class="q-pa-xs">
                    <q-icon name="arrow_downward" size="xs" />
                  </q-badge>
                  <span class="q-ml-xs">Itens faltantes: 42</span>
                </div>
                <div class="col-auto">
                  <q-badge color="positive" class="q-pa-xs">
                    <q-icon name="arrow_upward" size="xs" />
                  </q-badge>
                  <span class="q-ml-xs">Itens excedentes: 18</span>
                </div>
              </div>
            </q-item-section>
          </q-item>
        </q-list>
      </q-card>
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";

// Declara que o componente espera uma prop chamada "produtos"
const props = defineProps({
  produtos: {
    type: Array,
    required: true,
  },
});

// Funções para contagens - exatamente como implementado no componente que funciona
const getContagens = (row) => {
  if (row.contagens && Array.isArray(row.contagens) && row.contagens.length > 0) {
    const total = row.contagens.length;
    return row.contagens.map((contagem, index) => {
      return {
        number: total - index,
        value: contagem.quantidade_contada || 0,
      };
    });
  }

  // Se não tiver contagens reais, retorna um array vazio
  return [];
};

const getUltimaContagem = (row) => {
  const contagens = getContagens(row);
  return contagens.length > 0 ? contagens[0].value : "-";
};

// Computed que retorna os top 20 produtos com mais divergência em valor absoluto
const produtosDivergentes = computed(() => {
  return [...props.produtos]
    .filter((produto) => produto.divergencia !== 0 && produto.divergencia !== null)
    .sort((a, b) => Math.abs(b.divergencia) - Math.abs(a.divergencia))
    .slice(0, 20);
});
</script>
