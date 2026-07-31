package com.baronesa.website.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.Locale;

@Configuration
public class I18nConfig implements WebMvcConfigurer {

    @Bean
    public MessageSource messageSource(
            @Value("${spring.messages.basename:messages}") String basename,
            @Value("${spring.messages.encoding:UTF-8}") String encoding,
            @Value("${spring.messages.cache-duration:3600}") int cacheSeconds
    ) {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames(StringUtils.tokenizeToStringArray(basename, ","));
        messageSource.setDefaultEncoding(encoding);
        messageSource.setCacheSeconds(cacheSeconds);
        messageSource.setUseCodeAsDefaultMessage(true);
        return messageSource;
    }

    @Bean
    public LocaleResolver localeResolver(@Value("${app.default-locale:pt-BR}") String defaultLocaleTag) {
        Locale fallback = Locale.forLanguageTag(defaultLocaleTag);
        return new QueryParamLocaleResolver(fallback);
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        interceptor.setIgnoreInvalidLocale(true);
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }

    private static final class QueryParamLocaleResolver implements LocaleResolver {
        private static final String OVERRIDE_ATTRIBUTE = QueryParamLocaleResolver.class.getName() + ".OVERRIDE_LOCALE";
        private final Locale fallback;

        private QueryParamLocaleResolver(Locale fallback) {
            this.fallback = fallback;
        }

        @Override
        public Locale resolveLocale(HttpServletRequest request) {
            Object override = request.getAttribute(OVERRIDE_ATTRIBUTE);
            if (override instanceof Locale locale) {
                return locale;
            }

            String param = request.getParameter("lang");
            if (StringUtils.hasText(param)) {
                Locale requested = Locale.forLanguageTag(param);
                if (requested != null && StringUtils.hasText(requested.getLanguage())) {
                    return requested;
                }
            }

            Locale requestLocale = request.getLocale();
            return requestLocale != null ? requestLocale : fallback;
        }

        @Override
        public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
            if (locale == null) {
                request.removeAttribute(OVERRIDE_ATTRIBUTE);
            } else {
                request.setAttribute(OVERRIDE_ATTRIBUTE, locale);
            }
        }
    }
}
