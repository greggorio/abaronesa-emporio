import { ref, computed } from "vue";
import { useI18n } from "vue-i18n";
import * as validators from "@/utils/validators";

export const useFormValidation = (fieldDefinitions) => {
  const { t } = useI18n();
  const errors = ref({});

  const getValidationRules = (field) => {
    const rules = [];

    if (field.validations) {
      field.validations.forEach((validation) => {
        switch (validation) {
          case "required":
            rules.push(validators.requiredField);
            break;
          case "email":
            rules.push(validators.validateEmail);
            break;
          case "phone":
            rules.push(validators.validatePhone);
            break;
          default:
            console.warn(t("warnings.unknownValidation", { validation }));
        }
      });
    }

    return rules;
  };

  const validateAll = async (formData) => {
    errors.value = {};
    let isValid = true;

    for (const field of fieldDefinitions.value) {
      const value = formData[field.name];
      const rules = getValidationRules(field);

      for (const rule of rules) {
        const result = await rule(value);
        if (typeof result === "string") {
          errors.value[field.name] = result;
          isValid = false;
          break;
        }
      }
    }

    return isValid;
  };

  return {
    errors,
    validateAll,
  };
};
