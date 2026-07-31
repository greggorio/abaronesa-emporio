<template>
  <div class="metric-card" :class="`metric-card--${color}`">
    <div class="metric-card__content">
      <div class="metric-card__header">
        <span class="metric-card__label">{{ label }}</span>
        <q-icon
          v-if="icon"
          :name="icon"
          size="20px"
          class="metric-card__icon"
        />
      </div>
      <div class="metric-card__value">{{ value }}</div>
      <div v-if="subtitle" class="metric-card__subtitle">
        {{ subtitle }}
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  label: {
    type: String,
    required: true,
  },
  value: {
    type: [String, Number],
    required: true,
  },
  subtitle: {
    type: String,
    default: '',
  },
  icon: {
    type: String,
    default: '',
  },
  color: {
    type: String,
    default: 'primary',
    validator: (value) => ['primary', 'secondary', 'positive', 'warning', 'negative', 'info'].includes(value),
  },
});
</script>

<style scoped>
.metric-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(107, 62, 38, 0.08);
  padding: 16px 20px;
  transition: all 0.3s ease;
  border-left: 4px solid;
  height: 100%;
  min-height: 100px;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(107, 62, 38, 0.12);
}

.metric-card--primary {
  border-left-color: #6B3E26;
}

.metric-card--secondary {
  border-left-color: #C67C48;
}

.metric-card--positive {
  border-left-color: #B5854C;
}

.metric-card--warning {
  border-left-color: #E6A157;
}

.metric-card--negative {
  border-left-color: #D65A31;
}

.metric-card--info {
  border-left-color: #8B7355;
}

.metric-card__content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.metric-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.metric-card__label {
  font-size: 0.875rem;
  font-weight: 500;
  color: #8B7355;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.metric-card__icon {
  color: #C67C48;
}

.metric-card__value {
  font-size: 1.75rem;
  font-weight: 700;
  color: #6B3E26;
  line-height: 1.2;
}

.metric-card__subtitle {
  font-size: 0.813rem;
  color: #8B7355;
  font-weight: 400;
}
</style>
