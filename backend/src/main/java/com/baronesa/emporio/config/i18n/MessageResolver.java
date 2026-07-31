package com.baronesa.emporio.config.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MessageResolver {

    private final MessageSource messageSource;

    public String getMessage(String key) {
        return messageSource.getMessage(key, null, getCurrentLocale());
    }

    public String getMessage(String key, Object[] args) {
        return messageSource.getMessage(key, args, getCurrentLocale());
    }

    public String getMessage(String key, Object[] args, String defaultMessage) {
        return messageSource.getMessage(key, args, defaultMessage, getCurrentLocale());
    }

    public String getMessage(String key, Object[] args, Locale locale) {
        return messageSource.getMessage(key, args, locale);
    }

    public String getMessage(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }

    public String getMessage(String key, Object arg, Locale locale) {
        if (arg == null) {
            return getMessage(key, locale);
        }
        return getMessage(key, new Object[]{arg}, locale);
    }

    private Locale getCurrentLocale() {
        return LocaleContextHolder.getLocale();
    }

    public String getCurrentLanguage() {
        return getCurrentLocale().getLanguage();
    }

    public String getCurrentCountry() {
        return getCurrentLocale().getCountry();
    }

    /**
     * Método utilitário para facilitar a chamada passando uma chave e um argumento único.
     * Se o argumento for null, chama o método padrão sem argumentos.
     * Exemplo de uso para mensagens parametrizadas em arquivos .properties:
     * recebimento.error.create=Erro ao criar recebimento: {0}
     */
    public String getMessage(String key, Object arg) {
        if (arg == null) {
            return getMessage(key);
        }
        return getMessage(key, new Object[]{arg});
    }
}
