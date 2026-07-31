package com.baronesa.website.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

@Service
public class I18nService {

    private final MessageSource messageSource;
    private final LocaleResolver localeResolver;

    public I18nService(MessageSource messageSource, LocaleResolver localeResolver) {
        this.messageSource = messageSource;
        this.localeResolver = localeResolver;
    }

    public String getMessage(String code, HttpServletRequest request, Object... args) {
        Locale locale = localeResolver.resolveLocale(request);
        return messageSource.getMessage(code, args, locale);
    }

    public String getMessage(String code, Locale locale, Object... args) {
        return messageSource.getMessage(code, args, locale);
    }
}
