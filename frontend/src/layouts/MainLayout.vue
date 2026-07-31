<template>
  <q-layout view="lHh Lpr lFf">
    <HeaderPage />
    <SideNavigation />

    <q-page-container>
      <router-view />
    </q-page-container>
  </q-layout>
</template>

<script setup>
import HeaderPage from "components/HeaderPage.vue";
import SideNavigation from "components/SideNavigation.vue";

defineOptions({
  name: "MainLayout",
});
</script>
