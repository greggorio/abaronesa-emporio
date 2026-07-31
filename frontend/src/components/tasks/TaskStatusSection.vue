<template>
  <div class="status-section q-mb-md">
    <div class="row justify-between items-center q-mb-sm">
      <div class="text-caption" style="color: #8a8c8c">Status</div>
    </div>
    {{ listName }}
  </div>
</template>

<script setup>
const props = defineProps({
  listName: {
    type: String,
    default: "",
  },
});

const emit = defineEmits(["toggle-complete"]);
</script>
