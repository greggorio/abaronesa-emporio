<template>
  <div class="row q-col-gutter-md q-mb-md justify-between">
    <div class="col-12 col-sm-6 col-md-4 col-lg-2">
      <q-card class="bg-white full-height shadow-1" style="border-radius: 12px">
        <q-card-section class="q-pa-md">
          <div class="row items-center">
            <div class="col-8">
              <div class="text-h5 text-secondary text-weight-bold" :class="estatisticas.valor_total > 0 ? 'text-green' : 'text-red'">
                {{ formatCurrency(estatisticas.valor_total) }}
              </div>
              <div class="text-subtitle2 text-grey-8">Valor</div>
            </div>
            <div class="col-4 flex justify-end">
              <q-avatar rounded color="secondary" text-color="white" icon="attach_money" size="48px" />
            </div>
          </div>
        </q-card-section>
      </q-card>
    </div>
    <div class="col-12 col-sm-6 col-md-4 col-lg-2">
      <q-card class="bg-white full-height shadow-1" style="border-radius: 12px">
        <q-card-section class="q-pa-md">
          <div class="row items-center">
            <div class="col-8">
              <div class="text-h5 text-secondary text-weight-bold">{{ estatisticas.progresso }}%</div>
              <div class="text-subtitle2 text-grey-8">Executado</div>
            </div>
            <div class="col-4 flex justify-end">
              <q-avatar rounded color="secondary" text-color="white" icon="percent" size="48px" />
            </div>
          </div>
        </q-card-section>
      </q-card>
    </div>
    <div class="col-12 col-sm-6 col-md-4 col-lg-2">
      <q-card class="bg-white full-height shadow-1" style="border-radius: 12px">
        <q-card-section class="q-pa-md">
          <div class="row items-center">
            <div class="col-8">
              <div class="text-h5 text-secondary text-weight-bold">{{ estatisticas.total_itens }}</div>
              <div class="text-subtitle2 text-grey-8">Total de itens</div>
            </div>
            <div class="col-4 flex justify-end">
              <q-avatar rounded color="secondary" text-color="white" icon="o_pin" size="48px" />
            </div>
          </div>
        </q-card-section>
      </q-card>
    </div>
    <div class="col-12 col-sm-6 col-md-4 col-lg-2">
      <q-card class="bg-white full-height shadow-1" style="border-radius: 12px">
        <q-card-section class="q-pa-md">
          <div class="row items-center">
            <div class="col-8">
              <div class="text-h5 text-orange text-weight-bold">{{ estatisticas.divergencias }}</div>
              <div class="text-subtitle2 text-grey-8">Divergências</div>
            </div>
            <div class="col-4 flex justify-end">
              <q-avatar rounded color="orange" text-color="white" icon="warning" size="48px" />
            </div>
          </div>
        </q-card-section>
      </q-card>
    </div>
    <div class="col-12 col-sm-6 col-md-4 col-lg-2">
      <q-card class="bg-white full-height shadow-1" style="border-radius: 12px">
        <q-card-section class="q-pa-md">
          <div class="row items-center">
            <div class="col-8">
              <div class="text-h5 text-secondary text-weight-bold">{{ estatisticas.num_contagens }}</div>
              <div class="text-subtitle2 text-grey-8">Contagens</div>
            </div>
            <div class="col-4 flex justify-end">
              <q-avatar rounded color="secondary" text-color="white" icon="fact_check" size="48px" />
            </div>
          </div>
        </q-card-section>
      </q-card>
    </div>
    <div class="col-12 col-sm-6 col-md-4 col-lg-2">
      <q-card class="bg-white full-height shadow-1" style="border-radius: 12px">
        <q-card-section class="q-pa-md">
          <div class="row items-center">
            <div class="col-8">
              <div class="text-h5 text-secondary text-weight-bold">{{ estatisticas.usuarios_ativos }}</div>
              <div class="text-subtitle2 text-grey-8">Usuários Ativos</div>
            </div>
            <div class="col-4 flex justify-end">
              <q-avatar rounded color="secondary" text-color="white" icon="group" size="48px" />
            </div>
          </div>
        </q-card-section>
      </q-card>
    </div>
  </div>
</template>

<script setup>
import { defineProps } from "vue";

const props = defineProps({
  estatisticas: {
    type: Object,
    required: true,
  },
});

const formatCurrency = (value) => {
  const numberValue = Number(value) || 0;
  return (
    "R$ " +
    numberValue
      .toFixed(2)
      .replace(".", ",")
      .replace(/(\d)(?=(\d{3})+(?!\d))/g, "$1.")
  );
};
</script>
