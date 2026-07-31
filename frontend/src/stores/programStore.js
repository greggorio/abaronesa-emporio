import { defineStore } from "pinia";

export const useProgramStore = defineStore("program", {
  state: () => ({
    programTitle: "",
    programIcon: "", // novo estado
  }),

  actions: {
    setProgramTitle(title) {
      this.programTitle = title;
    },
    setProgramIcon(icon) {
      this.programIcon = icon;
    },
  },

  getters: {
    getProgramTitle: (state) => state.programTitle,
    getProgramIcon: (state) => state.programIcon,
  },
});
