package com.baronesa.emporio.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobRegistry {

    private final BirthdayNotificationService birthdayNotificationService;
    private final EventNotificationService eventNotificationService;
    private final TranslationJobService translationJobService;
    private final ThemeTranslationSeedService themeTranslationSeedService;
    private final VoucherExcedenteJobService voucherExcedenteJobService;
    private final ProductSignageJobService productSignageJobService;
    private final SignageVideoCleanupService signageVideoCleanupService;

    /**
     * Executa o job pela chave e retorna registros afetados.
     */
    public ExecutionResult run(String key) {
        switch (key) {
            case "BIRTHDAY_PRE" -> {
                int count = birthdayNotificationService.sendBirthdayNotifications("PRE", null);
                return new ExecutionResult(count, "Birthday PRE executado");
            }
            case "BIRTHDAY_DAY" -> {
                int count = birthdayNotificationService.sendBirthdayNotifications("DAY", null);
                return new ExecutionResult(count, "Birthday DAY executado");
            }
            case "EVENT_PRE" -> {
                int count = eventNotificationService.sendEventNotifications("PRE", null);
                return new ExecutionResult(count, "Evento PRE executado");
            }
            case "EVENT_DAY" -> {
                int count = eventNotificationService.sendEventNotifications("DAY", null);
                return new ExecutionResult(count, "Evento DAY executado");
            }
            case "TRANSLATION_SYNC" -> {
                int count = translationJobService.processPendingTranslations();
                return new ExecutionResult(count, "Traducoes processadas");
            }
            case "THEME_TRANSLATION_SEED" -> {
                int count = themeTranslationSeedService.seedTranslationsForActiveTheme();
                return new ExecutionResult(count, "Seed de traduções do tema ativo executado");
            }
            case "VOUCHER_EXCEDENTE" -> {
                var result = voucherExcedenteJobService.executar();
                return new ExecutionResult(result.criados(), result.message());
            }
            case "PRODUCT_SIGNAGE" -> {
                int count = productSignageJobService.executar();
                return new ExecutionResult(count, "Product signage executado: " + count + " produtos elegíveis");
            }
            case "SIGNAGE_VIDEO_CLEANUP" -> {
                int deleted = signageVideoCleanupService.cleanupOldVideos();
                return new ExecutionResult(deleted, "Limpeza de vídeos executada: " + deleted + " arquivo(s) removido(s)");
            }
            default -> throw new IllegalArgumentException("Job key não suportada: " + key);
        }
    }

    public record ExecutionResult(int recordsAffected, String message) {}
}
