<template>
  <q-card
    flat
    class="metric-card validade-card cursor-pointer"
    :class="statusClass"
    role="button"
    tabindex="0"
    @click="handleClick"
    @keyup.enter="handleClick"
    @keyup.space="handleClick"
  >
    <div class="validade-card-content">
      <div class="metric-line" :title="`${title}: ${count}`">
        <span class="metric-text">{{ title }}: {{ count }}</span>
      </div>
      <q-icon :name="icon" :color="color" size="xs" />
    </div>
  </q-card>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  title: {
    type: String,
    required: true
  },
  count: {
    type: [Number, String],
    required: true
  },
  status: {
    type: String,
    required: true
  },
  icon: {
    type: String,
    required: true
  },
  color: {
    type: String,
    required: true
  }
});

const emit = defineEmits(['click']);

const statusClass = computed(() => {
  const normalized = props.status.toLowerCase().replace(/_/g, '-');
  return `validade-card--${normalized}`;
});

const handleClick = () => {
  emit('click', props.status);
};
</script>

<style scoped>
.metric-card {
  background: white;
  border-radius: 8px;
  padding: 6px 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  width: 100%;
  min-height: 40px;
  display: flex;
  align-items: center;
}

.validade-card {
  transition: all 0.2s ease;
}

.validade-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.validade-card-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
}

.metric-line {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.metric-text {
  font-size: 0.8em;
  color: #2A1F1B;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.02em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.validade-card--vencido {
  border-left: 4px solid #d65a31;
}

.validade-card--critico {
  border-left: 4px solid #d65a31;
}

.validade-card--atencao {
  border-left: 4px solid #e6a157;
}

.validade-card--sem-vida-util {
  border-left: 4px solid #8b7355;
}

.validade-card--ok {
  border-left: 4px solid #b5854c;
}

.validade-card--total {
  border-left: 4px solid #6B3E26;
}
</style>
