export const validateEmail = (val) =>
  /^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$/.test(val) || t("errors.invalidEmail");

export const validatePhone = (val) =>
  /^\(\d{2}\) \d{5}-\d{4}$/.test(val) || t("errors.invalidPhone");

export const requiredField = (val) =>
  (val !== null && val !== undefined && val !== "") ||
  t("errors.requiredField");
